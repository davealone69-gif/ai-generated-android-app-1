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
    val pianoKeys = listOf(
        261.63f to "C", 293.66f to "D", 329.63f to "E", 349.23f to "F",
        392.00f to "G", 440.00f to "A", 493.88f to "B", 523.25f to "C#"
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Compose Piano", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))
        Row(modifier = Modifier.fillMaxWidth().height(200.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            pianoKeys.forEach { (freq, label) ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(2.dp)
                        .background(Color.DarkGray, RoundedCornerShape(8.dp))
                        .clickable {
                            coroutineScope.launch(Dispatchers.Default) {
                                playTone(freq, sampleRate)
                            }
                        },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Text(label, color = Color.White, modifier = Modifier.padding(bottom = 16.dp))
                }
            }
        }
    }
}

private fun playTone(freq: Float, sampleRate: Int) {
    val durationMs = 300
    val numSamples = (durationMs * sampleRate) / 1000
    val sample = ShortArray(numSamples)
    
    for (i in 0 until numSamples) {
        sample[i] = (sin(2.0 * Math.PI * i / (sampleRate / freq)) * 32767.0).toInt().toShort()
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
        .setBufferSizeInBytes(numSamples * 2)
        .setTransferMode(AudioTrack.MODE_STATIC)
        .build()
    
    audioTrack.write(sample, 0, numSamples)
    audioTrack.play()
    
    // Release track after playback
    audioTrack.setNotificationMarkerPosition(numSamples)
    audioTrack.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
        override fun onMarkerReached(track: AudioTrack) {
            track.release()
        }
        override fun onPeriodicNotification(track: AudioTrack) {}
    })
}