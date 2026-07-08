package com.example.droidcraft

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sin

class PianoViewModel : ViewModel() {
    private val sampleRate = 44100
    private var audioTrack: AudioTrack? = null

    init {
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build())
            .setAudioFormat(AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build())
            .setBufferSizeInBytes(bufferSize)
            .build()
        audioTrack?.play()
    }

    fun playTone(freq: Double) {
        viewModelScope.launch(Dispatchers.Default) {
            val duration = 0.3
            val numSamples = (duration * sampleRate).toInt()
            val buffer = ShortArray(numSamples)
            for (i in 0 until numSamples) {
                val angle = 2.0 * Math.PI * i.toDouble() / (sampleRate / freq)
                buffer[i] = (sin(angle) * 32767).toInt().toShort()
            }
            audioTrack?.write(buffer, 0, numSamples)
        }
    }

    override fun onCleared() {
        audioTrack?.release()
        super.onCleared()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewModel = PianoViewModel()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PianoScreen(viewModel)
                }
            }
        }
    }
}

@Composable
fun PianoScreen(viewModel: PianoViewModel) {
    val notes = listOf(
        "C" to 261.63, "D" to 293.66, "E" to 329.63, "F" to 349.23,
        "G" to 392.00, "A" to 440.00, "B" to 493.88
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Synth Piano", style = MaterialTheme.typography.displaySmall)
        Spacer(modifier = Modifier.height(48.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            notes.forEach { (name, freq) ->
                PianoKey(name) { viewModel.playTone(freq) }
            }
        }
    }
}

@Composable
fun PianoKey(note: String, onClick: () -> Unit) {
    var isPressed by remember { mutableStateOf(false) }
    val color by animateColorAsState(if (isPressed) Color(0xFF6200EE) else Color(0xFFEEEEEE), label = "color")

    Box(
        modifier = Modifier
            .width(50.dp)
            .height(200.dp)
            .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
            .background(color)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        onClick()
                        tryAwaitRelease()
                        isPressed = false
                    }
                )
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        Text(note, color = if (isPressed) Color.White else Color.Black, modifier = Modifier.padding(bottom = 16.dp))
    }
}