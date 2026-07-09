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
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    PianoScreen()
                }
            }
        }
    }
}

@Composable
fun PianoScreen() {
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
        Text("Compose Synthesizer", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))
        Row(
            modifier = Modifier.fillMaxWidth().height(200.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            notes.forEach { (name, freq) ->
                PianoKey(name, freq)
            }
        }
    }
}

@Composable
fun PianoKey(label: String, frequency: Double) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(40.dp)
            .padding(2.dp)
            .background(Color.DarkGray, RoundedCornerShape(4.dp))
            .clickable { playTone(frequency) },
        contentAlignment = Alignment.BottomCenter
    ) {
        Text(label, color = Color.White, modifier = Modifier.padding(bottom = 8.dp))
    }
}

fun playTone(freqOfTone: Double) {
    val sampleRate = 44100
    val duration = 0.3
    val numSamples = (duration * sampleRate).toInt()
    val generatedSnd = DoubleArray(numSamples)
    val audioTrack = AudioTrack(
        AudioManager.STREAM_MUSIC,
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
        numSamples * 2,
        AudioTrack.MODE_STATIC
    )

    for (i in 0 until numSamples) {
        generatedSnd[i] = sin(2.0 * Math.PI * i.toDouble() / (sampleRate / freqOfTone))
    }

    val generatedSndShort = ShortArray(numSamples)
    for (i in 0 until numSamples) {
        generatedSndShort[i] = (generatedSnd[i] * 32767).toInt().toShort()
    }

    audioTrack.write(generatedSndShort, 0, numSamples)
    audioTrack.play()
    
    // Auto-release thread logic handled implicitly by short duration and static mode
}