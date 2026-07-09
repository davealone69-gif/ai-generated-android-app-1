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
    private val sampleRate = 44100
    private val bufferSize = AudioTrack.getMinBufferSize(
        sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PianoScreen(::playTone)
        }
    }

    private fun playTone(freqHz: Double) {
        val audioTrack = AudioTrack(
            AudioManager.STREAM_MUSIC,
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize,
            AudioTrack.MODE_STATIC
        )

        val durationMs = 300
        val numSamples = (durationMs * sampleRate / 1000)
        val generatedSnd = ShortArray(numSamples)
        
        for (i in 0 until numSamples) {
            generatedSnd[i] = (sin(2.0 * Math.PI * i.toDouble() / (sampleRate / freqHz)) * Short.MAX_VALUE).toInt().toShort()
        }

        audioTrack.write(generatedSnd, 0, numSamples)
        audioTrack.play()
    }
}

@Composable
fun PianoScreen(onNotePressed: (Double) -> Unit) {
    val notes = mapOf("C" to 261.63, "D" to 293.66, "E" to 329.63, "F" to 349.23, "G" to 392.00)
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Synth Piano", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            notes.forEach { (name, freq) ->
                PianoKey(name) {
                    scope.launch(Dispatchers.Default) {
                        onNotePressed(freq)
                    }
                }
            }
        }
    }
}

@Composable
fun PianoKey(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(60.dp, 150.dp)
            .background(Color.White, shape = RoundedCornerShape(4.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Text(label, modifier = Modifier.padding(bottom = 8.dp))
    }
}