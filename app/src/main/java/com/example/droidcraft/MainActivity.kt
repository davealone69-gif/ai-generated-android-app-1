package com.example.droidcraft

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Waveform Type Enum
enum class Waveform {
    SINE, SQUARE, TRIANGLE, SAWTOOTH
}

// Data model for piano keys
data class PianoKey(
    val midiNote: Int,
    val name: String,
    val hasBlackKeyRight: Boolean,
    val blackKeyMidi: Int,
    val blackKeyName: String
)

// Data model for recording notes
data class RecordedNote(
    val midiNote: Int,
    val timestamp: Long
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MainAppScreen()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen() {
    val coroutineScope = rememberCoroutineScope()

    // Synthesizer State
    var selectedWaveform by remember { mutableStateOf(Waveform.SINE) }
    var octaveOffset by remember { mutableStateOf(0) } // Range: -2 to +2
    var toneDuration by remember { mutableStateOf(0.8) } // 0.1s to 2.0s
    var vibratoSpeed by remember { mutableStateOf(5.0) } // 1Hz to 15Hz
    var vibratoDepth by remember { mutableStateOf(0.0) } // 0% to 10%
    var masterVolume by remember { mutableStateOf(0.7f) }

    // Recording State
    val recordedNotes = remember { mutableStateListOf<RecordedNote>() }
    var isRecording by remember { mutableStateOf(false) }
    var isPlayingRecording by remember { mutableStateOf(false) }
    var recordStartTime by remember { mutableStateOf(0L) }
    var infoLog by remember { mutableStateOf("Ready to Play. Touch keys to synthesize sound.") }

    // Visualization and active state feedback
    var activeMidiHighlight by remember { mutableStateOf<Int?>(null) }
    var lastPlayedHz by remember { mutableStateOf(0.0) }

    // Waveform phase animation for the visualizer
    val infiniteTransition = rememberInfiniteTransition(label = "OscilloscopeTransition")
    val phaseOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "WavePhase"
    )

    // Base Piano Key mapping starting at MIDI 60 (Middle C)
    val pianoKeys = remember {
        listOf(
            PianoKey(60, "C", true, 61, "C#"),
            PianoKey(62, "D", true, 63, "D#"),
            PianoKey(64, "E", false, 0, ""),
            PianoKey(65, "F", true, 66, "F#"),
            PianoKey(67, "G", true, 68, "G#"),
            PianoKey(69, "A", true, 70, "A#"),
            PianoKey(71, "B", false, 0, ""),
            PianoKey(72, "C", true, 73, "C#"),
            PianoKey(74, "D", true, 75, "D#"),
            PianoKey(76, "E", false, 0, "")
        )
    }

    // Preset loader function
    val applyPreset: (Waveform, Double, Double, Double) -> Unit = { wave, dur, vibSpd, vibDpth ->
        selectedWaveform = wave
        toneDuration = dur
        vibratoSpeed = vibSpd
        vibratoDepth = vibDpth
        infoLog = "Preset Loaded: ${wave.name} Synth!"
    }

    // Core synthesizer playback function
    val playSynthNote: (Int) -> Unit = { midi ->
        val actualMidi = midi + (octaveOffset * 12)
        val frequency = 440.0 * Math.pow(2.0, (actualMidi - 69).toDouble() / 12.0)
        lastPlayedHz = frequency
        activeMidiHighlight = midi

        // Record notes if active
        if (isRecording) {
            val elapsed = System.currentTimeMillis() - recordStartTime
            recordedNotes.add(RecordedNote(midi, elapsed))
        }

        // Trigger safe thread-safe custom PCM synthesizer
        Thread {
            val sampleRate = 22050
            val numSamples = (sampleRate * toneDuration).toInt()
            val sample = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toDouble() / sampleRate
                // Vibrato Calculation (Modulate main frequency over time)
                val currentFreq = frequency * (1.0 + (vibratoDepth * 0.05) * Math.sin(2.0 * Math.PI * vibratoSpeed * t))
                val angle = 2.0 * Math.PI * currentFreq * t

                // Generate waveform value
                val rawValue = when (selectedWaveform) {
                    Waveform.SINE -> Math.sin(angle)
                    Waveform.SQUARE -> if (Math.sin(angle) >= 0.0) 0.5 else -0.5
                    Waveform.TRIANGLE -> (2.0 / Math.PI) * Math.asin(Math.sin(angle))
                    Waveform.SAWTOOTH -> 2.0 * (t * currentFreq - Math.floor(0.5 + t * currentFreq))
                }

                // Smooth ADSR Linear Envelope (Attack and Release fade to prevent hardware clicks)
                val attackBoundary = numSamples * 0.05
                val releaseBoundary = numSamples * 0.8
                val envelope = when {
                    i < attackBoundary -> i / attackBoundary
                    i > releaseBoundary -> 1.0 - (i - releaseBoundary) / (numSamples - releaseBoundary)
                    else -> 1.0
                }

                // Volume scaler
                sample[i] = (rawValue * envelope * masterVolume * Short.MAX_VALUE).toInt().toShort()
            }

            val bufferSize = sample.size * 2
            val audioTrack = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                AudioTrack.Builder()
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
            } else {
                @Suppress("DEPRECATION")
                AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize,
                    AudioTrack.MODE_STATIC
                )
            }

            try {
                audioTrack.write(sample, 0, sample.size)
                audioTrack.play()
                Thread.sleep((toneDuration * 1000).toLong() + 50)
                audioTrack.stop()
                audioTrack.release()
            } catch (e: Exception) {
                // Safely handle track release state exceptions
            }
        }.start()

        // Auto reset UI active highlights
        coroutineScope.launch {
            delay((toneDuration * 1000).toLong())
            if (activeMidiHighlight == midi) {
                activeMidiHighlight = null
            }
        }
    }

    // Playback routine for recorded sequences
    val playRecording: () -> Unit = {
        if (recordedNotes.isNotEmpty()) {
            coroutineScope.launch(Dispatchers.Default) {
                isPlayingRecording = true
                infoLog = "Playing recorded sequence..."
                val sorted = recordedNotes.toList()
                var lastTime = 0L

                for (note in sorted) {
                    val waitTime = note.timestamp - lastTime
                    if (waitTime > 0) {
                        delay(waitTime)
                    }
                    playSynthNote(note.midiNote)
                    lastTime = note.timestamp
                }
                isPlayingRecording = false
                infoLog = "Playback finished. Total notes: ${recordedNotes.size}"
            }
        } else {
            infoLog = "Nothing recorded yet!"
        }
    }

    // Deep Neon Theme Dark Blue/Grey background
    val darkGrayBg = Color(0xFF0D0F14)
    val cardBg = Color(0xFF161A23)
    val neonCyan = Color(0xFF00F5FF)
    val neonPurple = Color(0xFFBD93F9)
    val hotPink = Color(0xFFFF79C6)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = darkGrayBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Section
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "DROIDCRAFT SYNTHESIZER",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    color = neonCyan,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "CUSTOM REAL-TIME DSP ENGINE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = neonPurple,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Upper Controls: Waveform Oscilloscope Visualizer & Synth controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Oscilloscope Panel (Left side)
                Column(
                    modifier = Modifier
                        .weight(1.1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                        .background(cardBg)
                        .border(1.dp, Color(0xFF2E3440), RoundedCornerShape(12.dp))
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "OSCILLOSCOPE VIEW",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.LightGray,
                        fontFamily = FontFamily.Monospace
                    )

                    // Waveform Canvas (Visualizes selected synth wave dynamically)
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(vertical = 4.dp)
                    ) {
                        val width = size.width
                        val height = size.height
                        val midY = height / 2f
                        val path = Path()
                        path.moveTo(0f, midY)

                        val cycles = 3f
                        for (x in 0 until width.toInt() step 2) {
                            val proportion = x.toFloat() / width
                            val currentAngle = (proportion * cycles * 2 * Math.PI) + phaseOffset

                            val rawWaveY = when (selectedWaveform) {
                                Waveform.SINE -> Math.sin(currentAngle)
                                Waveform.SQUARE -> if (Math.sin(currentAngle) >= 0) 0.6 else -0.6
                                Waveform.TRIANGLE -> (2.0 / Math.PI) * Math.asin(Math.sin(currentAngle))
                                Waveform.SAWTOOTH -> {
                                    val localX = (proportion * cycles) % 1f
                                    2.0 * (localX - 0.5)
                                }
                            }
                            // Add small mock vibrato wobble dynamically to paths
                            val dynamicScale = 1.0 + (vibratoDepth * 0.25 * Math.sin(phaseOffset.toDouble()))
                            val finalY = midY + (rawWaveY * (height * 0.35) * dynamicScale).toFloat()
                            path.lineTo(x.toFloat(), finalY)
                        }

                        drawPath(
                            path = path,
                            color = neonCyan,
                            style = Stroke(width = 3.dp.toPx())
                        )
                    }

                    // Diagnostic telemetry readings
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Hz: ${String.format("%.1f", lastPlayedHz)}",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = hotPink
                        )
                        Text(
                            text = "VOL: ${(masterVolume * 100).toInt()}%",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color.Green
                        )
                    }
                }

                // Synth Controls Panel (Right side)
                Column(
                    modifier = Modifier
                        .weight(1.3f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                        .background(cardBg)
                        .border(1.dp, Color(0xFF2E3440), RoundedCornerShape(12.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "WAVE GENERATOR",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.LightGray,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )

                    // Waveform Selectors
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Waveform.values().forEach { wave ->
                            val isSelected = selectedWaveform == wave
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) neonCyan else Color(0xFF1E222B))
                                    .clickable { selectedWaveform = wave }
                                    .padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = wave.name.take(4),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.Black else Color.Gray,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    // Sound presets buttons row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Button(
                            onClick = { applyPreset(Waveform.SAWTOOTH, 0.4, 8.0, 0.6) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF232834)),
                            modifier = Modifier.weight(1f).height(24.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Retro Pop", fontSize = 8.sp, color = Color.White)
                        }
                        Button(
                            onClick = { applyPreset(Waveform.SINE, 1.5, 4.0, 0.8) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF232834)),
                            modifier = Modifier.weight(1f).height(24.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Dreamy", fontSize = 8.sp, color = Color.White)
                        }
                        Button(
                            onClick = { applyPreset(Waveform.SQUARE, 0.2, 0.0, 0.0) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF232834)),
                            modifier = Modifier.weight(1f).height(24.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Chiptune", fontSize = 8.sp, color = Color.White)
                        }
                    }

                    // Octave Multiplier Selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Octave", fontSize = 10.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            listOf(-1, 0, 1).forEach { oct ->
                                val active = octaveOffset == oct
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                    .background(if (active) neonPurple else Color(0xFF1E222B))
                                    .clickable { octaveOffset = oct },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (oct >= 0) "+$oct" else "$oct",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (active) Color.Black else Color.LightGray
                                    )
                                }
                            }
                        }
                    }

                    // Parameters sliders
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            text = "Release Length: ${String.format("%.1f", toneDuration)}s",
                            fontSize = 9.sp,
                            color = Color.LightGray,
                            fontFamily = FontFamily.Monospace
                        )
                        Slider(
                            value = toneDuration.toFloat(),
                            onValueChange = { toneDuration = it.toDouble() },
                            valueRange = 0.1f..2.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = neonCyan,
                                activeTrackColor = neonCyan.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.height(18.dp)
                        )

                        Text(
                            text = "Vibrato Depth: ${(vibratoDepth * 100).toInt()}%",
                            fontSize = 9.sp,
                            color = Color.LightGray,
                            fontFamily = FontFamily.Monospace
                        )
                        Slider(
                            value = vibratoDepth.toFloat(),
                            onValueChange = { vibratoDepth = it.toDouble() },
                            valueRange = 0.0f..1.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = neonPurple,
                                activeTrackColor = neonPurple.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.height(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Utility Recording & Playback Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E222B))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Record Controls
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = {
                            if (!isRecording) {
                                recordedNotes.clear()
                                recordStartTime = System.currentTimeMillis()
                                isRecording = true
                                infoLog = "Recording started... Play piano now."
                            } else {
                                isRecording = false
                                infoLog = "Recording saved! (${recordedNotes.size} notes stored)"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRecording) Color.Red else Color(0xFF3B4252)
                        ),
                        modifier = Modifier.height(30.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                    ) {
                        Text(
                            text = if (isRecording) "■ STOP" else "● REC",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Button(
                        onClick = playRecording,
                        enabled = !isPlayingRecording && recordedNotes.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4C566A)),
                        modifier = Modifier.height(30.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                    ) {
                        Text("▶ PLAY REC", fontSize = 11.sp, color = Color.White)
                    }

                    Button(
                        onClick = {
                            recordedNotes.clear()
                            infoLog = "Recording cleared."
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E3440)),
                        modifier = Modifier.height(30.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                    ) {
                        Text("CLEAR", fontSize = 11.sp, color = Color.Gray)
                    }
                }

                // Synth Telemetry Logging
                Text(
                    text = infoLog,
                    fontSize = 11.sp,
                    color = neonCyan,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                    textAlign = TextAlign.End,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Master volume controller
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Master Vol: ",
                    fontSize = 10.sp,
                    color = Color.Gray,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(end = 4.dp)
                )
                Slider(
                    value = masterVolume,
                    onValueChange = { masterVolume = it },
                    valueRange = 0.1f..1.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = hotPink,
                        activeTrackColor = hotPink.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.fillMaxWidth().height(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // INTERACTIVE PIANO KEYBOARD SECTION
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.3f)
                    .background(Color.Black, shape = RoundedCornerShape(8.dp))
                    .padding(vertical = 4.dp)
            ) {
                val totalWidth = maxWidth
                val totalWhiteKeysCount = pianoKeys.size
                val whiteKeyWidth = totalWidth / totalWhiteKeysCount
                val blackKeyWidth = whiteKeyWidth * 0.65f
                val blackKeyHeight = maxHeight * 0.6f

                // Layer 1: Render White Keys
                Row(modifier = Modifier.fillMaxSize()) {
                    pianoKeys.forEach { key ->
                        val isHighlighted = activeMidiHighlight == key.midiNote
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(horizontal = 1.dp)
                                .clip(RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp))
                                .background(
                                    if (isHighlighted) {
                                        Brush.verticalGradient(listOf(Color.White, neonCyan))
                                    } else {
                                        Brush.verticalGradient(listOf(Color(0xFFF0F0F0), Color.White))
                                    }
                                )
                                .clickable { playSynthNote(key.midiNote) }
                                .padding(bottom = 12.dp),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Text(
                                text = key.name,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                // Layer 2: Render Black Keys Overlay (Positioned accurately at boundaries)
                pianoKeys.forEachIndexed { index, key ->
                    if (key.hasBlackKeyRight) {
                        val xOffset = (index + 1) * whiteKeyWidth - (blackKeyWidth / 2)
                        val isHighlighted = activeMidiHighlight == key.blackKeyMidi

                        Box(
                            modifier = Modifier
                                .absoluteOffset(x = xOffset, y = 0.dp)
                                .size(width = blackKeyWidth, height = blackKeyHeight)
                                .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                                .background(
                                    if (isHighlighted) {
                                        Brush.verticalGradient(listOf(neonPurple, hotPink))
                                    } else {
                                        Brush.verticalGradient(listOf(Color(0xFF333333), Color(0xFF111111)))
                                    }
                                )
                                .border(1.dp, Color.Black, RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                                .clickable { playSynthNote(key.blackKeyMidi) }
                                .padding(bottom = 6.dp),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Text(
                                text = key.blackKeyName,
                                fontWeight = FontWeight.Bold,
                                color = if (isHighlighted) Color.Black else Color.White,
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}