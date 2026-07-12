package com.example.droidcraft

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.concurrent.Executors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.PI
import kotlin.math.pow

enum class WaveType {
    SINE, SQUARE, TRIANGLE, SAWTOOTH
}

class SynthEngine {
    private val executor = Executors.newCachedThreadPool()
    private val sampleRate = 22050 // Optimized standard sample rate for instant synth generation

    fun playNote(freq: Double, type: WaveType, durationMs: Int, volume: Float) {
        executor.execute {
            try {
                val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
                val buffer = ByteArray(numSamples * 2)
                
                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    val angle = 2.0 * PI * freq * t
                    val rawSample = when (type) {
                        WaveType.SINE -> sin(angle)
                        WaveType.SQUARE -> if (sin(angle) >= 0) 0.5 else -0.5
                        WaveType.TRIANGLE -> 2.0 * abs(2.0 * ((angle / (2.0 * PI)) % 1.0 - 0.5)) - 1.0
                        WaveType.SAWTOOTH -> 2.0 * ((angle / (2.0 * PI)) % 1.0 - 0.5)
                    }
                    
                    // Linear ADSR envelope (Attack and Release only for simplicity and latency removal)
                    val attackSamples = (sampleRate * 0.03).toInt() // 30ms attack
                    val releaseSamples = (sampleRate * 0.15).toInt() // 150ms release
                    val envelope = when {
                        i < attackSamples -> i.toFloat() / attackSamples
                        i > numSamples - releaseSamples -> (numSamples - i).toFloat() / releaseSamples
                        else -> 1.0f
                    }
                    
                    val sampleValue = (rawSample * envelope * 32767 * volume * 0.5).toInt().coerceIn(-32768, 32767)
                    buffer[i * 2] = (sampleValue and 0xFF).toByte()
                    buffer[i * 2 + 1] = ((sampleValue shr 8) and 0xFF).toByte()
                }

                val track = AudioTrack.Builder()
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
                    .setBufferSizeInBytes(buffer.size)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(buffer, 0, buffer.size)
                track.play()
                
                // Allow tone to complete play before tearing down
                Thread.sleep(durationMs.toLong() + 30)
                track.stop()
                track.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun shutdown() {
        executor.shutdown()
    }
}

data class PianoKey(
    val name: String,
    val semitoneOffset: Int,
    val isBlack: Boolean,
    val centerIndexOffset: Float = 0f
)

class MainActivity : ComponentActivity() {
    private val synthEngine = SynthEngine()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MainAppScreen(synthEngine)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        synthEngine.shutdown()
    }
}

@Composable
fun MainAppScreen(synthEngine: SynthEngine) {
    val scope = rememberCoroutineScope()
    var currentOctave by remember { mutableStateOf(4) }
    var currentWaveType by remember { mutableStateOf(WaveType.SINE) }
    var sustainMs by remember { mutableStateOf(600) }
    var volume by remember { mutableStateOf(0.7f) }
    
    var lastPlayedNote by remember { mutableStateOf("C") }
    var lastPlayedFreq by remember { mutableStateOf(261.63) }

    // State list tracking currently active/pressed keys visually
    val activeNotes = remember { mutableStateListOf<Int>() }

    val whiteKeys = remember {
        listOf(
            PianoKey("C", 0, false),
            PianoKey("D", 2, false),
            PianoKey("E", 4, false),
            PianoKey("F", 5, false),
            PianoKey("G", 7, false),
            PianoKey("A", 9, false),
            PianoKey("B", 11, false),
            PianoKey("C+", 12, false)
        )
    }

    val blackKeys = remember {
        listOf(
            PianoKey("C#", 1, true, 1f),
            PianoKey("D#", 3, true, 2f),
            PianoKey("F#", 6, true, 4f),
            PianoKey("G#", 8, true, 5f),
            PianoKey("A#", 10, true, 6f)
        )
    }

    fun getFrequency(octave: Int, semitoneOffset: Int): Double {
        val baseC = 261.63 * 2.0.pow((octave - 4).toDouble())
        return baseC * 2.0.pow(semitoneOffset / 12.0)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0F0F14)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header / Brand
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "DROIDCRFT SYNTH",
                        color = Color(0xFF00FFC2),
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.titleMedium,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text = "Custom Wave Synthesis Engine",
                        color = Color.LightGray.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                
                // Octave controller
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { if (currentOctave > 1) currentOctave-- },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E24)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.defaultMinSize(minWidth = 36.dp, minHeight = 36.dp)
                    ) {
                        Text("-", color = Color(0xFF00FFC2), fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = "OCTAVE $currentOctave",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(
                        onClick = { if (currentOctave < 7) currentOctave++ },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E24)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.defaultMinSize(minWidth = 36.dp, minHeight = 36.dp)
                    ) {
                        Text("+", color = Color(0xFF00FFC2), fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Main settings scroll container
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                // Waveform Selector Cards
                Text(
                    text = "WAVEFORM SELECTOR",
                    color = Color.White.copy(alpha = 0.5f),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WaveType.values().forEach { type ->
                        val isSelected = currentWaveType == type
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (isSelected) Color(0xFF00FFC2) else Color(0xFF1E1E24),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) Color.White else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .pointerInput(type) {
                                    detectTapGestures {
                                        currentWaveType = type
                                    }
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = type.name,
                                    color = if (isSelected) Color.Black else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Synthesizer Sliders
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "SUSTAIN: ${sustainMs}ms",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium
                        )
                        Slider(
                            value = sustainMs.toFloat(),
                            onValueChange = { sustainMs = it.toInt() },
                            valueRange = 200f..1500f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF00FFC2),
                                activeTrackColor = Color(0xFF00FFC2),
                                inactiveTrackColor = Color(0xFF1E1E24)
                            )
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "VOLUME: ${(volume * 100).toInt()}%",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium
                        )
                        Slider(
                            value = volume,
                            onValueChange = { volume = it },
                            valueRange = 0.1f..1.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF00D2FF),
                                activeTrackColor = Color(0xFF00D2FF),
                                inactiveTrackColor = Color(0xFF1E1E24)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Realtime Waveform Visualization
                Text(
                    text = "VISUALIZER: $lastPlayedNote (${String.format("%.2f", lastPlayedFreq)} Hz)",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(modifier = Modifier.height(6.dp))
                WaveformVisualizer(
                    waveType = currentWaveType,
                    activeNote = lastPlayedNote,
                    frequency = lastPlayedFreq
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Piano Keyboard UI
            Text(
                text = "PIANO KEYS (TOUCH TO PLAY)",
                color = Color.White.copy(alpha = 0.5f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
                    .background(Color.Black, shape = RoundedCornerShape(12.dp))
                    .padding(4.dp)
            ) {
                val containerWidth = maxWidth
                val totalWhiteKeys = whiteKeys.size
                val whiteKeyWidth = containerWidth / totalWhiteKeys
                val blackKeyWidth = whiteKeyWidth * 0.65f

                // Render White Keys
                Row(modifier = Modifier.fillMaxSize()) {
                    whiteKeys.forEach { key ->
                        val isActive = activeNotes.contains(key.semitoneOffset)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(horizontal = 2.dp)
                                .background(
                                    color = if (isActive) Color(0xFF00FFC2) else Color.White,
                                    shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)
                                )
                                .pointerInput(key) {
                                    detectTapGestures(
                                        onPress = {
                                            val freq = getFrequency(currentOctave, key.semitoneOffset)
                                            lastPlayedNote = "${key.name}$currentOctave"
                                            lastPlayedFreq = freq
                                            scope.launch {
                                                activeNotes.add(key.semitoneOffset)
                                                synthEngine.playNote(freq, currentWaveType, sustainMs, volume)
                                                delay(sustainMs.toLong().coerceAtLeast(150))
                                                activeNotes.remove(key.semitoneOffset)
                                            }
                                        }
                                    )
                                },
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Text(
                                text = key.name,
                                color = if (isActive) Color.Black else Color.DarkGray,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(bottom = 12.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Render Black Keys Overlaid
                blackKeys.forEach { key ->
                    val isActive = activeNotes.contains(key.semitoneOffset)
                    // Offset positioning matches key gaps precisely
                    val leftOffset = (key.centerIndexOffset * whiteKeyWidth.value).dp - (blackKeyWidth.value / 2).dp

                    Box(
                        modifier = Modifier
                            .absoluteOffset(x = leftOffset)
                            .width(blackKeyWidth)
                            .height(135.dp)
                            .background(
                                color = if (isActive) Color(0xFF00D2FF) else Color(0xFF232329),
                                shape = RoundedCornerShape(bottomStart = 5.dp, bottomEnd = 5.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = Color.Black,
                                shape = RoundedCornerShape(bottomStart = 5.dp, bottomEnd = 5.dp)
                            )
                            .pointerInput(key) {
                                detectTapGestures(
                                    onPress = {
                                        val freq = getFrequency(currentOctave, key.semitoneOffset)
                                        lastPlayedNote = "${key.name}$currentOctave"
                                        lastPlayedFreq = freq
                                        scope.launch {
                                            activeNotes.add(key.semitoneOffset)
                                            synthEngine.playNote(freq, currentWaveType, sustainMs, volume)
                                            delay(sustainMs.toLong().coerceAtLeast(150))
                                            activeNotes.remove(key.semitoneOffset)
                                        }
                                    }
                                )
                            },
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Text(
                            text = key.name,
                            color = if (isActive) Color.Black else Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            modifier = Modifier.padding(bottom = 8.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WaveformVisualizer(waveType: WaveType, activeNote: String, frequency: Double) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .background(Color(0xFF15151A), shape = RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF2A2A35), shape = RoundedCornerShape(8.dp))
    ) {
        val width = size.width
        val height = size.height
        val path = Path()
        
        val cycles = 3.5f
        val points = 250
        
        path.moveTo(0f, height / 2)
        for (i in 0..points) {
            val x = (i.toFloat() / points) * width
            val ratio = i.toFloat() / points
            val angle = ratio * cycles * 2.0 * PI
            
            // Generate visual approximation corresponding to chosen synthesis wave
            val yOffset = when (waveType) {
                WaveType.SINE -> sin(angle)
                WaveType.SQUARE -> if (sin(angle) >= 0) 0.6 else -0.6
                WaveType.TRIANGLE -> 2.0 * abs(2.0 * ((angle / (2.0 * PI)) % 1.0 - 0.5)) - 1.0
                WaveType.SAWTOOTH -> 2.0 * ((angle / (2.0 * PI)) % 1.0 - 0.5)
            }
            
            // Envelope damping visual output at ends for nice aesthetic
            val boundaryFade = sin(ratio * PI)
            val y = (height / 2) + (yOffset * (height * 0.38) * boundaryFade).toFloat()
            
            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        drawPath(
            path = path,
            color = Color(0xFF00FFC2),
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}