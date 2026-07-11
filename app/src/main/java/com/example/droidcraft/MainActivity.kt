package com.example.droidcraft

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Object for custom sound synthesis using AudioTrack
object AudioSynth {
    private const val SAMPLE_RATE = 44100 // Hz
    private const val DURATION_MS = 300 // milliseconds for each note
    private const val VOLUME = 0.3 // Amplitude multiplier (0.0 to 1.0)

    fun playTone(frequency: Double) {
        // Run audio generation and playback on a background thread
        Thread {
            val numSamples = (DURATION_MS * SAMPLE_RATE / 1000)
            val generatedSound = ShortArray(numSamples)

            // Generate a sine wave
            for (i in 0 until numSamples) {
                generatedSound[i] = (Short.MAX_VALUE * VOLUME * Math.sin(2 * Math.PI * frequency * i / SAMPLE_RATE)).toShort()
            }

            val minBufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            // Create and configure AudioTrack
            val audioTrack = AudioTrack(
                AudioManager.STREAM_MUSIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBufferSize,
                AudioTrack.MODE_STREAM
            )

            try {
                audioTrack.play()
                audioTrack.write(generatedSound, 0, generatedSound.size)
            } finally {
                audioTrack.stop()
                audioTrack.release()
            }
        }.start()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Apply Material3 theme (assuming default theme is provided by the project template)
            MaterialTheme {
                MainAppScreen()
            }
        }
    }
}

@Composable
fun PianoKey(
    noteFrequency: Double,
    label: String,
    modifier: Modifier = Modifier,
    isBlackKey: Boolean = false,
    keyWidth: Dp
) {
    var isPressed by remember { mutableStateOf(false) }

    val keyColor = when {
        isPressed && isBlackKey -> Color(0xFF333333) // Darker black when pressed
        isPressed && !isBlackKey -> Color(0xFFCCCCCC) // Darker white when pressed
        isBlackKey -> Color.Black
        else -> Color.White
    }
    val textColor = if (isBlackKey) Color.White else Color.Black

    Surface(
        color = keyColor,
        modifier = modifier
            .width(keyWidth)
            .padding(2.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        AudioSynth.playTone(noteFrequency)
                        tryAwaitRelease()
                        isPressed = false
                    }
                )
            },
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, Color.DarkGray)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Text(
                text = label,
                color = textColor,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
    }
}

@Composable
fun MainAppScreen() {
    val noteFrequencies = remember {
        mapOf(
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
    }

    val whiteKeyLabels = remember { listOf("C4", "D4", "E4", "F4", "G4", "A4", "B4", "C5") }
    val whiteKeyWidth = 60.dp
    val blackKeyWidth = 40.dp
    val keyboardHeight = 200.dp
    val blackKeyHeight = (keyboardHeight.value * 0.6f).dp

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
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Main Box to hold all keys, enabling layering for black keys
        Box(
            modifier = Modifier
                .wrapContentSize(align = Alignment.TopStart) // Adjusts size to content, aligns top-left
                .height(keyboardHeight)
                .border(1.dp, Color.DarkGray, MaterialTheme.shapes.small)
        ) {
            // White keys layout
            Row(modifier = Modifier.fillMaxHeight()) {
                whiteKeyLabels.forEach { noteName ->
                    PianoKey(
                        noteFrequency = noteFrequencies.getValue(noteName),
                        label = noteName,
                        modifier = Modifier.fillMaxHeight(),
                        isBlackKey = false,
                        keyWidth = whiteKeyWidth
                    )
                }
            }

            // Black keys layout (overlayed using offsets)
            val halfBlackKeyOffset = blackKeyWidth / 2

            // C#4 key (between C4 and D4)
            PianoKey(
                noteFrequency = noteFrequencies.getValue("C#4"),
                label = "C#4",
                modifier = Modifier
                    .offset(x = whiteKeyWidth - halfBlackKeyOffset)
                    .height(blackKeyHeight),
                isBlackKey = true,
                keyWidth = blackKeyWidth
            )
            // D#4 key (between D4 and E4)
            PianoKey(
                noteFrequency = noteFrequencies.getValue("D#4"),
                label = "D#4",
                modifier = Modifier
                    .offset(x = (whiteKeyWidth * 2) - halfBlackKeyOffset)
                    .height(blackKeyHeight),
                isBlackKey = true,
                keyWidth = blackKeyWidth
            )
            // F#4 key (between F4 and G4)
            PianoKey(
                noteFrequency = noteFrequencies.getValue("F#4"),
                label = "F#4",
                modifier = Modifier
                    .offset(x = (whiteKeyWidth * 4) - halfBlackKeyOffset)
                    .height(blackKeyHeight),
                isBlackKey = true,
                keyWidth = blackKeyWidth
            )
            // G#4 key (between G4 and A4)
            PianoKey(
                noteFrequency = noteFrequencies.getValue("G#4"),
                label = "G#4",
                modifier = Modifier
                    .offset(x = (whiteKeyWidth * 5) - halfBlackKeyOffset)
                    .height(blackKeyHeight),
                isBlackKey = true,
                keyWidth = blackKeyWidth
            )
            // A#4 key (between A4 and B4)
            PianoKey(
                noteFrequency = noteFrequencies.getValue("A#4"),
                label = "A#4",
                modifier = Modifier
                    .offset(x = (whiteKeyWidth * 6) - halfBlackKeyOffset)
                    .height(blackKeyHeight),
                isBlackKey = true,
                keyWidth = blackKeyWidth
            )
        }
    }
}