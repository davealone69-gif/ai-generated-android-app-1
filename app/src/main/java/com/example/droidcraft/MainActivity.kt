package com.example.droidcraft

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.sin

// --- Constants for Sound Synthesis ---
private const val SAMPLE_RATE = 44100 // samples per second
private const val DURATION_MS = 300 // milliseconds for each note
private const val MAX_AMPLITUDE = Short.MAX_VALUE.toDouble() / 2 // Max value for 16-bit audio, with headroom
private const val SAMPLES_COUNT = (SAMPLE_RATE * DURATION_MS / 1000)

// Pre-defined frequencies for a simple C major scale (C4 to C5)
// Source: https://pages.mtu.edu/~suits/notefreqs.html
private val NOTE_FREQUENCIES = mapOf(
    "C4" to 261.63,
    "D4" to 293.66,
    "E4" to 329.63,
    "F4" to 349.23,
    "G4" to 392.00,
    "A4" to 440.00,
    "B4" to 493.88,
    "C5" to 523.25
)

/**
 * Manages the custom sound synthesis and playback using AudioTrack.
 * This object generates sine waves for specified frequencies.
 */
object PianoSoundManager {

    /**
     * Plays a sine wave note at the given frequency for a fixed duration.
     * The synthesis and playback are performed on a background thread.
     *
     * @param frequency The frequency of the note to play in Hz.
     * @param coroutineScope The CoroutineScope to launch the playback task in.
     */
    fun playNote(frequency: Double, coroutineScope: CoroutineScope) {
        coroutineScope.launch(Dispatchers.Default) {
            // Determine the minimum buffer size required for the AudioTrack
            val minBufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            // Create an AudioTrack instance for playing sound
            val audioTrack = AudioTrack(
                AudioManager.STREAM_MUSIC,      // Use the standard music stream
                SAMPLE_RATE,                    // Sample rate in Hz
                AudioFormat.CHANNEL_OUT_MONO,   // Mono channel configuration
                AudioFormat.ENCODING_PCM_16BIT, // 16-bit PCM format
                minBufferSize,                  // Minimum buffer size
                AudioTrack.MODE_STREAM          // Stream mode for writing data as it's generated
            )

            // Generate sine wave samples
            val samples = ShortArray(SAMPLES_COUNT)
            val twoPiF = 2 * Math.PI * frequency
            for (i in 0 until SAMPLES_COUNT) {
                // Calculate the sample value for a sine wave and convert to Short
                val sample = (MAX_AMPLITUDE * sin(twoPiF * i / SAMPLE_RATE)).toInt().toShort()
                samples[i] = sample
            }

            try {
                // Start playback, write the generated samples, then stop and release
                audioTrack.play()
                audioTrack.write(samples, 0, samples.size)
            } catch (e: IllegalStateException) {
                // Handle cases where AudioTrack might be in an invalid state (e.g., rapidly stopped/started)
                e.printStackTrace()
            } finally {
                // Always ensure AudioTrack is stopped and released to free resources
                audioTrack.stop()
                audioTrack.release()
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Apply Material3 theme to the entire application
            MaterialTheme {
                MainAppScreen()
            }
        }
    }
}

/**
 * The main Composable screen for the DroidCraft Piano app.
 * It sets up the UI and orchestrates note playback.
 */
@Composable
fun MainAppScreen() {
    // Remember a CoroutineScope tied to the lifecycle of this Composable for sound playback
    val coroutineScope = rememberCoroutineScope()

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
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Create a PianoKey for each note in our predefined frequencies map
            NOTE_FREQUENCIES.forEach { (noteName, frequency) ->
                PianoKey(noteName = noteName, frequency = frequency) {
                    PianoSoundManager.playNote(it, coroutineScope)
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "Tap a key to play a note!",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * A Composable that represents a single piano key.
 * It provides visual feedback when pressed and triggers sound playback.
 *
 * @param noteName The display name of the note (e.g., "C4").
 * @param frequency The frequency of the note in Hz.
 * @param onNotePlayed A lambda to be invoked when the key is pressed, providing the note's frequency.
 */
@Composable
fun PianoKey(
    noteName: String,
    frequency: Double,
    onNotePlayed: (Double) -> Unit
) {
    // MutableInteractionSource helps detect press states for custom visual feedback
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Define key colors based on press state
    val keyColor = if (isPressed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isPressed) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = Modifier
            .width(60.dp) // Fixed width for a key
            .height(200.dp) // Fixed height for a key
            .padding(4.dp) // Padding around the key
            .clickable(
                interactionSource = interactionSource,
                indication = null, // Disable default ripple to allow custom press feedback
                onClick = { onNotePlayed(frequency) } // Trigger sound on click
            ),
        shape = RoundedCornerShape(8.dp), // Rounded corners for the key
        color = keyColor, // Apply the determined key color
        shadowElevation = if (isPressed) 2.dp else 4.dp, // Adjust shadow based on press state
        border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp) // Simple border
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter // Position note name at the bottom
        ) {
            Text(
                text = noteName,
                color = textColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
    }
}