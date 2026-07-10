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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.zIndex
import kotlinx.coroutines.*
import kotlin.math.sin
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    // Using a fixed thread pool for audio generation to manage concurrency.
    // Ensures audio operations don't block the UI thread and are handled sequentially.
    private val audioExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MainAppScreen(audioExecutor)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioExecutor.shutdownNow() // Ensure threads are stopped when activity is destroyed
    }
}

// Constants for audio generation
private const val SAMPLE_RATE = 44100 // Samples per second
private const val DURATION_MS_SHORT = 200 // Duration of a single key press in milliseconds
private const val MAX_VOLUME = 32767 // Max value for 16-bit PCM (Short.MAX_VALUE)

// Data class to hold note information
data class Note(val name: String, val frequency: Double, val isSharp: Boolean = false)

// Define an octave of notes with their frequencies (approximate for demonstration)
private val notes = listOf(
    Note("C4", 261.63),
    Note("C#4", 277.18, true),
    Note("D4", 293.66),
    Note("D#4", 311.13, true),
    Note("E4", 329.63),
    Note("F4", 349.23),
    Note("F#4", 369.99, true),
    Note("G4", 392.00),
    Note("G#4", 415.30, true),
    Note("A4", 440.00),
    Note("A#4", 466.16, true),
    Note("B4", 493.88),
    Note("C5", 523.25)
)

