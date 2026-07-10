package com.example.droidcraft

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MainAppScreen()
        }
    }
}

// Map of note names to their frequencies (Hz)
// Standard A4 = 440 Hz. Each semitone multiplies by 2^(1/12).
// C4 (Middle C) = 261.63 Hz.
val noteFrequencies = mapOf(
    "C4" to 261.63,
    "C#4" to 277.18,
    "D4" to 293.66,
    "D#4" to 311.13,
    "E4" to 329.63,
    "F4" to 349.23,
    "F#4" to 369.99,
    "G4" to 392.00,
    "G#4" to 415.30,
    "A4" to 440.00,
    "A#4" to 466.16,
    "B4" to 493.88,
    "C5" to 523.25
)

/**
 * Manages the generation and playback of a single continuous sine wave tone.
 * Each NoteAudioPlayer instance uses its own AudioTrack.
 */
class NoteAudioPlayer(
    private val frequency: Double,
    private val volume: Float = 0.2f // Keep volume lower to prevent clipping/distortion
) {
    private var audioTrack: AudioTrack? = null
    @Volatile private var isPlaying = false
    private val sampleRate = 44100 // Standard audio sample rate
    private val bufferSize = AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )
    private val audioBuffer = ShortArray(bufferSize) // Buffer to hold generated audio data

    // Using a Job to manage the coroutine for audio playback, allowing cancellation
    private var playbackJob: Job? = null

    /**
     * Starts playing the note. Launches a coroutine to continuously write audio data.
     * @param scope CoroutineScope to launch the playback job in.
     */
    fun start(scope: CoroutineScope) {
        if (isPlaying) return // Already playing
        isPlaying = true
        playbackJob = scope.launch(Dispatchers.Default) {
            // Set thread priority for urgent audio processing to minimize latency
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)

            try {
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
                    .setBufferSizeInBytes(bufferSize * 2) // bufferSize is in shorts, but AudioTrack expects bytes
                    .build()

                audioTrack?.play()

                var angle = 0.0 // Current phase angle for sine wave generation
                while (isPlaying && !playbackJob!!.isCancelled) {
                    for (i in 0 until audioBuffer.size) {
                        // Generate a sine wave sample
                        audioBuffer[i] = (Short.MAX_VALUE * volume * sin(angle)).toShort()
                        // Increment angle for the next sample
                        angle += 2 * Math.PI * frequency / sampleRate
                    }
                    audioTrack?.write(audioBuffer, 0, audioBuffer.size)
                }
            } catch (e: Exception) {
                // Log potential exceptions during audio playback (e.g., AudioTrack not initialized correctly)
                e.printStackTrace()
            } finally {
                // Ensure AudioTrack is stopped and released when playback ends or is cancelled
                audioTrack?.stop()
                audioTrack?.release()
                audioTrack = null
                isPlaying = false
            }
        }
    }

    /**
     * Stops playing the note. Cancels the playback coroutine.
     */
    fun stop() {
        isPlaying = false
        playbackJob?.cancel() // Request cancellation of the coroutine
        playbackJob = null
    }
}

/**
 * Manages multiple NoteAudioPlayer instances to handle polyphonic playback.
 * Uses a cache to reuse NoteAudioPlayer objects and ConcurrentHashMap for thread safety.
 */
class PianoSoundManager {
    // Map of currently active (playing) note players
    private val activeNotePlayers = ConcurrentHashMap<String, NoteAudioPlayer>()
    // Cache of all potential note players, so they are created once
    private val notePlayerCache = ConcurrentHashMap<String, NoteAudioPlayer>()

    /**
     * Starts playing a note. If the note is already playing, does nothing.
     * @param noteName The name of the note (e.g., "C4").
     * @param frequency The frequency of the note in Hz.
     * @param scope CoroutineScope to launch the note's audio playback.
     */
    fun playNote(noteName: String, frequency: Double, scope: CoroutineScope) {
        if (activeNotePlayers.containsKey(noteName)) return // Note already playing
        val player = notePlayerCache.getOrPut(noteName) { NoteAudioPlayer(frequency) }
        activeNotePlayers[noteName] = player
        player.start(scope)
    }

    /**
     * Stops playing a specific note.
     * @param noteName The name of the note to stop.
     */
    fun stopNote(noteName: String) {
        activeNotePlayers.remove(noteName)?.stop()
    }

    /**
     * Stops all currently playing notes.
     */
    fun stopAllNotes() {
        activeNotePlayers.values.forEach { it.stop() }
        activeNotePlayers.clear()
    }
}

