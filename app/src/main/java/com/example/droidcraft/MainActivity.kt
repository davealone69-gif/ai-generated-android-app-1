package com.example.droidcraft

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.*

class MainActivity : ComponentActivity() {
    private val synthEngine = SynthEngine()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        synthEngine.start()
        setContent {
            MainAppScreen(synthEngine)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        synthEngine.stop()
    }
}

enum class WaveType { SINE, SQUARE, TRIANGLE, SAWTOOTH }

class SynthEngine {
    private val sampleRate = 44100
    private val bufferSize = AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    ) * 2

    private val audioTrack = AudioTrack(
        AudioManager.STREAM_MUSIC,
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
        bufferSize,
        AudioTrack.MODE_STREAM
    )

    class ActiveNote(val frequency: Double) {
        var phase = 0.0
        var amplitude = 0.0
        var isReleasing = false
    }

    val activeNotes = CopyOnWriteArrayList<ActiveNote>()
    private var isRunning = false
    private var synthThread: Thread? = null

    @Volatile var waveType: WaveType = WaveType.SINE
    @Volatile var volume: Float = 0.6f
    @Volatile var attackSpeed: Double = 0.008
    @Volatile var releaseSpeed: Double = 0.005

    fun start() {
        if (isRunning) return
        isRunning = true
        audioTrack.play()
        synthThread = Thread {
            val shortBuffer = ShortArray(512)
            while (isRunning) {
                for (i in shortBuffer.indices) {
                    var sampleVal = 0.0
                    val currentNotes = activeNotes
                    if (currentNotes.isNotEmpty()) {
                        for (note in currentNotes) {
                            val rawSample = when (waveType) {
                                WaveType.SINE -> sin(note.phase)
                                WaveType.SQUARE -> if (sin(note.phase) >= 0) 1.0 else -1.0
                                WaveType.TRIANGLE -> {
                                    val x = note.phase / (2.0 * PI)
                                    2.0 * abs(2.0 * (x - floor(x + 0.5))) - 1.0
                                }
                                WaveType.SAWTOOTH -> {
                                    val x = note.phase / (2.0 * PI)
                                    2.0 * (x - floor(x + 0.5))
                                }
                            }

                            if (note.isReleasing) {
                                note.amplitude -= releaseSpeed
                                if (note.amplitude <= 0.0) {
                                    note.amplitude = 0.0
                                    activeNotes.remove(note)
                                }
                            } else {
                                if (note.amplitude < 1.0) {
                                    note.amplitude += attackSpeed
                                    if (note.amplitude > 1.0) note.amplitude = 1.0
                                }
                            }

                            sampleVal += rawSample * note.amplitude

                            val phaseIncrement = (2.0 * PI * note.frequency) / sampleRate
                            note.phase = (note.phase + phaseIncrement) % (2.0 * PI)
                        }
                        sampleVal = (sampleVal / max(1.0, currentNotes.size.toDouble())) * volume
                    }

                    val shortVal = (sampleVal * 32767.0).coerceIn(-32768.0, 32767.0).toInt().toShort()
                    shortBuffer[i] = shortVal
                }
                audioTrack.write(shortBuffer, 0, shortBuffer.size)
            }
        }
        synthThread?.start()
    }

    fun stop() {
        isRunning = false
        try {
            synthThread?.join()
        } catch (e: Exception) {}
        try {
            audioTrack.stop()
            audioTrack.release()
        } catch (e: Exception) {}
    }

    fun noteOn(frequency: Double) {
        val existing = activeNotes.find { it.frequency == frequency }
        if (existing != null) {
            existing.isReleasing = false
        } else {
            activeNotes.add(ActiveNote(frequency))
        }
    }

    fun noteOff(frequency: Double) {
        val existing = activeNotes.find { it.frequency == frequency }
        existing?.isReleasing = true
    }

    fun allNotesOff() {
        activeNotes.forEach { it.isReleasing = true }
    }
}

// Layout helper classes
data class WhiteKey(val label: String, val midiOffset: Int)
data class BlackKey(val label: String, val midiOffset: Int, val positionAfterWhiteIndex: Int)

