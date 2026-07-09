package com.example.droidcraft

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

// --- Sound Synthesis Engine ---

private const val SAMPLE_RATE = 44100 // Standard sample rate
private const val BUFFER_SIZE_SAMPLES = 2048 // Number of samples per audio buffer

/**
 * Manages the generation and playback of audio for the piano.
 * Uses a single AudioTrack and a background coroutine to mix active NoteVoices.
 */
class AudioSynthesizer(private val coroutineScope: CoroutineScope) {
    private val audioTrack: AudioTrack
    private val activeNotes = ConcurrentHashMap<Int, NoteVoice>() // Midi Note -> NoteVoice
    private val synthJob = MutableStateFlow<Job?>(null)

    init {
        val minBufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        // Ensure buffer is large enough, at least double BUFFER_SIZE_SAMPLES for smooth streaming
        val actualBufferSize = maxOf(minBufferSize, BUFFER_SIZE_SAMPLES * 2)

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(actualBufferSize * 2) // short is 2 bytes
            .setMode(AudioTrack.MODE_STREAM)
            .build()
    }

    /** Starts the audio synthesis and playback loop. */
    fun start() {
        if (synthJob.value?.isActive != true) {
            audioTrack.play()
            synthJob.value = coroutineScope.launch(Dispatchers.Default) {
                val audioBuffer = ShortArray(BUFFER_SIZE_SAMPLES)
                while (isActive) { // Loop while coroutine is active
                    for (i in 0 until BUFFER_SIZE_SAMPLES) {
                        var sampleValue = 0.0
                        activeNotes.forEach { (_, voice) ->
                            sampleValue += voice.getSample()
                        }
                        // Simple clipping and normalization
                        sampleValue = sampleValue.coerceIn(-1.0, 1.0) * Short.MAX_VALUE
                        audioBuffer[i] = sampleValue.toShort()
                    }
                    audioTrack.write(audioBuffer, 0, BUFFER_SIZE_SAMPLES)
                }
            }
        }
    }

    /** Stops the audio synthesis and pauses the AudioTrack. */
    fun stop() {
        synthJob.value?.cancel()
        synthJob.value = null
        if (audioTrack.playState == AudioTrack.PLAYSTATE_PLAYING) {
            audioTrack.pause()
            audioTrack.flush() // Clear any pending data
        }
        activeNotes.clear() // Stop all active notes
    }

    /** Releases all resources held by the AudioSynthesizer. */
    fun release() {
        stop()
        if (audioTrack.state != AudioTrack.STATE_UNINITIALIZED) {
            audioTrack.release()
        }
    }

    /** Triggers a note to start playing. */
    fun noteOn(midiNote: Int) {
        // Only add if not already playing to avoid resetting phase of an active note
        activeNotes.computeIfAbsent(midiNote) { NoteVoice(it) }
    }

    /** Triggers a note to stop playing. */
    fun noteOff(midiNote: Int) {
        activeNotes.remove(midiNote)
    }
}

/**
 * Represents a single oscillating voice (note).
 * Generates a sine wave based on its MIDI note frequency.
 */
class NoteVoice(private val midiNote: Int) {
    // Calculate frequency from MIDI note number (A4 = 440 Hz = MIDI 69)
    private val frequency = 440.0 * 2.0.pow((midiNote - 69) / 12.0)
    private var currentPhase = 0.0 // Current phase (0.0 to 1.0) of the sine wave

    /**
     * Generates the next sample for this note.
     * Each call advances the internal phase of the oscillator.
     */
    fun getSample(): Double {
        val sample = sin(2 * PI * currentPhase)
        currentPhase += frequency / SAMPLE_RATE // Increment phase based on frequency and sample rate
        // Keep phase within 0-1 to prevent large numbers and maintain precision
        if (currentPhase >= 1.0) currentPhase -= 1.0
        return sample * 0.2 // Apply a volume adjustment
    }
}

// --- Compose UI Components ---

/**
 * Custom pointer input detector for press and release events.
 */
suspend fun PointerInputScope.detectPressAndRelease(
    onPress: (Offset) -> Unit = {},
    onRelease: () -> Unit = {}
) {
    awaitEachGesture {
        val down = awaitFirstDown() // Wait for the first pointer to go down
        onPress(down.position)
        waitForUpOrCancellation() // Wait for all pointers to go up or for the gesture to be cancelled
        onRelease()
    }
}

/**
 * A single piano key, handling touch input and triggering sound.
 */
