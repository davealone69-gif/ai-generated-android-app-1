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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PianoApp()
        }
    }
}

@Composable
fun PianoApp() {
    val scope = rememberCoroutineScope()
    
    // Frequencies for one octave (C4 to C5)
    val notes = mapOf(
        "C" to 261.63, "C#" to 277.18, "D" to 293.66, "D#" to 311.13,
        "E" to 329.63, "F" to 349.23, "F#" to 369.99, "G" to 392.00,
        "G#" to 415.30, "A" to 440.00, "A#" to 466.16, "B" to 493.88
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Compose Synth Piano", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            notes.forEach { (name, freq) ->
                PianoKey(name) {
                    scope.launch(Dispatchers.Default) {
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
            .size(width = 40.dp, height = 120.dp)
            .background(Color.White, RoundedCornerShape(4.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Text(text = label, modifier = Modifier.padding(bottom = 8.dp))
    }
}

fun playTone(freqOfTone: Double) {
    val durationMs = 300
    val sampleRate = 44100
    val numSamples = durationMs * sampleRate / 1000
    val generatedSound = DoubleArray(numSamples)
    val buffer = ShortArray(numSamples)

    for (i in 0 until numSamples) {
        generatedSound[i] = Math.sin(2.0 * Math.PI * i / (sampleRate / freqOfTone))
        buffer[i] = (generatedSound[i] * Short.MAX_VALUE).toInt().toShort()
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
        .setBufferSizeInBytes(numSamples * 2)
        .build()

    audioTrack.play()
    audioTrack.write(buffer, 0, numSamples)
    audioTrack.stop()
    audioTrack.release()
}