@Composable
fun MainAppScreen(synthEngine: SynthEngine) {
    var baseOctave by remember { mutableStateOf(4) }
    var selectedWaveType by remember { mutableStateOf(WaveType.SINE) }
    var masterVolume by remember { mutableStateOf(0.6f) }
    var releaseSpeedVal by remember { mutableStateOf(0.005f) }
    var attackSpeedVal by remember { mutableStateOf(0.008f) }

    // Synchronize Synth Settings
    LaunchedEffect(selectedWaveType) { synthEngine.waveType = selectedWaveType }
    LaunchedEffect(masterVolume) { synthEngine.volume = masterVolume }
    LaunchedEffect(releaseSpeedVal) { synthEngine.releaseSpeed = releaseSpeedVal.toDouble() }
    LaunchedEffect(attackSpeedVal) { synthEngine.attackSpeed = attackSpeedVal.toDouble() }

    // Keep track of which frequencies are pressed visually
    val activePressedFrequencies = remember { mutableStateMapOf<Double, Boolean>() }

    // White Keys & Black Keys Layout mappings
    val whiteKeysList = listOf(
        WhiteKey("C", 0),
        WhiteKey("D", 2),
        WhiteKey("E", 4),
        WhiteKey("F", 5),
        WhiteKey("G", 7),
        WhiteKey("A", 9),
        WhiteKey("B", 11),
        WhiteKey("C", 12),
        WhiteKey("D", 14),
        WhiteKey("E", 16)
    )

    val blackKeysList = listOf(
        BlackKey("C#", 1, 0),
        BlackKey("D#", 3, 1),
        BlackKey("F#", 6, 3),
        BlackKey("G#", 8, 4),
        BlackKey("A#", 10, 5),
        BlackKey("C#", 13, 7),
        BlackKey("D#", 15, 8)
    )

    fun calculateFrequency(midiOffset: Int): Double {
        val midiNote = baseOctave * 12 + midiOffset
        return 440.0 * 2.0.pow((midiNote - 69).toDouble() / 12.0)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
    ) {
        val isLandscape = maxWidth > maxHeight

        if (isLandscape) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Left Panel: Synthesizer Controls
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(16.dp)
                        .background(Color(0xFF1E293B), RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "DroidCraft Synth v1.0",
                        color = Color(0xFF38BDF8),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OscilloscopeWidget(
                        activeNotes = synthEngine.activeNotes,
                        waveType = selectedWaveType,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                    )

                    SynthesizerControls(
                        selectedWaveType = selectedWaveType,
                        onWaveTypeSelected = { selectedWaveType = it },
                        masterVolume = masterVolume,
                        onVolumeChange = { masterVolume = it },
                        attackVal = attackSpeedVal,
                        onAttackChange = { attackSpeedVal = it },
                        releaseVal = releaseSpeedVal,
                        onReleaseChange = { releaseSpeedVal = it },
                        baseOctave = baseOctave,
                        onOctaveUp = { if (baseOctave < 7) baseOctave++ },
                        onOctaveDown = { if (baseOctave > 1) baseOctave-- },
                        onPanic = {
                            synthEngine.allNotesOff()
                            activePressedFrequencies.clear()
                        }
                    )
                }

                // Right Panel: Piano Keys
                Box(
                    modifier = Modifier
                        .weight(2.5f)
                        .fillMaxHeight()
                        .padding(16.dp)
                ) {
                    PianoKeyboard(
                        whiteKeys = whiteKeysList,
                        blackKeys = blackKeysList,
                        activeFrequencies = activePressedFrequencies,
                        calculateFrequency = ::calculateFrequency,
                        onNoteOn = { freq ->
                            activePressedFrequencies[freq] = true
                            synthEngine.noteOn(freq)
                        },
                        onNoteOff = { freq ->
                            activePressedFrequencies[freq] = false
                            synthEngine.noteOff(freq)
                        }
                    )
                }
            }
        } else {
            // Portrait Layout
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header & Visualizer
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E293B), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "DroidCraft Synth Engine",
                            color = Color(0xFF38BDF8),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        IconButton(onClick = {
                            synthEngine.allNotesOff()
                            activePressedFrequencies.clear()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Panic",
                                tint = Color.Red
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OscilloscopeWidget(
                        activeNotes = synthEngine.activeNotes,
                        waveType = selectedWaveType,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                    )
                }

                // Wave selectors, Volume, ADSR
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E293B), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    SynthesizerControls(
                        selectedWaveType = selectedWaveType,
                        onWaveTypeSelected = { selectedWaveType = it },
                        masterVolume = masterVolume,
                        onVolumeChange = { masterVolume = it },
                        attackVal = attackSpeedVal,
                        onAttackChange = { attackSpeedVal = it },
                        releaseVal = releaseSpeedVal,
                        onReleaseChange = { releaseSpeedVal = it },
                        baseOctave = baseOctave,
                        onOctaveUp = { if (baseOctave < 7) baseOctave++ },
                        onOctaveDown = { if (baseOctave > 1) baseOctave-- },
                        onPanic = {
                            synthEngine.allNotesOff()
                            activePressedFrequencies.clear()
                        }
                    )
                }

                // Keybed
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                ) {
                    PianoKeyboard(
                        whiteKeys = whiteKeysList,
                        blackKeys = blackKeysList,
                        activeFrequencies = activePressedFrequencies,
                        calculateFrequency = ::calculateFrequency,
                        onNoteOn = { freq ->
                            activePressedFrequencies[freq] = true
                            synthEngine.noteOn(freq)
                        },
                        onNoteOff = { freq ->
                            activePressedFrequencies[freq] = false
                            synthEngine.noteOff(freq)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun OscilloscopeWidget(
    activeNotes: List<SynthEngine.ActiveNote>,
    waveType: WaveType,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition()
    val phaseOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF020617))
            .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
    ) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f

        // Draw dynamic Grid lines
        val gridLines = 8
        for (i in 1 until gridLines) {
            val x = (width / gridLines) * i
            drawLine(
                color = Color(0xFF1E293B),
                start = Offset(x, 0f),
                end = Offset(x, height),
                strokeWidth = 1f
            )
        }
        drawLine(
            color = Color(0xFF334155),
            start = Offset(0f, centerY),
            end = Offset(width, centerY),
            strokeWidth = 1.5f
        )

        // Draw Waveform representation
        val path = Path()
        val step = 2
        path.moveTo(0f, centerY)

        for (x in 0..width.toInt() step step) {
            val t = (x.toDouble() / width.toDouble()) * 4.0 * PI
            var mixedSample = 0.0

            if (activeNotes.isNotEmpty()) {
                for (note in activeNotes) {
                    val scaleFactor = (note.frequency / 261.63).coerceIn(0.5, 4.0)
                    val sample = when (waveType) {
                        WaveType.SINE -> sin(t * scaleFactor + phaseOffset)
                        WaveType.SQUARE -> if (sin(t * scaleFactor + phaseOffset) >= 0) 1.0 else -1.0
                        WaveType.TRIANGLE -> {
                            val xPhase = (t * scaleFactor + phaseOffset) / (2.0 * PI)
                            2.0 * abs(2.0 * (xPhase - floor(xPhase + 0.5))) - 1.0
                        }
                        WaveType.SAWTOOTH -> {
                            val xPhase = (t * scaleFactor + phaseOffset) / (2.0 * PI)
                            2.0 * (xPhase - floor(xPhase + 0.5))
                        }
                    }
                    mixedSample += sample * note.amplitude
                }
                mixedSample = (mixedSample / max(1.0, activeNotes.size.toDouble()))
            } else {
                // Idle idle movement
                mixedSample = sin(t + phaseOffset) * 0.1
            }

            val y = centerY - (mixedSample * (centerY * 0.7f)).toFloat()
            path.lineTo(x.toFloat(), y)
        }

        drawPath(
            path = path,
            color = Color(0xFF38BDF8),
            style = Stroke(width = 3f, cap = StrokeCap.Round)
        )
    }
}

