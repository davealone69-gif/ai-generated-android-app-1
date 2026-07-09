package com.example.droidcraft

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.sin
import kotlin.math.PI

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MainAppScreen()
        }
    }
}

// This class handles the actual sound generation using AudioTrack.
// It generates a simple sine wave with an ADSR-like envelope for each note.
class SoundGenerator(private val scope: CoroutineScope) {

    private val sampleRate = 44100 // Samples per second
    private val bufferSize = AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    private var audioTrack: AudioTrack? = null

    init {
        // Initialize AudioTrack for streaming audio.
        // It starts in a playing state, ready to receive data.
        audioTrack = AudioTrack(
            AudioManager.STREAM_MUSIC, // Use music stream
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO, // Mono channel
            AudioFormat.ENCODING_PCM_16BIT, // 16-bit PCM encoding
            bufferSize,
            AudioTrack.MODE_STREAM // Stream mode for writing chunks of data
        )
        audioTrack?.play()
    }

    /**
     * Generates a sine wave for a given frequency and plays it for a specified duration.
     * The sound includes a basic attack/release envelope to reduce clicks.
     *
     * @param frequency The frequency of the note in Hz.
     * @param durationMillis The duration the note should play in milliseconds.
     */
    fun playNote(frequency: Double, durationMillis: Long = 250) {
        scope.launch(Dispatchers.Default) {
            // Calculate the number of samples needed for the given duration
            val numSamples = (sampleRate * (durationMillis / 1000.0)).toInt()
            val samples = ShortArray(numSamples)

            // Define attack and release phases for the sound envelope
            val envelopeAttackSamples = (0.05 * sampleRate).toInt() // 50ms attack
            val envelopeReleaseSamples = (0.15 * sampleRate).toInt() // 150ms release

            for (i in 0 until numSamples) {
                // Apply a simple ADSR-like envelope
                val amplitudeFactor = when {
                    i < envelopeAttackSamples -> i.toFloat() / envelopeAttackSamples // Attack phase
                    i > numSamples - envelopeReleaseSamples -> (numSamples - i).toFloat() / envelopeReleaseSamples // Release phase
                    else -> 1f // Sustain phase
                }

                // Generate the sine wave sample and scale it to 16-bit PCM range
                val sample = (amplitudeFactor * Short.MAX_VALUE * sin(2 * PI * frequency * i / sampleRate)).toInt().toShort()
                samples[i] = sample
            }

            // Write the generated samples to the AudioTrack
            // This will play the sound.
            audioTrack?.write(samples, 0, samples.size)
        }
    }

    /**
     * Releases the AudioTrack resources. Should be called when the generator is no longer needed.
     */
    fun release() {
        audioTrack?.stop() // Stop playback
        audioTrack?.release() // Release native resources
        audioTrack = null
    }
}

// --- UI Components ---
@Composable
fun MainAppScreen() {
    // Obtain a CoroutineScope tied to the Composable's lifecycle
    val coroutineScope = rememberCoroutineScope()
    // Create and remember our SoundGenerator, tying its lifecycle to the Composable
    val soundGenerator = remember { SoundGenerator(coroutineScope) }

    // Use DisposableEffect to ensure SoundGenerator resources are released when the Composable leaves the composition
    DisposableEffect(Unit) {
        onDispose {
            soundGenerator.release()
        }
    }

    // Define the piano notes with their names and corresponding frequencies
    val pianoNotes = remember {
        listOf(
            "C4" to 261.63,
            "D4" to 293.66,
            "E4" to 329.63,
            "F4" to 349.23,
            "G4" to 392.00,
            "A4" to 440.00,
            "B4" to 493.88,
            "C5" to 523.25
        )
    }

    // Main layout for the app screen
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
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
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(32.dp))

            // Layout for the piano keys
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Iterate through our defined notes and create a PianoKey for each
                pianoNotes.forEach { (noteName, frequency) ->
                    PianoKey(
                        noteName = noteName,
                        onClick = { soundGenerator.playNote(frequency) }
                    )
                }
            }
        }
    }
}

/**
 * A Composable that represents a single piano key.
 * It's a clickable Card displaying the note name.
 *
 * @param noteName The musical note name to display (e.g., "C4").
 * @param onClick Lambda to be invoked when the key is pressed.
 */
@Composable
fun PianoKey(noteName: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(50.dp) // Fixed width for each key
            .height(150.dp) // Fixed height for each key
            .padding(4.dp)
            .clickable(onClick = onClick), // Make the card clickable
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom // Align text to the bottom of the key
        ) {
            Text(
                text = noteName,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 8.dp) // Padding for the note name
            )
        }
    }
}