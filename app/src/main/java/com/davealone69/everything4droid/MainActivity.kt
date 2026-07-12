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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

enum class Waveform(val label: String) {
    SINE("Sine"),
    SQUARE("Square"),
    TRIANGLE("Triangle"),
    PIANO("Synth Piano")
}

@Composable
fun MainAppScreen() {
    var waveform by remember { mutableStateOf(Waveform.PIANO) }
    var decaySpeed by remember { mutableStateOf(1.2f) }
    var volume by remember { mutableStateOf(0.7f) }
    var octave by remember { mutableStateOf(4) }
    val activeNotes = remember { mutableStateMapOf<Int, Boolean>() }
    var isPlayingDemo by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Sound Synthesis Engine Player function
    fun playTone(frequency: Double) {
        Thread {
            val sampleRate = 22050
            val duration = decaySpeed.coerceIn(0.2f, 3.0f).toDouble()
            val numSamples = (sampleRate * duration).toInt()
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toDouble() / sampleRate
                // Exponential decay envelope
                val envelope = Math.exp(-t * (4.0 / duration))

                val value = when (waveform) {
                    Waveform.SINE -> Math.sin(2.0 * Math.PI * frequency * t)
                    Waveform.SQUARE -> if (Math.sin(2.0 * Math.PI * frequency * t) >= 0) 0.5 else -0.5
                    Waveform.TRIANGLE -> {
                        val period = 1.0 / frequency
                        val phase = (t % period) / period
                        if (phase < 0.5) (4.0 * phase - 1.0) else (3.0 - 4.0 * phase)
                    }
                    Waveform.PIANO -> {
                        // Additive synthesis simulation for classic mechanical piano timbre
                        val f1 = Math.sin(2.0 * Math.PI * frequency * t)
                        val f2 = 0.5 * Math.sin(2.0 * Math.PI * (2 * frequency) * t)
                        val f3 = 0.25 * Math.sin(2.0 * Math.PI * (3 * frequency) * t)
                        val f4 = 0.125 * Math.sin(2.0 * Math.PI * (4 * frequency) * t)
                        (f1 + f2 + f3 + f4) / 1.875
                    }
                }
                buffer[i] = (value * envelope * 32767.0 * volume.toDouble()).toInt().coerceIn(-32768, 32767).toShort()
            }

            try {
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
                    .setBufferSizeInBytes(buffer.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                audioTrack.write(buffer, 0, buffer.size)
                audioTrack.play()
                Thread.sleep((duration * 1000).toLong())
                audioTrack.stop()
                audioTrack.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    fun getFrequency(baseOctave: Int, semitoneOffset: Int): Double {
        val midi = 12 * (baseOctave + 1) + semitoneOffset
        return 440.0 * Math.pow(2.0, (midi - 69).toDouble() / 12.0)
    }

    fun playNoteInteractive(semitoneOffset: Int) {
        val freq = getFrequency(octave, semitoneOffset)
        playTone(freq)
        coroutineScope.launch {
            activeNotes[semitoneOffset] = true
            delay(250)
            activeNotes[semitoneOffset] = false
        }
    }

    // Demo song score (Mary Had a Little Lamb)
    val maryLambScore = listOf(
        4 to 400, 2 to 400, 0 to 400, 2 to 400, 4 to 400, 4 to 400, 4 to 800,
        2 to 400, 2 to 400, 2 to 800, 4 to 400, 7 to 400, 7 to 800,
        4 to 400, 2 to 400, 0 to 400, 2 to 400, 4 to 400, 4 to 400, 4 to 400,
        4 to 400, 2 to 400, 2 to 400, 4 to 400, 2 to 400, 0 to 800
    )

    fun startDemoPlayback() {
        if (isPlayingDemo) {
            isPlayingDemo = false
            return
        }
        isPlayingDemo = true
        coroutineScope.launch {
            for (note in maryLambScore) {
                if (!isPlayingDemo) break
                val semitone = note.first
                val duration = note.second

                val freq = getFrequency(octave, semitone)
                playTone(freq)

                activeNotes[semitone] = true
                delay(duration.toLong() - 50)
                activeNotes[semitone] = false
                delay(50)
            }
            isPlayingDemo = false
        }
    }

    // Phase accumulator for dynamic oscilloscope visualizer
    var visualizerPhase by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            visualizerPhase += 0.15f
            delay(16)
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
            // Title & Visualizer Panel
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "AURA SYNTH PIANO",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color(0xFF00FF66),
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Custom Realtime Additive & Waveform Synthesis",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Oscilloscope Visualizer Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .border(1.dp, Color(0xFF333333), RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val width = size.width
                        val height = size.height
                        val midY = height / 2f
                        val path = Path()
                        path.moveTo(0f, midY)

                        for (x in 0..width.toInt() step 6) {
                            val ratio = x.toFloat() / width
                            val angle = ratio * 2.0 * Math.PI * 4.0 + visualizerPhase
                            val rawWave = when (waveform) {
                                Waveform.SINE -> Math.sin(angle)
                                Waveform.SQUARE -> if (Math.sin(angle) >= 0) 0.6 else -0.6
                                Waveform.TRIANGLE -> {
                                    val phase = (angle % (2 * Math.PI)) / (2 * Math.PI)
                                    if (phase < 0.5) (4.0 * phase - 1.0) else (3.0 - 4.0 * phase)
                                }
                                Waveform.PIANO -> {
                                    val f1 = Math.sin(angle)
                                    val f2 = 0.5 * Math.sin(2 * angle)
                                    val f3 = 0.25 * Math.sin(3 * angle)
                                    (f1 + f2 + f3) / 1.75
                                }
                            }
                            val y = midY + (rawWave * (height * 0.35f)).toFloat()
                            path.lineTo(x.toFloat(), y)
                        }
                        drawPath(
                            path = path,
                            color = Color(0xFF00FF66),
                            style = Stroke(width = 4f)
                        )
                    }
                }
            }

            // Central Synthesizer Controls
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Row 1: Sound Generator Mode
                    Text(
                        text = "Waveform Profile",
                        fontWeight = FontWeight.SemiBold,
                        color = Color.LightGray,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Waveform.values().forEach { wave ->
                            val isSelected = wave == waveform
                            Button(
                                onClick = { waveform = wave },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) Color(0xFF00FF66) else Color(0xFF2C2C2E),
                                    contentColor = if (isSelected) Color.Black else Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
                            ) {
                                Text(
                                    text = wave.label.split(" ").last(),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Row 2: Sliders for synthesis values
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = "Decay: ${String.format("%.1f", decaySpeed)}s",
                                color = Color.LightGray,
                                fontSize = 12.sp
                            )
                            Slider(
                                value = decaySpeed,
                                onValueChange = { decaySpeed = it },
                                valueRange = 0.2f..3.0f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF00FF66),
                                    activeTrackColor = Color(0xFF00FF66)
                                )
                            )
                        }
                        Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                            Text(
                                text = "Volume: ${(volume * 100).toInt()}%",
                                color = Color.LightGray,
                                fontSize = 12.sp
                            )
                            Slider(
                                value = volume,
                                onValueChange = { volume = it },
                                valueRange = 0.1f..1.0f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF00FF66),
                                    activeTrackColor = Color(0xFF00FF66)
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Row 3: Octave selector and Demo Trigger
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Octave controller
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Octave: ",
                                color = Color.LightGray,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            FilledTonalButton(
                                onClick = { if (octave > 2) octave-- },
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.size(34.dp),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("-", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                            Text(
                                text = octave.toString(),
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 12.dp),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            FilledTonalButton(
                                onClick = { if (octave < 6) octave++ },
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.size(34.dp),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("+", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }

                        // Demo Song player button
                        Button(
                            onClick = { startDemoPlayback() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isPlayingDemo) Color.Red else Color(0xFFBB86FC)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = "Play",
                                modifier = Modifier.size(16.dp),
                                tint = Color.Black
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isPlayingDemo) "Stop Demo" else "Demo Melody",
                                color = Color.Black,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Dynamic Adaptive Piano Keyboard (Landscape or Portrait ratio compatible layout)
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black, RoundedCornerShape(12.dp))
                    .padding(4.dp)
            ) {
                val totalWidth = maxWidth
                val totalHeight = maxHeight

                // Define standard key configurations
                val whiteKeys = listOf(
                    Pair("C", 0), Pair("D", 2), Pair("E", 4), Pair("F", 5),
                    Pair("G", 7), Pair("A", 9), Pair("B", 11), Pair("C", 12),
                    Pair("D", 14), Pair("E", 16)
                )

                val blackKeys = listOf(
                    Triple("C#", 1, 1f),
                    Triple("D#", 3, 2f),
                    Triple("F#", 6, 4f),
                    Triple("G#", 8, 5f),
                    Triple("A#", 10, 6f),
                    Triple("C#", 13, 8f),
                    Triple("D#", 15, 9f)
                )

                val whiteCount = whiteKeys.size
                val whiteKeyWidth = totalWidth / whiteCount
                val blackKeyWidth = whiteKeyWidth * 0.65f
                val blackKeyHeight = totalHeight * 0.62f

                // Draw White Keys Background Layer
                Row(modifier = Modifier.fillMaxSize()) {
                    whiteKeys.forEachIndexed { _, keyInfo ->
                        val semitone = keyInfo.second
                        val isActive = activeNotes[semitone] == true
                        val animatedKeyColor by animateColorAsState(
                            targetValue = if (isActive) Color(0xFFAEFFA2) else Color.White,
                            label = "WhiteKeyColor"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(horizontal = 1.5.dp)
                                .background(
                                    color = animatedKeyColor,
                                    shape = RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp)
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    playNoteInteractive(semitone)
                                },
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Text(
                                text = keyInfo.first,
                                color = Color.DarkGray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                    }
                }

                // Overlay Black Keys Layer
                blackKeys.forEach { blackKey ->
                    val semitone = blackKey.second
                    val offsetUnits = blackKey.third
                    val isActive = activeNotes[semitone] == true
                    val animatedBlackKeyColor by animateColorAsState(
                        targetValue = if (isActive) Color(0xFFFF8B8B) else Color(0xFF242424),
                        label = "BlackKeyColor"
                    )

                    // Position black key in overlapping slots
                    val leftPosition = (whiteKeyWidth * offsetUnits) - (blackKeyWidth / 2f)

                    Box(
                        modifier = Modifier
                            .absoluteOffset(x = leftPosition, y = 0.dp)
                            .width(blackKeyWidth)
                            .height(blackKeyHeight)
                            .background(
                                color = animatedBlackKeyColor,
                                shape = RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp)
                            )
                            .border(1.dp, Color.Black, RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                playNoteInteractive(semitone)
                            },
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Text(
                            text = blackKey.first,
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Normal,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                }
            }
        }
    }
}