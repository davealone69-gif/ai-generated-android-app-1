package com.example.droidcraft

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectPressAndRelease
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.*
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

// Constants for audio synthesis
private const val SAMPLE_RATE = 44100 // samples per second
private const val BITS_PER_SAMPLE = 16
private const val MAX_VOLUME = Short.MAX_VALUE.toFloat() // Max value for 16-bit PCM (Short ranges from -32768 to 32767)

/**
 * Helper function to convert a MIDI note number to its corresponding frequency in Hz.
 * The standard A4 (MIDI note 69) is 440 Hz.
 * Formula: f = f0 * 2^((midiNote - 69)/12)
 */
private fun midiNoteToFrequency(midiNote: Int): Float {
    return 440.0f * (2.0f.pow((midiNote - 69) / 12.0f))
}

/**
 * Custom Synthesizer class responsible for generating and playing audio using AudioTrack.
 * It supports polyphonic sine wave generation.
 */
class Synthesizer {
    private var audioTrack: AudioTrack? = null
    // Coroutine scope for background audio processing, uses Dispatchers.Default for CPU-bound work
    private var scope = CoroutineScope(Dispatchers.Default)
    private var playbackJob: Job? = null

    // Mutable state map to keep track of currently active MIDI notes and their current phase.
    // The phase is used to ensure smooth waveform generation across buffer writes.
    private val activeNotes = mutableStateMapOf<Int, Float>()

    /**
     * Initializes AudioTrack and starts the background coroutine for continuous audio generation.
     */
    fun start() {
        // Calculate the minimum buffer size required for AudioTrack for 16-bit mono PCM.
        val bufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        // Build and initialize AudioTrack
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA) // Indicate media playback usage
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT) // 16-bit PCM sample format
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO) // Mono channel
                    .build()
            )
            .setBufferSizeInBytes(bufferSize) // Set the calculated buffer size
            .setMode(AudioTrack.MODE_STREAM) // Stream mode for continuous data writing
            .build()

        audioTrack?.play() // Start the AudioTrack playback

        // Launch a coroutine to continuously generate and write audio samples to AudioTrack
        playbackJob = scope.launch {
            // Audio buffer to hold generated samples (Short is 2 bytes, so bufferSize / 2 shorts)
            val audioBuffer = ShortArray(bufferSize / 2)

            while (isActive) { // Loop as long as the coroutine is active
                audioBuffer.fill(0) // Clear the buffer for mixing new samples

                // Take a snapshot of active notes to avoid concurrent modification issues
                val notesToPlay = activeNotes.toMap()

                if (notesToPlay.isNotEmpty()) {
                    // Generate samples for each point in the audio buffer
                    for (i in audioBuffer.indices) {
                        var mixedSample = 0f
                        notesToPlay.forEach { (midiNote, currentPhase) ->
                            val frequency = midiNoteToFrequency(midiNote)
                            // Generate a sine wave sample for the current note
                            val sample = sin(currentPhase + (2 * PI * frequency * (i.toFloat() / SAMPLE_RATE)))
                            mixedSample += sample
                        }
                        // Normalize the mixed sample by dividing by the number of active notes
                        // This helps prevent clipping when multiple notes play simultaneously, but can reduce overall volume.
                        mixedSample /= notesToPlay.size.toFloat()
                        // Clamp the sample value to the valid range [-1, 1]
                        mixedSample = mixedSample.coerceIn(-1f, 1f)

                        // Convert the float sample to a 16-bit short PCM value
                        audioBuffer[i] = (mixedSample * MAX_VOLUME).toShort()
                    }

                    // Update the phase for each active note for the next buffer generation
                    // This ensures continuity of the waveform.
                    notesToPlay.forEach { (midiNote, currentPhase) ->
                        val frequency = midiNoteToFrequency(midiNote)
                        // Calculate the new phase based on the number of samples just generated
                        val newPhase = (currentPhase + (2 * PI * frequency * (audioBuffer.size.toFloat() / SAMPLE_RATE))).toFloat()
                        activeNotes[midiNote] = newPhase % (2 * PI).toFloat() // Wrap phase around 0 to 2PI
                    }
                }

                // Write the generated audio buffer to the AudioTrack
                audioTrack?.write(audioBuffer, 0, audioBuffer.size)
            }
        }
    }

    /**
     * Stops the audio playback, cancels the background coroutine, and releases AudioTrack resources.
     */
    fun stop() {
        playbackJob?.cancel() // Cancel the coroutine responsible for playback
        playbackJob = null
        audioTrack?.stop()     // Stop the AudioTrack
        audioTrack?.release()  // Release AudioTrack resources
        audioTrack = null
        activeNotes.clear()    // Clear any active notes
        scope.cancel()         // Cancel the coroutine scope itself
    }

    /**
     * Adds a note to the list of active notes to be played. If the note is already active, nothing happens.
     */
    fun playNote(midiNote: Int) {
        if (!activeNotes.containsKey(midiNote)) {
            activeNotes[midiNote] = 0f // Start the note with an initial phase of 0
        }
    }

    /**
     * Removes a note from the list of active notes, stopping its playback.
     */
    fun stopNote(midiNote: Int) {
        activeNotes.remove(midiNote)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Apply Material 3 theme to the app content
            MaterialTheme {
                MainAppScreen()
            }
        }
    }
}

