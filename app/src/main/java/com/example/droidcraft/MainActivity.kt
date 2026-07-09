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
    private val sampleRate = 44100
    private var audioTrack: AudioTrack? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        
        audioTrack = AudioTrack(
            AudioManager.STREAM_MUSIC,
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize,
            AudioTrack.MODE_STREAM
        )
        audioTrack?.play()

        setContent {
            PianoScreen(::playTone)
        }
    }

    private fun playTone(freq: Double) {
        val scope = kotlinx.coroutines.MainScope()
        scope.launch(Dispatchers.Default) {
            val durationMs = 300
            val numSamples = (durationMs * sampleRate / 1000)
            val buffer = ShortArray(numSamples)
            for (i in 0 until numSamples) {
                buffer[i] = (sin(2.0 * Math.PI * i.toDouble() / (sampleRate / freq)) * Short.MAX_VALUE).toInt().toShort()
            }
            audioTrack?.write(buffer, 0, numSamples)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioTrack?.stop()
        audioTrack?.release()
    }
}

@Composable
fun PianoScreen(onNoteClick: (Double) -> Unit) {
    val notes = listOf(
        "C4" to 261.63,
        "D4" to 293.66,
        "E4" to 329.63,
        "F4" to 349.23,
        "G4" to 392.00,
        "A4" to 440.00,
        "B4" to 493.88
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Synth Piano", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            notes.forEach { (name, freq) ->
                PianoKey(name = name, onClick = { onNoteClick(freq) })
            }
        }
    }
}

@Composable
fun PianoKey(name: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp, 120.dp)
            .border(1.dp, Color.Black, RoundedCornerShape(4.dp))
            .background(Color.White, RoundedCornerShape(4.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Text(text = name, modifier = Modifier.padding(bottom = 8.dp))
    }
}