@Composable
fun SynthesizerControls(
    selectedWaveType: WaveType,
    onWaveTypeSelected: (WaveType) -> Unit,
    masterVolume: Float,
    onVolumeChange: (Float) -> Unit,
    attackVal: Float,
    onAttackChange: (Float) -> Unit,
    releaseVal: Float,
    onReleaseChange: (Float) -> Unit,
    baseOctave: Int,
    onOctaveUp: () -> Unit,
    onOctaveDown: () -> Unit,
    onPanic: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Wave Selection Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            WaveType.values().forEach { wave ->
                Button(
                    onClick = { onWaveTypeSelected(wave) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedWaveType == wave) Color(0xFF0EA5E9) else Color(0xFF334155),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    contentPadding = PaddingValues(0.dp),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = wave.name,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Sliders (Volume, Attack, Release)
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Volume", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text("${(masterVolume * 100).toInt()}%", color = Color(0xFF38BDF8), fontSize = 12.sp)
            }
            Slider(
                value = masterVolume,
                onValueChange = onVolumeChange,
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF38BDF8),
                    activeTrackColor = Color(0xFF0EA5E9)
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Attack speed", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(String.format("%.3f", attackVal), color = Color(0xFF38BDF8), fontSize = 12.sp)
            }
            Slider(
                value = attackVal,
                onValueChange = onAttackChange,
                valueRange = 0.001f..0.05f,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF38BDF8),
                    activeTrackColor = Color(0xFF0EA5E9)
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Release speed", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(String.format("%.3f", releaseVal), color = Color(0xFF38BDF8), fontSize = 12.sp)
            }
            Slider(
                value = releaseVal,
                onValueChange = onReleaseChange,
                valueRange = 0.001f..0.05f,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF38BDF8),
                    activeTrackColor = Color(0xFF0EA5E9)
                )
            )
        }

        // Octave & Panic Controller row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Octave Shift", color = Color.LightGray, fontSize = 11.sp)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onOctaveDown,
                        colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFF334155)),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Octave Down", tint = Color.White)
                    }
                    Text(
                        text = "C$baseOctave",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier.width(36.dp),
                        textAlign = TextAlign.Center
                    )
                    IconButton(
                        onClick = onOctaveUp,
                        colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFF334155)),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Octave Up", tint = Color.White)
                    }
                }
            }

            Button(
                onClick = onPanic,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF991B1B)),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.height(36.dp),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Text("PANIC", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun PianoKeyboard(
    whiteKeys: List<WhiteKey>,
    blackKeys: List<BlackKey>,
    activeFrequencies: Map<Double, Boolean>,
    calculateFrequency: (Int) -> Double,
    onNoteOn: (Double) -> Unit,
    onNoteOff: (Double) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .border(2.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF020617))
    ) {
        val totalWidth = maxWidth
        val height = maxHeight
        val whiteCount = whiteKeys.size
        val whiteKeyWidth = totalWidth / whiteCount

        // 1. Draw White Keys
        Row(modifier = Modifier.fillMaxSize()) {
            whiteKeys.forEach { keySpec ->
                val freq = calculateFrequency(keySpec.midiOffset)
                val isPressed = activeFrequencies[freq] == true

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(
                            brush = if (isPressed) {
                                Brush.verticalGradient(
                                    colors = listOf(Color(0xFF38BDF8), Color(0xFF0369A1))
                                )
                            } else {
                                Brush.verticalGradient(
                                    colors = listOf(Color(0xFFF8FAFC), Color(0xFFCBD5E1))
                                )
                            }
                        )
                        .border(1.dp, Color(0xFF64748B))
                        .pointerInput(keySpec.midiOffset) {
                            detectTapGestures(
                                onPress = {
                                    onNoteOn(freq)
                                    try {
                                        awaitRelease()
                                    } finally {
                                        onNoteOff(freq)
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Text(
                        text = keySpec.label,
                        color = if (isPressed) Color.White else Color(0xFF475569),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
            }
        }

        // 2. Draw Overlay Black Keys
        val blackKeyWidth = whiteKeyWidth * 0.65f
        val blackKeyHeight = height * 0.58f

        blackKeys.forEach { blackKey ->
            val freq = calculateFrequency(blackKey.midiOffset)
            val isPressed = activeFrequencies[freq] == true

            // Calculate exact overlay X coordinate based on its relative white key index
            val relativeXOffset = (whiteKeyWidth * (blackKey.positionAfterWhiteIndex + 1)) - (blackKeyWidth / 2f)

            Box(
                modifier = Modifier
                    .absoluteOffset(x = relativeXOffset, y = 0.dp)
                    .width(blackKeyWidth)
                    .height(blackKeyHeight)
                    .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                    .background(
                        brush = if (isPressed) {
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFFA855F7), Color(0xFF6B21A8))
                            )
                        } else {
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFF334155), Color(0xFF0F172A))
                            )
                        }
                    )
                    .border(
                        1.5.dp,
                        if (isPressed) Color(0xFFC084FC) else Color(0xFF475569),
                        RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp)
                    )
                    .pointerInput(blackKey.midiOffset) {
                        detectTapGestures(
                            onPress = {
                                onNoteOn(freq)
                                try {
                                    awaitRelease()
                                } finally {
                                    onNoteOff(freq)
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.BottomCenter
            ) {
                Text(
                    text = blackKey.label,
                    color = if (isPressed) Color.White else Color(0xFF94A3B8),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }
    }
}