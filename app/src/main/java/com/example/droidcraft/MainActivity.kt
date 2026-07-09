package com.example.droidcraft

import android.media.AudioFormat
import android.media.AudioManager
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
            PianoAppScreen()
        }
    }
}

@Composable
fun PianoAppScreen() {
    val notes = mapOf(
        "C" to 261.63,
        "D" to 293.66,
        "E" to 329.63,
        "F" to 349.23,
        "G" to 392.00,
        "A" to 440.00,
        "B" to 493.88
    )

    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Simple Synth Piano", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            notes.forEach { (note, freq) ->
                PianoKey(note) {
                    scope.launch(Dispatchers.IO) {
                        playTone(freq)
                    }
                }
            }
        }
    }
}

@Composable
fun PianoKey(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp, 120.dp)
            .background(Color.White, RoundedCornerShape(4.dp))
            .border(1.dp, Color.Black, RoundedCornerShape(4.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Text(label, modifier = Modifier.padding(bottom = 8.dp))
    }
}

fun playTone(freqOfTone: Double) {
    val durationMs = 300
    val sampleRate = 44100
    val numSamples = durationMs * sampleRate / 1000
    val generatedSnd = DoubleArray(numSamples)
    val buffer = ByteArray(numSamples * 2)

    for (i in 0 until numSamples) {
        generatedSnd[i] = sin(2.0 * Math.PI * i / (sampleRate / freqOfTone))
        val pcm = (generatedSnd[i] * 32767).toInt()
        buffer[2 * i] = (pcm and 0xff).toByte()
        buffer[2 * i + 1] = (pcm shr 8 and 0xff).toByte()
    }

    val audioTrack = AudioTrack(
        AudioManager.STREAM_MUSIC,
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
        numSamples * 2,
        AudioTrack.MODE_STATIC
    )
    audioTrack.write(buffer, 0, buffer.size)
    audioTrack.play()
    audioTrack.release()
}