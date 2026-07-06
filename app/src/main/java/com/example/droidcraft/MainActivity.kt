package com.example.droidcraft

import android.os.Bundle
import android.media.AudioTrack
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioAttributes
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.*

enum class Waveform {
    SINE, SQUARE, TRIANGLE, SAWTOOTH
}

data class WhiteKey(val label: String, val frequency: Double, val index: Int)
data class BlackKey(val label: String, val frequency: Double, val rightNeighborIndex: Int)
data class RecordedNote(val frequency: Double, val timeOffset: Long, val label: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MainAppScreen()
        }
    }
}

// Background thread pool to play and synthesize audio without blocking the UI
private val audioThreadPool = java.util.concurrent.Executors.newCachedThreadPool()

fun generateAudio(
    frequency: Double,
    waveform: Waveform,
    attack: Float,
    decay: Float,
    volume: Float
): ShortArray {
    val sampleRate = 22050
    val duration = (attack + decay).coerceAtMost(2.0f)
    val numSamples = (sampleRate * duration).toInt()
    val buffer = ShortArray(numSamples)
    
    val maxAmp = 32767.0 * volume
    
    for (i in 0 until numSamples) {
        val t = i.toDouble() / sampleRate
        val angle = 2.0 * Math.PI * frequency * t
        
        val value = when (waveform) {
            Waveform.SINE -> Math.sin(angle)
            Waveform.SQUARE -> if (Math.sin(angle) >= 0) 1.0 else -1.0
            Waveform.TRIANGLE -> {
                val cycle = (t * frequency) - Math.floor(t * frequency + 0.5)
                2.0 * Math.abs(2.0 * cycle) - 1.0
            }
            Waveform.SAWTOOTH -> {
                val cycle = (t * frequency) - Math.floor(t * frequency)
                2.0 * cycle - 1.0
            }
        }
        
        // Attack-Decay envelope calculation
        val env = if (t < attack) {
            t / attack.toDouble()
        } else {
            val progress = (t - attack) / decay.toDouble()
            Math.max(0.0, 1.0 - progress)
        }
        
        buffer[i] = (value * env * maxAmp).toInt().toShort()
    }
    return buffer
}

