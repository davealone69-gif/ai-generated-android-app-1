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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PianoAppScreen(onPlayNote = { frequency -> playTone(frequency) })
        }
    }

    private fun playTone(freq: Double) {
        val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.Default)
        scope.launch {
            val durationMs = 300
            val numSamples = (durationMs * sampleRate / 1000)
            val generatedSnd = ShortArray(numSamples)
            for (i in 0 until numSamples) {
                generatedSnd[i] = (sin(2.0 * Math.PI * i / (sampleRate / freq)) * 32767).toInt().toShort()
            }
            
            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build())
                .setAudioFormat(AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build())
                .setBufferSizeInBytes(numSamples * 2)
                .build()
            
            audioTrack.play()
            audioTrack.write(generatedSnd, 0, numSamples)
            audioTrack.stop()
            audioTrack.release()
        }
    }
}

@Composable
fun PianoAppScreen(onPlayNote: (Double) -> Unit) {
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
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Jetpack Compose Piano",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 32.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            notes.forEach { (name, freq) ->
                Box(
                    modifier = Modifier
                        .size(40.dp, 150.dp)
                        .background(Color.White, RoundedCornerShape(4.dp))
                        .clickable { onPlayNote(freq) },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Text(text = name, modifier = Modifier.padding(bottom = 8.dp))
                }
            }
        }
    }
}