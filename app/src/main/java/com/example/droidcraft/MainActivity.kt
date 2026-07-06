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
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.material3.* // Ensure all Material3 components are imported
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Custom Composable theme to wrap the application UI
@Composable
fun DroidCraftTheme(content: @Composable () -> Unit) {
    // A simple Material3 theme.
    // For a more comprehensive theme, you would define custom ColorScheme, Typography, and Shapes.
    // Here, we use the default Material3 color scheme which adapts to light/dark mode.
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme, // Uses default light/dark colors
        typography = MaterialTheme.typography,
        content = content
    )
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Wrap the entire app content with the DroidCraftTheme
            DroidCraftTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainAppScreen()
                }
            }
        }
    }
}

// region: Audio Synthesis Helpers
private const val SAMPLE_RATE = 44100 // Hz
private const val NOTE_DURATION_MS = 200 // Milliseconds for a single note playback

/**
 * Generates a simple sine wave and plays it using AudioTrack.
 * This function creates and releases an AudioTrack for each note,
 * which is simple for a demo but not efficient for polyphonic real-time synthesis.
 *
 * @param frequency The frequency of the note to play in Hz.
 */
private suspend fun playTone(frequency: Double) = withContext(Dispatchers.IO) {
    val numSamples = (NOTE_DURATION_MS * SAMPLE_RATE / 1000)
    val samples = ShortArray(numSamples)
    val amplitude = 32767 // Max 16-bit PCM value

    for (i in 0 until numSamples) {
        val angle = 2.0 * Math.PI * i / SAMPLE_RATE * frequency
        samples[i] = (amplitude * Math.sin(angle)).toInt().toShort()
    }

    val minBufferSize = AudioTrack.getMinBufferSize(
        SAMPLE_RATE,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    // Ensure buffer is large enough for our samples
    val bufferSize = maxOf(minBufferSize, numSamples * 2) // *2 bytes per short

    val audioTrack = AudioTrack(
        AudioManager.STREAM_MUSIC,
        SAMPLE_RATE,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
        bufferSize,
        AudioTrack.MODE_STREAM
    )

    try {
        audioTrack.play()
        audioTrack.write(samples, 0, samples.size)
        // Wait for the note to play out before releasing the AudioTrack.
        // This is important for the sound to be heard fully.
        Thread.sleep(NOTE_DURATION_MS.toLong())
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        audioTrack.stop()
        audioTrack.release()
    }
}

// Map of note names to their fundamental frequencies (C4 to C5 octave)
private val pianoNoteFrequencies = mapOf(
    "C4" to 261.63,
    "C#4" to 277.18,
    "D4" to 293.66,
    "D#4" to 311.13,
    "E4" to 329.63,
    "F4" to 349.23,
    "F#4" to 369.99,
    "G4" to 392.00,
    "G#4" to 415.30,
    "A4" to 440.00,
    "A#4" to 466.16,
    "B4" to 493.88,
    "C5" to 523.25
)
// endregion: Audio Synthesis Helpers

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen() {
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("DroidCraft Piano", fontWeight = FontWeight.Bold) }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Tap a key to play a note!",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            PianoKeyboard(coroutineScope)
        }
    }
}

@Composable
fun PianoKeyboard(coroutineScope: CoroutineScope) {
    val whiteKeyWidth = 60.dp
    val blackKeyWidth = 40.dp
    val whiteKeyHeight = 200.dp
    val blackKeyHeight = 120.dp

    Box(modifier = Modifier.wrapContentSize()) {
        // Layer for white keys
        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(0.dp) // No spacing between white keys
        ) {
            val whiteKeys = listOf("C4", "D4", "E4", "F4", "G4", "A4", "B4", "C5")
            whiteKeys.forEach { noteName ->
                PianoKey(
                    noteName = noteName,
                    keyModifier = Modifier
                        .width(whiteKeyWidth)
                        .height(whiteKeyHeight)
                        .border(1.dp, Color.Black)
                        .background(Color.White),
                    coroutineScope = coroutineScope
                )
            }
        }

        // Layer for black keys, carefully positioned over white keys
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = -40.dp) // Raise black keys slightly above white for visual separation
        ) {
            // C#4 between C4 and D4
            Spacer(modifier = Modifier.width(whiteKeyWidth - (blackKeyWidth / 2))) // Align left edge of black key to right half of C4
            PianoKey(
                noteName = "C#4",
                keyModifier = Modifier
                    .width(blackKeyWidth)
                    .height(blackKeyHeight)
                    .background(Color.Black)
                    .border(1.dp, Color.DarkGray),
                coroutineScope = coroutineScope
            )

            // D#4 between D4 and E4
            Spacer(modifier = Modifier.width(whiteKeyWidth - blackKeyWidth)) // Gap for D4
            PianoKey(
                noteName = "D#4",
                keyModifier = Modifier
                    .width(blackKeyWidth)
                    .height(blackKeyHeight)
                    .background(Color.Black)
                    .border(1.dp, Color.DarkGray),
                coroutineScope = coroutineScope
            )

            // Skip E4, F4 -> F#4
            Spacer(modifier = Modifier.width(whiteKeyWidth * 2 - blackKeyWidth)) // Gap for E4 and F4
            PianoKey(
                noteName = "F#4",
                keyModifier = Modifier
                    .width(blackKeyWidth)
                    .height(blackKeyHeight)
                    .background(Color.Black)
                    .border(1.dp, Color.DarkGray),
                coroutineScope = coroutineScope
            )

            // G#4 between G4 and A4
            Spacer(modifier = Modifier.width(whiteKeyWidth - blackKeyWidth)) // Gap for G4
            PianoKey(
                noteName = "G#4",
                keyModifier = Modifier
                    .width(blackKeyWidth)
                    .height(blackKeyHeight)
                    .background(Color.Black)
                    .border(1.dp, Color.DarkGray),
                coroutineScope = coroutineScope
            )

            // A#4 between A4 and B4
            Spacer(modifier = Modifier.width(whiteKeyWidth - blackKeyWidth)) // Gap for A4
            PianoKey(
                noteName = "A#4",
                keyModifier = Modifier
                    .width(blackKeyWidth)
                    .height(blackKeyHeight)
                    .background(Color.Black)
                    .border(1.dp, Color.DarkGray),
                coroutineScope = coroutineScope
            )
        }
    }
}

@Composable
fun PianoKey(
    noteName: String,
    keyModifier: Modifier,
    coroutineScope: CoroutineScope
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.interactions.collectAsState(initial = null)

    // Visual feedback for press
    val defaultKeyColor = if (noteName.contains("#")) Color.Black else Color.White
    val pressedKeyColor = if (noteName.contains("#")) Color.DarkGray else Color.LightGray
    val backgroundColor = if (isPressed is PressInteraction.Press) pressedKeyColor else defaultKeyColor

    Box(
        modifier = keyModifier
            .background(backgroundColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null // No default ripple effect, we manage visual feedback
            ) {
                // On key click, launch a coroutine to play the sound
                pianoNoteFrequencies[noteName]?.let { frequency ->
                    coroutineScope.launch {
                        playTone(frequency)
                    }
                }
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        // Display note name on the key
        Text(
            text = noteName,
            color = if (noteName.contains("#")) Color.White else Color.Black,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )
    }
}