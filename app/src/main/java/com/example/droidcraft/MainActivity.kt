package com.example.droidcraft

import android.media.AudioAttributes
import android.media.AudioFormat
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.concurrent.thread
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    PianoAppScreen()
                }
            }
        }
    }
}

@Composable
fun PianoAppScreen() {
    val notes = listOf(
        "C" to 261.63, "D" to 293.66, "E" to 329.63, "F" to 349.23,
        "G" to 392.00, "A" to 440.00, "B" to 493.88
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Compose Synth Piano", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            notes.forEach { (name, freq) ->
                PianoKey(name, freq)
            }
        }
    }
}

@Composable
fun PianoKey(note: String, frequency: Double) {
    Box(
        modifier = Modifier
            .width(40.dp)
            .height(150.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Gray)
            .clickable {
                thread { playTone(frequency) }
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        Text(text = note, color = Color.White, modifier = Modifier.padding(bottom = 8.dp))
    }
}

fun playTone(freqOfTone: Double) {
    val sampleRate = 44100
    val duration = 0.5
    val numSamples = (duration * sampleRate).toInt()
    val buffer = ByteArray(numSamples * 2)

    for (i in 0 until numSamples) {
        val angle = 2.0 * Math.PI * i.toDouble() / (sampleRate / freqOfTone)
        val pcm = (sin(angle) * 32767).toInt().toShort()
        buffer[2 * i] = (pcm.toInt() and 0x00ff).toByte()
        buffer[2 * i + 1] = ((pcm.toInt() shr 8) and 0x00ff).toByte()
    }

    val audioTrack = AudioTrack.Builder()
        .setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build())
        .setAudioFormat(AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build())
        .setBufferSizeInBytes(buffer.size)
        .build()

    audioTrack.write(buffer, 0, buffer.size)
    audioTrack.play()
    
    // Simple block to ensure sound finishes before cleanup
    Thread.sleep((duration * 1000).toLong())
    audioTrack.stop()
    audioTrack.release()
}