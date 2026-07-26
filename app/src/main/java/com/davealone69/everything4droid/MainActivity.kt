package com.davealone69.everything4droid

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.*

class MainActivity : ComponentActivity() {
    private val synthEngine = AudioEngine()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        synthEngine.start()
        setContent {
            MainAppScreen(synthEngine)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        synthEngine.destroy()
    }
}

// -------------------------------------------------------------
// Model definitions
// -------------------------------------------------------------
data class SynthKey(
    val name: String,
    val frequency: Float,
    val isBlack: Boolean,
    val octave: Int,
    val adjacentWhiteIndex: Int = -1
)

// -------------------------------------------------------------
// Dynamic Polyphonic Voice Model
// -------------------------------------------------------------
class SynthVoice(
    val keyName: String,
    val frequency: Float,
    val waveform: String,
    val sampleRate: Int = 44100
) {
    var sampleIndex = 0
    var active = true
    private val totalDurationSamples = (sampleRate * 2.5f).toInt() // Max duration safety clamp

    fun nextSample(decayRate: Float): Float {
        if (!active) return 0f
        val t = sampleIndex.toFloat() / sampleRate
        if (sampleIndex >= totalDurationSamples) {
            active = false
            return 0f
        }

        val attackTime = 0.005f
        val amplitudeEnvelope = if (t < attackTime) {
            (t / attackTime) * exp(-decayRate * t)
        } else {
            exp(-decayRate * t)
        }

        val angle = 2.0 * PI * frequency.toDouble() * t.toDouble()
        val rawValue = when (waveform) {
            "Sine" -> sin(angle)
            "Square" -> if (sin(angle) >= 0.0) 0.6 else -0.6
            "Triangle" -> asin(sin(angle)) * (2.0 / PI)
            "Piano" -> {
                val h1 = sin(angle)
                val h2 = 0.5 * sin(2.0 * angle) * exp(-1.5 * decayRate * t)
                val h3 = 0.25 * sin(3.0 * angle) * exp(-2.5 * decayRate * t)
                val h4 = 0.125 * sin(4.0 * angle) * exp(-3.5 * decayRate * t)
                (h1 + h2 + h3 + h4) / 1.875
            }
            else -> sin(angle)
        }

        sampleIndex++
        return (rawValue * amplitudeEnvelope).toFloat()
    }
}

