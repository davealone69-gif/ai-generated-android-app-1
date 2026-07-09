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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PianoScreen()
        }
    }
}

@Composable
fun PianoScreen() {
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
        Text("DroidCraft Synth Piano", style = MaterialTheme.typography.headlineMedium)
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

fun playTone(freq: Double) {
    val durationMs = 500
    val sampleRate = 44100
    val numSamples = durationMs * sampleRate / 1000
    val sample = DoubleArray(numSamples)
    val generatedSound = ByteArray(2 * numSamples)

    for (i in 0 until numSamples) {
        sample[i] = sin(2.0 * Math.PI * i / (sampleRate / freq))
        val pcm = (sample[i] * 32767).toInt()
        generatedSound[2 * i] = (pcm and 0x00ff).toByte()
        generatedSound[2 * i + 1] = ((pcm and 0xff00) ushr 8).toByte()
    }

    val audioTrack = AudioTrack(
        AudioManager.STREAM_MUSIC,
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
        generatedSound.size,
        AudioTrack.MODE_STATIC
    )
    audioTrack.write(generatedSound, 0, generatedSound.size)
    audioTrack.play()
    audioTrack.release()
}