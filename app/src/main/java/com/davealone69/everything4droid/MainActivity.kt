package com.davealone69.everything4droid

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import kotlin.math.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF121212)) {
                    PianoApp()
                }
            }
        }
    }
}

enum class Waveform(val label: String) { SINE("Sine"), SQUARE("Square"), TRIANGLE("Triangle"), PIANO("Synth Piano") }

@Composable
fun PianoApp() {
    val scope = rememberCoroutineScope()
    var waveform by remember { mutableStateOf(Waveform.PIANO) }
    var decay by remember { mutableStateOf(1.2f) }
    var volume by remember { mutableStateOf(0.7f) }
    var octave by remember { mutableStateOf(4) }
    val activeNotes = remember { mutableStateMapOf<Int, Boolean>() }
    
    val synth = remember { AudioEngine() }
    DisposableEffect(Unit) { onDispose { synth.release() } }

    fun triggerNote(semitone: Int) {
        val freq = 440.0 * 2.0.pow(((12 * (octave + 1) + semitone) - 69) / 12.0)
        scope.launch(Dispatchers.Default) {
            activeNotes[semitone] = true
            synth.play(freq, waveform, decay, volume)
            delay(200)
            activeNotes[semitone] = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Visualizer(waveform)
        Controls(waveform, { waveform = it }, decay, { decay = it }, volume, { volume = it }, octave, { octave = it })
        Keyboard(activeNotes) { triggerNote(it) }
    }
}

class AudioEngine {
    private val sampleRate = 44100
    private var audioTrack: AudioTrack = AudioTrack.Builder()
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
        .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
        .setBufferSizeInBytes(sampleRate * 2)
        .setTransferMode(AudioTrack.MODE_STREAM)
        .build().apply { play() }

    fun play(freq: Double, type: Waveform, duration: Float, vol: Float) {
        val numSamples = (sampleRate * duration).toInt()
        val buffer = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val env = exp(-t * (4.0 / duration))
            val wave = when (type) {
                Waveform.SINE -> sin(2.0 * PI * freq * t)
                Waveform.SQUARE -> if (sin(2.0 * PI * freq * t) >= 0) 0.5 else -0.5
                Waveform.TRIANGLE -> abs(4.0 * ((t * freq) - floor((t * freq) + 0.5))) - 1.0
                Waveform.PIANO -> (sin(2.0 * PI * freq * t) + 0.5 * sin(4.0 * PI * freq * t) + 0.25 * sin(6.0 * PI * freq * t)) / 1.75
            }
            buffer[i] = (wave * env * 32767 * vol).toInt().toShort()
        }
        audioTrack.write(buffer, 0, buffer.size)
    }

    fun release() { audioTrack.release() }
}

@Composable
fun Visualizer(waveform: Waveform) {
    var phase by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) { while(true) { phase += 0.1f; delay(16) } }
    Canvas(modifier = Modifier.fillMaxWidth().height(80.dp).background(Color(0xFF1E1E1E), RoundedCornerShape(8.dp))) {
        val path = Path()
        for (x in 0..size.width.toInt() step 5) {
            val y = size.height / 2 + (sin(x * 0.05 + phase) * 20)
            if (x == 0) path.moveTo(x.toFloat(), y) else path.lineTo(x.toFloat(), y)
        }
        drawPath(path, Color(0xFF00FF66), style = Stroke(width = 3f))
    }
}

@Composable
fun Controls(wave: Waveform, onW: (Waveform) -> Unit, dec: Float, onD: (Float) -> Unit, vol: Float, onV: (Float) -> Unit, oct: Int, onO: (Int) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 16.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
            Waveform.values().forEach { w -> FilterChip(selected = w == wave, onClick = { onW(w) }, label = { Text(w.label) }) }
        }
        Slider(value = dec, onValueChange = onD, valueRange = 0.2f..2f)
        Slider(value = vol, onValueChange = onV)
    }
}

@Composable
fun Keyboard(active: Map<Int, Boolean>, onNote: (Int) -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(200.dp).pointerInput(Unit) {
        detectDragGestures { change, _ -> onNote((change.position.x / (size.width / 12)).toInt()) }
    }) {
        Row(modifier = Modifier.fillMaxSize()) {
            repeat(12) { i ->
                val isPressed = active[i] == true
                val color by animateColorAsState(if (isPressed) Color.Cyan else Color.White)
                Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(1.dp).background(color))
            }
        }
    }
}