package com.example.droidcraft

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.pow

// Constants for audio generation
private const val SAMPLE_RATE = 44100
private const val BUFFER_SIZE = SAMPLE_RATE / 4 // 1/4 second buffer
private const val MAX_VOLUME = 32767 // Max value for 16-bit PCM

// A simple sine wave oscillator with ADSR-like decay
class Oscillator(private val frequency: Double, private val sampleRate: Int) {
    private var phase = 0.0
    private var amplitude = 0.0 // Current amplitude
    private var targetAmplitude = 0.0 // Target amplitude (1.0 for pressed, 0.0 for released)
    private val attackTimeSamples = (0.01 * sampleRate).toInt() // 10ms attack
    private val decayTimeSamples = (0.05 * sampleRate).toInt() // 50ms decay
    private var attackCounter = 0
    private var decayCounter = 0

    fun start() {
        targetAmplitude = 1.0
        attackCounter = 0
        decayCounter = 0 // Reset decay if note is re-pressed during decay
    }

    fun release() {
        targetAmplitude = 0.0
        decayCounter = 0
    }

    fun getNextSample(): Short {
        // Update amplitude based on attack/decay
        if (targetAmplitude > amplitude) { // Attack phase
            if (attackCounter < attackTimeSamples) {
                attackCounter++
                amplitude = (attackCounter.toDouble() / attackTimeSamples)
            } else {
                amplitude = targetAmplitude // Reached target
            }
        } else if (targetAmplitude < amplitude) { // Decay phase
            if (decayCounter < decayTimeSamples) {
                decayCounter++
                amplitude = (1.0 - (decayCounter.toDouble() / decayTimeSamples)).coerceAtLeast(0.0) // Ensure not negative
            } else {
                amplitude = targetAmplitude // Reached target (0)
            }
        }

        if (amplitude <= 0.001) { // Effectively silent
            return 0
        }

        phase += 2 * PI * frequency / sampleRate
        if (phase > 2 * PI) {
            phase -= 2 * PI
        }
        val sample = (amplitude * sin(phase) * MAX_VOLUME).toInt()
        return sample.toShort()
    }

    fun isActive(): Boolean {
        // Oscillator is active if it's playing or still decaying
        return amplitude > 0.001 || targetAmplitude > 0.001
    }
}

class SoundGenerator {
    private var audioTrack: AudioTrack? = null
    private val isPlaying = AtomicBoolean(false)
    private var audioThread: Thread? = null
    private val activeOscillators = ConcurrentHashMap<Double, Oscillator>()

    init {
        val minBufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
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
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBufferSize)
            .setMode(AudioTrack.MODE_STREAM)
            .build()
    }

    fun start() {
        if (!isPlaying.getAndSet(true)) {
            audioTrack?.play()
            audioThread = Thread {
                val buffer = ShortArray(BUFFER_SIZE)
                while (isPlaying.get()) {
                    var bufferFilled = false
                    for (i in 0 until BUFFER_SIZE) {
                        var mixedSample = 0.0
                        // Iterate over a copy of keys to avoid ConcurrentModificationException if notes are added/removed
                        val currentActiveFrequencies = activeOscillators.keys.toList()
                        var activeCount = 0

                        for (freq in currentActiveFrequencies) {
                            val osc = activeOscillators[freq]
                            if (osc != null) {
                                val sample = osc.getNextSample()
                                if (osc.isActive()) {
                                    mixedSample += sample
                                    activeCount++
                                } else {
                                    activeOscillators.remove(freq) // Remove silent oscillators
                                }
                            }
                        }

                        // Simple normalization to prevent clipping if multiple notes are active
                        if (activeCount > 1) {
                            mixedSample /= activeCount.toDouble()
                        }

                        buffer[i] = mixedSample.coerceIn(-MAX_VOLUME.toDouble(), MAX_VOLUME.toDouble()).toShort()
                        if (mixedSample.toInt() != 0) bufferFilled = true
                    }
                    if (bufferFilled) { // Only write if there's actual sound
                        audioTrack?.write(buffer, 0, BUFFER_SIZE)
                    } else {
                        // If no notes are active and buffer is silent, pause briefly to save CPU
                        Thread.sleep(10)
                    }
                }
                audioTrack?.stop()
                audioTrack?.release()
                audioTrack = null
            }
            audioThread?.start()
        }
    }

    fun stop() {
        if (isPlaying.getAndSet(false)) {
            // Signal the thread to stop and wait for it to finish
            audioThread?.join()
            audioThread = null
            activeOscillators.clear()
        }
    }

    fun playNote(frequency: Double) {
        // Get or create an oscillator for this frequency
        val osc = activeOscillators.computeIfAbsent(frequency) {
            Oscillator(frequency, SAMPLE_RATE)
        }
        osc.start()
    }

    fun releaseNote(frequency: Double) {
        activeOscillators[frequency]?.release()
    }
}

