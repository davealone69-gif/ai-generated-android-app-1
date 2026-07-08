package com.example.droidcraft

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.sin

class AudioEngine {
    private val sampleRate = 44100
    private val activeOscillators = ConcurrentHashMap<Double, Double>()
    private var audioTrack: AudioTrack? = null
    private var job: Job? = null

    fun start() {
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
            .setBufferSizeInBytes(minBufferSize * 4)
            .build()
        audioTrack?.play()

        job = CoroutineScope(Dispatchers.Default).launch {
            val buffer = ShortArray(256)
            val phases = mutableMapOf<Double, Double>()
            
            while (isActive) {
                for (i in buffer.indices) {
                    var sample = 0.0
                    val currentNotes = activeOscillators.keys.toList()
                    for (freq in currentNotes) {
                        val phase = phases.getOrDefault(freq, 0.0)
                        sample += sin(phase) * 0.2
                        phases[freq] = phase + (2.0 * PI * freq / sampleRate)
                        if (phases[freq]!! > 2.0 * PI) phases[freq] = phases[freq]!! - 2.0 * PI
                    }
                    buffer[i] = (sample.coerceIn(-1.0, 1.0) * 32767).toInt().toShort()
                }
                audioTrack?.write(buffer, 0, buffer.size)
            }
        }
    }

    fun noteOn(freq: Double) = activeOscillators.put(freq, 0.0)
    fun noteOff(freq: Double) = activeOscillators.remove(freq)

    fun release() {
        job?.cancel()
        audioTrack?.stop()
        audioTrack?.release()
    }
}

class MainActivity : ComponentActivity() {
    private val engine = AudioEngine()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        engine.start()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFBB86FC), background = Color(0xFF121212))) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    PianoScreen(engine::noteOn, engine::noteOff)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        engine.release()
    }
}

@Composable
fun PianoScreen(onDown: (Double) -> Unit, onUp: (Double) -> Unit) {
    val notes = listOf("C" to 261.63, "D" to 293.66, "E" to 329.63, "F" to 349.23, "G" to 392.00, "A" to 440.00, "B" to 493.88)
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center) {
        Text("DROIDCRAFT SYNTH", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 32.dp).align(Alignment.CenterHorizontally))
        Row(modifier = Modifier.fillMaxWidth().height(250.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            notes.forEach { (name, freq) -> PianoKey(name, { onDown(freq) }, { onUp(freq) }) }
        }
    }
}

@Composable
fun PianoKey(label: String, onDown: () -> Unit, onUp: () -> Unit) {
    var isPressed by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(45.dp)
            .padding(2.dp)
            .background(if (isPressed) MaterialTheme.colorScheme.primary else Color.White, RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { isPressed = true; onDown() },
                    onDragEnd = { isPressed = false; onUp() },
                    onDragCancel = { isPressed = false; onUp() },
                    onDrag = { _, _ -> }
                )
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        Text(label, color = Color.Black, modifier = Modifier.padding(bottom = 12.dp))
    }
}