package com.example.droidcraft

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import java.util.concurrent.Executors
import kotlin.math.sin
import kotlin.math.PI

// Constants for audio synthesis
private const val SAMPLE_RATE = 44100 // Audio samples per second
private const val DURATION_MS = 300 // Duration of a note in milliseconds
private const val MAX_AMPLITUDE = Short.MAX_VALUE.toFloat() * 0.5f // Max amplitude for 16-bit PCM (half to prevent clipping)

class MainActivity : ComponentActivity() {

    // Using a single-threaded executor to serialize audio playback requests.
    // This helps prevent overlapping notes from creating choppy sound artifacts
    // when using a simple AudioTrack instance for short, distinct notes.
    private val audioExecutor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Apply MaterialTheme to ensure Material Design styling is available for components.
            MaterialTheme {
                MainAppScreen(onPlayNote = { frequency -> playNote(frequency) })
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Ensure the executor is shut down when the activity is destroyed to prevent leaks.
        audioExecutor.shutdownNow()
    }

    /**
     * Generates a sine wave for a given frequency and plays it using AudioTrack.
     * This operation is offloaded to a background thread via the audioExecutor
     * to prevent blocking the UI thread.
     */
    private fun playNote(frequency: Float) {
        audioExecutor.execute {
            val numSamples = (DURATION_MS * SAMPLE_RATE / 1000).toInt()
            val samples = ShortArray(numSamples) // Array to hold 16-bit PCM audio samples

            val twoPiF = 2 * PI * frequency
            for (i in 0 until numSamples) {
                val t = i.toFloat() / SAMPLE_RATE // Time in seconds for the current sample
                // Generate sine wave sample value
                val sampleValue = (MAX_AMPLITUDE * sin(twoPiF * t)).toInt().toShort()
                samples[i] = sampleValue
            }

            // Calculate minimum buffer size required for the AudioTrack
            val minBufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO, // Mono channel configuration
                AudioFormat.ENCODING_PCM_16BIT // 16-bit Pulse Code Modulation encoding
            )

            // Create and configure AudioTrack for playback
            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA) // Indicate media playback
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC) // Content type is music
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBufferSize) // Set the buffer size
                .setMode(AudioTrack.MODE_STREAM) // Use stream mode for writing audio data
                .build()

            try {
                audioTrack.play() // Start audio playback
                // Write the generated samples to the AudioTrack
                audioTrack.write(samples, 0, samples.size)
            } finally {
                // Ensure AudioTrack resources are always released, even if an error occurs during playback.
                audioTrack.stop()
                audioTrack.release()
            }
        }
    }
}

@Composable
fun MainAppScreen(onPlayNote: (Float) -> Unit) {
    // Scaffold provides a basic Material Design visual structure for the app screen.
    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues) // Apply padding from Scaffold
                .padding(16.dp), // Additional padding for content
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "DroidCraft Piano",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            // Row to arrange the piano keys horizontally
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Define frequencies for a simple octave of white keys (C4 to C5)
                // These frequencies are standard for equal temperament tuning, with A4 = 440 Hz.
                val c4 = 261.63f // Middle C
                val d4 = 293.66f
                val e4 = 329.63f
                val f4 = 349.23f
                val g4 = 392.00f
                val a4 = 440.00f
                val b4 = 493.88f
                val c5 = 523.25f // High C

                // Render each piano key
                PianoKey("C4", c4, onPlayNote)
                PianoKey("D4", d4, onPlayNote)
                PianoKey("E4", e4, onPlayNote)
                PianoKey("F4", f4, onPlayNote)
                PianoKey("G4", g4, onPlayNote)
                PianoKey("A4", a4, onPlayNote)
                PianoKey("B4", b4, onPlayNote)
                PianoKey("C5", c5, onPlayNote)
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Tap a key to play a note!",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

/**
 * A Composable function representing a single piano key.
 * It's designed to be used within a [RowScope] for proper weighting.
 */
@Composable
fun RowScope.PianoKey(
    noteName: String,
    frequency: Float,
    onPlayNote: (Float) -> Unit
) {
    // Surface is used for a Material Design-styled clickable area, providing elevation and shape.
    Surface(
        modifier = Modifier
            .weight(1f) // Distribute available width evenly among keys in the Row
            .height(150.dp) // Fixed height for a piano key
            .padding(horizontal = 2.dp, vertical = 4.dp), // Padding around the key for visual separation
        shape = RoundedCornerShape(4.dp), // Slightly rounded corners for a softer look
        color = Color.White, // Default color for a white piano key
        contentColor = Color.Black, // Color for the text displayed on the key
        shadowElevation = 2.dp, // Adds a subtle shadow for visual depth
        onClick = { onPlayNote(frequency) } // Callback triggered when the key is tapped
    ) {
        // Box is used to center the note name text at the bottom of the key.
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Text(
                text = noteName,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp) // Padding to lift the text slightly from the bottom edge
            )
        }
    }
}