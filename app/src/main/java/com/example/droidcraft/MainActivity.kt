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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    PianoAppScreen()
                }
            }
        }
    }
}

@Composable
fun PianoAppScreen() {
    val coroutineScope = rememberCoroutineScope()
    val sampleRate = 44100
    val pianoKeys = listOf(261.63, 293.66, 329.63, 349.23, 392.00, 440.00, 493.88, 523.25)

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Compose Synth Piano", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))
        Row(modifier = Modifier.fillMaxWidth().height(200.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            pianoKeys.forEach { freq ->
                PianoKey {
                    coroutineScope.launch(Dispatchers.IO) {
                        playTone(freq, sampleRate)
                    }
                }
            }
        }
    }
}

@Composable
fun PianoKey(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(40.dp)
            .fillMaxHeight()
            .padding(2.dp)
            .background(Color.White, RoundedCornerShape(4.dp))
            .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
            .clickable { onClick() }
    )
}

fun playTone(freq: Double, sampleRate: Int) {
    val durationMs = 300
    val numSamples = durationMs * sampleRate / 1000
    val generatedSnd = ByteArray(2 * numSamples)

    for (i in 0 until numSamples) {
        val sample = sin(2.0 * Math.PI * i.toDouble() / (sampleRate / freq))
        val pcm = (sample * 32767).toInt().coerceIn(-32768, 32767)
        generatedSnd[2 * i] = (pcm and 0x00ff).toByte()
        generatedSnd[2 * i + 1] = ((pcm and 0xff00) ushr 8).toByte()
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
        .setTransferMode(AudioTrack.MODE_STATIC)
        .build()

    audioTrack.write(generatedSnd, 0, generatedSnd.size)
    audioTrack.play()
    
    // Allow enough time for the static buffer to finish playing
    Thread.sleep(durationMs.toLong())
    audioTrack.stop()
    audioTrack.release()
}