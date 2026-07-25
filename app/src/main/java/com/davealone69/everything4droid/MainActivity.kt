package com.davealone69.everything4droid

import android.os.Bundle
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MainAppScreen()
        }
    }
}

enum class WaveType {
    SINE, SQUARE, TRIANGLE, SAWTOOTH
}

data class KeyInfo(
    val noteName: String,
    val midiOffset: Int,
    val isBlack: Boolean,
    val blackKeyPositionAfterIndex: Int = -1
)

data class RecordedNote(
    val midiOffset: Int,
    val noteName: String,
    val timestampMs: Long
)

@Composable
fun MainAppScreen() {
    val coroutineScope = rememberCoroutineScope()

    // Synth configurations
    var selectedWaveType by remember { mutableStateOf(WaveType.SINE) }
    var baseOctave by remember { mutableStateOf(4) } // Middle C is C4
    var noteDurationMs by remember { mutableStateOf(800f) }
    var vibratoRate by remember { mutableStateOf(0f) } // 0Hz to 15Hz
    var vibratoDepth by remember { mutableStateOf(0f) } // 0.0 to 1.0

    // Sound states
    var lastPlayedNote by remember { mutableStateOf("None") }
    var lastPlayedFreq by remember { mutableStateOf(0.0) }

    // Recording system states
    var isRecording by remember { mutableStateOf(false) }
    var isPlayingBack by remember { mutableStateOf(false) }
    val recordedSequence = remember { mutableStateListOf<RecordedNote>() }
    var recordStartTime by remember { mutableStateOf(0L) }

    // Waveform visualization real-time points simulation
    var waveVisualOffset by remember { mutableStateOf(0f) }
    LaunchedEffect(lastPlayedFreq) {
        if (lastPlayedFreq > 0) {
            for (i in 1..20) {
                waveVisualOffset = i * 0.5f
                delay(15)
            }
        }
    }

    // Modern Synthwave / Neon Colors
    val backgroundColor = Color(0xFF0F0E17)
    val cardBackground = Color(0xFF1F1D2F)
    val accentNeonPink = Color(0xFFFF2A7A)
    val accentNeonCyan = Color(0xFF00F0FF)
    val accentNeonYellow = Color(0xFFFFD166)
    val textLight = Color(0xFFE2E2E9)
    val textDim = Color(0xFF94A1B2)

    // Piano Keyboard Layout Setup (1.5 Octaves)
    // 10 White Keys, 7 Black Keys mapping
    val whiteKeys = listOf(
        KeyInfo("C", 0, false),
        KeyInfo("D", 2, false),
        KeyInfo("E", 4, false),
        KeyInfo("F", 5, false),
        KeyInfo("G", 7, false),
        KeyInfo("A", 9, false),
        KeyInfo("B", 11, false),
        KeyInfo("C", 12, false),
        KeyInfo("D", 14, false),
        KeyInfo("E", 16, false)
    )

    val blackKeys = listOf(
        KeyInfo("C#", 1, true, 0),
        KeyInfo("D#", 3, true, 1),
        KeyInfo("F#", 6, true, 3),
        KeyInfo("G#", 8, true, 4),
        KeyInfo("A#", 10, true, 5),
        KeyInfo("C#", 13, true, 7),
        KeyInfo("D#", 15, true, 8)
    )

    // Synthesize and play pure digital frequencies
    fun playSound(midiOffset: Int, noteName: String) {
        val midiNote = (baseOctave + 1) * 12 + midiOffset
        // Standard formula to get frequency from midi note
        val frequency = 440.0 * Math.pow(2.0, (midiNote - 69.0) / 12.0)
        
        lastPlayedNote = "$noteName$baseOctave"
        lastPlayedFreq = Math.round(frequency * 10.0) / 10.0

        if (isRecording) {
            val elapsed = System.currentTimeMillis() - recordStartTime
            recordedSequence.add(RecordedNote(midiOffset, noteName, elapsed))
        }

        // Custom synthesis runner in safety thread pool
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val sampleRate = 22050
                val totalSamples = (sampleRate * (noteDurationMs / 1000f)).toInt()
                val generatedBuffer = ShortArray(totalSamples)

                for (i in 0 until totalSamples) {
                    val t = i.toDouble() / sampleRate
                    
                    // Modulate frequency with vibrato LFO
                    val vibratoLFO = if (vibratoRate > 0f) {
                        1.0 + (vibratoDepth * 0.05 * Math.sin(2.0 * Math.PI * vibratoRate * t))
                    } else {
                        1.0
                    }
                    
                    val currentFreq = frequency * vibratoLFO
                    val angle = 2.0 * Math.PI * currentFreq * t

                    // Custom waveshape mathematical generation
                    val rawSample = when (selectedWaveType) {
                        WaveType.SINE -> Math.sin(angle)
                        WaveType.SQUARE -> if (Math.sin(angle) >= 0) 0.6 else -0.6
                        WaveType.TRIANGLE -> {
                            val period = 1.0 / currentFreq
                            val phase = (t % period) / period
                            if (phase < 0.5) {
                                4.0 * phase - 1.0
                            } else {
                                3.0 - 4.0 * phase
                            }
                        }
                        WaveType.SAWTOOTH -> {
                            val period = 1.0 / currentFreq
                            val phase = (t % period) / period
                            2.0 * phase - 1.0
                        }
                    }

                    // ADSR Envelope Simulation: Smooth 15ms Attack, dynamic Exponential Release
                    val attackTime = 0.015 * sampleRate
                    val releaseTime = totalSamples * 0.25
                    val amplitudeEnvelope = when {
                        i < attackTime -> i / attackTime
                        i > totalSamples - releaseTime -> {
                            val releaseFraction = (totalSamples - i) / releaseTime
                            releaseFraction * releaseFraction
                        }
                        else -> 1.0
                    }

                    val finalSample = (rawSample * amplitudeEnvelope * 32767.0).toInt()
                    generatedBuffer[i] = finalSample.coerceIn(-32768, 32767).toShort()
                }

                val audioTrack = AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    totalSamples * 2,
                    AudioTrack.MODE_STATIC
                )

                audioTrack.write(generatedBuffer, 0, totalSamples)
                audioTrack.play()

                // Clean-up AudioTrack resources after sound completes
                delay(noteDurationMs.toLong() + 100)
                audioTrack.stop()
                audioTrack.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Playback system for recorded songs
    fun playRecordedSequence() {
        if (recordedSequence.isEmpty() || isPlayingBack) return
        isPlayingBack = true
        coroutineScope.launch {
            val playStartTime = System.currentTimeMillis()
            var lastEventIndex = 0
            while (lastEventIndex < recordedSequence.size && isPlayingBack) {
                val nextNote = recordedSequence[lastEventIndex]
                val elapsed = System.currentTimeMillis() - playStartTime
                if (elapsed >= nextNote.timestampMs) {
                    playSound(nextNote.midiOffset, nextNote.noteName)
                    lastEventIndex++
                }
                delay(10)
            }
            isPlayingBack = false
        }
    }

    // Root UI Layout
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "NEON SYNTH",
                    color = accentNeonPink,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Custom Wave Synthesis Engine",
                    color = textDim,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Real-time Visualizer Panel
            Box(
                modifier = Modifier
                    .width(130.dp)
                    .height(55.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(cardBackground)
                    .border(1.dp, accentNeonCyan, RoundedCornerShape(8.dp))
                    .padding(4.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (lastPlayedNote == "None") "IDLE" else "$lastPlayedNote: $lastPlayedFreq Hz",
                        color = accentNeonCyan,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    // Pure Canvas Custom Wave Oscilloscope
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val width = size.width
                        val height = size.height
                        val path = Path()
                        path.moveTo(0f, height / 2)

                        for (x in 0..width.toInt()) {
                            val normalizedX = x / width
                            val waveVal = when (selectedWaveType) {
                                WaveType.SINE -> Math.sin(normalizedX * 4 * Math.PI + waveVisualOffset)
                                WaveType.SQUARE -> if (Math.sin(normalizedX * 4 * Math.PI + waveVisualOffset) >= 0) 1.0 else -1.0
                                WaveType.TRIANGLE -> {
                                    val phase = (normalizedX * 2 + waveVisualOffset) % 1.0
                                    if (phase < 0.5) 4.0 * phase - 1.0 else 3.0 - 4.0 * phase
                                }
                                WaveType.SAWTOOTH -> ((normalizedX * 2 + waveVisualOffset) % 1.0) * 2.0 - 1.0
                            }
                            val y = (height / 2) + (waveVal * (height / 2.8)).toFloat()
                            path.lineTo(x.toFloat(), y)
                        }
                        drawPath(
                            path = path,
                            color = accentNeonPink,
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Synth Control Panel
        Card(
            colors = CardDefaults.cardColors(containerColor = cardBackground),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF2C2A3E), RoundedCornerShape(12.dp))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "WAVE SHAPE PARAMETERS",
                    color = textLight,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                
                Spacer(modifier = Modifier.height(8.dp))

                // Wave selectors
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    WaveType.values().forEach { wave ->
                        val isSelected = selectedWaveType == wave
                        Button(
                            onClick = { selectedWaveType = wave },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) accentNeonPink else Color(0xFF262535),
                                contentColor = if (isSelected) Color.White else textLight
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 2.dp)
                        ) {
                            Text(
                                text = wave.name,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Dynamic Audio Modulation Controls
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                        Text(
                            text = "DECAY: ${noteDurationMs.toInt()}ms",
                            color = textDim,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Slider(
                            value = noteDurationMs,
                            onValueChange = { noteDurationMs = it },
                            valueRange = 150f..2000f,
                            colors = SliderDefaults.colors(
                                thumbColor = accentNeonCyan,
                                activeTrackColor = accentNeonCyan,
                                inactiveTrackColor = Color(0xFF262535)
                            )
                        )
                    }

                    Column(modifier = Modifier.weight(1f).padding(start = 6.dp)) {
                        Text(
                            text = "LFO VIBRATO: ${"%.1f".format(vibratoRate)}Hz",
                            color = textDim,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Slider(
                            value = vibratoRate,
                            onValueChange = { 
                                vibratoRate = it
                                if (vibratoRate > 0 && vibratoDepth == 0f) {
                                    vibratoDepth = 0.4f
                                }
                            },
                            valueRange = 0f..15f,
                            colors = SliderDefaults.colors(
                                thumbColor = accentNeonYellow,
                                activeTrackColor = accentNeonYellow,
                                inactiveTrackColor = Color(0xFF262535)
                            )
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Octave Shift Control
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "OCTAVE: ",
                            color = textDim,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = { if (baseOctave > 2) baseOctave-- },
                            modifier = Modifier.size(32.dp).background(Color(0xFF262535), RoundedCornerShape(6.dp))
                        ) {
                            Text("-", color = accentNeonCyan, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Text(
                            text = "$baseOctave",
                            color = textLight,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        )
                        IconButton(
                            onClick = { if (baseOctave < 6) baseOctave++ },
                            modifier = Modifier.size(32.dp).background(Color(0xFF262535), RoundedCornerShape(6.dp))
                        ) {
                            Text("+", color = accentNeonCyan, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }

                    // Vibrato Depth Slider
                    if (vibratoRate > 0f) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).padding(start = 16.dp)) {
                            Text(
                                text = "DEPTH",
                                color = textDim,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Slider(
                                value = vibratoDepth,
                                onValueChange = { vibratoDepth = it },
                                valueRange = 0.1f..1.0f,
                                colors = SliderDefaults.colors(
                                    thumbColor = accentNeonYellow,
                                    activeTrackColor = accentNeonYellow,
                                    inactiveTrackColor = Color(0xFF262535)
                                )
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Record & Playback Live Sequencer Module
        Card(
            colors = CardDefaults.cardColors(containerColor = cardBackground),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF2C2A3E), RoundedCornerShape(12.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "LIVE SEQUENCER",
                        color = textLight,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${recordedSequence.size} Notes Captured",
                        color = if (isRecording) accentNeonPink else textDim,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Record Trigger
                    Button(
                        onClick = {
                            if (!isRecording) {
                                recordedSequence.clear()
                                recordStartTime = System.currentTimeMillis()
                                isRecording = true
                            } else {
                                isRecording = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRecording) accentNeonPink else Color(0xFF262535)
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isRecording) Color.White else accentNeonPink)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (isRecording) "STOP REC" else "RECORD",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Playback Trigger
                    Button(
                        onClick = { playRecordedSequence() },
                        enabled = recordedSequence.isNotEmpty() && !isRecording && !isPlayingBack,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentNeonCyan,
                            disabledContainerColor = Color(0xFF262535)
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            modifier = Modifier.size(12.dp),
                            tint = if (recordedSequence.isNotEmpty()) Color.Black else textDim
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            if (isPlayingBack) "PLAYING" else "PLAY",
                            fontSize = 10.sp,
                            color = if (recordedSequence.isNotEmpty()) Color.Black else textDim,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Reset/Clear Trigger
                    IconButton(
                        onClick = {
                            recordedSequence.clear()
                        },
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color(0xFF262535), RoundedCornerShape(8.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset",
                            modifier = Modifier.size(16.dp),
                            tint = textLight
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Synthesizer Piano Interactive Keyboard Section
        // Designed to fit exactly 10 beautifully rendered white keys and overlaying black keys on top
        val whiteKeyWidthDp = 58.dp
        val whiteKeyHeightDp = 240.dp
        val blackKeyWidthDp = 34.dp
        val blackKeyHeightDp = 145.dp

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(whiteKeyHeightDp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black)
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.TopStart
        ) {
            // 1. Layout of White Keys Row
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                whiteKeys.forEachIndexed { index, kInfo ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(horizontal = 1.5.dp)
                            .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFFEBEBEF),
                                        Color.White,
                                        Color(0xFFCCCCCC)
                                    )
                                )
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                playSound(kInfo.midiOffset, kInfo.noteName)
                            },
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Text(
                            text = "${kInfo.noteName}",
                            color = Color(0xFF1F1D2F),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(bottom = 12.dp),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // 2. Exact Layout overlays of Black Keys
            // Dynamic position calculation overlaying white key bounds
            Box(modifier = Modifier.fillMaxSize()) {
                blackKeys.forEach { bInfo ->
                    // Index of white key after which this black key is placed
                    val anchorIndex = bInfo.blackKeyPositionAfterIndex
                    // Placing it offset precisely to center the gap between key anchorIndex and anchorIndex+1
                    if (anchorIndex != -1) {
                        // Fraction calculation:
                        // Total 10 white keys. Each represents exactly 10% of overall width.
                        // Middle gap is situated at (anchorIndex + 1) * 10%
                        val widthPercentage = 1f / 10f
                        val offsetPercentage = (anchorIndex + 1) * widthPercentage

                        Box(
                            modifier = Modifier
                                .fillMaxWidth(widthPercentage)
                                .fillMaxHeight(0.6f)
                                .align(Alignment.TopStart)
                                .offset(
                                    x = ((anchorIndex + 1) * (whiteKeyWidthDp - 2.5.dp)) - (blackKeyWidthDp / 2.1f)
                                )
                                .padding(horizontal = 1.dp)
                                .shadow(4.dp, shape = RoundedCornerShape(bottomStart = 5.dp, bottomEnd = 5.dp))
                                .clip(RoundedCornerShape(bottomStart = 5.dp, bottomEnd = 5.dp))
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0xFF32303F),
                                            Color(0xFF12111A),
                                            Color.Black
                                        )
                                    )
                                )
                                .border(
                                    1.dp,
                                    accentNeonCyan.copy(alpha = 0.5f),
                                    RoundedCornerShape(bottomStart = 5.dp, bottomEnd = 5.dp)
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    playSound(bInfo.midiOffset, bInfo.noteName)
                                },
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Text(
                                text = bInfo.noteName,
                                color = accentNeonCyan,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                modifier = Modifier.padding(bottom = 10.dp),
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}