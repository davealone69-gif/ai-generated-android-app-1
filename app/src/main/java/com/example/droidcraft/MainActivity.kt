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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.abs
import kotlin.math.floor

class MainActivity : ComponentActivity() {
    private val synth = AudioSynth()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MainAppScreen(synth)
        }
    }

    override fun onResume() {
        super.onResume()
        synth.start()
    }

    override fun onPause() {
        super.onPause()
        synth.stop()
    }
}

enum class Waveform { SINE, SQUARE, TRIANGLE, SAWTOOTH }

class AudioSynth {
    private val sampleRate = 22050
    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    private var synthThread: Thread? = null

    class Note(val frequency: Float) {
        var phase = 0.0
        var vibratoPhase = 0.0
        var tremoloPhase = 0.0
        var amplitude = 1.0f
        var isReleased = false
    }

    private val activeNotes = ConcurrentHashMap<Float, Note>()

    @Volatile var currentWaveform = Waveform.SINE
    @Volatile var currentDecay = 0.015f
    @Volatile var isVibratoOn = false
    @Volatile var vibSpeed = 6.0f
    @Volatile var vibDepth = 0.015f
    @Volatile var isTremoloOn = false
    @Volatile var tremSpeed = 5.0f
    @Volatile var tremDepth = 0.35f

