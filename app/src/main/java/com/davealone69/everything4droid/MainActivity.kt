package com.davealone69.everything4droid

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    
    private val sampleRate = 44100
    private var audioTrack: AudioTrack? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PianoAppScreen()
        }
    }

    private fun playTone(frequency: Double) {
        val duration = 0.3
        val numSamples = (duration * sampleRate).toInt()
        val generatedSound = DoubleArray(numSamples)
        val buffer = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            generatedSound[i] = sin(2.0 * Math.PI * i.toDouble() / (sampleRate / frequency))
            buffer[i] = (generatedSound[i] * 32767).toInt().toShort()
        }

        audioTrack = AudioTrack(
            AudioManager.STREAM_MUSIC,
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            numSamples * 2,
            AudioTrack.MODE_STATIC
        )
        audioTrack?.write(buffer, 0, numSamples)
        audioTrack?.play()
    }

    override fun onDestroy() {
        super.onDestroy()
        audioTrack?.release()
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Compose Piano", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(32.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                notes.forEach { (name, freq) ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(200.dp)
                            .background(Color.White, RoundedCornerShape(8.dp))
                            .clickable {
                                scope.launch(Dispatchers.IO) {
                                    playTone(freq)
                                }
                            },
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Text(name, modifier = Modifier.padding(bottom = 16.dp))
                    }
                }
            }
        }
    }
}