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
            PianoScreen()
        }
    }
}

@Composable
fun PianoScreen() {
    val notes = mapOf(
        "C" to 261.63,
        "D" to 293.66,
        "E" to 329.63,
        "F" to 349.23,
        "G" to 392.00,
        "A" to 440.00,
        "B" to 493.88
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("DroidCraft Piano", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            notes.forEach { (name, freq) ->
                PianoKey(name, freq)
            }
        }
    }
}

@Composable
fun PianoKey(note: String, freq: Double) {
    Box(
        modifier = Modifier
            .size(40.dp, 120.dp)
            .background(Color.White, RoundedCornerShape(4.dp))
            .border(1.dp, Color.Black, RoundedCornerShape(4.dp))
            .clickable { playTone(freq) },
        contentAlignment = Alignment.BottomCenter
    ) {
        Text(note, modifier = Modifier.padding(bottom = 8.dp))
    }
}

fun playTone(freq: Double) {
    val sampleRate = 44100
    val duration = 0.3
    val numSamples = (duration * sampleRate).toInt()
    val generatedSnd = ByteArray(2 * numSamples)

    for (i in 0 until numSamples) {
        val sample = sin(2 * Math.PI * i.toDouble() / (sampleRate / freq))
        val pcm = (sample * 32767).toInt().toShort()
        generatedSnd[2 * i] = (pcm.toInt() and 0x00ff).toByte()
        generatedSnd[2 * i + 1] = ((pcm.toInt() and 0xff00) ushr 8).toByte()
    }

    val audioTrack = AudioTrack(
        AudioManager.STREAM_MUSIC,
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
        generatedSnd.size,
        AudioTrack.MODE_STATIC
    )
    audioTrack.write(generatedSnd, 0, generatedSnd.size)
    audioTrack.play()
}