@Composable
fun MainAppScreen() {
    val soundManager = remember { PianoSoundManager() }
    val coroutineScope = rememberCoroutineScope() // Coroutine scope for sound management

    // Ensure all notes are stopped when the composable leaves the screen (e.g., app closes)
    DisposableEffect(Unit) {
        onDispose {
            soundManager.stopAllNotes()
        }
    }

    MaterialTheme { // Apply MaterialTheme to ensure proper styling
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "DroidCraft Piano",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Piano keyboard layout using BoxWithConstraints for responsive sizing
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp) // Fixed height for the keyboard area
                    .padding(horizontal = 8.dp)
            ) {
                // Calculate dimensions based on available constraints
                val whiteKeyCount = 8 // Number of white keys (C4 to C5 inclusive)
                val whiteKeyWidth = maxWidth / whiteKeyCount
                val blackKeyWidth = whiteKeyWidth * 0.6f // Black keys are narrower
                val blackKeyHeight = maxHeight * 0.6f // Black keys are shorter

                // Layout for white keys
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceBetween // Distributes keys evenly
                ) {
                    val whiteKeys = listOf("C4", "D4", "E4", "F4", "G4", "A4", "B4", "C5")
                    whiteKeys.forEach { note ->
                        val frequency = noteFrequencies[note] ?: 0.0
                        PianoKey(
                            noteName = note,
                            frequency = frequency,
                            isBlackKey = false,
                            onNotePressed = { soundManager.playNote(note, frequency, coroutineScope) },
                            onNoteReleased = { soundManager.stopNote(note) },
                            modifier = Modifier
                                .weight(1f) // Each white key takes equal horizontal space
                                .fillMaxHeight()
                                .padding(horizontal = 1.dp) // Small visual gap between keys
                        )
                    }
                }

                // Layout for black keys (overlayed on top of white keys)
                // Positioned using Modifier.offset relative to the parent BoxWithConstraints
                val blackKeyOffsets = mapOf( // Horizontal offset from the left edge of the keyboard
                    "C#4" to whiteKeyWidth * 0.75f, // Between C4 and D4
                    "D#4" to whiteKeyWidth * 1.75f, // Between D4 and E4
                    "F#4" to whiteKeyWidth * 3.75f, // Between F4 and G4
                    "G#4" to whiteKeyWidth * 4.75f, // Between G4 and A4
                    "A#4" to whiteKeyWidth * 5.75f  // Between A4 and B4
                )

                val blackKeys = listOf("C#4", "D#4", "F#4", "G#4", "A#4")
                blackKeys.forEach { note ->
                    val frequency = noteFrequencies[note] ?: 0.0
                    val offset = blackKeyOffsets[note] ?: 0.dp
                    
                    PianoKey(
                        noteName = note,
                        frequency = frequency,
                        isBlackKey = true,
                        onNotePressed = { soundManager.playNote(note, frequency, coroutineScope) },
                        onNoteReleased = { soundManager.stopNote(note) },
                        modifier = Modifier
                            .offset(x = offset - (blackKeyWidth / 2)) // Center the black key at the calculated offset
                            .width(blackKeyWidth)
                            .height(blackKeyHeight)
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Press keys to play notes!",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

/**
 * Composable representing a single piano key (white or black).
 * Handles press/release interactions and triggers sound playback callbacks.
 */
@OptIn(ExperimentalMaterial3Api::class) // Required for using interactionSource directly on Surface
@Composable
fun PianoKey(
    noteName: String,
    frequency: Double,
    isBlackKey: Boolean,
    onNotePressed: () -> Unit,
    onNoteReleased: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    // Collect the press state from the interaction source
    val isPressed by interactionSource.collectIsPressedAsState()

    // Use LaunchedEffect to react to changes in the pressed state
    // This ensures sound functions are called only when the state actually changes
    LaunchedEffect(isPressed) {
        if (isPressed) {
            onNotePressed()
        } else {
            onNoteReleased()
        }
    }

    val backgroundColor = if (isBlackKey) Color.Black else Color.White
    val pressedColor = if (isBlackKey) Color.DarkGray else Color.LightGray
    val currentColor = if (isPressed) pressedColor else backgroundColor

    Surface(
        modifier = modifier
            .border(1.dp, Color.Gray) // Add a border for visual separation
            .background(currentColor), // Apply background color (can be overridden by Surface's color)
        color = currentColor, // Set Surface's background color
        interactionSource = interactionSource // This is crucial for detecting press events on the Surface
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter // Align text to the bottom center of the key
        ) {
            // Display note name for white keys only
            Text(
                text = if (isBlackKey) "" else noteName.dropLast(1), // e.g., "C4" -> "C", "D4" -> "D"
                color = if (isBlackKey) Color.White else Color.Black,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
    }
}