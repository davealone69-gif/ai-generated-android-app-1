package com.example.droidcraft

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import kotlin.math.asin
import kotlin.math.floor
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MainAppScreen()
        }
    }
}

// Custom Synth Sound Waveforms
enum class Waveform(val displayName: String) {
    SINE("Sine Wave"),
    SQUARE("Square Synth"),
    TRIANGLE("Triangle pluck"),
    SAWTOOTH("Sawtooth Brass")
}

// Song model for the helper/tutorial feature
data class Song(val title: String, val notes: List<Pair<Int, Long>>) // Note offset index, duration in ms

@Composable
fun MainAppScreen() {
    // Synth Parameters
    var currentWaveform by remember { mutableStateOf(Waveform.SINE) }
    var octaveShift by remember { mutableStateOf(4) } // Default Middle Octave (C4 - B4)
    var attackTime by remember { mutableStateOf(0.01f) } // Seconds
    var releaseTime by remember { mutableStateOf(0.8f) } // Seconds
    var volumeLevel by remember { mutableStateOf(0.7f) }

    // App Interactive States
    var activeNoteIndex by remember { mutableStateOf<Int?>(null) }
    var scoreCount by remember { mutableStateOf(0) }
    var highlightedNoteIndex by remember { mutableStateOf<Int?>(null) }
    var isAutoPlaying by remember { mutableStateOf(false) }

    // Real-time Visualizer Animation State
    val infiniteTransition = rememberInfiniteTransition(label = "oscilloscope")
    val phaseOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    // Demo Songs Database
    val songsList = remember {
        listOf(
            Song(
                "Ode to Joy", listOf(
                    Pair(4, 350L), Pair(4, 350L), Pair(5, 350L), Pair(7, 350L),
                    Pair(7, 350L), Pair(5, 350L), Pair(4, 350L), Pair(2, 350L),
                    Pair(0, 350L), Pair(0, 350L), Pair(2, 350L), Pair(4, 350L),
                    Pair(4, 450L), Pair(2, 200L), Pair(2, 600L)
                )
            ),
            Song(
                "Twinkle Twinkle", listOf(
                    Pair(0, 400L), Pair(0, 400L), Pair(7, 400L), Pair(7, 400L),
                    Pair(9, 400L), Pair(9, 400L), Pair(7, 700L), Pair(5, 400L),
                    Pair(5, 400L), Pair(4, 400L), Pair(4, 400L), Pair(2, 400L),
                    Pair(2, 400L), Pair(0, 700L)
                )
            ),
            Song(
                "Mary Lamb", listOf(
                    Pair(4, 300L), Pair(2, 300L), Pair(0, 300L), Pair(2, 300L),
                    Pair(4, 300L), Pair(4, 300L), Pair(4, 500L), Pair(2, 300L),
                    Pair(2, 300L), Pair(2, 500L), Pair(4, 300L), Pair(7, 300L),
                    Pair(7, 500L)
                )
            )
        )
    )
    var selectedSongIndex by remember { mutableStateOf(0) }
    var currentSongStep by remember { mutableStateOf(0) }

    // Core Audio Synthesizer Executor
    fun playSynthesizedNote(noteIndex: Int) {
        activeNoteIndex = noteIndex
        // Highlight logic for tutorial
        val expectedNote = songsList[selectedSongIndex].notes.getOrNull(currentSongStep)?.first
        if (expectedNote == noteIndex) {
            scoreCount += 10
            if (currentSongStep < songsList[selectedSongIndex].notes.size - 1) {
                currentSongStep++
                highlightedNoteIndex = songsList[selectedSongIndex].notes[currentSongStep].first
            } else {
                currentSongStep = 0 // reset/loop
                highlightedNoteIndex = songsList[selectedSongIndex].notes[0].first
            }
        }

        // Calculate custom frequency dynamically (A4 = 440Hz base)
        val frequency = calculateFrequency(noteIndex, octaveShift)

        // Asynchronous synthesization & raw PCM AudioTrack streaming
        CoroutineScope(Dispatchers.Default).launch {
            synthesizeAndPlay(
                frequency = frequency,
                waveform = currentWaveform,
                attack = attackTime,
                release = releaseTime,
                volume = volumeLevel
            )
        }
    }

    // Set up first highlighted note for selected song tutorial
    LaunchedEffect(selectedSongIndex) {
        currentSongStep = 0
        highlightedNoteIndex = songsList[selectedSongIndex].notes.firstOrNull()?.first
    }

    // Auto Player engine
    val autoPlayScope = rememberCoroutineScope()
    fun startAutoPlay() {
        if (isAutoPlaying) return
        isAutoPlaying = true
        autoPlayScope.launch {
            val song = songsList[selectedSongIndex]
            for (step in song.notes) {
                if (!isAutoPlaying) break
                currentSongStep = song.notes.indexOf(step)
                highlightedNoteIndex = step.first
                playSynthesizedNote(step.first)
                delay(step.second)
            }
            isAutoPlaying = false
            currentSongStep = 0
            highlightedNoteIndex = song.notes.firstOrNull()?.first
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF12141C) // Neo dark synthwave background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = "Piano logo",
                            tint = Color(0xFFFF2A6D),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "DroidCraft Synth",
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = Color.White,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Text(
                        text = "Pure Real-Time Custom DSP Engine",
                        fontSize = 11.sp,
                        color = Color(0xFF05D9E8),
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Interactive Tutorial Score Widget
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2235)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Score",
                            tint = Color.Yellow,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Score: $scoreCount",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Waveform and Parameter Control Panel
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF181C2E)),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "INSTRUMENT SYNTHESIS PARAMETERS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF05D9E8),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Waveform Selector Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Waveform.values().forEach { wave ->
                            val isSelected = currentWaveform == wave
                            val chipBg = if (isSelected) Color(0xFFFF2A6D) else Color(0xFF262C48)
                            val chipTextColor = if (isSelected) Color.White else Color(0xFF8F9BB3)

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 2.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(chipBg)
                                    .clickable { currentWaveform = wave }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = wave.displayName.split(" ")[0],
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = chipTextColor,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // ADSR & Octave controls layout
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Left parameter sliders
                        Column(modifier = Modifier.weight(1.2f)) {
                            // Volume control
                            Text(
                                text = "OUTPUT GAIN: ${(volumeLevel * 100).toInt()}%",
                                fontSize = 10.sp,
                                color = Color(0xFF8F9BB3)
                            )
                            Slider(
                                value = volumeLevel,
                                onValueChange = { volumeLevel = it },
                                valueRange = 0.1f..1.0f,
                                modifier = Modifier.height(28.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF05D9E8),
                                    activeTrackColor = Color(0xFF05D9E8)
                                )
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            // Attack Control
                            Text(
                                text = "ATTACK TIME: ${String.format("%.3f", attackTime)}s",
                                fontSize = 10.sp,
                                color = Color(0xFF8F9BB3)
                            )
                            Slider(
                                value = attackTime,
                                onValueChange = { attackTime = it },
                                valueRange = 0.001f..0.3f,
                                modifier = Modifier.height(28.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFFFF2A6D),
                                    activeTrackColor = Color(0xFFFF2A6D)
                                )
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Right parameter sliders
                        Column(modifier = Modifier.weight(1.2f)) {
                            // Release Control
                            Text(
                                text = "DECAY/RELEASE: ${String.format("%.2f", releaseTime)}s",
                                fontSize = 10.sp,
                                color = Color(0xFF8F9BB3)
                            )
                            Slider(
                                value = releaseTime,
                                onValueChange = { releaseTime = it },
                                valueRange = 0.1f..2.0f,
                                modifier = Modifier.height(28.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF01F976),
                                    activeTrackColor = Color(0xFF01F976)
                                )
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            // Octave Selector Buttons
                            Text(
                                text = "CURRENT BASE OCTAVE: C$octaveShift",
                                fontSize = 10.sp,
                                color = Color(0xFF8F9BB3)
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { if (octaveShift > 2) octaveShift-- },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(28.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF262C48))
                                ) {
                                    Text("-1 Oct", fontSize = 10.sp, color = Color.White)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = { if (octaveShift < 6) octaveShift++ },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(28.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF262C48))
                                ) {
                                    Text("+1 Oct", fontSize = 10.sp, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }

            // Real-Time Oscilloscope Visualizer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0B0D18)),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height
                    val middleY = height / 2f
                    val path = Path()

                    path.moveTo(0f, middleY)

                    // Draw an dynamic neon oscilloscope curve depending on the chosen waveform
                    for (x in 0..width.toInt() step 4) {
                        val relativeX = x / width
                        val angle = (relativeX * 6.0 * Math.PI) + phaseOffset

                        val amplitudeFactor = if (activeNoteIndex != null) 0.8f else 0.15f
                        val waveVal = when (currentWaveform) {
                            Waveform.SINE -> sin(angle)
                            Waveform.SQUARE -> if (sin(angle) >= 0) 0.8 else -0.8
                            Waveform.TRIANGLE -> (2.0 / Math.PI) * asin(sin(angle))
                            Waveform.SAWTOOTH -> 2.0 * (relativeX * 12.0 - floor(0.5 + relativeX * 12.0))
                        }

                        val y = middleY + (waveVal * (height * 0.35f) * amplitudeFactor).toFloat()
                        path.lineTo(x.toFloat(), y)
                    }

                    drawPath(
                        path = path,
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFF05D9E8), Color(0xFFFF2A6D))
                        ),
                        style = Stroke(width = 3.dp.toPx())
                    )
                }

                // Waveform overlay HUD info text
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .align(Alignment.TopStart),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "REALTIME DSP OSCILLOSCOPE",
                        fontSize = 9.sp,
                        color = Color(0xFF8F9BB3),
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "ACTIVE VOICE: ${if (activeNoteIndex != null) "C" + octaveShift + " SYNTH" else "IDLE"}",
                        fontSize = 9.sp,
                        color = if (activeNoteIndex != null) Color(0xFF01F976) else Color(0xFFFF2A6D),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Interactive Helper / Song Practice Center
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF181C2E)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1.5f)) {
                        Text(
                            text = "PRACTICE & TUTORIAL PLAYLIST",
                            fontSize = 9.sp,
                            color = Color(0xFF05D9E8),
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    if (selectedSongIndex > 0) selectedSongIndex--
                                    else selectedSongIndex = songsList.size - 1
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.ArrowBack,
                                    contentDescription = "Prev",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(
                                text = songsList[selectedSongIndex].title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp),
                                textAlign = TextAlign.Center
                            )
                            IconButton(
                                onClick = {
                                    if (selectedSongIndex < songsList.size - 1) selectedSongIndex++
                                    else selectedSongIndex = 0
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.ArrowForward,
                                    contentDescription = "Next",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    // Autoplay / Playback button
                    Button(
                        onClick = {
                            if (isAutoPlaying) {
                                isAutoPlaying = false
                            } else {
                                startAutoPlay()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isAutoPlaying) Color(0xFFFF2A6D) else Color(0xFF01F976)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isAutoPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = "Auto",
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isAutoPlaying) "STOP" else "AUTO PLAY",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Piano Keyboard Module
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0B0D18))
                    .padding(4.dp)
            ) {
                // Key Definitions of one full customized musical octave
                // We map 12 dynamic notes: C (0), C#(1), D(2), D#(3), E(4), F(5), F#(6), G(7), G#(8), A(9), A#(10), B(11)
                val whiteKeyOffsets = listOf(
                    Pair("C", 0),
                    Pair("D", 2),
                    Pair("E", 4),
                    Pair("F", 5),
                    Pair("G", 7),
                    Pair("A", 9),
                    Pair("B", 11)
                )

                val blackKeyOffsets = listOf(
                    Pair("C#", 1),
                    Pair("D#", 3),
                    null, // empty gap between E & F
                    Pair("F#", 6),
                    Pair("G#", 8),
                    Pair("A#", 10)
                )

                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val totalWidth = maxWidth
                    val whiteKeyWidth = totalWidth / 7

                    // 1. Render White Keys First (Underlay layer)
                    Row(modifier = Modifier.fillMaxSize()) {
                        whiteKeyOffsets.forEach { note ->
                            val isHighlighted = highlightedNoteIndex == note.second
                            val isActive = activeNoteIndex == note.second

                            val animatedKeyColor by animateColorAsState(
                                targetValue = when {
                                    isActive -> Color(0xFFFF2A6D)
                                    isHighlighted -> Color(0xFF05D9E8)
                                    else -> Color.White
                                },
                                animationSpec = tween(120),
                                label = "WhiteKeyColor"
                            )

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .padding(2.dp)
                                    .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                                    .background(animatedKeyColor)
                                    .clickable { playSynthesizedNote(note.second) },
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Text(
                                    text = note.first,
                                    color = if (isActive || isHighlighted) Color.White else Color.Black,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 12.dp),
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    // 2. Render Black Keys (Overlay Layer at boundary positions)
                    val blackKeyWidth = whiteKeyWidth * 0.65f
                    blackKeyOffsets.forEachIndexed { index, pair ->
                        if (pair != null) {
                            val isHighlighted = highlightedNoteIndex == pair.second
                            val isActive = activeNoteIndex == pair.second

                            val animatedBlackKeyColor by animateColorAsState(
                                targetValue = when {
                                    isActive -> Color(0xFFFF2A6D)
                                    isHighlighted -> Color(0xFF05D9E8)
                                    else -> Color(0xFF1E2235)
                                },
                                animationSpec = tween(120),
                                label = "BlackKeyColor"
                            )

                            // Position black keys perfectly on the dividing gaps between white keys
                            val leftOffset = (whiteKeyWidth * (index + 1)) - (blackKeyWidth / 2f)

                            Box(
                                modifier = Modifier
                                    .offset(x = leftOffset)
                                    .width(blackKeyWidth)
                                    .fillMaxHeight(0.58f)
                                    .clip(RoundedCornerShape(bottomStart = 5.dp, bottomEnd = 5.dp))
                                    .background(animatedBlackKeyColor)
                                    .clickable { playSynthesizedNote(pair.second) },
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Text(
                                    text = pair.first,
                                    color = if (isActive || isHighlighted) Color.Black else Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 8.dp),
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Reset active visual notes representation periodically to keep the scope clean
    LaunchedEffect(activeNoteIndex) {
        if (activeNoteIndex != null) {
            delay(350)
            activeNoteIndex = null
        }
    }
}

/**
 * Calculates raw note frequency from middle standard octave
 * We scale pitch according to musical semi-tone steps relative to A4 (440Hz)
 */
fun calculateFrequency(noteIndex: Int, octaveShift: Int): Double {
    val a4Index = 9 // A4 semi-tone index
    val a4Octave = 4
    val totalSteps = (noteIndex - a4Index) + (octaveShift - a4Octave) * 12
    return 440.0 * Math.pow(2.0, totalSteps / 12.0)
}

/**
 * Performs immediate high-speed memory-efficient audio synthesization using Android's AudioTrack.
 * Avoids any assets or disk reads for instant zero-dependency execution.
 */
fun synthesizeAndPlay(
    frequency: Double,
    waveform: Waveform,
    attack: Float,
    release: Float,
    volume: Float
) {
    val sampleRate = 22050 // Optimized standard target sample rate for custom synthetic outputs
    val durationSeconds = attack + release
    val totalSamples = (durationSeconds * sampleRate).toInt()
    val rawSamplesBuffer = ShortArray(totalSamples)

    // Synthesize physical digital waveform arrays directly to raw memory buffer
    for (i in 0 until totalSamples) {
        val t = i.toDouble() / sampleRate
        val angle = 2.0 * Math.PI * frequency * t

        val baseWaveVal = when (waveform) {
            Waveform.SINE -> sin(angle)
            Waveform.SQUARE -> if (sin(angle) >= 0.0) 1.0 else -1.0
            Waveform.TRIANGLE -> (2.0 / Math.PI) * asin(sin(angle))
            Waveform.SAWTOOTH -> 2.0 * (t * frequency - floor(0.5 + t * frequency))
        }

        // Custom ADSR (Attack, Decay, Sustain, Release) volume envelope shaping
        val amplitudeEnvelope = if (t < attack) {
            t / attack // Attack ramping up to peak amplitude
        } else {
            // Decay & release ramping down exponentially
            val decayTime = t - attack
            Math.exp(-3.0 * decayTime / release)
        }

        val scaledValue = (baseWaveVal * amplitudeEnvelope * Short.MAX_VALUE * volume).toInt()
        rawSamplesBuffer[i] = scaledValue.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }

    // Write straight to AudioTrack in static memory buffer mode for optimal hardware performance
    try {
        val audioTrack = AudioTrack(
            AudioManager.STREAM_MUSIC,
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            totalSamples * 2,
            AudioTrack.MODE_STATIC
        )
        audioTrack.write(rawSamplesBuffer, 0, totalSamples)
        audioTrack.play()

        // Clean up allocation resources instantly after execution to avoid system leaks
        Thread {
            try {
                Thread.sleep((durationSeconds * 1000).toLong() + 200)
                audioTrack.stop()
                audioTrack.release()
            } catch (e: Exception) {
                // Safe ignore if track was already destroyed
            }
        }.start()

    } catch (e: Exception) {
        e.printStackTrace()
    }
}