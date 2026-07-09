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
    private val sampleRate = 44100
    private var audioTrack: AudioTrack? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PianoScreen(onPlayNote = { frequency -> playTone(frequency) })
        }
    }

    private fun playTone(freq: Double) {
        val durationMs = 300
        val numSamples = durationMs * sampleRate / 1000
        val generatedSnd = DoubleArray(numSamples)
        val buffer = ByteArray(2 * numSamples)

        for (i in 0 until numSamples) {
            generatedSnd[i] = sin(2.0 * Math.PI * i / (sampleRate / freq))
        }

        var idx = 0
        for (dVal in generatedSnd) {
            val valShort = (dVal * 32767).toInt().toShort()
            buffer[idx++] = (valShort.toInt() and 0x00ff).toByte()
            buffer[idx++] = (valShort.toInt() ushr 8).toByte()
        }

        audioTrack?.release()
        audioTrack = AudioTrack(
            AudioManager.STREAM_MUSIC,
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            buffer.size,
            AudioTrack.MODE_STATIC
        )
        audioTrack?.write(buffer, 0, buffer.size)
        audioTrack?.play()
    }

    override fun onDestroy() {
        super.onDestroy()
        audioTrack?.release()
    }
}

@Composable
fun PianoScreen(onPlayNote: (Double) -> Unit) {
    val keys = listOf(
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
        Text("DroidCraft Synthesizer", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            keys.forEach { (name, freq) ->
                PianoKey(name) { onPlayNote(freq) }
            }
        }
    }
}

@Composable
fun PianoKey(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp, 150.dp)
            .border(1.dp, Color.Black, RoundedCornerShape(4.dp))
            .background(Color.White, RoundedCornerShape(4.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Text(text = label, modifier = Modifier.padding(bottom = 8.dp))
    }
}