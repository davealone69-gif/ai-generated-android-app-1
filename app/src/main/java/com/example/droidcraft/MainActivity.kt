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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    val sampleRate = 44100
    val scope = rememberCoroutineScope()

    val notes = mapOf(
        "C" to 261.63,
        "D" to 293.66,
        "E" to 329.63,
        "F" to 349.23,
        "G" to 392.00,
        "A" to 440.00,
        "B" to 493.88
    )

    fun playTone(freqOfTone: Double) {
        scope.launch(Dispatchers.IO) {
            val duration = 0.5
            val numSamples = (duration * sampleRate).toInt()
            val generatedSnd = DoubleArray(numSamples)
            val generatedBuf = ByteArray(2 * numSamples)

            for (i in 0 until numSamples) {
                generatedSnd[i] = sin(2.0 * Math.PI * i / (sampleRate / freqOfTone))
                val valShort = (generatedSnd[i] * 32767).toInt()
                generatedBuf[2 * i] = (valShort and 0x00ff).toByte()
                generatedBuf[2 * i + 1] = ((valShort and 0xff00) shr 8).toByte()
            }

            val audioTrack = AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                generatedBuf.size,
                AudioTrack.MODE_STATIC
            )
            audioTrack.write(generatedBuf, 0, generatedBuf.size)
            audioTrack.play()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Synth Piano", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            notes.forEach { (note, freq) ->
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
        }
    }
}