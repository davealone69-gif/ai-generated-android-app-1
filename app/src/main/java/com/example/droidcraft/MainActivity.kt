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
    private val sampleRate = 44100
    private var audioTrack: AudioTrack? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .build()

        setContent {
            PianoScreen(::playTone)
        }
    }

    private fun playTone(freq: Double) {
        val scope = rememberCoroutineScope() // Note: This logic is called from UI, handled by threading
        Thread {
            val durationMs = 300
            val numSamples = (durationMs * sampleRate / 1000)
            val generatedSnd = ShortArray(numSamples)
            
            for (i in 0 until numSamples) {
                generatedSnd[i] = (sin(2.0 * Math.PI * i.toDouble() / (sampleRate.toDouble() / freq)) * 32767.0).toInt().toShort()
            }
            
            audioTrack?.play()
            audioTrack?.write(generatedSnd, 0, numSamples)
            audioTrack?.stop()
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        audioTrack?.release()
    }
}

@Composable
fun PianoScreen(onNotePressed: (Double) -> Unit) {
    val notes = listOf(261.63, 293.66, 329.63, 349.23, 392.00, 440.00, 493.88, 523.25)
    
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Simple Synthesizer Piano", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))
        Row(
            modifier = Modifier.fillMaxWidth().height(200.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            notes.forEach { freq ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(2.dp)
                        .fillMaxHeight()
                        .background(Color.DarkGray, RoundedCornerShape(8.dp))
                        .clickable { onNotePressed(freq) },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Text("♪", color = Color.White, modifier = Modifier.padding(bottom = 16.dp))
                }
            }
        }
    }
}