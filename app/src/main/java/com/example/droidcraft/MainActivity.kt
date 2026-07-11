package com.example.droidcraft

import android.media.AudioAttributes
import android.media.AudioFormat
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.*

// Custom class for defining physical properties of synthesized keys
data class PianoKey(
    val name: String,
    val baseFreq: Double,
    val isBlack: Boolean,
    val positionIndex: Float // Index positioning for rendering alignments
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MainAppScreen()
        }
    }
}

@Composable
fun MainAppScreen() {
    // Synth Parameters
    var selectedWaveform by remember { mutableStateOf("Sine") }
    var attack by remember { mutableStateOf(0.1f) }
    var decay by remember { mutableStateOf(0.2f) }
    var sustain by remember { mutableStateOf(0.7f) }
    var release by remember { mutableStateOf(0.4f) }
    var octaveShift by remember { mutableStateOf(0) } // -1, 0, +1
    var sustainPedalOn by remember { mutableStateOf(false) }

    // Visual feedback values
    var lastPlayedFrequency by remember { mutableStateOf(440.0) }
    var triggerTriggerTime by remember { mutableStateOf(0L) }
    val activeNotes = remember { mutableStateMapOf<String, Boolean>() }

    // Presets
    val presets = listOf(
        Preset("Soft Sine Pad", "Sine", 0.35f, 0.4f, 0.8f, 0.7f),
        Preset("8-Bit Pulse", "Square", 0.01f, 0.15f, 0.4f, 0.1f),
        Preset("Fat Saw Lead", "Sawtooth", 0.05f, 0.3f, 0.7f, 0.3f),
        Preset("Space Triangle", "Triangle", 0.2f, 0.5f, 0.6f, 0.5f),
        Preset("Classic Organ", "Square", 0.05f, 0.1f, 0.9f, 0.2f)
    )

    // Calculate Octave multiplier
    val octaveMultiplier = when (octaveShift) {
        -1 -> 0.5
        1 -> 2.0
        else -> 1.0
    }

    // Keyboard mappings
    val whiteKeys = listOf(
        PianoKey("C4", 261.63, false, 0f),
        PianoKey("D4", 293.66, false, 1f),
        PianoKey("E4", 329.63, false, 2f),
        PianoKey("F4", 349.23, false, 3f),
        PianoKey("G4", 392.00, false, 4f),
        PianoKey("A4", 440.00, false, 5f),
        PianoKey("B4", 493.88, false, 6f),
        PianoKey("C5", 523.25, false, 7f),
        PianoKey("D5", 587.33, false, 8f),
        PianoKey("E5", 659.25, false, 9f),
    )

    val blackKeys = listOf(
        PianoKey("C#4", 277.18, true, 0.5f),
        PianoKey("D#4", 311.13, true, 1.5f),
        PianoKey("F#4", 369.99, true, 3.5f),
        PianoKey("G#4", 415.30, true, 4.5f),
        PianoKey("A#4", 466.16, true, 5.5f),
        PianoKey("C#5", 554.37, true, 7.5f),
        PianoKey("D#5", 622.25, true, 8.5f),
    )

    // Theme Colors
    val darkBackground = Color(0xFF12141C)
    val cardBackground = Color(0xFF1E2230)
    val accentNeonColor = Color(0xFF00E6FF)
    val secondaryNeonColor = Color(0xFF8A2BE2)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(darkBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Title Banner
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "DROIDCRAFT SYNTH",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = "Custom DSP Sound Synthesis Engine",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Medium
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF2E1A47))
                    .border(1.dp, accentNeonColor, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "DSP ALIVE",
                    color = accentNeonColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Live Oscilloscope Visualizer Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(cardBackground)
                .border(1.dp, Color(0xFF333A4E), RoundedCornerShape(12.dp))
        ) {
            WaveformOscilloscope(
                waveformType = selectedWaveform,
                frequency = lastPlayedFrequency,
                triggerTime = triggerTriggerTime,
                envelopeMultiplier = sustain,
                accentColor = accentNeonColor,
                secondaryColor = secondaryNeonColor
            )

            // Parameter details inside visualizer
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
            ) {
                Text(
                    text = "Oscillator Freq: ${String.format("%.2f", lastPlayedFrequency)} Hz",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Waveform: $selectedWaveform",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Preset Selector Row
        Text(
            text = "Engine Presets",
            color = Color.LightGray,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            presets.forEach { preset ->
                Button(
                    onClick = {
                        selectedWaveform = preset.waveform
                        attack = preset.attack
                        decay = preset.decay
                        sustain = preset.sustain
                        release = preset.release
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedWaveform == preset.waveform && attack == preset.attack) secondaryNeonColor else cardBackground
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = preset.name,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1
                    )
                }
            }
        }

        // Synthesis Control Panel (Sliders)
        Card(
            colors = CardDefaults.cardColors(containerColor = cardBackground),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF333A4E), RoundedCornerShape(12.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "ADSR ENVELOPE CONTROLLER",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 1.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Waveform Type Selection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Generator", color = Color.Gray, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("Sine", "Square", "Triangle", "Sawtooth").forEach { wave ->
                            val isSelected = selectedWaveform == wave
                            Button(
                                onClick = { selectedWaveform = wave },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) accentNeonColor else Color(0xFF262C3D)
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text(
                                    text = wave,
                                    color = if (isSelected) Color.Black else Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ADSR Parameter Sliders
                ParameterSlider("Attack (Fade-In)", attack, 0.01f, 1.0f) { attack = it }
                ParameterSlider("Decay (Fade-Down)", decay, 0.01f, 1.0f) { decay = it }
                ParameterSlider("Sustain (Hold Vol)", sustain, 0.0f, 1.0f) { sustain = it }
                ParameterSlider("Release (Fade-Out)", release, 0.01f, 2.0f) { release = it }

                Spacer(modifier = Modifier.height(8.dp))
                Divider(color = Color(0xFF333A4E))
                Spacer(modifier = Modifier.height(8.dp))

                // Synth Controls (Octave Shift & Sustain Pedal)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Octave Selection
                    Column {
                        Text("Octave Shift", color = Color.Gray, fontSize = 11.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(-1, 0, 1).forEach { oct ->
                                Button(
                                    onClick = { octaveShift = oct },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (octaveShift == oct) accentNeonColor else Color(0xFF262C3D)
                                    ),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Text(
                                        text = if (oct > 0) "+$oct" else "$oct",
                                        color = if (octaveShift == oct) Color.Black else Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Sustain Pedal Toggle
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Sustain Pedal", color = Color.Gray, fontSize = 11.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (sustainPedalOn) "HELD" else "OFF",
                                color = if (sustainPedalOn) accentNeonColor else Color.Gray,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(end = 8.dp),
                                fontFamily = FontFamily.Monospace
                            )
                            Switch(
                                checked = sustainPedalOn,
                                onCheckedChange = { sustainPedalOn = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = accentNeonColor,
                                    checkedTrackColor = accentNeonColor.copy(alpha = 0.3f),
                                    uncheckedThumbColor = Color.Gray,
                                    uncheckedTrackColor = Color(0xFF262C3D)
                                )
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Keyboard Label
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SYNTHESIZER KEYBOARD",
                fontSize = 12.sp,
                color = Color.LightGray,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "Tap & Hold Keys to Synthesize",
                fontSize = 10.sp,
                color = Color.Gray
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Dynamic Interactive Piano View
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black)
                .shadow(8.dp)
        ) {
            val totalWhiteKeys = whiteKeys.size
            val whiteKeyWidth = maxWidth / totalWhiteKeys
            val blackKeyWidth = whiteKeyWidth * 0.65f
            val blackKeyHeight = 130.dp

            // 1. Draw White Keys
            Row(modifier = Modifier.fillMaxSize()) {
                whiteKeys.forEach { key ->
                    val isPlaying = activeNotes[key.name] == true
                    val targetFreq = key.baseFreq * octaveMultiplier

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .border(1.dp, Color(0xFF1E2230))
                            .clip(RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp))
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = if (isPlaying) {
                                        listOf(accentNeonColor.copy(alpha = 0.7f), accentNeonColor)
                                    } else {
                                        listOf(Color.White, Color(0xFFE2E2E2))
                                    }
                                )
                            )
                            .pointerInput(key.name) {
                                detectTapGestures(
                                    onPress = {
                                        activeNotes[key.name] = true
                                        lastPlayedFrequency = targetFreq
                                        triggerTriggerTime = System.currentTimeMillis()
                                        playSynthTone(
                                            frequency = targetFreq,
                                            waveform = selectedWaveform,
                                            attack = attack,
                                            decay = decay,
                                            sustain = sustain,
                                            release = if (sustainPedalOn) 1.5f else release
                                        )
                                        tryAwaitRelease()
                                        activeNotes[key.name] = false
                                    }
                                )
                            },
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Text(
                            text = key.name,
                            color = if (isPlaying) Color.Black else Color.DarkGray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }
                }
            }

            // 2. Overlay Black Keys
            blackKeys.forEach { key ->
                val isPlaying = activeNotes[key.name] == true
                val targetFreq = key.baseFreq * octaveMultiplier
                val xOffset = (key.positionIndex * whiteKeyWidth.value).dp - (blackKeyWidth / 2)

                Box(
                    modifier = Modifier
                        .offset(x = xOffset, y = 0.dp)
                        .width(blackKeyWidth)
                        .height(blackKeyHeight)
                        .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                        .background(
                            brush = Brush.verticalGradient(
                                colors = if (isPlaying) {
                                    listOf(secondaryNeonColor.copy(alpha = 0.7f), secondaryNeonColor)
                                } else {
                                    listOf(Color(0xFF2E313E), Color(0xFF0F111A))
                                }
                            )
                        )
                        .border(1.dp, Color.Black, RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                        .pointerInput(key.name) {
                            detectTapGestures(
                                onPress = {
                                    activeNotes[key.name] = true
                                    lastPlayedFrequency = targetFreq
                                    triggerTriggerTime = System.currentTimeMillis()
                                    playSynthTone(
                                        frequency = targetFreq,
                                        waveform = selectedWaveform,
                                        attack = attack,
                                        decay = decay,
                                        sustain = sustain,
                                        release = if (sustainPedalOn) 1.5f else release
                                    )
                                    tryAwaitRelease()
                                    activeNotes[key.name] = false
                                }
                            )
                        },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Text(
                        text = key.name,
                        color = if (isPlaying) Color.White else Color.LightGray.copy(alpha = 0.7f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }
        }
    }
}

// Visual Waveform Canvas rendering continuous wave matching selection
@Composable
fun WaveformOscilloscope(
    waveformType: String,
    frequency: Double,
    triggerTime: Long,
    envelopeMultiplier: Float,
    accentColor: Color,
    secondaryColor: Color
) {
    // Phase mapping animation
    val infiniteTransition = rememberInfiniteTransition(label = "OscilloscopePhase")
    val waveOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val midY = height / 2f
        val points = width.toInt()
        val path = Path()

        // Scaled visually based on incoming note frequency
        val waveCycleFactor = (frequency / 261.63).coerceIn(0.5, 4.0)
        val waveFrequency = 0.05f * waveCycleFactor

        for (x in 0 until points) {
            val progress = x.toFloat() / points
            val angle = (x * waveFrequency) - waveOffset

            // Envelope shape mock based on Attack/Decay visual representation
            val age = (System.currentTimeMillis() - triggerTime).coerceAtLeast(0L) / 1000f
            val animVolume = if (age < 0.2f) {
                (age / 0.2f)
            } else {
                (1.0f - ((age - 0.2f) * 0.3f)).coerceAtLeast(envelopeMultiplier)
            }

            val amplitude = (height * 0.3f) * animVolume

            val y = when (waveformType) {
                "Sine" -> {
                    midY + sin(angle) * amplitude
                }
                "Square" -> {
                    val sign = if (sin(angle) >= 0) 1f else -1f
                    midY + sign * amplitude
                }
                "Triangle" -> {
                    val triangleVal = (2.0 * abs(2.0 * (angle / (2.0 * PI) - floor(angle / (2.0 * PI) + 0.5))) - 1.0).toFloat()
                    midY + triangleVal * amplitude
                }
                "Sawtooth" -> {
                    val sawVal = (2.0 * (angle / (2.0 * PI) - floor(angle / (2.0 * PI) + 0.5))).toFloat()
                    midY + sawVal * amplitude
                }
                else -> midY + sin(angle) * amplitude
            }

            if (x == 0) {
                path.moveTo(x.toFloat(), y)
            } else {
                path.lineTo(x.toFloat(), y)
            }
        }

        // Beautiful futuristic neon grid drawing
        val gridLines = 8
        for (i in 1..gridLines) {
            val gridX = (width / gridLines) * i
            drawLine(
                color = Color.White.copy(alpha = 0.05f),
                start = Offset(gridX, 0f),
                end = Offset(gridX, height),
                strokeWidth = 1f
            )
            val gridY = (height / gridLines) * i
            drawLine(
                color = Color.White.copy(alpha = 0.05f),
                start = Offset(0f, gridY),
                end = Offset(width, gridY),
                strokeWidth = 1f
            )
        }

        // Draw centerline
        drawLine(
            color = accentColor.copy(alpha = 0.15f),
            start = Offset(0f, midY),
            end = Offset(width, midY),
            strokeWidth = 2f
        )

        // Draw primary wave
        drawPath(
            path = path,
            brush = Brush.horizontalGradient(listOf(accentColor, secondaryColor)),
            style = Stroke(width = 3.dp.toPx())
        )
    }
}

// Slider Element for UI parameters
@Composable
fun ParameterSlider(
    label: String,
    value: Float,
    rangeStart: Float,
    rangeEnd: Float,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = Color.LightGray, fontSize = 11.sp)
            Text(
                text = String.format("%.2f", value),
                color = Color(0xFF00E6FF),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = rangeStart..rangeEnd,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF00E6FF),
                activeTrackColor = Color(0xFF00E6FF),
                inactiveTrackColor = Color(0xFF262C3D)
            ),
            modifier = Modifier.height(28.dp)
        )
    }
}

