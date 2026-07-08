package com.example.droidcraft

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import java.util.concurrent.Executors
import kotlin.math.sin

class SynthEngine {
    private val sampleRate = 44100
    private val executor = Executors.newSingleThreadExecutor()
    private val audioTrack: AudioTrack = AudioTrack.Builder()
        .setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build())
        .setAudioFormat(AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build())
        .setBufferSizeInBytes(AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT) * 2)
        .build().apply { play() }

    fun playNote(freq: Double) {
        executor.submit {
            val duration = 0.4
            val numSamples = (duration * sampleRate).toInt()
            val buffer = ShortArray(numSamples)
            val attack = (0.02 * sampleRate).toInt()
            
            for (i in 0 until numSamples) {
                val envelope = when {
                    i < attack -> i.toDouble() / attack
                    else -> (numSamples - i).toDouble() / (numSamples - attack)
                }
                buffer[i] = (sin(2.0 * Math.PI * i * freq / sampleRate) * envelope * 24000).toInt().toShort()
            }
            audioTrack.write(buffer, 0, numSamples)
        }
    }

    fun release() {
        executor.shutdown()
        audioTrack.stop()
        audioTrack.release()
    }
}

class PianoViewModel : ViewModel() {
    private val synth = SynthEngine()
    fun triggerNote(freq: Double) = synth.playNote(freq)
    override fun onCleared() = synth.release()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFBB86FC), background = Color(0xFF121212))) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    PianoScreen()
                }
            }
        }
    }
}

@Composable
fun PianoScreen(viewModel: PianoViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val notes = listOf("C" to 261.63, "D" to 293.66, "E" to 329.63, "F" to 349.23, "G" to 392.00, "A" to 440.00, "B" to 493.88)
    
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("DROIDCRAFT SYNTH", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Light, color = Color.Gray)
        Spacer(modifier = Modifier.height(48.dp))
        Row(modifier = Modifier.fillMaxWidth().height(280.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            notes.forEach { (name, freq) ->
                PianoKey(name) { viewModel.triggerNote(freq) }
            }
        }
    }
}

@Composable
fun PianoKey(note: String, onPlay: () -> Unit) {
    var isPressed by remember { mutableStateOf(false) }
    val color by animateColorAsState(if (isPressed) MaterialTheme.colorScheme.primary else Color(0xFFE0E0E0))

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(44.dp)
            .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
            .background(color)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    onPlay()
                    down.consume()
                    waitForUpOrCancellation()
                    isPressed = false
                }
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        Text(note, modifier = Modifier.padding(bottom = 16.dp), fontWeight = FontWeight.Bold, color = if(isPressed) Color.White else Color.Black)
    }
}