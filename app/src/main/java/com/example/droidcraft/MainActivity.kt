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
        
        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        
        audioTrack = AudioTrack(
            AudioManager.STREAM_MUSIC,
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBufferSize,
            AudioTrack.MODE_STREAM
        )
        audioTrack?.play()

        setContent {
            PianoScreen { frequency -> playTone(frequency) }
        }
    }

    private fun playTone(freqHz: Double) {
        val scope = kotlinx.coroutines.MainScope()
        scope.launch(Dispatchers.Default) {
            val durationMs = 300
            val numSamples = (durationMs * sampleRate) / 1000
            val generatedSnd = ShortArray(numSamples)
            
            for (i in 0 until numSamples) {
                generatedSnd[i] = (sin(2.0 * Math.PI * i / (sampleRate / freqHz)) * Short.MAX_VALUE).toInt().toShort()
            }
            audioTrack?.write(generatedSnd, 0, numSamples)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioTrack?.stop()
        audioTrack?.release()
    }
}

@Composable
fun PianoScreen(onNotePlay: (Double) -> Unit) {
    val notes = listOf(261.63, 293.66, 329.63, 349.23, 392.00, 440.00, 493.88, 523.25)
    
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "DroidCraft Synth Piano", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            notes.forEach { freq ->
                Box(
                    modifier = Modifier
                        .size(40.dp, 150.dp)
                        .background(Color.White)
                        .border(1.dp, Color.Black)
                        .clickable { onNotePlay(freq) },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Text("♪", modifier = Modifier.padding(bottom = 8.dp))
                }
            }
        }
    }
}