@Composable
fun MainAppScreen() {
    // Remember the Synthesizer instance across recompositions.
    // It's created once and reused.
    val synthesizer = remember { Synthesizer() }

    // Use DisposableEffect to manage the Synthesizer's lifecycle with the Composable's lifecycle.
    // The synthesizer starts when MainAppScreen enters composition and stops when it leaves.
    DisposableEffect(Unit) {
        synthesizer.start() // Start audio processing
        onDispose {
            synthesizer.stop() // Stop and release resources
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.DarkGray) // Dark background for the app
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "DroidCraft Piano",
            style = MaterialTheme.typography.headlineMedium, // Use Material3 typography
            color = Color.White,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        PianoKeyboard(synthesizer)
    }
}

@Composable
fun PianoKeyboard(synthesizer: Synthesizer) {
    // Define the MIDI notes for the white keys.
    // Covers two octaves: C3 to C5.
    val whiteMidiNotes = listOf(
        48, 50, 52, // C3, D3, E3
        53, 55, 57, 59, // F3, G3, A3, B3
        60, 62, 64, // C4, D4, E4
        65, 67, 69, 71, // F4, G4, A4, B4
        72 // C5
    )

    // Map a white key's MIDI note to its associated black key's MIDI note.
    // Notes E and B do not have black keys directly above them.
    val blackKeyMapping = mapOf(
        48 to 49, // C3 -> C#3
        50 to 51, // D3 -> D#3
        53 to 54, // F3 -> F#3
        55 to 56, // G3 -> G#3
        57 to 58, // A3 -> A#3

        60 to 61, // C4 -> C#4
        62 to 63, // D4 -> D#4
        65 to 66, // F4 -> F#4
        67 to 68, // G4 -> G#4
        69 to 70  // A4 -> A#4
    )

    val keyWidth = 60.dp       // Standard width for white keys
    val keyHeight = 200.dp     // Standard height for keys
    val blackKeyWidth = keyWidth * 0.6f  // Black keys are narrower
    val blackKeyHeight = keyHeight * 0.6f // Black keys are shorter

    // Use a Box to layer the white keys (bottom) and black keys (top)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(keyHeight)
            .padding(horizontal = 8.dp) // Horizontal padding for the entire keyboard
            .background(Color.Transparent) // Make the Box background transparent
    ) {
        // Layer 1: White Keys - laid out in a horizontal Row
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(0.dp) // No visible spacing between white keys
        ) {
            whiteMidiNotes.forEach { midiNote ->
                WhiteKey(
                    midiNote = midiNote,
                    width = keyWidth,
                    height = keyHeight,
                    synthesizer = synthesizer
                )
            }
        }

        // Layer 2: Black Keys - positioned individually using offsets
        var currentXOffset = 0.dp // Tracks the current X position for placing keys
        whiteMidiNotes.forEachIndexed { index, whiteMidiNote ->
            val blackMidiNote = blackKeyMapping[whiteMidiNote]

            // If a black key is associated with this white key, place it
            if (blackMidiNote != null) {
                // Calculate the X offset for the black key.
                // It's typically positioned to cover the right half of the current white key
                // and the left half of the next white key.
                // (keyWidth - blackKeyWidth / 2) places its left edge so its center aligns with the gap.
                val blackKeyOffsetX = currentXOffset + keyWidth - (blackKeyWidth / 2)

                Box(
                    modifier = Modifier
                        .offset(x = blackKeyOffsetX, y = 0.dp) // Position the black key
                        .zIndex(1f) // Ensure black keys are drawn on top of white keys
                ) {
                    BlackKey(
                        midiNote = blackMidiNote,
                        width = blackKeyWidth,
                        height = blackKeyHeight,
                        synthesizer = synthesizer
                    )
                }
            }
            // Advance the X offset by the width of the white key for the next iteration
            currentXOffset += keyWidth
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class) // Required for using Surface without an onClick, when combined with pointerInput
@Composable
fun WhiteKey(
    midiNote: Int,
    width: Dp,
    height: Dp,
    synthesizer: Synthesizer
) {
    // State to track if the key is currently being pressed for visual feedback
    val isPressed = remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .width(width)
            .height(height)
            .border(1.dp, Color.Black) // Black border for key separation
            .pointerInput(midiNote) { // Use pointerInput to detect press and release gestures
                detectPressAndRelease(
                    onPress = {
                        isPressed.value = true // Update visual state
                        synthesizer.playNote(midiNote) // Start playing the note
                    },
                    onRelease = {
                        isPressed.value = false // Update visual state
                        synthesizer.stopNote(midiNote) // Stop playing the note
                    }
                )
            },
        color = if (isPressed.value) Color.LightGray else Color.White, // Change color when pressed
        shape = MaterialTheme.shapes.small // Slightly rounded corners for the key
    ) {
        // Optional: display the MIDI note number or frequency for debugging purposes
        // Text(text = midiNote.toString(), modifier = Modifier.align(Alignment.BottomCenter), color = Color.Black)
    }
}

