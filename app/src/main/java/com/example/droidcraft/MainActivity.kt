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
            PianoScreen(onPlayNote = { frequency -> playTone(frequency) })
        }
    }

    private fun playTone(freqHz: Double) {
        val durationMs = 300
        val numSamples = (durationMs * sampleRate / 1000)
        val generatedSnd = ShortArray(numSamples)
        
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            generatedSnd[i] = (sin(2.0 * Math.PI * freqHz * t) * Short.MAX_VALUE).toInt().toShort()
        }
        
        audioTrack?.write(generatedSnd, 0, numSamples)
    }

    override fun onDestroy() {
        super.onDestroy()
        audioTrack?.stop()
        audioTrack?.release()
    }
}

@Composable
fun PianoScreen(onPlayNote: (Double) -> Unit) {
    val scope = rememberCoroutineScope()
    val notes = listOf(
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
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Synth Piano", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            notes.forEach { (name, freq) ->
                Box(
                    modifier = Modifier
                        .size(45.dp, 150.dp)
                        .background(Color.White, RoundedCornerShape(4.dp))
                        .clickable { scope.launch(Dispatchers.IO) { onPlayNote(freq) } },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Text(text = name, modifier = Modifier.padding(bottom = 8.dp))
                }
            }
        }
    }
}