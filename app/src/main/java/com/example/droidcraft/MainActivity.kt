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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PianoAppScreen()
        }
    }
}

@Composable
fun PianoAppScreen() {
    val coroutineScope = rememberCoroutineScope()
    val sampleRate = 44100
    val pianoKeys = listOf(
        261.63f to "C", 293.66f to "D", 329.63f to "E", 349.23f to "F", 
        392.00f to "G", 440.00f to "A", 493.88f to "B", 523.25f to "C+"
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Compose Synth Piano", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            pianoKeys.forEach { (freq, note) ->
                PianoKey(note) {
                    coroutineScope.launch(Dispatchers.Default) {
                        playTone(freq, sampleRate)
                    }
                }
            }
        }
    }
}

@Composable
fun PianoKey(note: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp, 120.dp)
            .background(Color.White, RoundedCornerShape(4.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Text(note, modifier = Modifier.padding(bottom = 8.dp))
    }
}

fun playTone(freq: Float, sampleRate: Int) {
    val durationMs = 300
    val numSamples = durationMs * sampleRate / 1000
    val sample = FloatArray(numSamples)
    val buffer = ShortArray(numSamples)

    for (i in 0 until numSamples) {
        sample[i] = sin(2.0 * Math.PI * i.toDouble() / (sampleRate / freq)).toFloat()
        buffer[i] = (sample[i] * Short.MAX_VALUE).toInt().toShort()
    }

    val audioTrack = AudioTrack(
        AudioManager.STREAM_MUSIC,
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
        buffer.size * 2,
        AudioTrack.MODE_STATIC
    )
    audioTrack.write(buffer, 0, buffer.size)
    audioTrack.play()
    
    // Cleanup simple release
    Thread {
        Thread.sleep(durationMs.toLong())
        audioTrack.stop()
        audioTrack.release()
    }.start()
}