fun playBuffer(buffer: ShortArray) {
    audioThreadPool.execute {
        try {
            val minBufSize = AudioTrack.getMinBufferSize(
                22050,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = Math.max(buffer.size * 2, minBufSize)
            
            val audioTrack = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
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
                            .setSampleRate(22050)
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
                    22050,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize,
                    AudioTrack.MODE_STATIC
                )
            }
            
            audioTrack.write(buffer, 0, buffer.size)
            audioTrack.play()
            
            val durationMs = (buffer.size.toFloat() / 22050f * 1000).toLong()
            Thread.sleep(durationMs + 100)
            audioTrack.stop()
            audioTrack.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@Composable
fun MainAppScreen() {
    var selectedWaveform by remember { mutableStateOf(Waveform.SINE) }
    var attackVal by remember { mutableStateOf(0.05f) }
    var decayVal by remember { mutableStateOf(0.8f) }
    var volumeVal by remember { mutableStateOf(0.7f) }
    var octaveShift by remember { mutableStateOf(0) } // -1, 0, +1
    
    var activeNoteName by remember { mutableStateOf<String?>(null) }
    var activeNoteFreq by remember { mutableStateOf<Double?>(null) }
    
    val recordedNotes = remember { mutableStateListOf<RecordedNote>() }
    var isRecording by remember { mutableStateOf(false) }
    var recordingStartTime by remember { mutableStateOf(0L) }
    var isPlayingSequence by remember { mutableStateOf(false) }
    
    val coroutineScope = rememberCoroutineScope()
    val activePressedKeys = remember { mutableStateMapOf<String, Boolean>() }
    
    val whiteKeys = remember {
        listOf(
            WhiteKey("C4", 261.63, 0),
            WhiteKey("D4", 293.66, 1),
            WhiteKey("E4", 329.63, 2),
            WhiteKey("F4", 349.23, 3),
            WhiteKey("G4", 392.00, 4),
            WhiteKey("A4", 440.00, 5),
            WhiteKey("B4", 493.88, 6),
            WhiteKey("C5", 523.25, 7),
            WhiteKey("D5", 587.33, 8),
            WhiteKey("E5", 659.25, 9),
            WhiteKey("F5", 698.46, 10),
            WhiteKey("G5", 783.99, 11),
            WhiteKey("A5", 880.00, 12),
            WhiteKey("B5", 987.77, 13),
            WhiteKey("C6", 1046.50, 14)
        )
    }
    
    val blackKeys = remember {
        listOf(
            BlackKey("C#4", 277.18, 1),
            BlackKey("D#4", 311.13, 2),
            BlackKey("F#4", 369.99, 4),
            BlackKey("G#4", 415.30, 5),
            BlackKey("A#4", 466.16, 6),
            BlackKey("C#5", 554.37, 8),
            BlackKey("D#5", 622.25, 9),
            BlackKey("F#5", 739.99, 11),
            BlackKey("G#5", 830.61, 12),
            BlackKey("A#5", 932.33, 13)
        )
    }
    
    val playNoteAction = { freq: Double, label: String ->
        val finalFreq = freq * when (octaveShift) {
            -1 -> 0.5
            1 -> 2.0
            else -> 1.0
        }
        
        val pcm = generateAudio(finalFreq, selectedWaveform, attackVal, decayVal, volumeVal)
        playBuffer(pcm)
        
        activeNoteName = label
        activeNoteFreq = finalFreq
        
        coroutineScope.launch {
            activePressedKeys[label] = true
            delay(200)
            activePressedKeys.remove(label)
        }
        
        if (isRecording) {
            val offset = System.currentTimeMillis() - recordingStartTime
            recordedNotes.add(RecordedNote(finalFreq, offset, label))
        }
    }
    
    val startRecording = {
        recordedNotes.clear()
        recordingStartTime = System.currentTimeMillis()
        isRecording = true
    }
    
    val playRecordedSequence = {
        if (recordedNotes.isNotEmpty() && !isPlayingSequence) {
            isPlayingSequence = true
            coroutineScope.launch(Dispatchers.Default) {
                val sortedNotes = recordedNotes.sortedBy { it.timeOffset }
                var lastOffset = 0L
                for (note in sortedNotes) {
                    val delayTime = note.timeOffset - lastOffset
                    if (delayTime > 0) {
                        delay(delayTime)
                    }
                    lastOffset = note.timeOffset
                    
                    val pcm = generateAudio(note.frequency, selectedWaveform, attackVal, decayVal, volumeVal)
                    playBuffer(pcm)
                    
                    withContext(Dispatchers.Main) {
                        activeNoteName = note.label
                        activeNoteFreq = note.frequency
                    }
                }
                withContext(Dispatchers.Main) {
                    isPlayingSequence = false
                }
            }
        }
    }
    
    val loadDemoMelody = {
        recordedNotes.clear()
        val demo = listOf(
            RecordedNote(261.63, 0L, "C4"),
            RecordedNote(329.63, 250L, "E4"),
            RecordedNote(392.00, 500L, "G4"),
            RecordedNote(523.25, 750L, "C5"),
            RecordedNote(392.00, 1000L, "G4"),
            RecordedNote(329.63, 1250L, "E4"),
            RecordedNote(261.63, 1500L, "C4"),
            RecordedNote(349.23, 1800L, "F4"),
            RecordedNote(440.00, 2050L, "A4"),
            RecordedNote(523.25, 2300L, "C5")
        )
        recordedNotes.addAll(demo)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F1A))
    ) {
        // App Header and HUD Panel
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "DROIDCRAFT PIANO",
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.headlineMedium,
                color = Color(0xFF00FFCC),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Text(
                text = "Real-time Synthesizer & Sequencer",
                style = MaterialTheme.typography.titleSmall,
                color = Color(0xFF9F9FBF),
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // HUD Monitor
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2F)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFF3B3B54)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "STATUS",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF9F9FBF)
                        )
                        Text(
                            text = when {
                                isPlayingSequence -> "PLAYING SEQUENCE"
                                isRecording -> "RECORDING ON"
                                else -> "SYNTH READY"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isRecording) Color(0xFFFF007F) else Color(0xFF00FFCC)
                        )
                    }
                    
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "ACTIVE KEY",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF9F9FBF)
                        )
                        Text(
                            text = if (activeNoteName != null) "$activeNoteName (${String.format("%.1f", activeNoteFreq)} Hz)" else "None",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
            
            // Real-time Waveform Preview
            Text(
                text = "Waveform Visualizer",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF9F9FBF),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                textAlign = TextAlign.Left
            )
            WaveformVisualizer(
                waveform = selectedWaveform,
                frequency = activeNoteFreq ?: 440.0,
                activeNote = activeNoteName,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .padding(bottom = 16.dp)
            )
            
            // Waveform Selector Panel
            Text(
                text = "Synthesis Model",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF9F9FBF),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                textAlign = TextAlign.Left
            )
            WaveformSelector(
                selected = selectedWaveform,
                onSelected = { selectedWaveform = it }
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            // Control Sliders Panel
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    ParameterSlider(
                        label = "Attack (Sec)",
                        value = attackVal,
                        range = 0.01f..0.5f,
                        onValueChange = { attackVal = it },
                        valueFormatter = { String.format("%.2f s", it) }
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    ParameterSlider(
                        label = "Decay (Sec)",
                        value = decayVal,
                        range = 0.1f..1.5f,
                        onValueChange = { decayVal = it },
                        valueFormatter = { String.format("%.2f s", it) }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    ParameterSlider(
                        label = "Main Vol",
                        value = volumeVal,
                        range = 0.1f..1.0f,
                        onValueChange = { volumeVal = it },
                        valueFormatter = { String.format("%.1f", it) }
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Octave Shift",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF9F9FBF),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    OctaveSelector(
                        currentShift = octaveShift,
                        onShiftChange = { octaveShift = it }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Sequencer Controls
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161626)),
                border = BorderStroke(1.dp, Color(0xFF222235)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Sequencer Status: ${recordedNotes.size} Notes Recorded",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF9F9FBF),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (isRecording) {
                                    isRecording = false
                                } else {
                                    startRecording()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isRecording) Color(0xFFFF007F) else Color(0xFFBD00FF)
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = if (isRecording) "Stop Rec" else "Record")
                        }
                        
                        Button(
                            onClick = { playRecordedSequence() },
                            enabled = recordedNotes.isNotEmpty() && !isPlayingSequence,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FFCC)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Play Seq",
                                color = Color(0xFF0F0F1A)
                            )
                        }
                        
                        Button(
                            onClick = {
                                recordedNotes.clear()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3F3F5F)),
                            modifier = Modifier.weight(0.8f)
                        ) {
                            Text(text = "Clear")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedButton(
                        onClick = { loadDemoMelody() },
                        border = BorderStroke(1.dp, Color(0xFF00FFCC)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Load Sweet Demo Synthesizer Melody",
                            color = Color(0xFF00FFCC),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
        
        // Premium Docked Piano Keyboard
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
                .background(Color(0xFF0C0C14))
        ) {
            val scrollState = rememberScrollState()
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(scrollState)
            ) {
                // Layer 1: Render White Keys
                Row(
                    modifier = Modifier.fillMaxHeight()
                ) {
                    whiteKeys.forEach { key ->
                        val isPressed = activePressedKeys.containsKey(key.label)
                        Box(
                            modifier = Modifier
                                .width(60.dp)
                                .fillMaxHeight()
                                .padding(horizontal = 2.dp, vertical = 4.dp)
                                .background(
                                    brush = if (isPressed) {
                                        Brush.verticalGradient(
                                            listOf(Color(0xFF00FFCC), Color(0xFF00B3FF))
                                        )
                                    } else {
                                        Brush.verticalGradient(
                                            listOf(Color(0xFFFFFFFF), Color(0xFFE2E2EC))
                                        )
                                    },
                                    shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isPressed) Color(0xFF00FFCC) else Color(0xFFCCCCCC),
                                    shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)
                                )
                                .clickable {
                                    playNoteAction(key.frequency, key.label)
                                },
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Text(
                                text = key.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isPressed) Color.White else Color(0xFF333333),
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        }
                    }
                }
                
                // Layer 2: Render Black Keys overlayed absolutely on top of White keys
                blackKeys.forEach { key ->
                    val isPressed = activePressedKeys.containsKey(key.label)
                    val leftOffset = (key.rightNeighborIndex * 60) - 18
                    
                    Box(
                        modifier = Modifier
                            .offset(x = leftOffset.dp, y = 0.dp)
                            .width(36.dp)
                            .height(135.dp)
                            .background(
                                brush = if (isPressed) {
                                    Brush.verticalGradient(
                                        listOf(Color(0xFFFF007F), Color(0xFF9E0059))
                                    )
                                } else {
                                    Brush.verticalGradient(
                                        listOf(Color(0xFF2C2C35), Color(0xFF15151A))
                                    )
                                },
                                shape = RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isPressed) Color(0xFFFF007F) else Color(0xFF3F3F4F),
                                shape = RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp)
                            )
                            .clickable {
                                playNoteAction(key.frequency, key.label)
                            },
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Text(
                            text = key.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isPressed) Color.White else Color(0xFF9F9FBF),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WaveformSelector(
    selected: Waveform,
    onSelected: (Waveform) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF222235), shape = RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF3F3F5F), shape = RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Waveform.values().forEach { waveform ->
            val isSelected = selected == waveform
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(4.dp)
                    .background(
                        brush = if (isSelected) {
                            Brush.horizontalGradient(
                                listOf(Color(0xFFBD00FF), Color(0xFFFF007F))
                            )
                        } else {
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, Color.Transparent)
                            )
                        },
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { onSelected(waveform) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = waveform.name,
                    color = if (isSelected) Color.White else Color(0xFF9F9FBF),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun OctaveSelector(
    currentShift: Int,
    onShiftChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF222235), shape = RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF3F3F5F), shape = RoundedCornerShape(12.dp))
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(-1, 0, 1).forEach { shift ->
            val isSelected = currentShift == shift
            val label = when (shift) {
                -1 -> "Low"
                0 -> "Norm"
                1 -> "High"
                else -> ""
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(4.dp)
                    .background(
                        color = if (isSelected) Color(0xFF00FFCC) else Color.Transparent,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { onShiftChange(shift) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (isSelected) Color(0xFF0C0C14) else Color(0xFF9F9FBF),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun ParameterSlider(
    label: String,
    value: Float,
    range: ClosedRange<Float>,
    onValueChange: (Float) -> Unit,
    valueFormatter: (Float) -> String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E1E2F), shape = RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF3F3F5F), shape = RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF9F9FBF),
                fontWeight = FontWeight.Bold
            )
            Text(
                text = valueFormatter(value),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF00FFCC),
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                activeTrackColor = Color(0xFFBD00FF),
                inactiveTrackColor = Color(0xFF3F3F5F),
                thumbColor = Color(0xFF00FFCC)
            )
        )
    }
}

