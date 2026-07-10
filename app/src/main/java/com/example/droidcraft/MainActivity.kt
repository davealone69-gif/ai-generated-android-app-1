package com.example.droidcraft

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectPressAndRelease
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    private lateinit var audioGenerator: AudioGenerator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize audio generator
        audioGenerator = AudioGenerator()
        audioGenerator.start() // Start the audio generation thread

        setContent {
            // Apply MaterialTheme for consistent look and feel
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PianoAppScreen(audioGenerator)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioGenerator.stop() // Stop and release audio resources when activity is destroyed
    }
}

/**
 * Data class to represent a piano note with its name and fundamental frequency.
 */
data class PianoNote(val name: String, val frequency: Double)

/**
 * Object to hold common piano note frequencies for a simple octave.
 */
object PianoNotes {
    val C4 = PianoNote("C4", 261.63)
    val D4 = PianoNote("D4", 293.66)
    val E4 = PianoNote("E4", 329.63)
    val F4 = PianoNote("F4", 349.23)
    val G4 = PianoNote("G4", 392.00)
    val A4 = PianoNote("A4", 440.00)
    val B4 = PianoNote("B4", 493.88)
    val C5 = PianoNote("C5", 523.25)

    val allNotes = listOf(C4, D4, E4, F4, G4, A4, B4, C5)
}

/**
 * The main Composable screen for the piano application.
 * Displays the app title and a row of piano keys.
 */
@Composable
fun PianoAppScreen(audioGenerator: AudioGenerator) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "DroidCraft Piano",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .height(200.dp), // Fixed height for the piano keys
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PianoNotes.allNotes.forEach { note ->
                PianoKey(note = note, audioGenerator = audioGenerator)
            }
        }
    }
}

/**
 * A Composable that represents a single piano key.
 * It detects press and release gestures to start and stop audio synthesis for its corresponding note.
 */