// Preset definition for convenient sound profiling
data class Preset(
    val name: String,
    val waveform: String,
    val attack: Float,
    val decay: Float,
    val sustain: Float,
    val release: Float
)

// Dynamic Audio Synthesis Generator & Engine Implementation
fun generateSynthTone(
    frequency: Double,
    waveform: String,
    attack: Float,
    decay: Float,
    sustain: Float,
    release: Float
): ShortArray {
    val sampleRate = 22050 // Optimized audio sampling rate
    val duration = 1.6f    // Fixed window sizing for real-time playbuffer
    val numSamples = (sampleRate * duration).toInt()
    val samples = ShortArray(numSamples)

    // Calculate sample sizes for envelope stages safely
    val attackSamples = (attack * sampleRate).toInt().coerceAtLeast(1)
    val decaySamples = (decay * sampleRate).toInt().coerceAtLeast(1)
    val releaseSamples = (release * sampleRate).toInt().coerceAtLeast(1)
    val sustainSamples = (numSamples - attackSamples - decaySamples - releaseSamples).coerceAtLeast(0)

    for (i in 0 until numSamples) {
        val t = i.toDouble() / sampleRate

        // 1. Compute Base Oscillator Signal
        val rawValue = when (waveform) {
            "Sine" -> {
                sin(2.0 * PI * frequency * t)
            }
            "Square" -> {
                if (sin(2.0 * PI * frequency * t) >= 0) 1.0 else -1.0
            }
            "Triangle" -> {
                val cyclePos = t * frequency - floor(t * frequency)
                if (cyclePos < 0.5) {
                    -1.0 + 4.0 * cyclePos
                } else {
                    3.0 - 4.0 * cyclePos
                }
            }
            "Sawtooth" -> {
                2.0 * (t * frequency - floor(t * frequency + 0.5))
            }
            else -> sin(2.0 * PI * frequency * t)
        }

        // 2. Compute Envelope Gain Factor (ADSR)
        val envelope: Double = when {
            i < attackSamples -> {
                // Linear fade-in
                i.toDouble() / attackSamples
            }
            i < attackSamples + decaySamples -> {
                // Drop from max volume (1.0) to Sustain Level
                val progress = (i - attackSamples).toDouble() / decaySamples
                1.0 - (1.0 - sustain) * progress
            }
            i < attackSamples + decaySamples + sustainSamples -> {
                // Maintain sustain level
                sustain.toDouble()
            }
            else -> {
                // Fade-out decay to zero amplitude
                val releaseStage = i - attackSamples - decaySamples - sustainSamples
                val progress = releaseStage.toDouble() / releaseSamples.coerceAtLeast(1)
                (sustain * (1.0 - progress)).coerceAtLeast(0.0)
            }
        }

        // Complete PCM output assignment with safe hardware boundary clipping
        val maxVolumeScalar = 0.75 // Prevent distortion/clipping
        val sampleValue = (rawValue * envelope * Short.MAX_VALUE * maxVolumeScalar).toInt()
        samples[i] = sampleValue.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }
    return samples
}

// Safe thread execution for low-latency synthesis streaming
fun playSynthTone(
    frequency: Double,
    waveform: String,
    attack: Float,
    decay: Float,
    sustain: Float,
    release: Float
) {
    CoroutineScope(Dispatchers.Default).launch {
        val sampleRate = 22050
        val samples = generateSynthTone(frequency, waveform, attack, decay, sustain, release)
        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = (samples.size * 2).coerceAtLeast(minBufferSize)

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
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        audioTrack.write(samples, 0, samples.size)
        audioTrack.play()

        // Non-blocking cleanup watch delay loop
        val playDurationMs = (samples.size * 1000L) / sampleRate
        kotlinx.coroutines.delay(playDurationMs + 100)
        
        try {
            audioTrack.stop()
            audioTrack.release()
        } catch (e: Exception) {
            // Safe track recycling if overlapping play occurs
        }
    }
}