class MainActivity : ComponentActivity() {
    private val soundGenerator = SoundGenerator() // Instantiate once

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MainAppScreen(soundGenerator)
        }
    }

    override fun onResume() {
        super.onResume()
        soundGenerator.start() // Start audio generation when activity resumes
    }

    override fun onPause() {
        super.onPause()
        soundGenerator.stop() // Stop audio generation when activity pauses
    }

    override fun onDestroy() {
        super.onDestroy()
        soundGenerator.stop() // Ensure generator is stopped if app is destroyed
    }
}

@Composable
fun MainAppScreen(soundGenerator: SoundGenerator) {
    val coroutineScope = rememberCoroutineScope()

    // Define piano notes and their frequencies
    val c4 = 261.63 // Middle C
    val notes = remember {
        listOf(
            "C4" to c4,
            "C#4" to c4 * 2.0.pow(1.0/12.0),
            "D4" to c4 * 2.0.pow(2.0/12.0),
            "D#4" to c4 * 2.0.pow(3.0/12.0),
            "E4" to c4 * 2.0.pow(4.0/12.0),
            "F4" to c4 * 2.0.pow(5.0/12.0),
            "F#4" to c4 * 2.0.pow(6.0/12.0),
            "G4" to c4 * 2.0.pow(7.0/12.0),
            "G#4" to c4 * 2.0.pow(8.0/12.0),
            "A4" to c4 * 2.0.pow(9.0/12.0),
            "A#4" to c4 * 2.0.pow(10.0/12.0),
            "B4" to c4 * 2.0.pow(11.0/12.0),
            "C5" to c4 * 2.0.pow(12.0/12.0)
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "DroidCraft Piano",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.headlineLarge
        )
        Spacer(modifier = Modifier.height(32.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp) // Height of the entire keyboard area
                .align(Alignment.CenterHorizontally)
        ) {
            // White keys layer
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.BottomCenter), // Align white keys to the bottom of the box
                horizontalArrangement = Arrangement.Center
            ) {
                val whiteKeys = notes.filter { !it.first.contains("#") }
                whiteKeys.forEach { (label, freq) ->
                    PianoKey(
                        label = label,
                        frequency = freq,
                        isSharp = false,
                        onNotePressed = { frequency -> coroutineScope.launch { withContext(Dispatchers.Default) { soundGenerator.playNote(frequency) } } },
                        onNoteReleased = { frequency -> coroutineScope.launch { withContext(Dispatchers.Default) { soundGenerator.releaseNote(frequency) } } }
                    )
                }
            }

            // Black keys layer, slightly offset and narrower
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp) // Black keys are shorter
                    .align(Alignment.TopCenter) // Align black keys to the top of the box
                    .offset(y = (-20).dp), // Slight vertical adjustment to overlap white keys more
                horizontalArrangement = Arrangement.Center // Center the black keys row
            ) {
                // Approximate width of a white key and black key for spacing.
                // These values are for visual alignment to place black keys over gaps.
                val whiteKeyVisualWidth = 54.dp // 50.dp key + 4.dp horizontal padding for white keys
                val blackKeyVisualWidth = 30.dp // 30.dp key
                val offsetForCsharp = (whiteKeyVisualWidth / 2) + 2.dp // Center C# between C and D, adjusted for padding

                Spacer(modifier = Modifier.width(offsetForCsharp)) // Before C#
                notes.filter { it.first == "C#4" }.forEach { (label, freq) ->
                    PianoKey(label = label, frequency = freq, isSharp = true, onNotePressed = { f -> coroutineScope.launch { withContext(Dispatchers.Default) { soundGenerator.playNote(f) } } }, onNoteReleased = { f -> coroutineScope.launch { withContext(Dispatchers.Default) { soundGenerator.releaseNote(f) } } })
                }
                Spacer(modifier = Modifier.width(whiteKeyVisualWidth - blackKeyVisualWidth)) // Between C# and D#
                notes.filter { it.first == "D#4" }.forEach { (label, freq) ->
                    PianoKey(label = label, frequency = freq, isSharp = true, onNotePressed = { f -> coroutineScope.launch { withContext(Dispatchers.Default) { soundGenerator.playNote(f) } } }, onNoteReleased = { f -> coroutineScope.launch { withContext(Dispatchers.Default) { soundGenerator.releaseNote(f) } } })
                }
                Spacer(modifier = Modifier.width((whiteKeyVisualWidth * 2) - blackKeyVisualWidth)) // Between D# and F# (skips E)
                notes.filter { it.first == "F#4" }.forEach { (label, freq) ->
                    PianoKey(label = label, frequency = freq, isSharp = true, onNotePressed = { f -> coroutineScope.launch { withContext(Dispatchers.Default) { soundGenerator.playNote(f) } } }, onNoteReleased = { f -> coroutineScope.launch { withContext(Dispatchers.Default) { soundGenerator.releaseNote(f) } } })
                }
                Spacer(modifier = Modifier.width(whiteKeyVisualWidth - blackKeyVisualWidth)) // Between F# and G#
                notes.filter { it.first == "G#4" }.forEach { (label, freq) ->
                    PianoKey(label = label, frequency = freq, isSharp = true, onNotePressed = { f -> coroutineScope.launch { withContext(Dispatchers.Default) { soundGenerator.playNote(f) } } }, onNoteReleased = { f -> coroutineScope.launch { withContext(Dispatchers.Default) { soundGenerator.releaseNote(f) } } })
                }
                Spacer(modifier = Modifier.width(whiteKeyVisualWidth - blackKeyVisualWidth)) // Between G# and A#
                notes.filter { it.first == "A#4" }.forEach { (label, freq) ->
                    PianoKey(label = label, frequency = freq, isSharp = true, onNotePressed = { f -> coroutineScope.launch { withContext(Dispatchers.Default) { soundGenerator.playNote(f) } } }, onNoteReleased = { f -> coroutineScope.launch { withContext(Dispatchers.Default) { soundGenerator.releaseNote(f) } } })
                }
                Spacer(modifier = Modifier.width(offsetForCsharp)) // After A#
            }
        }
    }
}

