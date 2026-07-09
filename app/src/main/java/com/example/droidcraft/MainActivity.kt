package com.example.droidcraft

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectPressAndRelease
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MainAppScreen()
        }
    }
}

// --- Sound Synthesis Logic ---
object AudioEngine {
    private const val SAMPLE_RATE = 44100
    private const val BUFFER_SIZE = SAMPLE_RATE / 4 // 0.25 seconds of audio data
    private const val AMPLITUDE = 32767.0 // Max for 16-bit PCM

    private var audioTrack: AudioTrack? = null
    private var playingThread: Thread? = null
    @Volatile private var isPlaying = false

    // Function to get frequency from MIDI note number
    fun midiNoteToFrequency(midiNote: Int): Float {
        return 440f * (2.0.pow((midiNote - 69) / 12.0)).toFloat()
    }

    fun startNote(frequency: Float) {
        if (isPlaying) {
            stopNote() // Stop any currently playing note before starting a new one
        }

        isPlaying = true
        playingThread = Thread {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)

            audioTrack = AudioTrack(
                AudioManager.STREAM_MUSIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                BUFFER_SIZE * 2, // Multiply by 2 for 16-bit stereo (or mono)
                AudioTrack.MODE_STREAM
            )
            audioTrack?.play()

            var phase = 0.0 // To maintain phase continuity
            val samples = ShortArray(BUFFER_SIZE)

            while (isPlaying) {
                for (i in 0 until BUFFER_SIZE) {
                    // Generate a sine wave sample
                    val sample = (AMPLITUDE * sin(2 * PI * frequency * (phase / SAMPLE_RATE))).toFloat()
                    samples[i] = sample.toInt().toShort()
                    phase++
                }
                audioTrack?.write(samples, 0, BUFFER_SIZE)
            }
        }
        playingThread?.start()
    }

    fun stopNote() {
        isPlaying = false
        playingThread?.join() // Wait for the thread to finish its current loop
        playingThread = null
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }
}

// --- Composable UI ---
@Composable
fun MainAppScreen() {
    val coroutineScope = rememberCoroutineScope()
    MaterialTheme { // Use MaterialTheme for consistent styling
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Compose Piano",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                PianoKeyboard(coroutineScope)
            }
        }
    }
}

@Composable
fun PianoKeyboard(coroutineScope: CoroutineScope) {
    // Define MIDI notes for a simple scale (C4 to C5)
    val midiNotes = remember {
        listOf(60, 62, 64, 65, 67, 69, 71, 72) // C4, D4, E4, F4, G4, A4, B4, C5
    }

    Row(
        modifier = Modifier.fillMaxWidth().height(200.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        midiNotes.forEach { midiNote ->
            val frequency = AudioEngine.midiNoteToFrequency(midiNote)
            PianoKey(
                midiNote = midiNote,
                frequency = frequency,
                coroutineScope = coroutineScope
            )
        }
    }
}

@Composable
fun PianoKey(midiNote: Int, frequency: Float, coroutineScope: CoroutineScope) {
    var isPressed by remember { mutableStateOf(false) }

    val backgroundColor = if (isPressed) MaterialTheme.colorScheme.primary else Color.White
    val contentColor = if (isPressed) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

    Card(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .padding(4.dp)
            .pointerInput(Unit) {
                detectPressAndRelease(
                    onPress = { offset ->
                        isPressed = true
                        coroutineScope.launch(Dispatchers.Default) {
                            AudioEngine.startNote(frequency)
                        }
                    },
                    onRelease = {
                        isPressed = false
                        coroutineScope.launch(Dispatchers.Default) {
                            AudioEngine.stopNote()
                        }
                    }
                )
            },
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isPressed) 0.dp else 4.dp),
        border = if (isPressed) null else CardDefaults.outlinedCardBorder()
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Text(
                text = "C${(midiNote - 60)/12 + 4}" + if (midiNote % 12 == 1 || midiNote % 12 == 3 || midiNote % 12 == 6 || midiNote % 12 == 8 || midiNote % 12 == 10) "#" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    }
}