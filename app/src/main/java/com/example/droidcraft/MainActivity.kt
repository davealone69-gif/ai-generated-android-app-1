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

    private fun playTone(freqOfTone: Double) {
        Thread {
            val toneDuration = 0.5 
            val numSamples = (toneDuration * sampleRate).toInt()
            val sample = DoubleArray(numSamples)
            val generatedSnd = ByteArray(2 * numSamples)

            for (i in 0 until numSamples) {
                sample[i] = sin(2.0 * Math.PI * i.toDouble() / (sampleRate / freqOfTone))
                val pcm = (sample[i] * 32767).toInt().toShort()
                generatedSnd[2 * i] = (pcm.toInt() and 0xff).toByte()
                generatedSnd[2 * i + 1] = (pcm.toInt() shr 8 and 0xff).toByte()
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
                .setBufferSizeInBytes(generatedSnd.size)
                .build()

            audioTrack.play()
            audioTrack.write(generatedSnd, 0, generatedSnd.size)
            audioTrack.stop()
            audioTrack.release()
        }.start()
    }
}

@Composable
fun PianoScreen(onPlay: (Double) -> Unit) {
    val notes = listOf(261.63, 293.66, 329.63, 349.23, 392.00, 440.00, 493.88, 523.25)
    val noteNames = listOf("C", "D", "E", "F", "G", "A", "B", "C")
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("DroidCraft Synthesizer", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))
        Row(modifier = Modifier.fillMaxWidth().height(200.dp)) {
            notes.forEachIndexed { index, freq ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(2.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable { scope.launch(Dispatchers.IO) { onPlay(freq) } },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Text(noteNames[index], modifier = Modifier.padding(bottom = 16.dp))
                }
            }
        }
    }
}