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
    private val bufferSize = AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PianoScreen(::playTone)
        }
    }

    private fun playTone(frequency: Double) {
        Thread {
            val audioTrack = AudioTrack.Builder()
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

            val durationMs = 300
            val numSamples = durationMs * sampleRate / 1000
            val generatedSnd = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                generatedSnd[i] = (sin(2.0 * Math.PI * i.toDouble() / (sampleRate / frequency)) * 32767.0).toInt().toShort()
            }

            audioTrack.play()
            audioTrack.write(generatedSnd, 0, numSamples)
            audioTrack.stop()
            audioTrack.release()
        }.start()
    }
}

@Composable
fun PianoScreen(onPlayNote: (Double) -> Unit) {
    val notes = listOf(261.63, 293.66, 329.63, 349.23, 392.00, 440.00, 493.88, 523.25)
    
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("DroidCraft Synthesizer", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            notes.forEach { frequency ->
                PianoKey(onClick = { onPlayNote(frequency) })
            }
        }
    }
}

@Composable
fun PianoKey(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 40.dp, height = 150.dp)
            .background(Color.White, RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(1.dp)
            .background(Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.BottomCenter
    ) {
        Divider(color = Color.Black, thickness = 2.dp)
    }
}