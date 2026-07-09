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
    
    val notes = mapOf(
        "C" to 261.63,
        "D" to 293.66,
        "E" to 329.63,
        "F" to 349.23,
        "G" to 392.00
    )

    fun playTone(freq: Double) {
        coroutineScope.launch(Dispatchers.IO) {
            val duration = 0.3
            val numSamples = (duration * sampleRate).toInt()
            val generatedSnd = DoubleArray(numSamples)
            val buffer = ByteArray(2 * numSamples)

            for (i in 0 until numSamples) {
                generatedSnd[i] = sin(2.0 * Math.PI * i.toDouble() / (sampleRate / freq))
            }

            var idx = 0
            for (dVal in generatedSnd) {
                val valShort = (dVal * 32767).toInt().toShort()
                buffer[idx++] = (valShort.toInt() and 0x00ff).toByte()
                buffer[idx++] = ((valShort.toInt() and 0xff00) shr 8).toByte()
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
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Compose Synth Piano",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(32.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            notes.forEach { (name, freq) ->
                Box(
                    modifier = Modifier
                        .size(60.dp, 150.dp)
                        .background(Color.White, RoundedCornerShape(8.dp))
                        .clickable { playTone(freq) },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Text(name, modifier = Modifier.padding(bottom = 8.dp))
                }
            }
        }
    }
}