@Composable
fun MainAppScreen(audioExecutor: ExecutorService) {
    val coroutineScope = rememberCoroutineScope()
    // Holds a reference to the currently playing AudioTrack, allowing it to be stopped/released
    val currentAudioTrackRef = remember { mutableStateOf<AudioTrack?>(null) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "DroidCraft Piano",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineLarge
            )
            Spacer(modifier = Modifier.height(32.dp))

            // Main Box for the keyboard layout, acting as a Z-stack for white and black keys
            Box(
                modifier = Modifier
                    .width(700.dp) // Fixed width for the keyboard for consistent sizing
                    .height(200.dp) // Fixed height for the keyboard area
                    .background(Color.DarkGray) // Background visible between keys
            ) {
                // Layer 1: White keys
                Row(modifier = Modifier.fillMaxSize()) {
                    val whiteNotes = notes.filter { !it.isSharp }
                    whiteNotes.forEach { note ->
                        PianoKey(
                            note = note,
                            keyColor = Color.White,
                            modifier = Modifier
                                .weight(1f) // Distribute width equally among white keys
                                .fillMaxHeight(),
                            onNotePlayed = { frequency ->
                                coroutineScope.launch {
                                    audioExecutor.execute {
                                        // Stop and release any previously playing track to prevent overlapping sounds
                                        currentAudioTrackRef.value?.stop()
                                        currentAudioTrackRef.value?.release()
                                        currentAudioTrackRef.value = null

                                        // Play the new tone and store its AudioTrack reference
                                        val newTrack = playTone(frequency, DURATION_MS_SHORT)
                                        currentAudioTrackRef.value = newTrack
                                    }
                                }
                            }
                        )
                    }
                }

                // Layer 2: Black keys (positioned absolutely on top of white keys)
                val whiteKeyCount = notes.filter { !it.isSharp }.size
                val whiteKeyWidthDp = 700.dp / whiteKeyCount // Calculate individual white key width
                val blackKeyWidthDp = whiteKeyWidthDp * 0.6f // Black keys are narrower
                val blackKeyHeightDp = 200.dp * 0.6f // Black keys are shorter

                // Iterate through all notes to place black keys at their calculated positions
                notes.forEach { note ->
                    if (note.isSharp) {
                        // Calculate X offset for each black key. These values are empirical
                        // to place black keys over the gaps between white keys.
                        val xOffset: Dp = when (note.name) {
                            "C#4" -> (whiteKeyWidthDp * 0.75f) - (blackKeyWidthDp / 2) // Between C4 and D4
                            "D#4" -> (whiteKeyWidthDp * 1.75f) - (blackKeyWidthDp / 2) // Between D4 and E4
                            "F#4" -> (whiteKeyWidthDp * 3.75f) - (blackKeyWidthDp / 2) // Between F4 and G4
                            "G#4" -> (whiteKeyWidthDp * 4.75f) - (blackKeyWidthDp / 2) // Between G4 and A4
                            "A#4" -> (whiteKeyWidthDp * 5.75f) - (blackKeyWidthDp / 2) // Between A4 and B4
                            else -> 0.dp // Fallback, should not be reached with current notes list
                        }

                        PianoKey(
                            note = note,
                            keyColor = Color.Black,
                            modifier = Modifier
                                .offset(x = xOffset, y = 0.dp) // Position black key
                                .width(blackKeyWidthDp)
                                .height(blackKeyHeightDp)
                                .zIndex(1f), // Ensure black keys are drawn on top of white keys
                            onNotePlayed = { frequency ->
                                coroutineScope.launch {
                                    audioExecutor.execute {
                                        currentAudioTrackRef.value?.stop()
                                        currentAudioTrackRef.value?.release()
                                        currentAudioTrackRef.value = null

                                        val newTrack = playTone(frequency, DURATION_MS_SHORT)
                                        currentAudioTrackRef.value = newTrack
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun PianoKey(
    note: Note,
    keyColor: Color,
    modifier: Modifier = Modifier,
    onNotePlayed: (Double) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Determine the color based on key type and press state
    val pressedColor = if (keyColor == Color.White) Color.LightGray else Color.DarkGray
    val currentColor = if (isPressed) pressedColor else keyColor

    // Borders for keys for visual separation
    val borderWidth = if (keyColor == Color.White) 1.dp else 0.dp // White keys often have borders
    val borderColor = if (keyColor == Color.White) Color.Gray else Color.Transparent

    Box(
        modifier = modifier
            .border(borderWidth, borderColor) // Add border for white keys
            .background(currentColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null // Disable default ripple effect for custom press feedback
            ) {
                onNotePlayed(note.frequency)
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        // Display note name on the key (e.g., "C" for C#, "D" for D# to avoid long text)
        Text(
            text = note.name.first().toString(), // Show just the root note for sharps
            color = if (keyColor == Color.White) Color.Black else Color.White,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(bottom = 4.dp)
        )
    }
}

/**
 * Generates a sine wave tone for the given frequency and duration, then plays it using AudioTrack
 * in MODE_STATIC. Returns the AudioTrack instance.
 * The caller is responsible for stopping and releasing this instance when no longer needed
 * (e.g., when a new note is played).
 */
private fun playTone(frequency: Double, durationMillis: Int): AudioTrack {
    // Calculate number of samples needed for the given duration and sample rate
    val numSamples = (durationMillis * SAMPLE_RATE / 1000).toInt()
    val samples = ShortArray(numSamples)

    // Generate sine wave samples with a simple attack/decay envelope
    for (i in 0 until numSamples) {
        val phase = 2 * Math.PI * i / (SAMPLE_RATE / frequency)
        val amplitude = sin(phase)

        // Simple ADSR-like envelope: attack (0-10%), sustain (10-70%), decay (70-100%)
        val envelope: Float = when {
            i < numSamples * 0.1 -> i / (numSamples * 0.1f) // Attack phase (fade in)
            i > numSamples * 0.7 -> 1 - ((i - numSamples * 0.7f) / (numSamples * 0.3f)) // Decay phase (fade out)
            else -> 1f // Sustain phase (full volume)
        }
        samples[i] = (amplitude * MAX_VOLUME * envelope).toShort()
    }

    // Initialize AudioTrack in MODE_STATIC. The buffer size must be large enough for all samples.
    val audioTrack = AudioTrack(
        AudioManager.STREAM_MUSIC,
        SAMPLE_RATE,
        AudioFormat.CHANNEL_OUT_MONO, // Mono output
        AudioFormat.ENCODING_PCM_16BIT, // 16-bit PCM format
        numSamples * 2, // Buffer size in bytes (2 bytes per Short sample)
        AudioTrack.MODE_STATIC // Static mode: write all data once
    )

    // Write the generated samples to the AudioTrack buffer
    audioTrack.write(samples, 0, numSamples)
    // Start playback
    audioTrack.play()

    return audioTrack
}