@OptIn(ExperimentalMaterial3Api::class) // Required for using Surface without an onClick, when combined with pointerInput
@Composable
fun BlackKey(
    midiNote: Int,
    width: Dp,
    height: Dp,
    synthesizer: Synthesizer,
    modifier: Modifier = Modifier // Allows additional modifiers to be passed for positioning
) {
    // State to track if the key is currently being pressed for visual feedback
    val isPressed = remember { mutableStateOf(false) }

    Surface(
        modifier = modifier // Apply any external modifiers (e.g., offset for positioning)
            .width(width)
            .height(height)
            .border(1.dp, Color.Black) // Black border for key separation
            .pointerInput(midiNote) { // Use pointerInput to detect press and release gestures
                detectPressAndRelease(
                    onPress = {
                        isPressed.value = true // Update visual state
                        synthesizer.playNote(midiNote) // Start playing the note
                    },
                    onRelease = {
                        isPressed.value = false // Update visual state
                        synthesizer.stopNote(midiNote) // Stop playing the note
                    }
                )
            },
        color = if (isPressed.value) Color.DarkGray else Color.Black, // Change color when pressed
        shape = MaterialTheme.shapes.small // Slightly rounded corners for the key
    ) {
        // Optional: display the MIDI note number or frequency for debugging purposes
        // Text(text = midiNote.toString(), modifier = Modifier.align(Alignment.BottomCenter), color = Color.White)
    }
}