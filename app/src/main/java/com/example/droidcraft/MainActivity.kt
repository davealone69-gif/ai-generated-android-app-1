package com.example.droidcraft

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sin
import kotlin.math.PI

class MainActivity : ComponentActivity() {
    private var audioTrack: AudioTrack? = null
    private val sampleRate = 44100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build())
            .setAudioFormat(AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build())
            .setBufferSizeInBytes(bufferSize)
            .build()
        
        audioTrack?.play()

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PianoScreen(::playSound)
                }
            }
        }
    }

    private fun playSound(freqHz: Double) {
        val duration = 0.2
        val numSamples = (duration * sampleRate).toInt()
        val buffer = ShortArray(numSamples)
        
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            // Simple ADSR envelope: Linear Attack/Release to prevent clicking
            val envelope = when {
                i < 1000 -> i / 1000.0
                i > numSamples - 1000 -> (numSamples - i) / 1000.0
                else -> 1.0
            }
            val angle = 2.0 * PI * freqHz * t
            buffer[i] = (sin(angle) * Short.MAX_VALUE * 0.5 * envelope).toInt().toShort()
        }
        audioTrack?.write(buffer, 0, numSamples)
    }

    override fun onDestroy() {
        super.onDestroy()
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }
}

@Composable
fun PianoScreen(onPlay: (Double) -> Unit) {
    val notes = mapOf("C" to 261.63, "D" to 293.66, "E" to 329.63, "F" to 349.23, "G" to 392.00)
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("DroidCraft Pro", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(48.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            notes.forEach { (name, freq) ->
                PianoKey(name) {
                    scope.launch(Dispatchers.Default) { onPlay(freq) }
                }
            }
        }
    }
}

@Composable
fun PianoKey(label: String, onClick: () -> Unit) {
    var isPressed by remember { mutableStateOf(false) }
    val color by animateColorAsState(if (isPressed) Color.LightGray else Color.White, label = "Color")

    Box(
        modifier = Modifier
            .size(65.dp, 200.dp)
            .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
            .background(color)
            .border(1.dp, Color.Gray, RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        onClick()
                        tryAwaitRelease()
                        isPressed = false
                    }
                )
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        Text(label, modifier = Modifier.padding(bottom = 16.dp), color = Color.Black)
    }
}