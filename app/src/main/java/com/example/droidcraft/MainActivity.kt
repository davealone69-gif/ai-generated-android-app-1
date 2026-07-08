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
        261.63, 293.66, 329.63, 349.23, 392.00, 440.00, 493.88, 523.25
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("DroidCraft Synth Piano", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pianoKeys.forEach { freq ->
                PianoKey(freq) {
                    coroutineScope.launch(Dispatchers.Default) {
                        playTone(freq, sampleRate)
                    }
                }
            }
        }
    }
}

@Composable
fun PianoKey(frequency: Double, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp, 120.dp)
            .background(Color.White, RoundedCornerShape(4.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Text(text = "Hz", style = MaterialTheme.typography.labelSmall)
    }
}

fun playTone(freq: Double, sampleRate: Int) {
    val durationMs = 300
    val numSamples = (durationMs * sampleRate) / 1000
    val generatedSound = DoubleArray(numSamples)
    val buffer = ShortArray(numSamples)

    for (i in 0 until numSamples) {
        generatedSound[i] = sin(2.0 * Math.PI * i.toDouble() / (sampleRate / freq))
        buffer[i] = (generatedSound[i] * 32767).toInt().toShort()
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
    
    // Release after playing
    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
        audioTrack.release()
    }, durationMs.toLong() + 100)
}