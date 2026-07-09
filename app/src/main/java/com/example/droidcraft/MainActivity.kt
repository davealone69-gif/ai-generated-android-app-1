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
import kotlinx.coroutines.withContext
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    private val sampleRate = 44100
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val scope = rememberCoroutineScope()
                    PianoScreen { freq ->
                        scope.launch {
                            playTone(freq)
                        }
                    }
                }
            }
        }
    }

    private suspend fun playTone(freqHz: Double) = withContext(Dispatchers.IO) {
        val durationMs = 250
        val numSamples = (durationMs * sampleRate / 1000)
        val generatedSnd = ShortArray(numSamples)
        
        for (i in 0 until numSamples) {
            val angle = 2.0 * Math.PI * i / (sampleRate / freqHz)
            val envelope = when {
                i < 100 -> i / 100.0
                i > numSamples - 100 -> (numSamples - i) / 100.0
                else -> 1.0
            }
            generatedSnd[i] = (sin(angle) * Short.MAX_VALUE * envelope).toInt().toShort()
        }

        val audioTrack = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
            numSamples * 2,
            AudioTrack.MODE_STATIC,
            0
        )
        
        audioTrack.write(generatedSnd, 0, numSamples)
        audioTrack.play()
        
        // Wait for buffer to finish
        Thread.sleep(durationMs.toLong())
        audioTrack.stop()
        audioTrack.release()
    }
}

@Composable
fun PianoScreen(onNotePressed: (Double) -> Unit) {
    val notes = mapOf("C" to 261.63, "D" to 293.66, "E" to 329.63, "F" to 349.23, "G" to 392.00)

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Synth Piano", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            notes.forEach { (name, freq) ->
                PianoKey(name) { onNotePressed(freq) }
            }
        }
    }
}

@Composable
fun PianoKey(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(60.dp, 150.dp)
            .background(Color.White, shape = RoundedCornerShape(4.dp))
            .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Text(label, modifier = Modifier.padding(bottom = 8.dp), color = Color.Black)
    }
}