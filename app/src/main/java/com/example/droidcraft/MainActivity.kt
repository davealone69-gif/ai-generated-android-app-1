package com.example.droidcraft

import android.media.AudioAttributes
import android.media.AudioFormat
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
        
        val minBufferSize = AudioTrack.getMinBufferSize(
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
            .setBufferSizeInBytes(minBufferSize * 2)
            .setTransferMode(AudioTrack.MODE_STREAM) 
            .build()

        audioTrack?.play()

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    PianoScreen(::playTone)
                }
            }
        }
    }

    private fun playTone(frequency: Double) {
        val duration = 0.3
        val numSamples = (duration * sampleRate).toInt()
        val generatedSound = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            generatedSound[i] = (sin(2.0 * Math.PI * i / (sampleRate / frequency)) * Short.MAX_VALUE).toInt().toShort()
        }

        audioTrack?.write(generatedSound, 0, numSamples)
    }

    override fun onDestroy() {
        super.onDestroy()
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }
}

@Composable
fun PianoScreen(onPlay: (Double) -> Unit) {
    val scope = rememberCoroutineScope()
    val keys = listOf(261.63, 293.66, 329.63, 349.23, 392.00, 440.00, 493.88, 523.25)

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("DroidCraft Synthesizer", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            keys.forEach { freq ->
                Box(
                    modifier = Modifier
                        .size(40.dp, 150.dp)
                        .background(Color.White, RoundedCornerShape(4.dp))
                        .border(1.dp, Color.Black, RoundedCornerShape(4.dp))
                        .clickable { scope.launch(Dispatchers.IO) { onPlay(freq) } }
                )
            }
        }
    }
}