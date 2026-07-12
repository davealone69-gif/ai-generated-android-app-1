package com.davealone69.everything4droid

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF00E676),
                    secondary = Color(0xFF00B0FF),
                    background = Color(0xFF121212),
                    surface = Color(0xFF1E1E1E),
                    onPrimary = Color.Black,
                    onSecondary = Color.Black,
                    onBackground = Color.White,
                    onSurface = Color.White
                )
            ) {
                MainAppScreen()
            }
        }
    }
}

// Data models for Tutor Songs
data class SongStep(val name: String, val midiOffset: Int)

data class Song(val title: String, val steps: List<SongStep>)

// Synthesizer Waveform types
enum class Waveform {
    SINE, TRIANGLE, SAWTOOTH, SQUARE
}

// Custom recorded note representation
data class RecordedNote(
    val noteName: String,
    val midiOffset: Int,
    val timestamp: Long,
    val durationMs: Int,
    val wave: Waveform
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen() {
    // Synth parameters
    var baseOctave by remember { mutableStateOf(4) } // Default C4 starts at 60
    var selectedWaveform by remember { mutableStateOf(Waveform.SINE) }
    var noteDurationMs by remember { mutableStateOf(600) }
    var envelopeDecay by remember { mutableStateOf(0.8f) } // 0.1f to 1.0f

    // Interactive state helpers
    var activeNoteName by remember { mutableStateOf("") }
    var lastPlayedFrequency by remember { mutableStateOf(0.0) }
    val audioPoints = remember { mutableStateListOf<Float>() }

    // Recording features
    var isRecording by remember { mutableStateOf(false) }
    val recordedSession = remember { mutableStateListOf<RecordedNote>() }
    var isPlayingBack by remember { mutableStateOf(false) }
    var recordStartTime by remember { mutableStateOf(0L) }

    // Tutor / Mini Game state
    val songs = remember {
        listOf(
            Song(
                "Twinkle Twinkle", listOf(
                    SongStep("C", 0), SongStep("C", 0), SongStep("G", 7), SongStep("G", 7),
                    SongStep("A", 9), SongStep("A", 9), SongStep("G", 7), SongStep("F", 5),
                    SongStep("F", 5), SongStep("E", 4), SongStep("E", 4), SongStep("D", 2),
                    SongStep("D", 2), SongStep("C", 0)
                )
            ),
            Song(
                "Ode to Joy", listOf(
                    SongStep("E", 4), SongStep("E", 4), SongStep("F", 5), SongStep("G", 7),
                    SongStep("G", 7), SongStep("F", 5), SongStep("E", 4), SongStep("D", 2),
                    SongStep("C", 0), SongStep("C", 0), SongStep("D", 2), SongStep("E", 4),
                    SongStep("E", 4), SongStep("D", 2), SongStep("D", 2)
                )
            ),
            Song(
                "Jingle Bells", listOf(
                    SongStep("E", 4), SongStep("E", 4), SongStep("E", 4),
                    SongStep("E", 4), SongStep("E", 4), SongStep("E", 4),
                    SongStep("E", 4), SongStep("G", 7), SongStep("C", 0),
                    SongStep("D", 2), SongStep("E", 4)
                )
            )
        )
    }
    var activeSongIndex by remember { mutableStateOf(-1) }
    var currentSongStepIndex by remember { mutableStateOf(0) }

    // Audio Visualizer effect scope
    val scope = rememberCoroutineScope()

    // Trigger synthesising note audio track real-time
    fun playSynthTone(midiOffset: Int, noteName: String) {
        val midiNote = 12 * (baseOctave + 1) + midiOffset
        val frequency = 440.0 * Math.pow(2.0, (midiNote - 69) / 12.0)

        activeNoteName = "$noteName${baseOctave + (if (midiOffset >= 12) 1 else 0)}"
        lastPlayedFrequency = frequency

        // Capture recording events
        if (isRecording) {
            val elapsed = System.currentTimeMillis() - recordStartTime
            recordedSession.add(
                RecordedNote(
                    noteName = noteName,
                    midiOffset = midiOffset,
                    timestamp = elapsed,
                    durationMs = noteDurationMs,
                    wave = selectedWaveform
                )
            )
        }

        // Handle tutorial progression matching
        if (activeSongIndex >= 0) {
            val song = songs[activeSongIndex]
            val expectedStep = song.steps[currentSongStepIndex]
            if (expectedStep.midiOffset == midiOffset) {
                if (currentSongStepIndex < song.steps.lastIndex) {
                    currentSongStepIndex++
                } else {
                    currentSongStepIndex = 0 // loop or complete
                }
            }
        }

        // Spark dynamic wave generator visual points
        scope.launch(Dispatchers.Default) {
            audioPoints.clear()
            for (i in 0..40) {
                audioPoints.add((Math.sin(i * 0.4 + System.currentTimeMillis() % 100) * 40).toFloat())
            }
        }

        // Custom Sound synthesis running on a lightweight thread
        Thread {
            try {
                val sampleRate = 44100
                val totalSamples = (sampleRate * (noteDurationMs / 1000.0)).toInt()
                val samples = ShortArray(totalSamples)

                for (i in 0 until totalSamples) {
                    val t = i.toDouble() / sampleRate
                    val rawWave = when (selectedWaveform) {
                        Waveform.SINE -> sin(2.0 * Math.PI * frequency * t)
                        Waveform.SQUARE -> if (sin(2.0 * Math.PI * frequency * t) >= 0) 1.0 else -1.0
                        Waveform.TRIANGLE -> 2.0 * abs(2.0 * (t * frequency - floor(t * frequency + 0.5))) - 1.0
                        Waveform.SAWTOOTH -> 2.0 * (t * frequency - floor(t * frequency + 0.5))
                    }

                    // Simple Attack-Decay envelope
                    val envelope = if (i < totalSamples * 0.1) {
                        i.toDouble() / (totalSamples * 0.1) // Quick attack
                    } else {
                        // Linear decay slope based on decay slider coefficient
                        val decayFactor = (totalSamples - i).toDouble() / (totalSamples * 0.9)
                        Math.max(0.0, decayFactor * envelopeDecay.toDouble())
                    }

                    val rawSample = (rawWave * Short.MAX_VALUE * envelope).toInt()
                    samples[i] = rawSample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }

                val minBufSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

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
                    .setBufferSizeInBytes(Math.max(minBufSize, samples.size * 2))
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                audioTrack.write(samples, 0, samples.size)
                audioTrack.play()

                Thread.sleep(noteDurationMs.toLong() + 50)
                audioTrack.stop()
                audioTrack.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    // Playback captured session notes
    fun startRecordingPlayback() {
        if (recordedSession.isEmpty() || isPlayingBack) return
        isPlayingBack = true
        scope.launch(Dispatchers.Default) {
            val sessionCopy = recordedSession.toList()
            var lastTime = 0L
            for (note in sessionCopy) {
                if (!isPlayingBack) break
                val delayTime = note.timestamp - lastTime
                if (delayTime > 0) {
                    delay(delayTime)
                }
                playSynthTone(note.midiOffset, note.noteName)
                lastTime = note.timestamp
            }
            isPlayingBack = false
        }
    }

    // UI Structure
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Title and Status Banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "AURA SYNTH PIANO",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Real-time custom PCM wave synthesizer",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray
                )
            }

            // Waveform and frequency indicator
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (activeNoteName.isNotEmpty()) "$activeNoteName (${String.format("%.1f", lastPlayedFrequency)}Hz)" else "Ready",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Custom Audio Visualizer Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFF333333), RoundedCornerShape(12.dp))
                .background(Color(0xFF0A0A0A)),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val midY = height / 2f

                // Draw background grid lines
                drawLine(Color(0xFF151515), Offset(0f, midY), Offset(width, midY), strokeWidth = 1f)
                drawLine(Color(0xFF151515), Offset(0f, midY - height/4), Offset(width, midY - height/4), strokeWidth = 1f)
                drawLine(Color(0xFF151515), Offset(0f, midY + height/4), Offset(width, midY + height/4), strokeWidth = 1f)

                if (audioPoints.isNotEmpty()) {
                    val step = width / (audioPoints.size - 1)
                    for (i in 0 until audioPoints.size - 1) {
                        val startX = i * step
                        val startY = midY + audioPoints[i]
                        val endX = (i + 1) * step
                        val endY = midY + audioPoints[i + 1]
                        drawLine(
                            color = Color(0xFF00E676),
                            start = Offset(startX, startY),
                            end = Offset(endX, endY),
                            strokeWidth = 3f
                        )
                    }
                } else {
                    // Draw flat synth line if inactive
                    drawLine(Color(0xFF00B0FF), Offset(0f, midY), Offset(width, midY), strokeWidth = 2f)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Control Panel
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(4.dp, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Row 1: Sound Generator Wave Selector
                Text(
                    text = "SYNTH GENERATOR WAVEFORM",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Waveform.values().forEach { wave ->
                        val isSelected = selectedWaveform == wave
                        Button(
                            onClick = { selectedWaveform = wave },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF2E2E2E),
                                contentColor = if (isSelected) Color.Black else Color.White
                            ),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                text = wave.name,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Row 2: Sliders
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("DURATION", fontSize = 11.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                            Text("${noteDurationMs}ms", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary, fontFamily = FontFamily.Monospace)
                        }
                        Slider(
                            value = noteDurationMs.toFloat(),
                            onValueChange = { noteDurationMs = it.toInt() },
                            valueRange = 200f..1500f,
                            steps = 13,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.secondary,
                                activeTrackColor = MaterialTheme.colorScheme.secondary
                            )
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("DECAY SLOPE", fontSize = 11.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                            Text(String.format("%.1f", envelopeDecay), fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary, fontFamily = FontFamily.Monospace)
                        }
                        Slider(
                            value = envelopeDecay,
                            onValueChange = { envelopeDecay = it },
                            valueRange = 0.1f..1.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.secondary,
                                activeTrackColor = MaterialTheme.colorScheme.secondary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Octave controller
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("BASE OCTAVE: ", fontSize = 12.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                        Text("C$baseOctave", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace)
                    }

                    Row {
                        IconButton(
                            onClick = { if (baseOctave > 2) baseOctave-- },
                            enabled = baseOctave > 2
                        ) {
                            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Lower octave", tint = if (baseOctave > 2) Color.White else Color.DarkGray)
                        }
                        IconButton(
                            onClick = { if (baseOctave < 6) baseOctave++ },
                            enabled = baseOctave < 6
                        ) {
                            Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Higher octave", tint = if (baseOctave < 6) Color.White else Color.DarkGray)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Session Recording Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    if (!isRecording) {
                        recordedSession.clear()
                        recordStartTime = System.currentTimeMillis()
                        isRecording = true
                    } else {
                        isRecording = false
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRecording) Color.Red else Color(0xFF2E2E2E)
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (isRecording) Icons.Default.Close else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (isRecording) "STOP REC" else "RECORD", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }

            Button(
                onClick = { startRecordingPlayback() },
                enabled = recordedSession.isNotEmpty() && !isPlayingBack,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    disabledContainerColor = Color(0xFF222222)
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("PLAY REC (${recordedSession.size})", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Music Tutor Mode
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF181818)),
            border = borderStrokeForTutor(activeSongIndex >= 0)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "ACADEMY TUTOR",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                        fontFamily = FontFamily.Monospace
                    )

                    if (activeSongIndex >= 0) {
                        TextButton(onClick = {
                            activeSongIndex = -1
                            currentSongStepIndex = 0
                        }) {
                            Text("Quit Tutor", color = Color.Red, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }

                if (activeSongIndex < 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        songs.forEachIndexed { index, song ->
                            Button(
                                onClick = {
                                    activeSongIndex = index
                                    currentSongStepIndex = 0
                                },
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E2E2E)),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(song.title, fontSize = 10.sp, maxLines = 1, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                } else {
                    val currentSong = songs[activeSongIndex]
                    val nextNote = currentSong.steps[currentSongStepIndex]

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Playing: ${currentSong.title}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.LightGray
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "TAP NOTE: ",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                fontFamily = FontFamily.Monospace
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = nextNote.name,
                                    color = Color.Black,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Simple progression timeline
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            currentSong.steps.forEachIndexed { i, step ->
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(
                                            if (i < currentSongStepIndex) MaterialTheme.colorScheme.primary
                                            else if (i == currentSongStepIndex) MaterialTheme.colorScheme.secondary
                                            else Color.DarkGray
                                        )
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // THE PIANO KEYBOARD DESIGN
        // Supporting 2 Octaves visually inside a scrollable viewport
        val whiteKeys = listOf(
            WhiteKeyInfo("C", 0),
            WhiteKeyInfo("D", 2),
            WhiteKeyInfo("E", 4),
            WhiteKeyInfo("F", 5),
            WhiteKeyInfo("G", 7),
            WhiteKeyInfo("A", 9),
            WhiteKeyInfo("B", 11),
            WhiteKeyInfo("C", 12),
            WhiteKeyInfo("D", 14),
            WhiteKeyInfo("E", 16),
            WhiteKeyInfo("F", 17),
            WhiteKeyInfo("G", 19),
            WhiteKeyInfo("A", 21),
            WhiteKeyInfo("B", 23)
        )

        val blackKeys = listOf(
            BlackKeyInfo("C#", 1, 0),
            BlackKeyInfo("D#", 3, 1),
            BlackKeyInfo("F#", 6, 3),
            BlackKeyInfo("G#", 8, 4),
            BlackKeyInfo("A#", 10, 5),
            BlackKeyInfo("C#", 13, 7),
            BlackKeyInfo("D#", 15, 8),
            BlackKeyInfo("F#", 18, 10),
            BlackKeyInfo("G#", 20, 11),
            BlackKeyInfo("A#", 22, 12)
        )

        val whiteKeyWidth = 56.dp
        val blackKeyWidth = 36.dp
        val keyboardScrollState = rememberScrollState()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .horizontalScroll(keyboardScrollState)
                .background(Color.Black)
                .padding(vertical = 6.dp)
        ) {
            // 1. Render White Keys first
            Row(modifier = Modifier.fillMaxHeight()) {
                whiteKeys.forEach { key ->
                    val isTutorTarget = activeSongIndex >= 0 &&
                            songs[activeSongIndex].steps[currentSongStepIndex].midiOffset == key.midiOffset

                    Box(
                        modifier = Modifier
                            .width(whiteKeyWidth)
                            .fillMaxHeight()
                            .padding(horizontal = 2.dp)
                            .clip(RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp))
                            .background(
                                if (isTutorTarget) Color(0xFF1B5E20) else Color.White
                            )
                            .clickable {
                                playSynthTone(key.midiOffset, key.name)
                            }
                            .border(1.dp, Color.Gray, RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp)),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Text(
                            text = key.name,
                            color = if (isTutorTarget) Color.White else Color.Black,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }
                }
            }

            // 2. Overlay Black Keys on Top
            blackKeys.forEach { key ->
                val isTutorTarget = activeSongIndex >= 0 &&
                        songs[activeSongIndex].steps[currentSongStepIndex].midiOffset == key.midiOffset

                val leftOffset = (key.leftWhiteKeyIndex + 1) * whiteKeyWidth.value - (blackKeyWidth.value / 2)

                Box(
                    modifier = Modifier
                        .offset(x = leftOffset.dp)
                        .width(blackKeyWidth)
                        .fillMaxHeight(0.60f)
                        .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                        .background(
                            if (isTutorTarget) Color(0xFF2E7D32) else Color(0xFF1E1E1E)
                        )
                        .border(1.dp, Color.Black, RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                        .clickable {
                            playSynthTone(key.midiOffset, key.name)
                        },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Text(
                        text = key.name,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }
        }
    }
}

// Data structures mapping exact offsets on the scrollable surface
data class WhiteKeyInfo(val name: String, val midiOffset: Int)
data class BlackKeyInfo(val name: String, val midiOffset: Int, val leftWhiteKeyIndex: Int)

@Composable
fun borderStrokeForTutor(isActive: Boolean): androidx.compose.foundation.BorderStroke {
    return androidx.compose.foundation.BorderStroke(
        width = if (isActive) 1.5.dp else 0.dp,
        color = if (isActive) Color(0xFF00E676) else Color.Transparent
    )
}