@Composable
fun Key(
    midiNote: Int,
    isBlack: Boolean,
    synthesizer: AudioSynthesizer,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }

    val keyColor = if (isPressed) {
        if (isBlack) Color(0xFF333333) else Color(0xFFDDDDDD) // Darker when pressed
    } else {
        if (isBlack) Color.Black else Color.White
    }
    val borderColor = if (isBlack) Color.DarkGray else Color.Gray

    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(keyColor, shape = RoundedCornerShape(4.dp))
            .border(1.dp, borderColor, shape = RoundedCornerShape(4.dp))
            .pointerInput(midiNote) { // key by midiNote to recompose only when midiNote changes
                detectPressAndRelease(
                    onPress = {
                        isPressed = true
                        synthesizer.noteOn(midiNote)
                    },
                    onRelease = {
                        isPressed = false
                        synthesizer.noteOff(midiNote)
                    }
                )
            }
    ) {
        // Optional: uncomment to show midi note on key for debugging
        /*
        Text(
            text = "$midiNote",
            color = if (isBlack) Color.White else Color.Black,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 4.dp)
        )
        */
    }
}

/**
 * The main piano keyboard composable, arranging white and black keys.
 */
@Composable
fun PianoKeyboard(synthesizer: AudioSynthesizer) {
    // Define MIDI notes for the white keys (C4 to C5)
    val whiteKeysMidi = remember { listOf(60, 62, 64, 65, 67, 69, 71, 72) } // C4, D4, E4, F4, G4, A4, B4, C5
    // Define MIDI notes for the black keys, with -1 placeholders for missing black keys (E-F, B-C)
    val blackKeysMidi = remember { listOf(61, 63, -1, 66, 68, 70) } // C#4, D#4, F#4, G#4, A#4

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .height(220.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.DarkGray)
            .padding(all = 4.dp)
    ) {
        val totalWidth = maxWidth
        val whiteKeyWidth = totalWidth / whiteKeysMidi.size
        val blackKeyWidth = whiteKeyWidth * 0.6f // Black keys are narrower than white keys

        // White Keys Layer
        Row(modifier = Modifier.fillMaxSize()) {
            whiteKeysMidi.forEach { midiNote ->
                Key(
                    midiNote = midiNote,
                    isBlack = false,
                    synthesizer = synthesizer,
                    modifier = Modifier
                        .width(whiteKeyWidth)
                        .padding(horizontal = 1.dp) // Small gap between white keys
                )
            }
        }

        // Black Keys Layer (positioned absolutely on top of white keys)
        // Position offsets are calculated relative to the start of the entire keyboard.
        blackKeysMidi.forEachIndexed { index, midiNote ->
            if (midiNote != -1) {
                // Determine the x-offset for each black key
                val offsetFraction = when (midiNote) {
                    61 -> 0.7f // C#4, between C4 and D4
                    63 -> 1.7f // D#4, between D4 and E4
                    66 -> 3.7f // F#4, between F4 and G4
                    68 -> 4.7f // G#4, between G4 and A4
                    70 -> 5.7f // A#4, between A4 and B4
                    else -> 0f // Should not happen for valid black keys
                }
                val xOffset = (whiteKeyWidth * offsetFraction) - (blackKeyWidth / 2)

                // The Key composable for black keys
                Box(
                    modifier = Modifier
                        .offset(x = xOffset, y = 0.dp) // Position black key
                        .zIndex(1f) // Ensure black keys render above white keys
                ) {
                    Key(
                        midiNote = midiNote,
                        isBlack = true,
                        synthesizer = synthesizer,
                        modifier = Modifier
                            .width(blackKeyWidth)
                            .height(120.dp) // Black keys are shorter
                            .padding(horizontal = 1.dp) // Small gap
                    )
                }
            }
        }
    }
}

// --- Main App Screen ---

@Composable
fun MainAppScreen() {
    // Remember a CoroutineScope tied to the Composable's lifecycle
    val coroutineScope = rememberCoroutineScope()
    // Remember and initialize the AudioSynthesizer
    val audioSynthesizer = remember { AudioSynthesizer(coroutineScope) }

    // Manage the AudioSynthesizer's lifecycle with DisposableEffect
    DisposableEffect(Unit) {
        audioSynthesizer.start() // Start the synthesis thread when composable enters composition
        onDispose {
            audioSynthesizer.release() // Release resources when composable leaves composition
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "DroidCraft Piano",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // The piano keyboard UI
        PianoKeyboard(audioSynthesizer)

        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "Tap keys to play!",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// --- MainActivity ---

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Apply Material Design 3 theme (using default for simplicity)
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainAppScreen()
                }
            }
        }
    }
}