@OptIn(ExperimentalMaterial3Api::class) // Required for InteractionSource usage in `Card` (implicitly)
@Composable
fun PianoKey(note: PianoNote, audioGenerator: AudioGenerator) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Use LaunchedEffect to react to changes in the pressed state
    LaunchedEffect(isPressed) {
        if (isPressed) {
            audioGenerator.startNote(note.frequency)
        } else {
            audioGenerator.stopNote(note.frequency)
        }
    }

    // Modifier to detect continuous press and release gestures.
    // This is more suitable for musical instruments than a simple clickable.
    val pressAndReleaseModifier = Modifier.pointerInput(Unit) {
        detectPressAndRelease(
            onPress = { offset ->
                // Emit a PressInteraction.Press when the key is initially pressed
                interactionSource.tryEmit(androidx.compose.foundation.interaction.PressInteraction.Press(offset))
            },
            onRelease = {
                // Emit a PressInteraction.Release when the key is released.
                // For simplicity, we create a dummy PressInteraction.Press object here.
                // A more robust solution for complex interactions might store the original PressInteraction.Press.
                interactionSource.tryEmit(androidx.compose.foundation.interaction.PressInteraction.Release(
                    androidx.compose.foundation.interaction.PressInteraction.Press(androidx.compose.ui.geometry.Offset.Zero)
                ))
            },
            onCancel = {
                // Emit a PressInteraction.Cancel if the gesture is cancelled (e.g., parent scroll takes over).
                interactionSource.tryEmit(androidx.compose.foundation.interaction.PressInteraction.Cancel(
                    androidx.compose.foundation.interaction.PressInteraction.Press(androidx.compose.ui.geometry.Offset.Zero)
                ))
            }
        )
    }

    Card(
        modifier = Modifier
            .weight(1f) // Distribute horizontal space evenly among keys
            .fillMaxHeight()
            .padding(4.dp)
            .then(pressAndReleaseModifier) // Attach custom press/release detector
            .background(if (isPressed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
        shape = MaterialTheme.shapes.small,
        elevation = CardDefaults.cardElevation(if (isPressed) 8.dp else 2.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Text(
                text = note.name,
                color = if (isPressed) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    }
}

/**
 * Helper class to manage the phase for an active note's waveform generation.
 */
class ActiveNote(val frequency: Double) {
    var phase: Double = 0.0 // Current phase of the sine wave
}

/**
 * Manages custom sound synthesis using Android's AudioTrack.
 * Generates and plays mixed sine waves for polyphonic piano sounds.
 */
class AudioGenerator {
    private val sampleRate = 44100 // Audio sample rate in Hz
    private val bufferSize: Int = AudioTrack.getMinBufferSize( // Minimum buffer size required for AudioTrack
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )
    private var audioTrack: AudioTrack? = null
    private var isPlaying = AtomicBoolean(false) // Flag to control the audio generation thread
    private var audioThread: Thread? = null

    // ConcurrentHashMap to store currently active notes (frequency -> ActiveNote object)
    private val activeNotes = ConcurrentHashMap<Double, ActiveNote>()

    init {
        // Initialize AudioTrack. It will be started when the thread begins.
        audioTrack = AudioTrack(
            AudioManager.STREAM_MUSIC, // Use the music stream type
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO, // Mono channel configuration
            AudioFormat.ENCODING_PCM_16BIT, // 16-bit PCM audio encoding
            bufferSize,
            AudioTrack.MODE_STREAM // Stream mode for continuous playback
        )
    }

    /**
     * Starts the audio generation thread.
     */
    fun start() {
        if (!isPlaying.get()) {
            isPlaying.set(true)
            audioThread = Thread {
                audioTrack?.play() // Start playing the audio track
                val samples = ShortArray(bufferSize / 2) // Buffer to hold 16-bit PCM samples

                while (isPlaying.get()) {
                    // Get a snapshot of currently active notes for this buffer generation cycle
                    val currentActiveNotes = activeNotes.values.toList()

                    if (currentActiveNotes.isNotEmpty()) {
                        for (i in samples.indices) {
                            var sampleValue = 0.0
                            // Sum waveforms from all active notes for polyphony
                            for (note in currentActiveNotes) {
                                sampleValue += sin(note.phase)
                                // Advance phase for the next sample based on frequency and sample rate
                                note.phase += (2 * PI * note.frequency / sampleRate)
                                // Keep phase within 0 to 2PI to prevent potential floating-point precision issues
                                if (note.phase >= 2 * PI) {
                                    note.phase -= (2 * PI)
                                }
                            }
                            // Scale the summed waveform to 16-bit PCM max value.
                            // Divide by the number of active notes to prevent clipping if multiple notes are playing.
                            samples[i] = (sampleValue * 32767 / currentActiveNotes.size.coerceAtLeast(1)).toShort()
                        }
                    } else {
                        // If no notes are playing, send silence to prevent buffer underrun warnings and keep AudioTrack active
                        samples.fill(0)
                    }
                    audioTrack?.write(samples, 0, samples.size) // Write samples to the AudioTrack
                }
                audioTrack?.stop()    // Stop the AudioTrack when the loop finishes
                audioTrack?.release() // Release AudioTrack resources
            }
            audioThread?.start() // Start the audio generation thread
        }
    }

    /**
     * Stops the audio generation thread and releases resources.
     */
    fun stop() {
        isPlaying.set(false) // Signal the thread to stop
        audioThread?.join()  // Wait for the audio thread to finish
        audioThread = null
    }

    /**
     * Starts playing a note with the given frequency.
     * If the note is already playing, its state is maintained.
     */
    fun startNote(frequency: Double) {
        // Only add if not already active; computeIfAbsent creates a new ActiveNote if key is not present
        activeNotes.computeIfAbsent(frequency) { ActiveNote(frequency) }
    }

    /**
     * Stops playing the note with the given frequency.
     */
    fun stopNote(frequency: Double) {
        activeNotes.remove(frequency) // Remove the note from the active notes map
    }
}