package com.example.droidcraft

import android.media.AudioFormat
import android.media.AudioManager
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
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                PianoAppScreen()
            }
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

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Synth Piano", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            notes.forEach { (name, freq) ->
                PianoKey(name, freq)
            }
        }
    }
}

@Composable
fun PianoKey(note: String, frequency: Double) {
    Box(
        modifier = Modifier
            .size(40.dp, 150.dp)
            .background(Color.White, RoundedCornerShape(4.dp))
            .clickable { playTone(frequency) },
        contentAlignment = Alignment.BottomCenter
    ) {
        Text(text = note, modifier = Modifier.padding(bottom = 8.dp))
    }
}

fun playTone(freqOfTone: Double) {
    val sampleRate = 44100
    val numSamples = sampleRate / 4
    val sample = DoubleArray(numSamples)
    val generatedSnd = ByteArray(2 * numSamples)

    for (i in 0 until numSamples) {
        sample[i] = sin(2.0 * Math.PI * i.toDouble() / (sampleRate / freqOfTone))
        val pcm = (sample[i] * 32767).toInt()
        generatedSnd[2 * i] = (pcm and 0xff).toByte()
        generatedSnd[2 * i + 1] = ((pcm shr 8) and 0xff).toByte()
    }

    val audioTrack = AudioTrack(
        AudioManager.STREAM_MUSIC,
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
        generatedSnd.size,
        AudioTrack.MODE_STATIC
    )
    audioTrack.write(generatedSnd, 0, generatedSnd.size)
    audioTrack.play()
    audioTrack.release()
}