// -------------------------------------------------------------
// Real-time Low-latency Streaming Audio Engine
// -------------------------------------------------------------
class AudioEngine {
    private val sampleRate = 44100
    private val bufferSize = AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    ).coerceAtLeast(1024)

    private var audioTrack: AudioTrack? = null
    private val activeVoices = CopyOnWriteArrayList<SynthVoice>()
    private var isRunning = false
    private var mixerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    var sustain: Boolean = true
    var volume: Float = 0.85f

    fun start() {
        if (isRunning) return
        isRunning = true

        audioTrack = AudioTrack.Builder()
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
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.play()

        mixerJob = scope.launch {
            val buffer = ShortArray(512)
            while (isActive && isRunning) {
                val currentDecay = if (sustain) 1.2f else 3.8f
                val currentVol = volume

                for (i in buffer.indices) {
                    var mixedSample = 0f
                    if (activeVoices.isNotEmpty()) {
                        for (voice in activeVoices) {
                            if (voice.active) {
                                mixedSample += voice.nextSample(currentDecay)
                            } else {
                                activeVoices.remove(voice)
                            }
                        }
                    }
                    val finalSample = (mixedSample * currentVol * Short.MAX_VALUE)
                    buffer[i] = finalSample.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }
                audioTrack?.write(buffer, 0, buffer.size)
            }
        }
    }

    fun playNote(keyName: String, frequency: Float, waveform: String) {
        // Remove preexisting instances to prevent overlapping key phase clipping
        activeVoices.removeAll { it.keyName == keyName }
        activeVoices.add(SynthVoice(keyName, frequency, waveform, sampleRate))
    }

    fun stopAll() {
        activeVoices.clear()
    }

    fun destroy() {
        isRunning = false
        mixerJob?.cancel()
        stopAll()
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

// -------------------------------------------------------------
// Keyboard Generation Helper
// -------------------------------------------------------------
fun generateKeyboard(baseOctave: Int): Pair<List<SynthKey>, List<SynthKey>> {
    val notesInOctave = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    val blackKeyIndices = setOf(1, 3, 6, 8, 10)
    val whiteKeys = mutableListOf<SynthKey>()
    val blackKeys = mutableListOf<SynthKey>()
    var whiteCount = 0

    for (oct in baseOctave..(baseOctave + 1)) {
        for (i in 0..11) {
            val noteName = notesInOctave[i]
            val isBlack = blackKeyIndices.contains(i)
            val midi = 12 + (oct * 12) + i
            val freq = 440.0f * 2.0f.pow((midi - 69).toFloat() / 12.0f)

            if (isBlack) {
                blackKeys.add(
                    SynthKey(
                        name = "$noteName$oct",
                        frequency = freq,
                        isBlack = true,
                        octave = oct,
                        adjacentWhiteIndex = whiteCount - 1
                    )
                )
            } else {
                whiteKeys.add(
                    SynthKey(
                        name = "$noteName$oct",
                        frequency = freq,
                        isBlack = false,
                        octave = oct
                    )
                )
                whiteCount++
            }
        }
    }
    return Pair(whiteKeys, blackKeys)
}

// -------------------------------------------------------------
// Interactive Jetpack Compose UI
// -------------------------------------------------------------
@Composable
fun MainAppScreen(synthEngine: AudioEngine) {
    var baseOctave by remember { mutableStateOf(3) }
    var waveform by remember { mutableStateOf("Piano") }
    var sustain by remember { mutableStateOf(true) }
    var volume by remember { mutableStateOf(0.85f) }
    
    // Polyphonic reactive key tracking (Resolves key overlap conflicts)
    val activeNotes = remember { mutableStateListOf<String>() }
    var playHistory by remember { mutableStateOf(listOf<String>()) }
    var demoJob by remember { mutableStateOf<Job?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val (whiteKeys, blackKeys) = remember(baseOctave) { generateKeyboard(baseOctave) }

    // Sync parameters into runtime engine immediately
    LaunchedEffect(sustain, volume) {
        synthEngine.sustain = sustain
        synthEngine.volume = volume
    }

    val triggerNote: (SynthKey) -> Unit = { key ->
        if (!activeNotes.contains(key.name)) {
            activeNotes.add(key.name)
        }
        synthEngine.playNote(key.name, key.frequency, waveform)
        
        playHistory = if (playHistory.size > 15) {
            playHistory.drop(1) + key.name
        } else {
            playHistory + key.name
        }

        coroutineScope.launch {
            delay(280L)
            activeNotes.remove(key.name)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0F0F13)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header panel
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "AURA SYNTH STUDIO",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF00E5FF),
                    letterSpacing = 2.5.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Zero Latency Dynamic Multi-Voice Synthesis",
                    fontSize = 11.sp,
                    color = Color.LightGray.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
            }

            // Real-time Sound Visualizer Panel
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(55.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF141419))
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                val infiniteTransition = rememberInfiniteTransition(label = "visualizer")
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val totalBars = 20
                    for (i in 0 until totalBars) {
                        val duration = 200 + (i * 35)
                        val animHeight by infiniteTransition.animateFloat(
                            initialValue = 0.12f,
                            targetValue = 0.95f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(duration, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "bar_$i"
                        )
                        val isAnyNotePlaying = activeNotes.isNotEmpty()
                        val scaledHeight = if (isAnyNotePlaying) animHeight else 0.12f
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(scaledHeight)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color(0xFF00B0FF), Color(0xFF00E676))
                                    )
                                )
                        )
                    }
                }
            }

            // Multi-parameter Synth Controllers
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF16161F)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Row 1: Waveform parameters
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "WAVEFORM:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("Sine", "Square", "Triangle", "Piano").forEach { wave ->
                                val selected = waveform == wave
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selected) Color(0xFF00E5FF) else Color(0xFF252533))
                                        .clickable { waveform = wave }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = wave,
                                        color = if (selected) Color.Black else Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFF252533))

                    // Row 2: Volume, Octave and Sustain Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "OCTAVE: C$baseOctave - B${baseOctave + 1}",
                                fontSize = 11.sp,
                                color = Color.LightGray,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = { if (baseOctave > 2) baseOctave-- },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF252533)),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("DOWN", fontSize = 10.sp, color = Color.White)
                                }
                                Button(
                                    onClick = { if (baseOctave < 5) baseOctave++ },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF252533)),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("UP", fontSize = 10.sp, color = Color.White)
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("SUSTAIN", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            Switch(
                                checked = sustain,
                                onCheckedChange = { sustain = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF00E5FF),
                                    checkedTrackColor = Color(0xFF00373E)
                                )
                            )
                        }
                    }

                    HorizontalDivider(color = Color(0xFF252533))

                    // Row 3: Master Gain & Interactive Playback Demos
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1.2f)
                        ) {
                            Text("GAIN: ", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            Slider(
                                value = volume,
                                onValueChange = { volume = it },
                                valueRange = 0.1f..1.0f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF00E5FF),
                                    activeTrackColor = Color(0xFF00E5FF)
                                ),
                                modifier = Modifier.height(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Play/Stop Demo Song button
                        Button(
                            onClick = {
                                if (demoJob?.isActive == true) {
                                    demoJob?.cancel()
                                    synthEngine.stopAll()
                                    activeNotes.clear()
                                } else {
                                    demoJob = coroutineScope.launch(Dispatchers.Default) {
                                        val odeToJoy = listOf(
                                            "E4" to 300L, "E4" to 300L, "F4" to 300L, "G4" to 300L,
                                            "G4" to 300L, "F4" to 300L, "E4" to 300L, "D4" to 300L,
                                            "C4" to 300L, "C4" to 300L, "D4" to 300L, "E4" to 300L,
                                            "E4" to 450L, "D4" to 150L, "D4" to 500L,
                                            "E4" to 300L, "E4" to 300L, "F4" to 300L, "G4" to 300L,
                                            "G4" to 300L, "F4" to 300L, "E4" to 300L, "D4" to 300L,
                                            "C4" to 300L, "C4" to 300L, "D4" to 300L, "E4" to 300L,
                                            "D4" to 450L, "C4" to 150L, "C4" to 500L
                                        )

                                        for (item in odeToJoy) {
                                            if (!isActive) break
                                            val noteName = item.first
                                            val matchedKey = (whiteKeys + blackKeys).find {
                                                it.name.startsWith(noteName.substring(0, noteName.length - 1))
                                            }
                                            val targetFreq = matchedKey?.frequency ?: 329.63f
                                            val finalKeyName = matchedKey?.name ?: noteName

                                            withContext(Dispatchers.Main) {
                                                if (!activeNotes.contains(finalKeyName)) {
                                                    activeNotes.add(finalKeyName)
                                                }
                                                playHistory = if (playHistory.size > 15) {
                                                    playHistory.drop(1) + finalKeyName
                                                } else {
                                                    playHistory + finalKeyName
                                                }
                                            }

                                            synthEngine.playNote(finalKeyName, targetFreq, waveform)
                                            delay(item.second)

                                            withContext(Dispatchers.Main) {
                                                activeNotes.remove(finalKeyName)
                                            }
                                            delay(50L) // articulate gap
                                        }
                                        withContext(Dispatchers.Main) {
                                            activeNotes.clear()
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (demoJob?.isActive == true) Color(0xFFFF1744) else Color(0xFF00E676)
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(
                                imageVector = if (demoJob?.isActive == true) Icons.Filled.Refresh else Icons.Filled.PlayArrow,
                                contentDescription = "Play/Stop Demo",
                                tint = Color.Black,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (demoJob?.isActive == true) "STOP" else "PLAY DEMO",
                                color = Color.Black,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Real-time dynamic Tape Stream of Notes Played
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "SYNTH RECORDER TRAIL:",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF141419))
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (playHistory.isEmpty()) {
                        Text(
                            text = "Play keys to stream real-time notation...",
                            color = Color.DarkGray,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState(), reverseScrolling = true)
                        ) {
                            playHistory.forEach { note ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF252533))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = note,
                                        color = Color(0xFF00E5FF),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Premium Scrollable Tactile Keyboard Container
            val whiteKeyWidth = 56.dp
            val whiteKeyHeight = 210.dp
            val blackKeyWidth = 36.dp
            val blackKeyHeight = 125.dp
            val scrollState = rememberScrollState()

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0B0B0E))
                    .padding(vertical = 10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .horizontalScroll(scrollState)
                        .fillMaxHeight()
                        .width(whiteKeyWidth * whiteKeys.size)
                ) {
                    // Layer 1: White Keys
                    Row(modifier = Modifier.fillMaxSize()) {
                        whiteKeys.forEach { key ->
                            val isHighlighted = activeNotes.contains(key.name)
                            Box(
                                modifier = Modifier
                                    .width(whiteKeyWidth)
                                    .fillMaxHeight()
                                    .padding(horizontal = 1.5.dp)
                                    .shadow(
                                        elevation = if (isHighlighted) 10.dp else 2.dp,
                                        shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)
                                    )
                                    .background(
                                        brush = Brush.verticalGradient(
                                            colors = if (isHighlighted) {
                                                listOf(Color(0xFF80D8FF), Color(0xFF00B0FF))
                                            } else {
                                                listOf(Color(0xFFFFFFFF), Color(0xFFE5E5E9))
                                            }
                                        ),
                                        shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)
                                    )
                                    .clickable { triggerNote(key) },
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Text(
                                    text = key.name,
                                    color = if (isHighlighted) Color.White else Color.Black,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.SansSerif,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )
                            }
                        }
                    }

                    // Layer 2: Black Keys
                    blackKeys.forEach { key ->
                        val isHighlighted = activeNotes.contains(key.name)
                        val offsetDp = (key.adjacentWhiteIndex * whiteKeyWidth.value + (whiteKeyWidth.value - blackKeyWidth.value / 2)).dp

                        Box(
                            modifier = Modifier
                                .offset(x = offsetDp)
                                .width(blackKeyWidth)
                                .height(blackKeyHeight)
                                .shadow(
                                    elevation = if (isHighlighted) 12.dp else 4.dp,
                                    shape = RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp)
                                )
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = if (isHighlighted) {
                                            listOf(Color(0xFFFF8A80), Color(0xFFFF1744))
                                        } else {
                                            listOf(Color(0xFF2C2C35), Color(0xFF141419))
                                        }
                                    ),
                                    shape = RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp)
                                )
                                .clickable { triggerNote(key) },
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Text(
                                text = key.name.replace(key.octave.toString(), ""),
                                color = if (isHighlighted) Color.White else Color.LightGray,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}