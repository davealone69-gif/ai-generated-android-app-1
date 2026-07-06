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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.sin
import kotlin.math.PI

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Use MaterialTheme for basic styling and theming
            MaterialTheme {
                MainAppScreen()
            }
        }
    }
}

// Global constants for audio generation
private const val SAMPLE_RATE = 44100 // Samples per second (Hz)
private const val DURATION_MS = 500 // Milliseconds for note duration
private const val NUM_SAMPLES = (SAMPLE_RATE * DURATION_MS / 1000) // Total samples for duration
private const val MAX_VOLUME = Short.MAX_VALUE.toFloat() // Max amplitude for 16-bit PCM

/**
 * Calculates the frequency for a musical note based on its semitone offset from A4 (440 Hz).
 * A4 is at 0 semitones. C4 is 9 semitones below A4 (semitonesFromA4 = -9).
 *
 * @param semitonesFromA4 The number of semitones from A4. Positive for higher, negative for lower.
 * @return The frequency in Hz.
 */
private fun getFrequency(semitonesFromA4: Int): Double {
    return 440.0 * Math.pow(2.0, semitonesFromA4 / 12.0)
}

@Composable
fun MainAppScreen() {
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
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

        // Piano keyboard layout container
        val pianoWidth = 380.dp // A fixed width for the entire keyboard for easier layout calculations
        val whiteKeyCount = 8
        val whiteKeyTotalWidth: Dp = pianoWidth / whiteKeyCount // Width of each white key 'slot' including padding
        val whiteKeyInnerWidth: Dp = whiteKeyTotalWidth - 4.dp // Actual visible width of a white key
        val blackKeyWidth = whiteKeyInnerWidth * 0.6f
        val blackKeyHeight = 120.dp

        Box(
            modifier = Modifier
                .width(pianoWidth)
                .height(200.dp) // Fixed height for the keyboard
        ) {
            // White keys layout
            Row(modifier = Modifier.fillMaxSize()) {
                val whiteKeyFrequencies = listOf(
                    getFrequency(-9), // C4
                    getFrequency(-7), // D4
                    getFrequency(-5), // E4
                    getFrequency(-4), // F4
                    getFrequency(-2), // G4
                    getFrequency(0),  // A4
                    getFrequency(2),  // B4
                    getFrequency(3)   // C5
                )
                val whiteNoteNames = listOf("C4", "D4", "E4", "F4", "G4", "A4", "B4", "C5")

                whiteKeyFrequencies.forEachIndexed { index, frequency ->
                    PianoKey(
                        modifier = Modifier
                            .weight(1f) // Distribute available width evenly among white keys
                            .padding(horizontal = 2.dp), // Add horizontal padding for visual separation
                        keyColor = Color.White,
                        noteName = whiteNoteNames[index]
                    ) {
                        // Launch audio playback in a background coroutine
                        coroutineScope.launch(Dispatchers.IO) {
                            playNote(frequency)
                        }
                    }
                }
            }

            // Black keys layout, positioned manually using offsets over the white keys
            // C#4 (between C4 and D4)
            Box(
                modifier = Modifier
                    .width(blackKeyWidth)
                    .height(blackKeyHeight)
                    .offset(x = whiteKeyTotalWidth - (blackKeyWidth / 2), y = 0.dp)
                    .align(Alignment.TopStart)
            ) {
                PianoKey(
                    modifier = Modifier.fillMaxSize(),
                    keyColor = Color.Black,
                    noteName = "C#4"
                ) { coroutineScope.launch(Dispatchers.IO) { playNote(getFrequency(-8)) } }
            }

            // D#4 (between D4 and E4)
            Box(
                modifier = Modifier
                    .width(blackKeyWidth)
                    .height(blackKeyHeight)
                    .offset(x = (whiteKeyTotalWidth * 2) - (blackKeyWidth / 2), y = 0.dp)
                    .align(Alignment.TopStart)
            ) {
                PianoKey(
                    modifier = Modifier.fillMaxSize(),
                    keyColor = Color.Black,
                    noteName = "D#4"
                ) { coroutineScope.launch(Dispatchers.IO) { playNote(getFrequency(-6)) } }
            }

            // F#4 (between F4 and G4)
            Box(
                modifier = Modifier
                    .width(blackKeyWidth)
                    .height(blackKeyHeight)
                    .offset(x = (whiteKeyTotalWidth * 4) - (blackKeyWidth / 2), y = 0.dp)
                    .align(Alignment.TopStart)
            ) {
                PianoKey(
                    modifier = Modifier.fillMaxSize(),
                    keyColor = Color.Black,
                    noteName = "F#4"
                ) { coroutineScope.launch(Dispatchers.IO) { playNote(getFrequency(-3)) } }
            }

            // G#4 (between G4 and A4)
            Box(
                modifier = Modifier
                    .width(blackKeyWidth)
                    .height(blackKeyHeight)
                    .offset(x = (whiteKeyTotalWidth * 5) - (blackKeyWidth / 2), y = 0.dp)
                    .align(Alignment.TopStart)
            ) {
                PianoKey(
                    modifier = Modifier.fillMaxSize(),
                    keyColor = Color.Black,
                    noteName = "G#4"
                ) { coroutineScope.launch(Dispatchers.IO) { playNote(getFrequency(-1)) } }
            }

            // A#4 (between A4 and B4)
            Box(
                modifier = Modifier
                    .width(blackKeyWidth)
                    .height(blackKeyHeight)
                    .offset(x = (whiteKeyTotalWidth * 6) - (blackKeyWidth / 2), y = 0.dp)
                    .align(Alignment.TopStart)
            ) {
                PianoKey(
                    modifier = Modifier.fillMaxSize(),
                    keyColor = Color.Black,
                    noteName = "A#4"
                ) { coroutineScope.launch(Dispatchers.IO) { playNote(getFrequency(1)) } }
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "Tap a key to play a note!",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

/**
 * A reusable Composable for a single piano key.
 *
 * @param modifier Modifier to be applied to the key.
 * @param keyColor The color of the key (e.g., Color.White or Color.Black).
 * @param noteName The musical note name to display on the key (e.g., "C4", "C#4").
 * @param onClick Lambda to be invoked when the key is clicked.
 */
@Composable
fun PianoKey(
    modifier: Modifier = Modifier,
    keyColor: Color,
    noteName: String,
    onClick: () -> Unit
) {
    val textColor = if (keyColor == Color.Black) Color.White else Color.Black
    Surface(
        modifier = modifier
            .clickable(onClick = onClick), // Make the key clickable
        color = keyColor,
        shadowElevation = 4.dp,
        // Add a subtle border for visual definition
        border = ButtonDefaults.outlinedButtonBorder.copy(color = Color.DarkGray)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter // Align note name to the bottom center
        ) {
            Text(
                text = noteName,
                color = textColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold, // Make note names more prominent
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    }
}

/**
 * Generates a sine wave for a given frequency and plays it using AudioTrack.
 * The sound plays for a fixed DURATION_MS.
 *
 * @param frequency The desired frequency of the note in Hz.
 */
private fun playNote(frequency: Double) {
    // Determine minimum buffer size for AudioTrack
    val minBufferSize = AudioTrack.getMinBufferSize(
        SAMPLE_RATE,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    val audioTrack = AudioTrack(
        AudioManager.STREAM_MUSIC,      // Use music stream type
        SAMPLE_RATE,                    // Sampling rate
        AudioFormat.CHANNEL_OUT_MONO,   // Mono output
        AudioFormat.ENCODING_PCM_16BIT, // 16-bit PCM audio
        minBufferSize,
        AudioTrack.MODE_STREAM          // Stream mode for continuous writing (though we write once)
    )

    // Generate sine wave samples
    val samples = ShortArray(NUM_SAMPLES)
    for (i in 0 until NUM_SAMPLES) {
        val sample = (sin(2 * PI * i / (SAMPLE_RATE / frequency)) * MAX_VOLUME).toShort()
        samples[i] = sample
    }

    try {
        audioTrack.play() // Start playing
        audioTrack.write(samples, 0, samples.size) // Write the generated samples to the buffer
    } finally {
        audioTrack.stop()    // Stop playback
        audioTrack.release() // Release AudioTrack resources
    }
}