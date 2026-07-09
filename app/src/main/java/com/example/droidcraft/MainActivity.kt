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
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build())
            .setAudioFormat(AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build())
            .setBufferSizeInBytes(bufferSize)
            .build()
        
        audioTrack?.play()

        setContent {
            PianoScreen(::playSound)
        }
    }

    private fun playSound(freq: Double) {
        val durationMs = 300
        val numSamples = (durationMs * sampleRate / 1000)
        val sample = ShortArray(numSamples)
        
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            sample[i] = (Math.sin(2.0 * Math.PI * freq * t) * Short.MAX_VALUE * 0.5).toInt().toShort()
        }
        audioTrack?.write(sample, 0, numSamples)
    }

    override fun onDestroy() {
        audioTrack?.release()
        super.onDestroy()
    }
}

@Composable
fun PianoScreen(onNotePressed: (Double) -> Unit) {
    val notes = listOf(
        "C" to 261.63, "D" to 293.66, "E" to 329.63, 
        "F" to 349.23, "G" to 392.00, "A" to 440.00, "B" to 493.88
    )
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Compose Synthesizer", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            notes.forEach { (name, freq) ->
                Box(
                    modifier = Modifier
                        .size(45.dp, 150.dp)
                        .background(Color.White, RoundedCornerShape(4.dp))
                        .clickable {
                            scope.launch(Dispatchers.IO) { onNotePressed(freq) }
                        },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Text(name, modifier = Modifier.padding(bottom = 8.dp))
                }
            }
        }
    }
}