@Composable
fun PianoKey(
    label: String,
    frequency: Double,
    isSharp: Boolean,
    onNotePressed: (Double) -> Unit,
    onNoteReleased: (Double) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    LaunchedEffect(isPressed) {
        if (isPressed) {
            onNotePressed(frequency)
        } else {
            onNoteReleased(frequency)
        }
    }

    val backgroundColor = when {
        isSharp -> if (isPressed) Color(0xFF333333) else Color.Black
        isPressed -> Color.LightGray
        else -> Color.White
    }
    val textColor = if (isSharp) Color.White else Color.Black
    val keyWidth = if (isSharp) 30.dp else 50.dp
    val keyHeight = if (isSharp) 120.dp else 180.dp // White keys taller
    val elevation = if (isPressed) 2.dp else 8.dp

    Surface(
        modifier = Modifier
            .width(keyWidth)
            .height(keyHeight)
            .padding(horizontal = if (isSharp) 0.dp else 2.dp), // Horizontal padding for white keys to give separation
        shape = MaterialTheme.shapes.small,
        color = backgroundColor,
        shadowElevation = elevation,
        interactionSource = interactionSource,
        onClick = { /* No action on simple click, only press/release */ }
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Text(
                text = label,
                color = textColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewMainAppScreen() {
    MainAppScreen(soundGenerator = SoundGenerator()) // Provide a mock or dummy SoundGenerator for preview
}