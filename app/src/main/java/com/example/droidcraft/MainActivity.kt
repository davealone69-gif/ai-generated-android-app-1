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
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    PianoScreen()
                }
            }
        }
    }
}

@Composable
fun PianoScreen() {
    val scope = rememberCoroutineScope()
    
    val keys = listOf(
        "C" to 261.63, "D" to 293.66, "E" to 329.63,
        "F" to 349.23, "G" to 392.00, "A" to 440.00, "B" to 493.88
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("DroidCraft Synth Piano", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))
        Row(modifier = Modifier.fillMaxWidth().height(200.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            keys.forEach { (note, freq) ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(2.dp)
                        .background(Color.DarkGray, RoundedCornerShape(8.dp))
                        .clickable { scope.launch(Dispatchers.IO) { playTone(freq) } },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Text(note, color = Color.White, modifier = Modifier.padding(8.dp))
                }
            }
        }
    }
}

fun playTone(freq: Double) {
    val sampleRate = 44100
    val durationSecs = 0.5
    val numSamples = (durationSecs * sampleRate).toInt()
    val buffer = ShortArray(numSamples)

    for (i in 0 until numSamples) {
        buffer[i] = (sin(2.0 * Math.PI * i.toDouble() / (sampleRate / freq)) * 32767).toInt().toShort()
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
        .setBufferSizeInBytes(buffer.size * 2)
        .setTransferMode(AudioTrack.MODE_STATIC)
        .build()

    audioTrack.write(buffer, 0, buffer.size)
    audioTrack.play()
    
    // Simple way to wait for finish (in production use a listener)
    Thread.sleep((durationSecs * 1000).toLong())
    audioTrack.stop()
    audioTrack.release()
}