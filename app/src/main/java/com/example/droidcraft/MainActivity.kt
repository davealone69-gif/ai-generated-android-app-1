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
            PianoAppScreen()
        }
    }
}

@Composable
fun PianoAppScreen() {
    val scope = rememberCoroutineScope()
    val notes = mapOf(
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
        Text("DroidCraft Piano", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))
        Row(
            modifier = Modifier.fillMaxWidth().height(200.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            notes.forEach { (name, freq) ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(2.dp)
                        .background(Color.DarkGray, RoundedCornerShape(8.dp))
                        .clickable {
                            scope.launch(Dispatchers.Default) {
                                playTone(freq)
                            }
                        },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Text(name, color = Color.White, modifier = Modifier.padding(bottom = 16.dp))
                }
            }
        }
    }
}

fun playTone(freqOfTone: Double) {
    val durationMs = 300
    val sampleRate = 44100
    val numSamples = durationMs * sampleRate / 1000
    val sample = DoubleArray(numSamples)
    val generatedSnd = ByteArray(2 * numSamples)

    for (i in 0 until numSamples) {
        sample[i] = sin(2.0 * Math.PI * i.toDouble() / (sampleRate / freqOfTone))
        val pcm = (sample[i] * 32767).toInt()
        generatedSnd[2 * i] = (pcm and 0x00ff).toByte()
        generatedSnd[2 * i + 1] = ((pcm and 0xff00) ushr 8).toByte()
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