    fun start() {
        if (isPlaying) return
        isPlaying = true
        try {
            val minBufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = (minBufferSize * 2).coerceAtLeast(1024)
            audioTrack = AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
                AudioTrack.MODE_STREAM
            ).apply {
                if (state == AudioTrack.STATE_INITIALIZED) {
                    play()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        synthThread = Thread {
            val buffer = ShortArray(256)
            while (isPlaying) {
                for (i in buffer.indices) {
                    var sampleSum = 0.0
                    val notes = activeNotes.values
                    if (notes.isNotEmpty()) {
                        for (note in notes) {
                            val pitchMod = if (isVibratoOn) {
                                1.0 + sin(note.vibratoPhase) * vibDepth
                            } else {
                                1.0
                            }
                            val currentFreq = note.frequency * pitchMod

                            val rawSample = when (currentWaveform) {
                                Waveform.SINE -> sin(note.phase)
                                Waveform.SQUARE -> if (sin(note.phase) >= 0) 0.25 else -0.25
                                Waveform.TRIANGLE -> {
                                    val p = note.phase / (2.0 * PI)
                                    val t = 2.0 * abs(2.0 * (p - floor(p + 0.5))) - 1.0
                                    t * 0.4
                                }
                                Waveform.SAWTOOTH -> {
                                    val p = note.phase / (2.0 * PI)
                                    val s = 2.0 * (p - floor(p + 0.5))
                                    s * 0.35
                                }
                            }

                            val ampMod = if (isTremoloOn) {
                                1.0 - (sin(note.tremoloPhase) + 1.0) * 0.5 * tremDepth
                            } else {
                                1.0
                            }

                            sampleSum += rawSample * note.amplitude * ampMod

                            // Advance phases
                            note.phase += (2.0 * PI * currentFreq) / sampleRate
                            if (note.phase > 2.0 * PI) note.phase -= 2.0 * PI

                            if (isVibratoOn) {
                                note.vibratoPhase += (2.0 * PI * vibSpeed) / sampleRate
                                if (note.vibratoPhase > 2.0 * PI) note.vibratoPhase -= 2.0 * PI
                            }
                            if (isTremoloOn) {
                                note.tremoloPhase += (2.0 * PI * tremSpeed) / sampleRate
                                if (note.tremoloPhase > 2.0 * PI) note.tremoloPhase -= 2.0 * PI
                            }

                            // Handle release envelope
                            if (note.isReleased) {
                                note.amplitude -= currentDecay
                                if (note.amplitude <= 0f) {
                                    activeNotes.remove(note.frequency)
                                }
                            }
                        }
                        sampleSum = sampleSum.coerceIn(-1.0, 1.0)
                    }
                    buffer[i] = (sampleSum * 32767.0).toInt().toShort()
                }
                try {
                    audioTrack?.write(buffer, 0, buffer.size)
                } catch (e: Exception) {
                    break
                }
            }
        }.apply { start() }
    }

    fun stop() {
        isPlaying = false
        try {
            synthThread?.interrupt()
            synthThread = null
            audioTrack?.apply {
                if (state == AudioTrack.STATE_INITIALIZED) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        audioTrack = null
    }

    fun noteOn(frequency: Float) {
        val note = activeNotes[frequency]
        if (note != null) {
            note.isReleased = false
            note.amplitude = 1.0f
        } else {
            activeNotes[frequency] = Note(frequency)
        }
    }

    fun noteOff(frequency: Float) {
        activeNotes[frequency]?.let {
            it.isReleased = true
        }
    }
}

data class PianoKey(
    val name: String,
    val semitone: Int,
    val isBlack: Boolean,
    val leftWhiteIndex: Int = 0
)

fun calculateFrequency(semitone: Int, octaveShift: Int): Float {
    val octaveFactor = Math.pow(2.0, octaveShift.toDouble())
    return (261.63 * Math.pow(2.0, semitone / 12.0) * octaveFactor).toFloat()
}

@Composable
fun MainAppScreen(synth: AudioSynth) {
    var selectedWaveform by remember { mutableStateOf(Waveform.SINE) }
    var octaveShift by remember { mutableStateOf(0) }
    var decaySetting by remember { mutableStateOf(0.015f) }
    var vibratoActive by remember { mutableStateOf(false) }
    var vibratoSpeed by remember { mutableStateOf(6f) }
    var vibratoDepth by remember { mutableStateOf(0.015f) }
    var tremoloActive by remember { mutableStateOf(false) }
    var tremoloSpeed by remember { mutableStateOf(5f) }
    var tremoloDepth by remember { mutableStateOf(0.35f) }

    val playingNotes = remember { mutableStateMapOf<Float, Boolean>() }

    // Synchronize Synth Volatiles with Compose States
    LaunchedEffect(selectedWaveform) { synth.currentWaveform = selectedWaveform }
    LaunchedEffect(decaySetting) { synth.currentDecay = decaySetting }
    LaunchedEffect(vibratoActive) { synth.isVibratoOn = vibratoActive }
    LaunchedEffect(vibratoSpeed) { synth.vibSpeed = vibratoSpeed }
    LaunchedEffect(vibratoDepth) { synth.vibDepth = vibratoDepth }
    LaunchedEffect(tremoloActive) { synth.isTremoloOn = tremoloActive }
    LaunchedEffect(tremoloSpeed) { synth.tremSpeed = tremoloSpeed }
    LaunchedEffect(tremoloDepth) { synth.tremDepth = tremoloDepth }

    val keysList = remember {
        listOf(
            PianoKey("C", 0, false),
            PianoKey("D", 2, false),
            PianoKey("E", 4, false),
            PianoKey("F", 5, false),
            PianoKey("G", 7, false),
            PianoKey("A", 9, false),
            PianoKey("B", 11, false),
            PianoKey("C2", 12, false),
            PianoKey("D2", 14, false),
            PianoKey("E2", 16, false),
            PianoKey("F2", 17, false),
            PianoKey("G2", 19, false),
            PianoKey("A2", 21, false),
            PianoKey("B2", 23, false),
            PianoKey("C3", 24, false),

            PianoKey("C#", 1, true, leftWhiteIndex = 0),
            PianoKey("D#", 3, true, leftWhiteIndex = 1),
            PianoKey("F#", 6, true, leftWhiteIndex = 3),
            PianoKey("G#", 8, true, leftWhiteIndex = 4),
            PianoKey("A#", 10, true, leftWhiteIndex = 5),
            PianoKey("C#2", 13, true, leftWhiteIndex = 7),
            PianoKey("D#2", 15, true, leftWhiteIndex = 8),
            PianoKey("F#2", 18, true, leftWhiteIndex = 10),
            PianoKey("G#2", 20, true, leftWhiteIndex = 11),
            PianoKey("A#2", 22, true, leftWhiteIndex = 12)
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF121212)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header & Synthesizer controls (Scrollable)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Stylized Title
                Text(
                    text = "DROIDCRAFT POLYSYNTH",
                    color = Color(0xFF00E676),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                // Live Oscilloscope Visualizer
                LiveVisualizer(playingNotes = playingNotes)

                // Row for Waveform selection & Decay slider
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "SOUND WAVEFORM",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Waveform.values().forEach { wave ->
                                val active = selectedWaveform == wave
                                Button(
                                    onClick = { selectedWaveform = wave },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (active) Color(0xFF00E676) else Color(0xFF2E2E2E),
                                        contentColor = if (active) Color.Black else Color.White
                                    ),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(8.dp)
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
                    }
                }

                // Tuning & Decay settings
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "OCTAVE SHIFT: ${if (octaveShift >= 0) "+$octaveShift" else octaveShift}",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = { if (octaveShift > -2) octaveShift-- },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E2E2E)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                                ) {
                                    Text("-", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = { if (octaveShift < 2) octaveShift++ },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E2E2E)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                                ) {
                                    Text("+", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Envelope decay
                        Text(
                            text = "ENVELOPE DECAY (FADE): ${(1.0f / (decaySetting * 1000f)).format(1)}s",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Slider(
                            value = decaySetting,
                            onValueChange = { decaySetting = it },
                            valueRange = 0.005f..0.05f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF00E676),
                                activeTrackColor = Color(0xFF00E676)
                            )
                        )
                    }
                }

                // LFO Effects Card (Vibrato & Tremolo)
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        // Vibrato Control
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = vibratoActive,
                                onCheckedChange = { vibratoActive = it },
                                colors = CheckboxDefaults.colors(checkedColor = Color(0xFF00E676))
                            )
                            Text(
                                text = "VIBRATO (PITCH MOD)",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        if (vibratoActive) {
                            Column(modifier = Modifier.padding(start = 12.dp)) {
                                Text(
                                    text = "Speed: ${vibratoSpeed.format(1)} Hz",
                                    color = Color.LightGray,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Slider(
                                    value = vibratoSpeed,
                                    onValueChange = { vibratoSpeed = it },
                                    valueRange = 2f..15f,
                                    colors = SliderDefaults.colors(thumbColor = Color(0xFF00E676))
                                )
                                Text(
                                    text = "Depth: ${(vibratoDepth * 100f).format(1)}%",
                                    color = Color.LightGray,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Slider(
                                    value = vibratoDepth,
                                    onValueChange = { vibratoDepth = it },
                                    valueRange = 0.005f..0.05f,
                                    colors = SliderDefaults.colors(thumbColor = Color(0xFF00E676))
                                )
                            }
                        }

                        Divider(color = Color(0xFF2E2E2E), modifier = Modifier.padding(vertical = 8.dp))

                        // Tremolo Control
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = tremoloActive,
                                onCheckedChange = { tremoloActive = it },
                                colors = CheckboxDefaults.colors(checkedColor = Color(0xFF00E676))
                            )
                            Text(
                                text = "TREMOLO (VOLUME MOD)",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        if (tremoloActive) {
                            Column(modifier = Modifier.padding(start = 12.dp)) {
                                Text(
                                    text = "Speed: ${tremoloSpeed.format(1)} Hz",
                                    color = Color.LightGray,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Slider(
                                    value = tremoloSpeed,
                                    onValueChange = { tremoloSpeed = it },
                                    valueRange = 1f..12f,
                                    colors = SliderDefaults.colors(thumbColor = Color(0xFF00E676))
                                )
                                Text(
                                    text = "Depth: ${(tremoloDepth * 100f).format(0)}%",
                                    color = Color.LightGray,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Slider(
                                    value = tremoloDepth,
                                    onValueChange = { tremoloDepth = it },
                                    valueRange = 0.1f..0.8f,
                                    colors = SliderDefaults.colors(thumbColor = Color(0xFF00E676))
                                )
                            }
                        }
                    }
                }
            }

            // Piano Keyboard pinned at the bottom
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(Color(0xFF090909))
                    .padding(horizontal = 4.dp, vertical = 8.dp)
            ) {
                val whiteKeys = remember(keysList) { keysList.filter { !it.isBlack } }
                val blackKeys = remember(keysList) { keysList.filter { it.isBlack } }
                val totalWhiteKeys = whiteKeys.size

                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val totalWidth = maxWidth
                    val whiteKeyWidth = totalWidth / totalWhiteKeys

                    // 1. Draw White Keys
                    Row(modifier = Modifier.fillMaxSize()) {
                        whiteKeys.forEach { key ->
                            val freq = calculateFrequency(key.semitone, octaveShift)
                            val isPressed = playingNotes.containsKey(freq)

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .padding(horizontal = 1.dp)
                                    .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                                    .background(
                                        if (isPressed) {
                                            Brush.verticalGradient(
                                                listOf(Color.White, Color(0xFF00E676), Color(0xFF00B0FF))
                                            )
                                        } else {
                                            Brush.verticalGradient(
                                                listOf(Color(0xFFEFEFEF), Color.White, Color(0xFFDCDCDC))
                                            )
                                        }
                                    )
                                    .border(1.dp, Color.Black, RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                                    .pointerInput(key.semitone, octaveShift) {
                                        awaitEachGesture {
                                            awaitFirstDown()
                                            val noteFreq = calculateFrequency(key.semitone, octaveShift)
                                            synth.noteOn(noteFreq)
                                            playingNotes[noteFreq] = true

                                            waitForUpOrCancellation()
                                            synth.noteOff(noteFreq)
                                            playingNotes.remove(noteFreq)
                                        }
                                    },
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Text(
                                    text = key.name,
                                    fontSize = 11.sp,
                                    color = if (isPressed) Color.Black else Color.DarkGray,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                        }
                    }

                    // 2. Draw Black Keys overlapping white keys
                    blackKeys.forEach { key ->
                        val freq = calculateFrequency(key.semitone, octaveShift)
                        val isPressed = playingNotes.containsKey(freq)

                        val blackKeyWidth = whiteKeyWidth * 0.65f
                        val blackKeyHeight = maxHeight * 0.62f
                        val leftPosition = (key.leftWhiteIndex + 1) * whiteKeyWidth - (blackKeyWidth / 2f)

                        Box(
                            modifier = Modifier
                                .absoluteOffset(x = leftPosition)
                                .width(blackKeyWidth)
                                .height(blackKeyHeight)
                                .clip(RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp))
                                .background(
                                    if (isPressed) {
                                        Brush.verticalGradient(
                                            listOf(Color(0xFFCC00FF), Color(0xFF00E676))
                                        )
                                    } else {
                                        Brush.verticalGradient(
                                            listOf(Color(0xFF424242), Color(0xFF212121), Color.Black)
                                        )
                                    }
                                )
                                .border(1.dp, Color(0xFF151515), RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp))
                                .pointerInput(key.semitone, octaveShift) {
                                    awaitEachGesture {
                                        awaitFirstDown()
                                        val noteFreq = calculateFrequency(key.semitone, octaveShift)
                                        synth.noteOn(noteFreq)
                                        playingNotes[noteFreq] = true

                                        waitForUpOrCancellation()
                                        synth.noteOff(noteFreq)
                                        playingNotes.remove(noteFreq)
                                    }
                                },
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Text(
                                text = key.name,
                                fontSize = 8.sp,
                                color = if (isPressed) Color.Black else Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LiveVisualizer(playingNotes: Map<Float, Boolean>) {
    val infiniteTransition = rememberInfiniteTransition()
    val animatedPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A)),
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .border(1.dp, Color(0xFF00E676).copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val path = Path()
                val width = size.width
                val height = size.height
                val midY = height / 2f

                path.moveTo(0f, midY)
                val activeFreqs = playingNotes.keys.toList()

                if (activeFreqs.isNotEmpty()) {
                    // Draw synthesized combination waveform
                    for (x in 0..width.toInt() step 2) {
                        var sum = 0.0
                        activeFreqs.forEach { freq ->
                            val normalizedFreq = (freq / 400.0) * 8.0
                            sum += sin((x / width.toDouble()) * normalizedFreq * 2.0 * PI + animatedPhase)
                        }
                        sum /= activeFreqs.size.coerceAtLeast(1)
                        val y = midY + (sum * midY * 0.7).toFloat()
                        path.lineTo(x.toFloat(), y)
                    }
                } else {
                    // Idle flat line with light organic hum
                    for (x in 0..width.toInt() step 4) {
                        val y = midY + sin((x / width.toDouble()) * 4.0 * PI + animatedPhase).toFloat() * 3f
                        path.lineTo(x.toFloat(), y)
                    }
                }

                drawPath(
                    path = path,
                    color = Color(0xFF00E676),
                    style = Stroke(width = 3.dp.toPx())
                )
            }
            // Grid Lines Background Overlay for Sci-Fi Synth feeling
            Canvas(modifier = Modifier.fillMaxSize()) {
                val gridColor = Color(0xFF00E676).copy(alpha = 0.08f)
                val stepX = size.width / 16f
                val stepY = size.height / 6f

                for (i in 1..15) {
                    drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(i * stepX, 0f), end = androidx.compose.ui.geometry.Offset(i * stepX, size.height))
                }
                for (i in 1..5) {
                    drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(0f, i * stepY), end = androidx.compose.ui.geometry.Offset(size.width, i * stepY))
                }
            }
        }
    }
}

fun Float.format(digits: Int) = String.format("%.${digits}f", this)