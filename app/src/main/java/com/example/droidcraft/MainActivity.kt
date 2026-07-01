package com.example.droidcraft

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import kotlin.math.asin
import kotlin.math.floor
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFF0F0F13)
            ) {
                MainAppScreen()
            }
        }
    }
}

enum class WaveType {
    SINE, SQUARE, TRIANGLE, SAWTOOTH
}

data class PianoKey(
    val name: String,
    val semitones: Int,
    val isBlack: Boolean,
    val whiteKeyIndexBefore: Int = -1
)

data class RecordedNote(
    val noteName: String,
    val frequency: Double,
    val relativeTimeMs: Long,
    val waveType: WaveType,
    val decay: Float
)

@Composable
fun MainAppScreen() {
    val coroutineScope = rememberCoroutineScope()

    // Synth configurations
    var selectedWave by remember { mutableStateOf(WaveType.SINE) }
    var decayTime by remember { mutableStateOf(1.0f) }
    var volume by remember { mutableStateOf(0.7f) }
    var octaveShift by remember { mutableStateOf(0) }

    // State for interactive feedback
    var lastPlayedNoteName by remember { mutableStateOf("---") }
    var lastPlayedFreq by remember { mutableStateOf(0.0) }

    // Sequence Recording state
    var isRecording by remember { mutableStateOf(false) }
    var isPlayingBack by remember { mutableStateOf(false) }
    val recordedNotes = remember { mutableStateListOf<RecordedNote>() }
    var recordStartTime by remember { mutableStateOf(0L) }
    var playbackJob by remember { mutableStateOf<Job?>(null) }

    // Keyboard configuration
    val baseFreqC4 = 261.63 // C4 frequency

    val whiteKeys = remember {
        listOf(
            PianoKey("C", 0, false),
            PianoKey("D", 2, false),
            PianoKey("E", 4, false),
            PianoKey("F", 5, false),
            PianoKey("G", 7, false),
            PianoKey("A", 9, false),
            PianoKey("B", 11, false),
            PianoKey("C", 12, false),
            PianoKey("D", 14, false),
            PianoKey("E", 16, false)
        )
    }

    val blackKeys = remember {
        listOf(
            PianoKey("C#", 1, true, 0),
            PianoKey("D#", 3, true, 1),
            PianoKey("F#", 6, true, 3),
            PianoKey("G#", 8, true, 4),
            PianoKey("A#", 10, true, 5),
            PianoKey("C#", 13, true, 7),
            PianoKey("D#", 15, true, 8)
        )
    }

    // Custom sound generator function
    fun playNoteAudio(frequency: Double, waveType: WaveType, decaySeconds: Float, vol: Float) {
        coroutineScope.launch(Dispatchers.Default) {
            val sampleRate = 22050
            val duration = decaySeconds.coerceIn(0.1f, 3.0f)
            val numSamples = (sampleRate * duration).toInt()
            val sample = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toDouble() / sampleRate
                val angle = 2.0 * Math.PI * frequency * t
                val rawValue = when (waveType) {
                    WaveType.SINE -> sin(angle)
                    WaveType.SQUARE -> if (sin(angle) >= 0.0) 1.0 else -1.0
                    WaveType.TRIANGLE -> (2.0 / Math.PI) * asin(sin(angle))
                    WaveType.SAWTOOTH -> 2.0 * (t * frequency - floor(0.5 + t * frequency))
                }

                // Smooth attack envelope (5ms) to eliminate audio pop
                val attackSamples = (sampleRate * 0.005).toInt()
                val envelope = if (i < attackSamples) {
                    i.toDouble() / attackSamples
                } else {
                    // Exponential decay envelope
                    Math.exp(-4.0 * (i - attackSamples) / numSamples)
                }

                sample[i] = (rawValue * envelope * vol * Short.MAX_VALUE).toInt().toShort()
            }

            var audioTrack: AudioTrack? = null
            try {
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
                    .setBufferSizeInBytes(sample.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                audioTrack.write(sample, 0, sample.size)
                audioTrack.play()
                delay((duration * 1000).toLong())
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try {
                    audioTrack?.stop()
                    audioTrack?.release()
                } catch (e: Exception) {
                    // safe ignore
                }
            }
        }
    }

    // Key trigger handler
    val triggerKey = { key: PianoKey ->
        val shiftMultiplier = Math.pow(2.0, octaveShift.toDouble())
        val finalFreq = baseFreqC4 * Math.pow(2.0, key.semitones / 12.0) * shiftMultiplier
        val octaveLabel = 4 + octaveShift + (key.semitones / 12)
        val fullNoteName = "${key.name}$octaveLabel"

        lastPlayedNoteName = fullNoteName
        lastPlayedFreq = finalFreq

        // Play Synthesized sound
        playNoteAudio(finalFreq, selectedWave, decayTime, volume)

        // If recording active, capture note parameters and timestamp
        if (isRecording) {
            val relativeTime = System.currentTimeMillis() - recordStartTime
            recordedNotes.add(
                RecordedNote(
                    noteName = fullNoteName,
                    frequency = finalFreq,
                    relativeTimeMs = relativeTime,
                    waveType = selectedWave,
                    decay = decayTime
                )
            )
        }
    }

    // Trigger sequential playback of recorded notes
    val triggerPlayback = {
        if (recordedNotes.isNotEmpty() && !isPlayingBack) {
            isPlayingBack = true
            playbackJob = coroutineScope.launch(Dispatchers.Default) {
                val startTime = System.currentTimeMillis()
                var index = 0
                while (index < recordedNotes.size && isPlayingBack) {
                    val note = recordedNotes[index]
                    val elapsed = System.currentTimeMillis() - startTime
                    if (elapsed >= note.relativeTimeMs) {
                        playNoteAudio(note.frequency, note.waveType, note.decay, volume)
                        lastPlayedNoteName = note.noteName
                        lastPlayedFreq = note.frequency
                        index++
                    }
                    delay(10) // Small polling sleep for high responsiveness
                }
                isPlayingBack = false
            }
        }
    }

    // Stop playback
    val stopPlayback = {
        isPlayingBack = false
        playbackJob?.cancel()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Title & Neon Header
        Text(
            text = "DROIDSYNTH PIANO",
            color = Color(0xFF00FFCC),
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 4.sp,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        // Custom Visualizer + Status Card
        StatusVisualizerCard(
            selectedWave = selectedWave,
            noteName = lastPlayedNoteName,
            freqVal = lastPlayedFreq,
            isRecording = isRecording,
            recordedCount = recordedNotes.size
        )

        // Synthesizer Parameter Customizer Panel
        SynthesizerControlPanel(
            selectedWave = selectedWave,
            onWaveSelected = { selectedWave = it },
            decayTime = decayTime,
            onDecayChange = { decayTime = it },
            volume = volume,
            onVolumeChange = { volume = it },
            octaveShift = octaveShift,
            onOctaveShiftChange = { octaveShift = it }
        )

        // Recorder Status Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    if (isRecording) {
                        isRecording = false
                    } else {
                        stopPlayback()
                        recordedNotes.clear()
                        recordStartTime = System.currentTimeMillis()
                        isRecording = true
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRecording) Color(0xFFFF2A6D) else Color(0xFF1E1E24)
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (isRecording) Color.White else Color(0xFFFF2A6D))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isRecording) "Stop Rec" else "Record Synth",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Button(
                onClick = {
                    if (isPlayingBack) stopPlayback() else triggerPlayback()
                },
                enabled = recordedNotes.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isPlayingBack) Color(0xFF05D9E8) else Color(0xFF00FFCC),
                    disabledContainerColor = Color(0xFF152A2A)
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play back seq",
                    tint = if (isPlayingBack) Color.Black else Color(0xFF0F0F13)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isPlayingBack) "Playing..." else "Play Synth Loop (${recordedNotes.size})",
                    fontWeight = FontWeight.Bold,
                    color = if (isPlayingBack) Color.Black else Color(0xFF0F0F13)
                )
            }

            IconButton(
                onClick = {
                    stopPlayback()
                    isRecording = false
                    recordedNotes.clear()
                },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color(0xFF1E1E24)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Clear loop",
                    tint = Color.White
                )
            }
        }

        // Piano Keyboard Layout
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 8.dp)
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val totalWhiteKeys = whiteKeys.size
                val keyWidth = maxWidth / totalWhiteKeys

                // Render White Keys
                Row(modifier = Modifier.fillMaxSize()) { 
                    whiteKeys.forEach { key ->
                        val octaveLabel = 4 + octaveShift + (key.semitones / 12)
                        WhitePianoKey(
                            label = "${key.name}$octaveLabel",
                            modifier = Modifier
                                .width(keyWidth)
                                .fillMaxHeight(),
                            onKeyPress = { triggerKey(key) }
                        )
                    }
                }

                // Render Black Keys on absolute layer
                blackKeys.forEach { key ->
                    val offsetLeft = (key.whiteKeyIndexBefore + 1) * keyWidth - (keyWidth * 0.32f)
                    val octaveLabel = 4 + octaveShift + (key.semitones / 12)
                    BlackPianoKey(
                        label = "${key.name}$octaveLabel",
                        modifier = Modifier
                            .offset(x = offsetLeft)
                            .width(keyWidth * 0.64f)
                            .fillMaxHeight(0.6f),
                        onKeyPress = { triggerKey(key) }
                    )
                }
            }
        }
    }
}

