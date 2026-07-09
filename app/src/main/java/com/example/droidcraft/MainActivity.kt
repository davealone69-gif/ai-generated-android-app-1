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
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    PianoScreen()
                }
            }
        }
    }
}

@Composable
fun PianoScreen() {
    val notes = listOf(261.63, 293.66, 329.63, 349.23, 392.00, 440.00, 493.88, 523.25)

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Synth Piano", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            notes.forEach { frequency ->
                PianoKey(frequency)
            }
        }
    }
}

@Composable
fun PianoKey(frequency: Double) {
    Box(
        modifier = Modifier
            .size(40.dp, 150.dp)
            .background(Color.White, RoundedCornerShape(4.dp))
            .clickable { playTone(frequency) },
        contentAlignment = Alignment.BottomCenter
    ) {
        // Aesthetic divider
    }
}

fun playTone(freq: Double) {
    val sampleRate = 44100
    val duration = 0.5
    val numSamples = (duration * sampleRate).toInt()
    val generatedSound = DoubleArray(numSamples)
    val buffer = ByteArray(2 * numSamples)

    for (i in 0 until numSamples) {
        generatedSound[i] = sin(2.0 * Math.PI * i.toDouble() / (sampleRate / freq))
    }

    var idx = 0
    for (dVal in generatedSound) {
        val valShort = (dVal * 32767).toInt()
        buffer[idx++] = (valShort and 0x00ff).toByte()
        buffer[idx++] = ((valShort and 0xff00) ushr 8).toByte()
    }

    val audioTrack = AudioTrack(
        AudioManager.STREAM_MUSIC,
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
        buffer.size,
        AudioTrack.MODE_STATIC
    )
    audioTrack.write(buffer, 0, buffer.size)
    audioTrack.play()
}