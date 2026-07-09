package com.example.droidcraft

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

// --- Audio Synthesis Constants and Helper Functions ---
private const val SAMPLE_RATE = 44100
private const val DURATION_MS_PER_BUFFER = 10 // Milliseconds of audio generated per buffer write
private const val SAMPLES_PER_BUFFER = (SAMPLE_RATE * DURATION_MS_PER_BUFFER / 1000)
private val BUFFER_SIZE = AudioTrack.getMinBufferSize(
    SAMPLE_RATE,
    AudioFormat.CHANNEL_OUT_MONO,
    AudioFormat.ENCODING_PCM_16BIT
)

/**
 * Converts a MIDI note number to its corresponding frequency in Hz.
 * Standard tuning, A4 (MIDI 69) = 440 Hz.
 */
private fun midiNoteToFrequency(midiNote: Int): Double {
    return 440.0 * Math.pow(2.0, (midiNote - 69) / 12.0)
}

/**
 * Converts a MIDI note number to a human-readable note name (e.g., "C4", "G#5").
 */
private fun midiNoteToNoteName(midiNote: Int): String {
    val noteNames = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    val octave = (midiNote / 12) - 1 // MIDI 0 is C-1, C4 is MIDI 60
    val noteIndex = midiNote % 12
    return "${noteNames[noteIndex]}$octave"
}

/**
 * A simple sine wave synthesizer using AudioTrack.
 * It manages a single AudioTrack and mixes multiple active notes in a background coroutine.
 */
class SimpleSynthesizer {
    private val audioTrack: AudioTrack
    private val activeNotes = mutableSetOf<Int>() // MIDI notes currently playing
    private val synthJob: Job
    private val scope = CoroutineScope(Dispatchers.IO) // Use IO dispatcher for background audio
    private var isRunning = true

    init {
        audioTrack = AudioTrack(
            AudioManager.STREAM_MUSIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            BUFFER_SIZE,
            AudioTrack.MODE_STREAM
        )
        audioTrack.play() // Start the audio track playback thread

        synthJob = scope.launch {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO) // Higher priority for audio thread
            val audioBuffer = ShortArray(SAMPLES_PER_BUFFER)
            val notePhases = mutableMapOf<Int, Double>() // Store phase for each active note

            while (isRunning) {
                // Clear buffer for mixing
                audioBuffer.fill(0)

                val currentActiveNotes = synchronized(activeNotes) { activeNotes.toSet() }

                if (currentActiveNotes.isNotEmpty()) {
                    val maxAmplitude = (Short.MAX_VALUE / currentActiveNotes.size).toDouble() // Simple gain reduction

                    for (midiNote in currentActiveNotes) {
                        val frequency = midiNoteToFrequency(midiNote)
                        val phaseIncrement = 2.0 * PI * frequency / SAMPLE_RATE

                        notePhases.putIfAbsent(midiNote, 0.0) // Initialize phase if not present

                        for (i in 0 until SAMPLES_PER_BUFFER) {
                            val sampleValue = sin(notePhases[midiNote]!!) // Generate sine wave sample
                            // Accumulate into the buffer, applying gain reduction and clamping
                            audioBuffer[i] = (audioBuffer[i] + (sampleValue * maxAmplitude))
                                .coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat())
                                .toShort()
                            notePhases[midiNote] = (notePhases[midiNote]!! + phaseIncrement) % (2.0 * PI) // Update phase
                        }
                    }
                }
                audioTrack.write(audioBuffer, 0, SAMPLES_PER_BUFFER)
            }
        }
    }

    /** Adds a MIDI note to the set of currently playing notes. */
    fun startNote(midiNote: Int) {
        synchronized(activeNotes) {
            activeNotes.add(midiNote)
        }
    }

    /** Removes a MIDI note from the set of currently playing notes. */
    fun stopNote(midiNote: Int) {
        synchronized(activeNotes) {
            activeNotes.remove(midiNote)
        }
    }

    /** Releases audio resources when the synthesizer is no longer needed. */
    fun release() {
        isRunning = false
        synthJob.cancel()
        audioTrack.stop()
        audioTrack.release()
    }
}

// --- MainActivity and Compose UI ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MainAppScreen()
        }
    }
}