@Composable
fun StatusVisualizerCard(
    selectedWave: WaveType,
    noteName: String,
    freqVal: Double,
    isRecording: Boolean,
    recordedCount: Int
) {
    val infiniteTransition = rememberInfiniteTransition(label = "")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = ""
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF14141A)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Stats Panel
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "SYNTH STATUS",
                    color = Color.Gray,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "ACTIVE KEY: $noteName",
                    color = Color(0xFF05D9E8),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = String.format("FREQ: %.2f Hz", freqVal),
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
                if (isRecording) {
                    Text(
                        text = "RECORDING: $recordedCount notes",
                        color = Color(0xFFFF2A6D),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Real-time custom Oscilloscope simulator
            Box(
                modifier = Modifier
                    .width(130.dp)
                    .height(60.dp)
                    .background(Color(0xFF0B0B0E), RoundedCornerShape(8.dp))
                    .padding(4.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height
                    val centerY = canvasHeight / 2f
                    val path = Path()

                    path.moveTo(0f, centerY)
                    val points = 60
                    for (i in 0..points) {
                        val x = (i.toFloat() / points) * canvasWidth
                        // Generate synthesis simulation wave cycle
                        val normalizedX = (i.toFloat() / points) * 2.0 * Math.PI * 2.5 // 2.5 cycles
                        val waveVal = when (selectedWave) {
                            WaveType.SINE -> sin(normalizedX + wavePhase)
                            WaveType.SQUARE -> if (sin(normalizedX + wavePhase) >= 0) 0.6 else -0.6
                            WaveType.TRIANGLE -> (2.0 / Math.PI) * asin(sin(normalizedX + wavePhase))
                            WaveType.SAWTOOTH -> {
                                val p = (normalizedX + wavePhase) / (2.0 * Math.PI)
                                2.0 * (p - floor(0.5 + p))
                            }
                        }
                        val y = centerY + (waveVal * (canvasHeight * 0.38f)).toFloat()
                        path.lineTo(x, y)
                    }

                    drawPath(
                        path = path,
                        color = Color(0xFF00FFCC),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }
        }
    }
}

@Composable
fun SynthesizerControlPanel(
    selectedWave: WaveType,
    onWaveSelected: (WaveType) -> Unit,
    decayTime: Float,
    onDecayChange: (Float) -> Unit,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    octaveShift: Int,
    onOctaveShiftChange: (Int) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E24)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Waveform selectors
            Text(
                text = "CUSTOM SYNTH WAVEFORM GENERATOR",
                color = Color.LightGray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                WaveType.values().forEach { wave ->
                    val isSelected = selectedWave == wave
                    Button(
                        onClick = { onWaveSelected(wave) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) Color(0xFF00FFCC) else Color(0xFF14141A)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 2.dp)
                            .height(34.dp),
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = wave.name,
                            color = if (isSelected) Color.Black else Color.Gray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Sliders for parameters
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ENVELOPE DECAY (${String.format("%.1fs", decayTime)})",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Slider(
                        value = decayTime,
                        onValueChange = onDecayChange,
                        valueRange = 0.2f..2.5f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF00FFCC),
                            activeTrackColor = Color(0xFF00FFCC)
                        ),
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SYNTH VOLUME (${(volume * 100).toInt()}%)",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Slider(
                        value = volume,
                        onValueChange = onVolumeChange,
                        valueRange = 0.1f..1.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF05D9E8),
                            activeTrackColor = Color(0xFF05D9E8)
                        ),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            // Octave Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "OCTAVE SHIFT RANGE",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { if (octaveShift > -2) onOctaveShiftChange(octaveShift - 1) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF14141A)),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("-", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }

                    Text(
                        text = if (octaveShift >= 0) "+$octaveShift" else octaveShift.toString(),
                        color = Color(0xFF00FFCC),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        fontFamily = FontFamily.Monospace
                    )

                    Button(
                        onClick = { if (octaveShift < 2) onOctaveShiftChange(octaveShift + 1) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF14141A)),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("+", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun WhitePianoKey(
    label: String,
    modifier: Modifier = Modifier,
    onKeyPress: () -> Unit
) {
    Box(
        modifier = modifier
            .padding(horizontal = 2.dp)
            .shadow(4.dp, shape = RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFCFCFC),
                        Color(0xFFECECEC),
                        Color(0xFFE0E0E0)
                    )
                ),
                shape = RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = LocalIndication.current,
                onClick = onKeyPress
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        Text(
            text = label,
            color = Color(0xFF333333),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 12.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun BlackPianoKey(
    label: String,
    modifier: Modifier = Modifier,
    onKeyPress: () -> Unit
) {
    Box(
        modifier = modifier
            .shadow(6.dp, shape = RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF2C2D35),
                        Color(0xFF15161B),
                        Color(0xFF020202)
                    )
                ),
                shape = RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = LocalIndication.current,
                onClick = onKeyPress
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Vertical neon indicator strip
        Box(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(4.dp)
                .padding(bottom = 2.dp)
                .background(Color(0xFF00FFCC), RoundedCornerShape(2.dp))
        )
        Text(
            text = label,
            color = Color.LightGray,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 8.dp),
            textAlign = TextAlign.Center
        )
    }
}
