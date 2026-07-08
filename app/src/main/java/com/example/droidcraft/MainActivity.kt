package com.example.droidcraft

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    private val sampleRate = 44100
    private lateinit var audioTrack: AudioTrack
    private val activeNotes = ConcurrentHashMap<Double, Job>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val minBufferSize = AudioTrack.getMinBufferSize(
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
            .setBufferSizeInBytes(minBufferSize * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack.play()

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = Color(0xFF121212))) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    PianoScreen(::noteOn, ::noteOff)
                }
            }
        }
    }

    private fun noteOn(freq: Double) {
        if (activeNotes.containsKey(freq)) return
        
        val job = scope.launch {
            var phase = 0.0
            val phaseInc = 2.0 * PI * freq / sampleRate
            val buffer = ShortArray(512)
            
            while (isActive) {
                for (i in buffer.indices) {
                    buffer[i] = (sin(phase) * 8000).toInt().toShort()
                    phase += phaseInc
                    if (phase > 2.0 * PI) phase -= 2.0 * PI
                }
                audioTrack.write(buffer, 0, buffer.size)
            }
        }
        activeNotes[freq] = job
    }

    private fun noteOff(freq: Double) {
        activeNotes.remove(freq)?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        audioTrack.stop()
        audioTrack.release()
    }
}

@Composable
fun PianoScreen(onDown: (Double) -> Unit, onUp: (Double) -> Unit) {
    val notes = listOf("C" to 261.63, "D" to 293.66, "E" to 329.63, "F" to 349.23, "G" to 392.00)
    
    Column(modifier = Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("DROIDCRAFT SYNTH", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            notes.forEach { (name, freq) ->
                PianoKey(name, { onDown(freq) }, { onUp(freq) })
            }
        }
        Spacer(modifier = Modifier.height(48.dp))
    }
}

@Composable
fun PianoKey(label: String, onDown: () -> Unit, onUp: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val color = if (pressed) MaterialTheme.colorScheme.primary else Color.White
    
    Box(
        modifier = Modifier
            .width(60.dp)
            .height(220.dp)
            .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
            .background(color)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        onDown()
                        tryAwaitRelease()
                        pressed = false
                        onUp()
                    }
                )
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        Text(label, modifier = Modifier.padding(bottom = 16.dp), color = Color.Black, style = MaterialTheme.typography.labelLarge)
    }
}