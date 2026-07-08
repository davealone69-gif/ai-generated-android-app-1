package com.example.droidcraft

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.exp
import kotlin.math.sin

class SynthEngine {
    private val sampleRate = 44100
    private val audioTrack: AudioTrack

    init {
        val bufferSize = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
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
            .setBufferSizeInBytes(bufferSize * 2)
            .build()
        audioTrack.play()
    }

    fun playNote(freq: Double) {
        val durationSeconds = 0.5
        val numSamples = (durationSeconds * sampleRate).toInt()
        val buffer = ShortArray(numSamples)
        
        // ADSR Envelope Parameters
        val attackSamples = (0.05 * sampleRate).toInt()
        val releaseSamples = (0.2 * sampleRate).toInt()

        for (i in 0 until numSamples) {
            var envelope = 1.0
            if (i < attackSamples) {
                envelope = i.toDouble() / attackSamples
            } else if (i > numSamples - releaseSamples) {
                envelope = (numSamples - i).toDouble() / releaseSamples
            }
            
            val angle = 2.0 * Math.PI * i.toDouble() * freq / sampleRate
            buffer[i] = (sin(angle) * envelope * 30000).toInt().toShort()
        }
        
        // Non-blocking write to prevent UI stutters
        Thread {
            audioTrack.write(buffer, 0, numSamples)
        }.start()
    }

    fun release() {
        audioTrack.stop()
        audioTrack.release()
    }
}

class PianoViewModel : ViewModel() {
    private val synth = SynthEngine()

    fun triggerNote(freq: Double) {
        synth.playNote(freq)
    }

    override fun onCleared() {
        synth.release()
        super.onCleared()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFBB86FC),
                    background = Color(0xFF121212),
                    surface = Color(0xFF1E1E1E)
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    PianoScreen(viewModel())
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
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("DROIDCRAFT SYNTH", style = MaterialTheme.typography.headlineMedium, color = Color.White, letterSpacing = 2.sp)
        Spacer(modifier = Modifier.height(64.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            notes.forEach { (name, freq) ->
                PianoKey(name) { viewModel.triggerNote(freq) }
            }
        }
    }
}

@Composable
fun PianoKey(note: String, onClick: () -> Unit) {
    var isPressed by remember { mutableStateOf(false) }
    val color by animateColorAsState(
        if (isPressed) MaterialTheme.colorScheme.primary else Color(0xFFE0E0E0),
        animationSpec = tween(50)
    )

    Box(
        modifier = Modifier
            .width(52.dp)
            .height(240.dp)
            .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
            .background(color)
            .border(1.dp, Color.Black.copy(alpha = 0.2f), RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
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
        Text(
            note,
            color = if (isPressed) Color.White else Color.Black.copy(alpha = 0.6f),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )
    }
}