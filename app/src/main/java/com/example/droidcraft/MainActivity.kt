package com.example.droidcraft

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.*
import kotlin.math.sin
import kotlin.math.PI
import kotlin.math.pow

// Constants for audio synthesis
const val SAMPLE_RATE = 44100 // Samples per second
const val NUM_CHANNELS = 1 // Mono
const val BITS_PER_SAMPLE = 16 // 16-bit PCM
val BUFFER_SIZE: Int = AudioTrack.getMinBufferSize(
    SAMPLE_RATE,
    AudioFormat.CHANNEL_OUT_MONO,
    AudioFormat.ENCODING_PCM_16BIT
)

/**
 * Helper function to calculate frequency from MIDI note number.
 * A4 (MIDI note 69) is set to 440 Hz.
 */
fun midiNoteToFrequency(midiNote: Int): Float {
    return (440.0 * 2.0.pow((midiNote - 69) / 12.0)).toFloat()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Apply a basic MaterialTheme for consistent UI styling.
            // In a larger app, you'd typically define a custom theme.
            MaterialTheme {
                PianoAppScreen()
            }
        }
    }
}

/**
 * Manages the AudioTrack and sound synthesis logic.
 * Encapsulates the audio playback details, allowing Composables to trigger notes easily.
 */
class SoundSynthesizer(private val coroutineScope: CoroutineScope) {
    private var audioTrack: AudioTrack? = null
    private var currentPlaybackJob: Job? = null

    init {
        // Initialize AudioTrack upon instantiation.
        // MODE_STREAM is used for continuous writing of audio data.
        audioTrack = AudioTrack(
            AudioManager.STREAM_MUSIC, // Use the music stream type
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            BUFFER_SIZE,
            AudioTrack.MODE_STREAM
        ).apply {
            play() // Start the audio track playback loop, ready to receive data
        }
    }

    /**
     * Plays a single note with a given frequency for a specified duration.
     * Cancels any previously playing note to maintain monophonic playback (only one note at a time).
     */
    fun playNote(frequency: Float, durationMs: Long = 500) {
        // Ensure that the AudioTrack is not null and is ready for playback
        audioTrack?.let { track ->
            // Cancel any ongoing sound generation to make it monophonic
            currentPlaybackJob?.cancel()

            // Launch a new coroutine for sound generation to avoid blocking the UI thread
            currentPlaybackJob = coroutineScope.launch(Dispatchers.Default) {
                // Calculate the number of samples needed for the desired duration
                val numSamples = (SAMPLE_RATE * durationMs / 1000).toInt()
                val audioBuffer = ShortArray(numSamples)
                // Set amplitude, reducing it slightly to prevent clipping and for a softer sound
                val amplitude = Short.MAX_VALUE.toFloat() * 0.5f

                // Generate sine wave samples
                for (i in 0 until numSamples) {
                    val time = i.toFloat() / SAMPLE_RATE
                    audioBuffer[i] = (amplitude * sin(2 * PI * frequency * time)).toInt().toShort()
                }

                // Write the generated samples to the AudioTrack.
                // This call is blocking until the data is written to the AudioTrack's internal buffer.
                track.write(audioBuffer, 0, numSamples)
            }
        }
    }

    /**
     * Releases the AudioTrack resources. This method MUST be called when the synthesizer
     * is no longer needed (e.g., when the Composable leaves the composition)
     * to prevent resource leaks.
     */
    fun release() {
        currentPlaybackJob?.cancel() // Cancel any ongoing sound generation coroutine
        audioTrack?.apply {
            stop() // Stop playback
            release() // Release native resources associated with the AudioTrack
        }
        audioTrack = null
    }
}

@Composable
fun PianoAppScreen() {
    // Obtain a CoroutineScope tied to the lifecycle of this Composable.
    // This is crucial for managing background tasks like sound generation.
    val coroutineScope = rememberCoroutineScope()
    
    // Create and remember the SoundSynthesizer instance.
    // It will be initialized once and retained across recompositions.
    val synthesizer = remember {
        SoundSynthesizer(coroutineScope)
    }

    // Use DisposableEffect to manage the lifecycle of the SoundSynthesizer.
    // The onDispose block ensures that `synthesizer.release()` is called
    // when PianoAppScreen leaves the composition, preventing resource leaks.
    DisposableEffect(synthesizer) {
        onDispose {
            synthesizer.release()
        }
    }

    // Define the set of piano notes (white keys for simplicity) and their corresponding frequencies.
    val pianoNotes = remember {
        listOf(
            "C4" to midiNoteToFrequency(60),
            "D4" to midiNoteToFrequency(62),
            "E4" to midiNoteToFrequency(64),
            "F4" to midiNoteToFrequency(65),
            "G4" to midiNoteToFrequency(67),
            "A4" to midiNoteToFrequency(69),
            "B4" to midiNoteToFrequency(71),
            "C5" to midiNoteToFrequency(72)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background) // Use app's background color
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "DroidCraft Piano",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(32.dp))

        // Card component to visually group and style the piano keys area.
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp) // Fixed height for the keyboard layout
                .padding(horizontal = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.LightGray),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp), // Padding inside the card to space keys from edges
                horizontalArrangement = Arrangement.SpaceAround, // Distribute keys evenly
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Iterate through the defined piano notes to create clickable PianoKey Composables.
                pianoNotes.forEach { (noteName, frequency) ->
                    PianoKey(noteName = noteName) {
                        synthesizer.playNote(frequency) // Play the note when a key is tapped
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Tap a key to play a note!",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

/**
 * A Composable representing a single piano key.
 * It's styled as a white key and displays the note name.
 */
@Composable
fun PianoKey(noteName: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(48.dp) // Fixed width for a standard-looking key
            .fillMaxHeight(0.9f) // Keys fill most of the height of their parent Row
            .padding(4.dp) // Internal padding around each key
            .clickable(onClick = onClick), // Make the entire card respond to clicks
        colors = CardDefaults.cardColors(containerColor = Color.White), // White keys
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = MaterialTheme.shapes.small // Slightly rounded corners for keys
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter // Position the note name at the bottom
        ) {
            Text(
                text = noteName,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Black,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp) // Padding for text from bottom edge
            )
        }
    }
}