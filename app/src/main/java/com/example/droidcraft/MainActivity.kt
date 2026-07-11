package com.example.droidcraft

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Using MaterialTheme defaults. A custom theme could be added if needed.
            MaterialTheme {
                MainAppScreen()
            }
        }
    }
}

/**
 * Data class to hold piano note information.
 * @param label The textual label for the key (e.g., "C4", "C#4").
 * @param frequency The fundamental frequency of the note in Hz.
 * @param isWhite True if it's a white key, false if it's a black key.
 */
data class PianoNote(val label: String, val frequency: Double, val isWhite: Boolean)

@Composable
fun MainAppScreen() {
    val coroutineScope = rememberCoroutineScope()

    // --- Audio Synthesis Constants ---
    val SAMPLE_RATE = 44100 // Hz, standard CD quality sample rate
    val DURATION_SECONDS = 0.5f // Duration a single note press will sustain
    val AMPLITUDE = 32767.0 // Max amplitude for 16-bit PCM signed short

    // --- Piano Key Data ---
    // Define white keys for C4 to C5 octave
    val whiteNotes = remember {
        listOf(
            PianoNote("C4", 261.63, true),
            PianoNote("D4", 293.66, true),
            PianoNote("E4", 329.63, true),
            PianoNote("F4", 349.23, true),
            PianoNote("G4", 392.00, true),
            PianoNote("A4", 440.00, true),
            PianoNote("B4", 493.88, true),
            PianoNote("C5", 523.25, true)
        )
    }

    // Define black keys for C4 to C5 octave
    val blackNotes = remember {
        listOf(
            PianoNote("C#4", 277.18, false), // Between C4 and D4
            PianoNote("D#4", 311.13, false), // Between D4 and E4
            // E-F has no black key
            PianoNote("F#4", 369.99, false), // Between F4 and G4
            PianoNote("G#4", 415.30, false), // Between G4 and A4
            PianoNote("A#4", 466.16, false)  // Between A4 and B4
            // B-C has no black key
        )
    }

    /**
     * Plays a sine wave sound for the given frequency.
     * Uses a new AudioTrack instance for each sound event for simplicity in this single-file example.
     * For true polyphony and better performance, a single AudioTrack with a mixing buffer or a pool
     * of AudioTracks should be managed.
     */
    fun playNote(frequency: Double) {
        coroutineScope.launch(Dispatchers.IO) {
            val numSamples = (SAMPLE_RATE * DURATION_SECONDS).toInt()
            val audioData = ShortArray(numSamples)

            // Generate a simple sine wave
            for (i in 0 until numSamples) {
                val time = i.toFloat() / SAMPLE_RATE
                // Convert sine wave value to 16-bit signed short PCM sample
                val sample = (AMPLITUDE * sin(2 * Math.PI * frequency * time)).toInt().toShort()
                audioData[i] = sample
            }

            // Determine the minimum buffer size required for AudioTrack
            val minBufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            // Ensure the buffer is large enough to hold our generated samples in bytes
            val bufferSizeInBytes = maxOf(minBufferSize, numSamples * Short.SIZE_BYTES)

            var audioTrack: AudioTrack? = null
            try {
                audioTrack = AudioTrack(
                    AudioManager.STREAM_MUSIC,      // Use the music stream type
                    SAMPLE_RATE,                    // Sample rate
                    AudioFormat.CHANNEL_OUT_MONO,   // Mono channel configuration
                    AudioFormat.ENCODING_PCM_16BIT, // 16-bit PCM encoding
                    bufferSizeInBytes,              // Buffer size in bytes
                    AudioTrack.MODE_STREAM          // Streaming mode for playback
                )

                if (audioTrack.state == AudioTrack.STATE_INITIALIZED) {
                    audioTrack.play() // Start playback
                    audioTrack.write(audioData, 0, audioData.size) // Write audio data to the buffer

                    // Keep the AudioTrack playing for the duration of the note, then release it.
                    // This creates a new AudioTrack per press, which is acceptable for a basic demo.
                    delay((DURATION_SECONDS * 1000).toLong())
                }
            } catch (e: Exception) {
                e.printStackTrace() // Log any errors during AudioTrack operation
            } finally {
                // Stop and release the AudioTrack resources to prevent leaks
                audioTrack?.stop()
                audioTrack?.release()
            }
        }
    }


    Scaffold(
        topBar = {
            TopAppBar(title = { Text("DroidCraft Piano", fontWeight = FontWeight.Bold) })
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 8.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            // --- Piano Keys Layout Parameters ---
            val whiteKeyWidth = 60.dp
            val whiteKeyHeight = 250.dp
            val blackKeyWidth = 40.dp
            val blackKeyHeight = 150.dp
            val keySpacing = 2.dp // Spacing between white keys

            // Calculate total width for the keys section
            val totalKeysWidth = (whiteKeyWidth * whiteNotes.size) + (keySpacing * (whiteNotes.size - 1))

            // Main container for the piano keys, aligning them to the bottom-center
            Box(
                modifier = Modifier
                    .fillMaxHeight(0.8f) // Occupy 80% of available vertical space
                    .width(totalKeysWidth)
                    .align(Alignment.BottomCenter)
            ) {
                // --- White Keys Row ---
                Row(
                    modifier = Modifier
                        .fillMaxSize() // Fills the parent Box
                        .zIndex(0f), // Ensures white keys are underneath black keys
                    horizontalArrangement = Arrangement.spacedBy(keySpacing)
                ) {
                    whiteNotes.forEach { note ->
                        val interactionSource = remember { MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()

                        PianoKey(
                            note = note,
                            modifier = Modifier
                                .width(whiteKeyWidth)
                                .height(whiteKeyHeight)
                                .weight(1f), // Distribute white keys evenly within the Row
                            isPressed = isPressed,
                            isWhiteKey = true,
                            interactionSource = interactionSource,
                            onNotePressed = { freq -> playNote(freq) }
                        )
                    }
                }

                // --- Black Keys Layer ---
                // Helper composable to render and position a black key
                @Composable
                fun BoxScope.BlackKeyDisplay(note: PianoNote, offsetX: Dp) {
                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()

                    // Calculate vertical offset so black keys sit on top of white keys
                    val offsetY = whiteKeyHeight - blackKeyHeight

                    PianoKey(
                        note = note,
                        modifier = Modifier
                            .width(blackKeyWidth)
                            .height(blackKeyHeight)
                            .offset(x = offsetX, y = -offsetY) // Position relative to bottom-start of parent Box
                            .align(Alignment.BottomStart) // Reference point for offset
                            .zIndex(1f), // Ensures black keys are drawn on top
                        isPressed = isPressed,
                        isWhiteKey = false,
                        interactionSource = interactionSource,
                        onNotePressed = { freq -> playNote(freq) }
                    )
                }

                // Calculate positions for each black key
                val whiteKeySlotWidth = whiteKeyWidth + keySpacing // Effective width of a white key + its right spacing
                val blackKeyBaseOffset = whiteKeyWidth / 2 - blackKeyWidth / 2 // Offset to center black key over the crack between two white keys

                // C#4 position (after C4, index 0 white key)
                BlackKeyDisplay(
                    note = blackNotes[0],
                    offsetX = blackKeyBaseOffset + (whiteKeySlotWidth * 0) + keySpacing / 2
                )
                // D#4 position (after D4, index 1 white key)
                BlackKeyDisplay(
                    note = blackNotes[1],
                    offsetX = blackKeyBaseOffset + (whiteKeySlotWidth * 1) + keySpacing / 2
                )
                // F#4 position (after F4, index 3 white key)
                BlackKeyDisplay(
                    note = blackNotes[2],
                    offsetX = blackKeyBaseOffset + (whiteKeySlotWidth * 3) + keySpacing / 2
                )
                // G#4 position (after G4, index 4 white key)
                BlackKeyDisplay(
                    note = blackNotes[3],
                    offsetX = blackKeyBaseOffset + (whiteKeySlotWidth * 4) + keySpacing / 2
                )
                // A#4 position (after A4, index 5 white key)
                BlackKeyDisplay(
                    note = blackNotes[4],
                    offsetX = blackKeyBaseOffset + (whiteKeySlotWidth * 5) + keySpacing / 2
                )
            }
        }
    }
}

/**
 * A composable representing a single piano key.
 * Changes visual appearance based on whether it's pressed and if it's a white or black key.
 *
 * @param note The PianoNote data for this key.
 * @param modifier Modifier for layout and size.
 * @param isPressed True if the key is currently being pressed.
 * @param isWhiteKey True if this is a white key, false for a black key.
 * @param interactionSource MutableInteractionSource to detect press gestures.
 * @param onNotePressed Callback function to invoke when the key is pressed, providing its frequency.
 */
@Composable
fun PianoKey(
    note: PianoNote,
    modifier: Modifier = Modifier,
    isPressed: Boolean,
    isWhiteKey: Boolean,
    interactionSource: MutableInteractionSource,
    onNotePressed: (Double) -> Unit
) {
    // Determine background color based on key type and pressed state
    val backgroundColor = when {
        isWhiteKey && isPressed -> Color.LightGray
        isWhiteKey -> Color.White
        !isWhiteKey && isPressed -> Color.DarkGray
        else -> Color.Black
    }
    // Determine border color
    val borderColor = if (isWhiteKey) Color.Black else Color.Gray

    // Define the rounded corner shape for the bottom of the keys
    val keyShape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)

    Box(
        modifier = modifier
            .background(backgroundColor, keyShape)
            .border(1.dp, borderColor, keyShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null, // Disable default visual indication to use custom pressed state
                onClick = { onNotePressed(note.frequency) } // Trigger sound on click
            ),
        contentAlignment = Alignment.BottomCenter // Align text label to the bottom center of the key
    ) {
        Text(
            text = note.label,
            color = if (isWhiteKey) Color.Black else Color.White,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(bottom = 8.dp), // Padding for the label
            fontSize = 10.sp // Smaller font size for key labels
        )
    }
}