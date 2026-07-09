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
            PianoScreen { frequency -> playTone(frequency) }
        }
    }

    private fun playTone(freqHz: Double) {
        Thread {
            val durationMs = 300
            val numSamples = (durationMs * sampleRate / 1000)
            val generatedSnd = ShortArray(numSamples)
            for (i in 0 until numSamples) {
                // Sine wave synthesis
                generatedSnd[i] = (sin(2.0 * Math.PI * i / (sampleRate / freqHz)) * 32767).toInt().toShort()
            }
            audioTrack?.apply {
                if (playState != AudioTrack.PLAYSTATE_PLAYING) play()
                write(generatedSnd, 0, numSamples)
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        audioTrack?.release()
    }
}

@Composable
fun PianoScreen(onPlay: (Double) -> Unit) {
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
        modifier = Modifier.fillMaxSize().background(Color(0xFF121212)),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Compose Piano", color = Color.White, style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(40.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            notes.forEach { (name, freq) ->
                PianoKey(name) { onPlay(freq) }
            }
        }
    }
}

@Composable
fun PianoKey(label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .size(45.dp, 150.dp)
            .clickable { onClick() },
        color = Color.White,
        shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)
    ) {
        Box(contentAlignment = Alignment.BottomCenter, modifier = Modifier.padding(bottom = 10.dp)) {
            Text(label, color = Color.Black)
        }
    }
}