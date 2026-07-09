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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
    val notes = listOf(
        "C" to 261.63, "D" to 293.66, "E" to 329.63, 
        "F" to 349.23, "G" to 392.00, "A" to 440.00, "B" to 493.88
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Compose Synth Piano", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            notes.forEach { (name, freq) ->
                PianoKey(name = name, frequency = freq)
            }
        }
    }
}

@Composable
fun PianoKey(name: String, frequency: Double) {
    Box(
        modifier = Modifier
            .size(40.dp, 150.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White)
            .clickable { playTone(frequency) },
        contentAlignment = Alignment.BottomCenter
    ) {
        Text(text = name, modifier = Modifier.padding(bottom = 8.dp))
    }
}

fun playTone(freq: Double) {
    val sampleRate = 44100
    val duration = 0.5 // seconds
    val numSamples = (duration * sampleRate).toInt()
    val sample = DoubleArray(numSamples)
    val buffer = ShortArray(numSamples)

    for (i in 0 until numSamples) {
        sample[i] = sin(2.0 * Math.PI * i.toDouble() / (sampleRate / freq))
        buffer[i] = (sample[i] * Short.MAX_VALUE).toInt().toShort()
    }

    val audioTrack = AudioTrack(
        AudioManager.STREAM_MUSIC,
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
        numSamples * 2,
        AudioTrack.MODE_STATIC
    )

    audioTrack.write(buffer, 0, numSamples)
    audioTrack.play()
    
    // Release is handled by the OS GC or manual management, 
    // for simple synth demos in MainActivity, standard AudioTrack is sufficient.
}