@Composable
fun MainAppScreen() {
    // Create and remember the synthesizer instance, tied to the Composable's lifecycle
    val synthesizer = remember { SimpleSynthesizer() }
    val scope = rememberCoroutineScope() // Coroutine scope for launching note actions

    // Dispose synthesizer resources when the Composable leaves the composition
    DisposableEffect(Unit) {
        onDispose {
            synthesizer.release()
        }
    }

    // Piano key dimensions
    val whiteKeyWidth = 48.dp
    val whiteKeyHeight = 200.dp
    val blackKeyWidth = 32.dp
    val blackKeyHeight = 120.dp

    // MIDI notes for a single octave (C4 to B4)
    val whiteKeysMidi = listOf(60, 62, 64, 65, 67, 69, 71) // C4, D4, E4, F4, G4, A4, B4

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "DroidCraft Piano",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Box to stack white and black keys
        Box(
            modifier = Modifier
                .width(whiteKeyWidth * whiteKeysMidi.size) // Total width for all white keys
                .height(whiteKeyHeight)
        ) {
            // White keys layer
            Row(modifier = Modifier.fillMaxSize()) {
                whiteKeysMidi.forEach { midiNote ->
                    PianoKey(
                        midiNote = midiNote,
                        synthesizer = synthesizer,
                        isBlack = false,
                        keyWidth = whiteKeyWidth,
                        keyHeight = whiteKeyHeight,
                        scope = scope
                    )
                }
            }

            // Black keys layer, positioned with offsets
            // C#4 (MIDI 61)
            PianoKey(
                midiNote = 61, synthesizer = synthesizer, isBlack = true,
                keyWidth = blackKeyWidth, keyHeight = blackKeyHeight, scope = scope,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = whiteKeyWidth - blackKeyWidth / 2) // Between C4 and D4
            )
            // D#4 (MIDI 63)
            PianoKey(
                midiNote = 63, synthesizer = synthesizer, isBlack = true,
                keyWidth = blackKeyWidth, keyHeight = blackKeyHeight, scope = scope,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = whiteKeyWidth * 2 - blackKeyWidth / 2) // Between D4 and E4
            )
            // F#4 (MIDI 66)
            PianoKey(
                midiNote = 66, synthesizer = synthesizer, isBlack = true,
                keyWidth = blackKeyWidth, keyHeight = blackKeyHeight, scope = scope,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = whiteKeyWidth * 4 - blackKeyWidth / 2) // Between F4 and G4
            )
            // G#4 (MIDI 68)
            PianoKey(
                midiNote = 68, synthesizer = synthesizer, isBlack = true,
                keyWidth = blackKeyWidth, keyHeight = blackKeyHeight, scope = scope,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = whiteKeyWidth * 5 - blackKeyWidth / 2) // Between G4 and A4
            )
            // A#4 (MIDI 70)
            PianoKey(
                midiNote = 70, synthesizer = synthesizer, isBlack = true,
                keyWidth = blackKeyWidth, keyHeight = blackKeyHeight, scope = scope,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = whiteKeyWidth * 6 - blackKeyWidth / 2) // Between A4 and B4
            )
        }
    }
}

/**
 * Composable for a single piano key, handling visual state and sound playback.
 */
@Composable
fun PianoKey(
    midiNote: Int,
    synthesizer: SimpleSynthesizer,
    isBlack: Boolean,
    keyWidth: Dp,
    keyHeight: Dp,
    scope: CoroutineScope,
    modifier: Modifier = Modifier // Allow external modifiers for positioning
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Start/stop note playback based on press state
    LaunchedEffect(isPressed) {
        if (isPressed) {
            scope.launch { synthesizer.startNote(midiNote) }
        } else {
            scope.launch { synthesizer.stopNote(midiNote) }
        }
    }

    val backgroundColor = if (isBlack) {
        if (isPressed) Color.DarkGray else Color.Black
    } else {
        if (isPressed) Color.LightGray else Color.White
    }
    val borderColor = if (isBlack) Color.Black else Color.Gray

    Box(
        modifier = modifier // Apply incoming modifier for positioning/sizing
            .width(keyWidth)
            .height(keyHeight)
            .padding(horizontal = if (isBlack) 0.dp else 1.dp, vertical = 1.dp) // Visual separation
            .background(backgroundColor, shape = MaterialTheme.shapes.small)
            .border(1.dp, borderColor, shape = MaterialTheme.shapes.small)
            .clickable(
                interactionSource = interactionSource,
                indication = null // Disable default ripple to keep clean piano aesthetic
            ) { /* Clickable is solely for detecting press/release via interactionSource */ },
        contentAlignment = Alignment.BottomCenter
    ) {
        // Display note label for white keys
        if (!isBlack) {
            Text(
                text = midiNoteToNoteName(midiNote),
                color = Color.Black,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
    }
}