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
    val scope = rememberCoroutineScope()
    val sampleRate = 44100
    val pianoKeys = listOf(261.63f, 293.66f, 329.63f, 349.23f, 392.00f, 440.00f, 493.88f, 523.25f)

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("DroidCraft Piano", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pianoKeys.forEach { frequency ->
                PianoKey(frequency) {
                    scope.launch(Dispatchers.IO) {
                        playTone(frequency, sampleRate)
                    }
                }
            }
        }
    }
}

@Composable
fun PianoKey(frequency: Float, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp, 150.dp)
            .background(Color.White, RoundedCornerShape(4.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Divider(color = Color.Black, thickness = 2.dp)
    }
}

fun playTone(freq: Float, sampleRate: Int) {
    val durationMs = 500
    val numSamples = (durationMs * sampleRate / 1000)
    val buffer = ShortArray(numSamples)
    
    for (i in 0 until numSamples) {
        val angle = 2.0 * Math.PI * i * freq / sampleRate
        buffer[i] = (sin(angle) * Short.MAX_VALUE).toInt().toShort()
    }

    val audioTrack = AudioTrack(
        AudioManager.STREAM_MUSIC,
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
        numSamples * 2,
        AudioTrack.MODE_STATIC
    )
    
    audioTrack.write(buffer, 0, numSamples)
    audioTrack.play()
    
    // Cleanup simple implementation
    Thread.sleep(durationMs.toLong())
    audioTrack.stop()
    audioTrack.release()
}