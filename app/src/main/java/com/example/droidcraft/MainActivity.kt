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
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*

class MainActivity : ComponentActivity() {
    private val synthEngine = SynthEngine()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MainAppScreen(synthEngine)
        }
    }

    override fun onResume() {
        super.onResume()
        synthEngine.start()
    }

    override fun onPause() {
        super.onPause()
        synthEngine.stop()
    }
}

enum class Waveform { SINE, SQUARE, TRIANGLE, SAWTOOTH }

class SynthEngine {
    private val sampleRate = 44100
    private var audioTrack: AudioTrack? = null
    @Volatile private var isRunning = false
    private var thread: Thread? = null

    // Audio Engine Controls
    @Volatile var waveform = Waveform.SINE
    @Volatile var attackTimeMs = 20f
    @Volatile var releaseTimeMs = 250f
    @Volatile var vibratoRate = 5.0f
    @Volatile var vibratoDepth = 0.0f
    @Volatile var cutoffFrequency = 8000f

    // Synthesis Voices
    class VoiceState(
        val frequency: Double,
        var phase: Double = 0.0,
        var amplitude: Float = 0.0f,
        var isReleased: Boolean = false
    )

    private val activeVoices = ConcurrentHashMap<Double, VoiceState>()

    fun start() {
        if (isRunning) return
        isRunning = true

        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

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
            .setBufferSizeInBytes(minBufferSize.coerceAtLeast(4096))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.play()

        thread = Thread {
            val bufferSize = 512
            val buffer = ShortArray(bufferSize)
            var lastFilteredSample = 0.0

            while (isRunning) {
                val voices = activeVoices.values.toList()
                val lfoTime = System.nanoTime() / 1_000_000_000.0
                val lfo = sin(2.0 * PI * vibratoRate * lfoTime) * vibratoDepth * 0.08

                for (i in 0 until bufferSize) {
                    var sampleSum = 0.0

                    if (voices.isNotEmpty()) {
                        for (voice in voices) {
                            val attackStep = 1000.0f / (attackTimeMs.coerceAtLeast(1f) * sampleRate)
                            val releaseStep = 1000.0f / (releaseTimeMs.coerceAtLeast(1f) * sampleRate)

                            if (!voice.isReleased) {
                                if (voice.amplitude < 1.0f) {
                                    voice.amplitude = (voice.amplitude + attackStep).coerceAtMost(1.0f)
                                }
                            } else {
                                voice.amplitude = (voice.amplitude - releaseStep).coerceAtLeast(0.0f)
                                if (voice.amplitude <= 0.0f) {
                                    activeVoices.remove(voice.frequency)
                                }
                            }

                            // Calculate target dynamic modulated frequency with Vibrato
                            val modulatedFreq = voice.frequency * (1.0 + lfo)

                            // Raw generator waveform
                            val rawSample = when (waveform) {
                                Waveform.SINE -> sin(voice.phase)
                                Waveform.SQUARE -> if (sin(voice.phase) >= 0.0) 0.5 else -0.5
                                Waveform.TRIANGLE -> {
                                    val p = voice.phase / (2.0 * PI)
                                    2.0 * abs(2.0 * (p - floor(p + 0.5))) - 1.0
                                }
                                Waveform.SAWTOOTH -> {
                                    val p = voice.phase / (2.0 * PI)
                                    2.0 * (p - floor(p + 0.5))
                                }
                            }

                            sampleSum += rawSample * voice.amplitude

                            // Update phase step
                            voice.phase += 2.0 * PI * modulatedFreq / sampleRate
                            if (voice.phase >= 2.0 * PI) {
                                voice.phase -= 2.0 * PI
                            }
                        }

                        // Prevent clipping and compress mixed voice
                        sampleSum = sampleSum.coerceIn(-1.5, 1.5) / 1.5
                    }

                    // Apply low-pass filter
                    val dt = 1.0 / sampleRate
                    val rc = 1.0 / (2.0 * PI * cutoffFrequency.coerceAtLeast(50f))
                    val alpha = dt / (rc + dt)
                    sampleSum = lastFilteredSample + alpha * (sampleSum - lastFilteredSample)
                    lastFilteredSample = sampleSum

                    // Quantize to 16-bit PCM buffer
                    buffer[i] = (sampleSum * 32767.0).coerceIn(-32768.0, 32767.0).toInt().toShort()
                }

                audioTrack?.write(buffer, 0, bufferSize)
            }
        }.apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun stop() {
        isRunning = false
        try {
            thread?.join(1000)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        audioTrack?.apply {
            try {
                stop()
                release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        audioTrack = null
    }

    fun playNote(frequency: Double) {
        val voice = activeVoices[frequency]
        if (voice != null) {
            voice.isReleased = false
        } else {
            activeVoices[frequency] = VoiceState(frequency)
        }
    }

    fun stopNote(frequency: Double) {
        activeVoices[frequency]?.isReleased = true
    }

    fun panicReset() {
        activeVoices.clear()
    }

    fun activeVoiceCount(): Int = activeVoices.size
}

data class PianoKey(
    val name: String,
    val isBlack: Boolean,
    val baseFrequency: Double
)

@Composable
fun MainAppScreen(synthEngine: SynthEngine) {
    var selectedWaveform by remember { mutableStateOf(synthEngine.waveform) }
    var octaveShift by remember { mutableStateOf(0) }
    var attackMs by remember { mutableStateOf(synthEngine.attackTimeMs) }
    var releaseMs by remember { mutableStateOf(synthEngine.releaseTimeMs) }
    var lfoRate by remember { mutableStateOf(synthEngine.vibratoRate) }
    var lfoDepth by remember { mutableStateOf(synthEngine.vibratoDepth) }
    var filterCutoff by remember { mutableStateOf(synthEngine.cutoffFrequency) }

    // Map 1.5 octaves: C4 through E5
    val keyboardKeys = remember {
        listOf(
            PianoKey("C", false, 261.63),
            PianoKey("C#", true, 277.18),
            PianoKey("D", false, 293.66),
            PianoKey("D#", true, 311.13),
            PianoKey("E", false, 329.63),
            PianoKey("F", false, 349.23),
            PianoKey("F#", true, 369.99),
            PianoKey("G", false, 392.00),
            PianoKey("G#", true, 415.30),
            PianoKey("A", false, 440.00),
            PianoKey("A#", true, 466.16),
            PianoKey("B", false, 493.88),
            PianoKey("C2", false, 523.25),
            PianoKey("C#2", true, 554.37),
            PianoKey("D2", false, 587.33),
            PianoKey("D#2", true, 622.25),
            PianoKey("E2", false, 659.25)
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "DroidCraft Synth",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Interactive Piano & Custom Sound Synthesis",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }

                IconButton(
                    onClick = { synthEngine.panicReset() },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset audio"
                    )
                }
            }

            // Top control settings panel
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Waveform Selector Row
                Text(
                    text = "Oscillator Waveform",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Waveform.values().forEach { wave ->
                        val isSelected = selectedWaveform == wave
                        Button(
                            onClick = {
                                selectedWaveform = wave
                                synthEngine.waveform = wave
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Text(
                                text = wave.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Wave Visualizer
                Text(
                    text = "Real-time Synthesis Path",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge
                )
                VisualizerView(
                    waveform = selectedWaveform,
                    activeCount = synthEngine.activeVoiceCount()
                )

                // Envelope Controls Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "ADSR Envelope Parameters",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Attack Time
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Attack: ${attackMs.toInt()} ms",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.width(100.dp)
                            )
                            Slider(
                                value = attackMs,
                                onValueChange = {
                                    attackMs = it
                                    synthEngine.attackTimeMs = it
                                },
                                valueRange = 5f..1000f,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Release Time
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Release: ${releaseMs.toInt()} ms",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.width(100.dp)
                            )
                            Slider(
                                value = releaseMs,
                                onValueChange = {
                                    releaseMs = it
                                    synthEngine.releaseTimeMs = it
                                },
                                valueRange = 10f..2000f,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Modulation Filters
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Low-Pass Filter & Vibrato (LFO)",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Filter Cutoff
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Filter Cutoff: ${filterCutoff.toInt()} Hz",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.width(130.dp)
                            )
                            Slider(
                                value = filterCutoff,
                                onValueChange = {
                                    filterCutoff = it
                                    synthEngine.cutoffFrequency = it
                                },
                                valueRange = 200f..15000f,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Vibrato Depth
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Vibrato Depth: ${(lfoDepth * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.width(130.dp)
                            )
                            Slider(
                                value = lfoDepth,
                                onValueChange = {
                                    lfoDepth = it
                                    synthEngine.vibratoDepth = it
                                },
                                valueRange = 0.0f..0.8f,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Vibrato Speed
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Vibrato Rate: ${String.format("%.1f", lfoRate)} Hz",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.width(130.dp)
                            )
                            Slider(
                                value = lfoRate,
                                onValueChange = {
                                    lfoRate = it
                                    synthEngine.vibratoRate = it
                                },
                                valueRange = 1.0f..15.0f,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Keyboard and Octave controls section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(vertical = 8.dp)
            ) {
                // Octave Shift Control Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "OCTAVE RANGE",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { octaveShift = (octaveShift - 1).coerceAtLeast(-2) },
                            enabled = octaveShift > -2,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                        ) {
                            Text("-1 Oct")
                        }

                        Text(
                            text = if (octaveShift >= 0) "+$octaveShift" else "$octaveShift",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            modifier = Modifier.width(36.dp),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Button(
                            onClick = { octaveShift = (octaveShift + 1).coerceIn(-2, 2) },
                            enabled = octaveShift < 2,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                        ) {
                            Text("+1 Oct")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Custom Visual Piano Keyboard
                InteractivePianoKeyboard(
                    keyboardKeys = keyboardKeys,
                    octaveShift = octaveShift,
                    synthEngine = synthEngine
                )
            }
        }
    }
}

@Composable
fun VisualizerView(waveform: Waveform, activeCount: Int) {
    val transition = rememberInfiniteTransition(label = "visualization")
    val waveOffset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phaseOffset"
    )

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1E1E1E))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
    ) {
        val path = Path()
        val midY = size.height / 2f
        val points = size.width.toInt()

        val dynamicAmp = if (activeCount > 0) size.height * 0.4f else size.height * 0.08f
        val waveFrequency = if (activeCount > 0) 0.045f else 0.015f

        path.moveTo(0f, midY)
        for (x in 0..points step 2) {
            val radians = x.toFloat() * waveFrequency + waveOffset
            val yValue = when (waveform) {
                Waveform.SINE -> sin(radians)
                Waveform.SQUARE -> if (sin(radians) >= 0f) 0.7f else -0.7f
                Waveform.TRIANGLE -> {
                    val p = radians / (2f * PI.toFloat())
                    2f * abs(2f * (p - floor(p + 0.5f))) - 1f
                }
                Waveform.SAWTOOTH -> {
                    val p = radians / (2f * PI.toFloat())
                    2f * (p - floor(p + 0.5f))
                }
            } * dynamicAmp

            path.lineTo(x.toFloat(), midY + yValue)
        }

        drawPath(
            path = path,
            color = if (activeCount > 0) Color(0xFF00E676) else Color(0xFF00B0FF),
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

@Composable
fun InteractivePianoKeyboard(
    keyboardKeys: List<PianoKey>,
    octaveShift: Int,
    synthEngine: SynthEngine
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(230.dp)
            .background(Color.DarkGray)
    ) {
        val totalWhiteKeys = 10
        val whiteKeyWidth = maxWidth / totalWhiteKeys
        val blackKeyWidth = whiteKeyWidth * 0.62f
        val blackKeyHeight = 135.dp

        // 1. Render all White Keys
        Row(modifier = Modifier.fillMaxSize()) {
            val whiteKeys = keyboardKeys.filter { !it.isBlack }
            whiteKeys.forEach { key ->
                val shiftedFreq = key.baseFrequency * 2.0.pow(octaveShift.toDouble())
                var isPressed by remember { mutableStateOf(false) }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 1.dp)
                        .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                        .background(if (isPressed) Color(0xFFDDDDDD) else Color.White)
                        .border(
                            1.dp,
                            Color(0xFF757575),
                            RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)
                        )
                        .pointerInput(key.name) {
                            forEachGesture {
                                awaitPointerEventScope {
                                    awaitFirstDown()
                                    isPressed = true
                                    synthEngine.playNote(shiftedFreq)

                                    waitForUpOrCancellation()
                                    isPressed = false
                                    synthEngine.stopNote(shiftedFreq)
                                }
                            }
                        },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Text(
                        text = key.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.DarkGray,
                        modifier = Modifier.padding(bottom = 12.dp),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 2. Render all Overlayed Black Keys
        val blackKeysLayout = listOf(
            Pair(keyboardKeys[1], 1f),   // C# between white key 0 and 1
            Pair(keyboardKeys[3], 2f),   // D# between white key 1 and 2
            Pair(keyboardKeys[6], 4f),   // F# between white key 3 and 4
            Pair(keyboardKeys[8], 5f),   // G# between white key 4 and 5
            Pair(keyboardKeys[10], 6f),  // A# between white key 5 and 6
            Pair(keyboardKeys[13], 8f),  // C#2 between white key 7 and 8
            Pair(keyboardKeys[15], 9f)   // D#2 between white key 8 and 9
        )

        blackKeysLayout.forEach { (key, offset) ->
            val shiftedFreq = key.baseFrequency * 2.0.pow(octaveShift.toDouble())
            var isPressed by remember { mutableStateOf(false) }
            val leftPositionOffset = (whiteKeyWidth * offset) - (blackKeyWidth / 2f)

            Box(
                modifier = Modifier
                    .absoluteOffset(x = leftPositionOffset)
                    .width(blackKeyWidth)
                    .height(blackKeyHeight)
                    .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                    .background(if (isPressed) Color(0xFF424242) else Color.Black)
                    .border(
                        1.dp,
                        Color(0xFF212121),
                        RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp)
                    )
                    .pointerInput(key.name) {
                        forEachGesture {
                            awaitPointerEventScope {
                                awaitFirstDown()
                                isPressed = true
                                synthEngine.playNote(shiftedFreq)

                                waitForUpOrCancellation()
                                isPressed = false
                                synthEngine.stopNote(shiftedFreq)
                            }
                        }
                    },
                contentAlignment = Alignment.BottomCenter
            ) {
                Text(
                    text = key.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 8.dp),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}