@Composable
fun WaveformVisualizer(
    waveform: Waveform,
    frequency: Double,
    activeNote: String?,
    modifier: Modifier = Modifier
) {
    val phaseState = remember { mutableStateOf(0f) }
    
    LaunchedEffect(activeNote) {
        if (activeNote != null) {
            while (true) {
                phaseState.value = (phaseState.value + 0.25f) % (2f * Math.PI.toFloat())
                delay(30)
            }
        }
    }

    Box(
        modifier = modifier
            .background(Color(0xFF1E1E2F), shape = RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF3F3F5F), shape = RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val centerY = height / 2f
            
            if (activeNote == null) {
                drawLine(
                    color = Color(0xFF4F4F6F),
                    start = androidx.compose.ui.geometry.Offset(0f, centerY),
                    end = androidx.compose.ui.geometry.Offset(width, centerY),
                    strokeWidth = 3f
                )
                return@Canvas
            }
            
            val points = 80
            val path = androidx.compose.ui.graphics.Path()
            path.moveTo(0f, centerY)
            
            val cycles = 3.5f
            for (i in 0..points) {
                val x = (i.toFloat() / points) * width
                val ratio = i.toFloat() / points
                val angle = (ratio * cycles * 2f * Math.PI.toFloat()) + phaseState.value
                val envelope = 1.0f - ratio
                
                val waveVal = when (waveform) {
                    Waveform.SINE -> Math.sin(angle.toDouble())
                    Waveform.SQUARE -> if (Math.sin(angle.toDouble()) >= 0) 0.6 else -0.6
                    Waveform.TRIANGLE -> {
                        val cycle = (angle / (2f * Math.PI.toFloat())) - Math.floor(angle / (2f * Math.PI.toFloat()) + 0.5)
                        2.0 * Math.abs(2.0 * cycle) - 1.0
                    }
                    Waveform.SAWTOOTH -> {
                        val cycle = (angle / (2f * Math.PI.toFloat())) - Math.floor(angle / (2f * Math.PI.toFloat()))
                        2.0 * cycle - 1.0
                    }
                }
                
                val y = centerY + (waveVal.toFloat() * (height / 2.5f) * envelope)
                if (i == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }
            
            drawPath(
                path = path,
                color = Color(0xFF00FFCC),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 4f,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                )
            )
        }
    }
}