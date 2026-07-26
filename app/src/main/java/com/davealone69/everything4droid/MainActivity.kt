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
import kotlin.math.*

class MainActivity : ComponentActivity() {
    private val synthEngine = PianoSynthEngine()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MainAppScreen(synthEngine)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        synthEngine.stopAll()
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
// Low-latency custom audio synthesis engine
// -------------------------------------------------------------
class PianoSynthEngine {
    private val cache = java.util.concurrent.ConcurrentHashMap<String, ShortArray>()
    private val activeTracks = java.util.concurrent.CopyOnWriteArrayList<AudioTrack>()

    // Pre-calculate audio buffers to ensure instantaneous touch response
    fun preheat(keys: List<SynthKey>, waveform: String, sustain: Boolean, volume: Float) {
        Thread {
            for (key in keys) {
                val duration = if (sustain) 1.8f else 1.0f
                val samples = generatePianoSound(key.frequency, waveform, sustain, duration)
                if (volume != 1.0f) {
                    for (i in samples.indices) {
                        samples[i] = (samples[i] * volume).toInt()
                            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    }
                }
                cache[key.name] = samples
            }
        }.start()
    }

    // Play a note. If not cached, synthesize on-the-fly instantly
    fun play(keyName: String, fallbackFreq: Float, waveform: String, sustain: Boolean, volume: Float) {
        Thread {
            try {
                val samples = cache[keyName] ?: run {
                    val duration = if (sustain) 1.8f else 1.0f
                    val generated = generatePianoSound(fallbackFreq, waveform, sustain, duration)
                    if (volume != 1.0f) {
                        for (i in generated.indices) {
                            generated[i] = (generated[i] * volume).toInt()
                                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                        }
                    }
                    generated
                }

                val minBufSize = AudioTrack.getMinBufferSize(
                    44100,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val size = samples.size * 2
                val audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(44100)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(maxOf(minBufSize, size))
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                audioTrack.write(samples, 0, samples.size)
                audioTrack.play()
                activeTracks.add(audioTrack)

                val durationMs = (samples.size.toFloat() / 44100f * 1000).toLong()
                Thread.sleep(durationMs + 100)

                audioTrack.stop()
                audioTrack.release()
                activeTracks.remove(audioTrack)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    fun stopAll() {
        for (track in activeTracks) {
            try {
                track.stop()
                track.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        activeTracks.clear()
    }

    fun clearCache() {
        cache.clear()
    }

    // Pure dynamic digital wave generation with dynamic decay envelopes
    private fun generatePianoSound(
        frequency: Float,
        waveform: String,
        sustain: Boolean,
        durationSeconds: Float
    ): ShortArray {
        val sampleRate = 44100
        val numSamples = (sampleRate * durationSeconds).toInt()
        val samples = ShortArray(numSamples)
        val decay = if (sustain) 1.2f else 3.8f

        for (i in 0 until numSamples) {
            val t = i.toFloat() / sampleRate
            val attackTime = 0.006f
            val amplitudeEnvelope = if (t < attackTime) {
                (t / attackTime) * exp(-decay * t)
            } else {
                exp(-decay * t)
            }

            val angle = 2.0 * PI * frequency.toDouble() * t.toDouble()
            val rawValue = when (waveform) {
                "Sine" -> sin(angle)
                "Square" -> if (sin(angle) >= 0.0) 1.0 else -1.0
                "Triangle" -> asin(sin(angle)) * (2.0 / PI)
                "Piano" -> {
                    // Physical harmonic synthesis matching acoustic instruments
                    val h1 = sin(angle)
                    val h2 = 0.5 * sin(2.0 * angle) * exp(-1.5 * decay.toDouble() * t.toDouble())
                    val h3 = 0.25 * sin(3.0 * angle) * exp(-2.5 * decay.toDouble() * t.toDouble())
                    val h4 = 0.125 * sin(4.0 * angle) * exp(-3.5 * decay.toDouble() * t.toDouble())
                    (h1 + h2 + h3 + h4) / 1.875
                }
                else -> sin(angle)
            }

            val sampleValue = (rawValue * amplitudeEnvelope * Short.MAX_VALUE).toInt()
            samples[i] = sampleValue.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return samples
    }
}

// -------------------------------------------------------------
// Dynamic Key Generator for customizable ranges
// -------------------------------------------------------------
fun generateKeyboard(baseOctave: Int): Pair<List<SynthKey>, List<SynthKey>> {
    val notesInOctave = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    val blackKeyIndices = setOf(1, 3, 6, 8, 10)
    val whiteKeys = mutableListOf<SynthKey>()
    val blackKeys = mutableListOf<SynthKey>()
    var whiteCount = 0

    // Generate 2 entire consecutive octaves
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
fun MainAppScreen(synthEngine: PianoSynthEngine) {
    var baseOctave by remember { mutableStateOf(3) }
    var waveform by remember { mutableStateOf("Piano") }
    var sustain by remember { mutableStateOf(true) }
    var volume by remember { mutableStateOf(0.85f) }
    var activeNote by remember { mutableStateOf<String?>(null) }
    var isSoundPlaying by remember { mutableStateOf(false) }
    var playHistory by remember { mutableStateOf(listOf<String>()) }
    var demoJob by remember { mutableStateOf<Job?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // Dynamically regenerate keyboard lists and preheat cache when settings shift
    val (whiteKeys, blackKeys) = remember(baseOctave) { generateKeyboard(baseOctave) }
    
    LaunchedEffect(baseOctave, waveform, sustain, volume) {
        synthEngine.clearCache()
        synthEngine.preheat(whiteKeys + blackKeys, waveform, sustain, volume)
    }

    // Interactive custom triggering function
    val triggerNote: (SynthKey) -> Unit = { key ->
        activeNote = key.name
        isSoundPlaying = true
        synthEngine.play(key.name, key.frequency, waveform, sustain, volume)
        
        // Append note to historical logs
        playHistory = if (playHistory.size > 15) {
            playHistory.drop(1) + key.name
        } else {
            playHistory + key.name
        }
        
        // Brief visual release delay
        coroutineScope.launch {
            delay(150L)
            if (activeNote == key.name) {
                activeNote = null
                isSoundPlaying = false
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF121212)
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
                    color = Color(0xFF80D8FF),
                    letterSpacing = 2.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Real-time High-fidelity Custom Audio Synthesis Engine",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
            }

            // Real-time Sound Visualizer Panel
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(55.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1E1E1E))
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                val infiniteTransition = rememberInfiniteTransition(label = "visualizer")
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val totalBars = 16
                    for (i in 0 until totalBars) {
                        val duration = 250 + (i * 45)
                        val animHeight by infiniteTransition.animateFloat(
                            initialValue = 0.15f,
                            targetValue = 0.95f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(duration, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "bar_$i"
                        )
                        val scaledHeight = if (isSoundPlaying) animHeight else 0.15f
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(scaledHeight)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color(0xFF00B0FF), Color(0xFF00E5FF))
                                    )
                                )
                        )
                    }
                }
            }

            // Multi-parameter Synth Controllers
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
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
                                        .background(if (selected) Color(0xFF00B0FF) else Color(0xFF2E2E2E))
                                        .clickable { waveform = wave }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
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

                    Divider(color = Color(0xFF2E2E2E))

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
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E2E2E)),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("DOWN", fontSize = 10.sp, color = Color.White)
                                }
                                Button(
                                    onClick = { if (baseOctave < 5) baseOctave++ },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E2E2E)),
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
                                    checkedThumbColor = Color(0xFF00B0FF),
                                    checkedTrackColor = Color(0xFF1E3D59)
                                )
                            )
                        }
                    }

                    Divider(color = Color(0xFF2E2E2E))

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
                                    thumbColor = Color(0xFF00B0FF),
                                    activeTrackColor = Color(0xFF00B0FF)
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
                                    activeNote = null
                                    isSoundPlaying = false
                                } else {
                                    demoJob = coroutineScope.launch(Dispatchers.Default) {
                                        // Standard high-value melody: Ode to Joy
                                        val odeToJoy = listOf(
                                            "E4" to 350L, "E4" to 350L, "F4" to 350L, "G4" to 350L,
                                            "G4" to 350L, "F4" to 350L, "E4" to 350L, "D4" to 350L,
                                            "C4" to 350L, "C4" to 350L, "D4" to 350L, "E4" to 350L,
                                            "E4" to 500L, "D4" to 150L, "D4" to 600L,
                                            "E4" to 350L, "E4" to 350L, "F4" to 350L, "G4" to 350L,
                                            "G4" to 350L, "F4" to 350L, "E4" to 350L, "D4" to 350L,
                                            "C4" to 350L, "C4" to 350L, "D4" to 350L, "E4" to 350L,
                                            "D4" to 500L, "C4" to 150L, "C4" to 600L
                                        )

                                        for (item in odeToJoy) {
                                            val noteName = item.first
                                            val matchedKey = (whiteKeys + blackKeys).find {
                                                it.name.startsWith(noteName.substring(0, noteName.length - 1))
                                            }
                                            val targetFreq = matchedKey?.frequency ?: 329.63f
                                            val finalKeyName = matchedKey?.name ?: noteName

                                            withContext(Dispatchers.Main) {
                                                activeNote = finalKeyName
                                                isSoundPlaying = true
                                                playHistory = if (playHistory.size > 15) {
                                                    playHistory.drop(1) + finalKeyName
                                                } else {
                                                    playHistory + finalKeyName
                                                }
                                            }

                                            synthEngine.play(finalKeyName, targetFreq, waveform, sustain, volume)
                                            delay(item.second)

                                            withContext(Dispatchers.Main) {
                                                activeNote = null
                                                isSoundPlaying = false
                                            }
                                            delay(50L) // gap for note articulation
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
                                text = if (demoJob?.isActive == true) "STOP DEMO" else "ODE TO JOY",
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
                        .background(Color(0xFF1E1E1E))
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (playHistory.isEmpty()) {
                        Text(
                            text = "Play keys to stream midi matrix logs...",
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
                                        .background(Color(0xFF292929))
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
                    .background(Color(0xFF111111))
                    .padding(vertical = 10.dp)
            ) {
                // Interactive Scrollable Row with absolute note placements
                Box(
                    modifier = Modifier
                        .horizontalScroll(scrollState)
                        .fillMaxHeight()
                        .width(whiteKeyWidth * whiteKeys.size)
                ) {
                    // Layer 1: Render White Keys
                    Row(modifier = Modifier.fillMaxSize()) {
                        whiteKeys.forEach { key ->
                            val isHighlighted = activeNote == key.name
                            Box(
                                modifier = Modifier
                                    .width(whiteKeyWidth)
                                    .fillMaxHeight()
                                    .padding(horizontal = 1.5.dp)
                                    .shadow(
                                        elevation = if (isHighlighted) 8.dp else 2.dp,
                                        shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)
                                    )
                                    .background(
                                        brush = Brush.verticalGradient(
                                            colors = if (isHighlighted) {
                                                listOf(Color(0xFF80D8FF), Color(0xFF00B0FF))
                                            } else {
                                                listOf(Color(0xFFFFFFFF), Color(0xFFE0E0E0))
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

                    // Layer 2: Render overlapping Black Keys placed exactly at intervals
                    blackKeys.forEach { key ->
                        val isHighlighted = activeNote == key.name
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
                                            listOf(Color(0xFF333333), Color(0xFF1A1A1A))
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