package com.example.droidcraft

import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

// Constants for audio synthesis
private const val SAMPLE_RATE = 44100 // samples per second
private const val DURATION_MS = 200 // milliseconds for each note
private const val VOLUME = 0.5f // Master volume, 0.0 to 1.0

/**
 * A simple sine wave synthesizer that uses AudioTrack to play generated tones.
 * This class should be remembered and its resources released with DisposableEffect.
 */
class SimpleSineSynthesizer {
    private var audioTrack: AudioTrack? = null
    private val bufferSize = AudioTrack.getMinBufferSize(
        SAMPLE_RATE,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    ) * 2 // Double buffer size for robustness
    private val audioBuffer = ByteBuffer.allocateDirect(bufferSize).order(ByteOrder.LITTLE_ENDIAN)

    init {
        initAudioTrack()
    }

    private fun initAudioTrack() {
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        // Start playback
        audioTrack?.play()
    }

    /**
     * Generates a sine wave for the given frequency and plays it using AudioTrack.
     * The generation happens on a background coroutine to avoid blocking the UI.
     * @param frequency The frequency of the note in Hz.
     * @param scope The CoroutineScope to launch the sound generation task.
     */
    fun playNote(frequency: Double, scope: CoroutineScope) {
        scope.launch(Dispatchers.Default) {
            val numSamples = (DURATION_MS * SAMPLE_RATE / 1000).toInt()
            val samples = ShortArray(numSamples)
            for (i in 0 until numSamples) {
                // Generate sine wave sample and scale it to 16-bit PCM range
                val sample = (sin(2 * Math.PI * frequency * i / SAMPLE_RATE) * Short.MAX_VALUE * VOLUME).toInt().toShort()
                samples[i] = sample
            }

            // Write samples to the AudioTrack buffer
            audioBuffer.clear()
            audioBuffer.asShortBuffer().put(samples)
            
            // Ensure audioTrack is not null and is playing before writing
            audioTrack?.apply {
                if (playState == AudioTrack.PLAYSTATE_PLAYING) {
                    // Write size in bytes (2 bytes per Short)
                    write(audioBuffer, numSamples * 2, AudioTrack.WRITE_BLOCKING)
                }
            }
        }
    }

    /**
     * Releases the AudioTrack resources. Should be called when the synthesizer is no longer needed.
     */
    fun release() {
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MainAppScreen()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class) // For TopAppBar
@Composable
fun MainAppScreen() {
    // Remember the synthesizer instance across recompositions
    val synthesizer = remember { SimpleSineSynthesizer() }
    // Coroutine scope for launching sound generation tasks
    val scope = rememberCoroutineScope() 

    // Release synthesizer resources when the Composable leaves the composition
    DisposableEffect(Unit) {
        onDispose {
            synthesizer.release()
        }
    }

    // Frequencies for a simple piano scale with some sharps
    val noteFrequencies = remember {
        listOf(
            "C4" to 261.63, // C4
            "C#4" to 277.18, // C#4
            "D4" to 293.66, // D4
            "D#4" to 311.13, // D#4
            "E4" to 329.63, // E4
            "F4" to 349.23, // F4
            "F#4" to 369.99, // F#4
            "G4" to 392.00, // G4
            "G#4" to 415.30, // G#4
            "A4" to 440.00, // A4
            "A#4" to 466.16, // A#4
            "B4" to 493.88, // B4
            "C5" to 523.25  // C5
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("DroidCraft Piano", fontWeight = FontWeight.Bold) })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Tap a key to play a note!",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Piano Keys Layout
            // For simplicity, keys are arranged in a single row.
            // Black keys are visually shorter.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.Bottom // Align keys to the bottom
            ) {
                noteFrequencies.forEach { (noteName, frequency) ->
                    PianoKey(noteName) {
                        synthesizer.playNote(frequency, scope)
                    }
                }
            }
        }
    }
}

/**
 * A Composable representing a single piano key.
 * @param noteName The name of the note (e.g., "C4", "C#4").
 * @param onClick Lambda to be invoked when the key is clicked.
 */
@Composable
fun RowScope.PianoKey(noteName: String, onClick: () -> Unit) {
    val isBlackKey = noteName.contains("#")
    val keyColor = if (isBlackKey) Color.Black else Color.White
    val textColor = if (isBlackKey) Color.White else Color.Black

    Card(
        modifier = Modifier
            .weight(1f) // Distribute keys evenly in the row
            .height(if (isBlackKey) 120.dp else 180.dp) // Black keys are shorter
            .padding(horizontal = 2.dp, vertical = 4.dp) // Spacing between keys
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = keyColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        border = if (!isBlackKey) BorderStroke(1.dp, Color.LightGray) else null
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Text(
                text = noteName,
                color = textColor,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    }
}