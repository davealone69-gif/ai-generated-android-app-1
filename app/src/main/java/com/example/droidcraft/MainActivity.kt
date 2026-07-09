package com.example.droidcraft

import android.media.AudioAttributes
import android.media.AudioFormat
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    PianoAppScreen()
                }
            }
        }
    }
}

@Composable
fun PianoAppScreen() {
    val coroutineScope = rememberCoroutineScope()
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
                        playTone(freq)
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
            .border(1.dp, Color.Black, RoundedCornerShape(4.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Text(note, modifier = Modifier.padding(bottom = 8.dp), color = Color.Black)
    }
}

fun playTone(freq: Float) {
    val sampleRate = 44100
    val durationMs = 200
    val numSamples = (durationMs * sampleRate / 1000)
    val buffer = ShortArray(numSamples)

    for (i in 0 until numSamples) {
        buffer[i] = (sin(2.0 * Math.PI * i.toDouble() / (sampleRate.toDouble() / freq.toDouble())) * Short.MAX_VALUE).toInt().toShort()
    }

    val minBufferSize = AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )

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
        .setBufferSizeInBytes(maxOf(numSamples * 2, minBufferSize))
        .setTransferMode(AudioTrack.MODE_STATIC)
        .build()

    audioTrack.write(buffer, 0, numSamples)
    audioTrack.play()
    
    try {
        Thread.sleep(durationMs.toLong())
    } catch (e: InterruptedException) {
        e.printStackTrace()
    } finally {
        audioTrack.stop()
        audioTrack.release()
    }
}