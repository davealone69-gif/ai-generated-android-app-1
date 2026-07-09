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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                PianoScreen()
            }
        }
    }
}

@Composable
fun PianoScreen() {
    val scope = rememberCoroutineScope()
    val notes = listOf(261.63, 293.66, 329.63, 349.23, 392.00, 440.00, 493.88, 523.25) // C4 to C5

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Synthesizer Piano", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))
        Row(
            modifier = Modifier.fillMaxWidth().height(200.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            notes.forEach { frequency ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(2.dp)
                        .background(Color.DarkGray, RoundedCornerShape(8.dp))
                        .clickable {
                            scope.launch(Dispatchers.Default) {
                                playTone(frequency)
                            }
                        }
                )
            }
        }
    }
}

fun playTone(freqOfTone: Double) {
    val sampleRate = 44100
    val durationMs = 300
    val numSamples = durationMs * sampleRate / 1000
    val generatedSnd = ByteArray(2 * numSamples)
    val sample = DoubleArray(numSamples)

    for (i in 0 until numSamples) {
        sample[i] = sin(2.0 * Math.PI * i / (sampleRate / freqOfTone))
        val pcm = (sample[i] * 32767).toInt()
        generatedSnd[2 * i] = (pcm and 0xff).toByte()
        generatedSnd[2 * i + 1] = (pcm shr 8 and 0xff).toByte()
    }

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
        .setBufferSizeInBytes(generatedSnd.size)
        .build()

    audioTrack.play()
    audioTrack.write(generatedSnd, 0, generatedSnd.size)
    audioTrack.stop()
    audioTrack.release()
}