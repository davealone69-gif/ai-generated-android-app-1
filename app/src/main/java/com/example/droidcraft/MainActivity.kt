package com.example.droidcraft

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
    val keys = listOf(
        "C" to 261.63, "D" to 293.66, "E" to 329.63,
        "F" to 349.23, "G" to 392.00, "A" to 440.00, "B" to 493.88
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Synth Piano", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            keys.forEach { (note, freq) ->
                PianoKey(note, freq)
            }
        }
    }
}

@Composable
fun PianoKey(note: String, frequency: Double) {
    Box(
        modifier = Modifier
            .size(45.dp, 150.dp)
            .border(1.dp, Color.Black, RoundedCornerShape(4.dp))
            .background(Color.White, RoundedCornerShape(4.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        playTone(frequency)
                    }
                )
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        Text(text = note, modifier = Modifier.padding(bottom = 8.dp))
    }
}

fun playTone(freq: Double) {
    val sampleRate = 44100
    val durationMs = 300
    val numSamples = (durationMs * sampleRate) / 1000
    val buffer = ShortArray(numSamples)
    
    for (i in 0 until numSamples) {
        buffer[i] = (sin(2.0 * Math.PI * i.toDouble() / (sampleRate / freq)) * Short.MAX_VALUE * 0.5).toInt().toShort()
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
}