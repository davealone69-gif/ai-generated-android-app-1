package com.example.droidcraft

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // A basic Material Theme for the app
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PianoScreen()
                }
            }
        }
    }
}

/**
 * Manages custom sound synthesis and playback for a single note at a time (monophonic).
 * Generates a sine wave for the given frequency.
 */
class NotePlayer {
    private val sampleRate = 44100 // Standard audio sample rate
    private val bufferSize = AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    ) // Minimum buffer size for AudioTrack
    private var audioTrack: AudioTrack? = null
    private var playerThread: Thread? = null
    private val isPlaying = AtomicBoolean(false) // Flag to control playback thread loop
    private var currentFrequency = 0.0 // The frequency of the currently playing note

    init {
        // Initialize AudioTrack. It will be started/stopped by the player thread.
        audioTrack = AudioTrack(
            AudioManager.STREAM_MUSIC, // Stream type for music playback
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO, // Mono channel configuration
            AudioFormat.ENCODING_PCM_16BIT, // 16-bit PCM encoding
            bufferSize,
            AudioTrack.MODE_STREAM // Stream mode for continuous data writing
        )
    }

    /**
     * Starts playing a note at the given frequency. Stops any currently playing note first.
     */
    fun startNote(frequency: Double) {
        // If the same note is already playing, do nothing to avoid restarting
        if (isPlaying.get() && currentFrequency == frequency) {
            return
        }
        stopNote() // Stop any previous note to ensure monophony

        currentFrequency = frequency
        isPlaying.set(true)

        // Start a new thread for audio generation and playback to avoid blocking the UI
        playerThread = Thread {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
            audioTrack?.play() // Start the AudioTrack
            var phase = 0.0 // Keeps track of the sine wave phase for continuous tone
            val samples = ShortArray(bufferSize / 2) // Buffer to hold generated samples (Short is 2 bytes)

            while (isPlaying.get()) {
                // Generate sine wave samples
                for (i in samples.indices) {
                    val angle = 2.0 * Math.PI * currentFrequency * phase / sampleRate
                    samples[i] = (Short.MAX_VALUE * 0.5 * sin(angle)).toShort() // Amplitude reduced slightly (0.5)
                    phase++
                }
                // Write samples to AudioTrack
                audioTrack?.write(samples, 0, samples.size)
            }
            audioTrack?.stop() // Stop AudioTrack when thread finishes
        }.also { it.start() }
    }

    /**
     * Stops the currently playing note and its associated thread.
     */
    fun stopNote() {
        isPlaying.set(false) // Signal the playback thread to stop
        playerThread?.join(500) // Wait for the thread to finish, with a timeout
        playerThread = null
        currentFrequency = 0.0
    }

    /**
     * Releases all resources held by the NotePlayer.
     * Should be called when the player is no longer needed.
     */
    fun release() {
        stopNote() // Ensure playback is stopped
        audioTrack?.release() // Release the AudioTrack
        audioTrack = null
    }
}

/**
 * Enum defining musical notes with their base frequencies (in Hz).
 * Octaves are specified (e.g., C4, C5).
 */
enum class Note(val baseFrequency: Double) {
    C4(261.63), Cs4(277.18), D4(293.66), Ds4(311.13), E4(329.63), F4(349.23), Fs4(369.99), G4(392.00), Gs4(415.30), A4(440.00), As4(466.16), B4(493.88),
    C5(523.25), Cs5(554.37), D5(587.33), Ds5(622.25), E5(659.25), F5(698.46), Fs5(739.99), G5(783.99), Gs5(830.61), A5(880.00), As5(932.33), B5(987.77);

    // Helper to identify if a note is a sharp/flat (black key)
    fun isSharpOrFlat() = this.name.contains("s")
}

/**
 * Composable function for the entire piano keyboard UI.
 */
@Composable
fun PianoScreen() {
    // Remember and manage the lifecycle of the NotePlayer
    val notePlayer = remember { NotePlayer() }
    DisposableEffect(Unit) {
        onDispose {
            notePlayer.release() // Release resources when the Composable is removed
        }
    }

    // Define the sequence of white keys for display
    val whiteNotesSequence = listOf(
        Note.C4, Note.D4, Note.E4, Note.F4, Note.G4, Note.A4, Note.B4,
        Note.C5, Note.D5, Note.E5, Note.F5, Note.G5, Note.A5, Note.B5
    )

    // Define the black keys that need to be drawn
    val blackNotesToDraw = listOf(
        Note.Cs4, Note.Ds4, Note.Fs4, Note.Gs4, Note.As4,
        Note.Cs5, Note.Ds5, Note.Fs5, Note.Gs5, Note.As5
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.DarkGray)
            .padding(8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "DroidCraft Piano",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Use BoxWithConstraints to dynamically size keys based on available space
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth(0.9f) // Occupy 90% of screen width
                .fillMaxHeight(0.6f) // Occupy 60% of screen height
        ) {
            val totalWhiteKeys = whiteNotesSequence.size
            val whiteKeyWidth = maxWidth / totalWhiteKeys // Calculate width of each white key
            val blackKeyWidth = whiteKeyWidth * 0.6f // Black keys are narrower
            val blackKeyHeight = maxHeight * 0.6f // Black keys are shorter

            // Layer 1: White Keys
            Row(modifier = Modifier.fillMaxSize()) {
                whiteNotesSequence.forEach { note ->
                    PianoKey(
                        note = note,
                        keyColor = Color.White,
                        pressedColor = Color.LightGray,
                        modifier = Modifier
                            .width(whiteKeyWidth)
                            .fillMaxHeight(),
                        notePlayer = notePlayer
                    )
                }
            }

            // Layer 2: Black Keys (positioned absolutely on top of white keys)
            blackNotesToDraw.forEach { blackNote ->
                // Determine the white key to the left of this black key for positioning reference
                val baseNoteName = blackNote.name.substring(0, blackNote.name.length - 2) + blackNote.name.last()
                val whiteKeyLeftOfBlack = whiteNotesSequence.first { it.name == baseNoteName }

                val whiteKeyIndex = whiteNotesSequence.indexOf(whiteKeyLeftOfBlack)
                // Calculate the x-offset for the black key to sit between white keys
                // (index of left white key + 1) * whiteKeyWidth gives the right edge of the left white key
                // Subtract half blackKeyWidth to center it over the gap
                val xOffset = (whiteKeyIndex + 1) * whiteKeyWidth - (blackKeyWidth / 2)

                Box(
                    modifier = Modifier
                        .offset(x = xOffset) // Position horizontally
                        .width(blackKeyWidth)
                        .height(blackKeyHeight)
                        .align(Alignment.TopStart) // Align to the top-left of the parent BoxWithConstraints
                ) {
                    PianoKey(
                        note = blackNote,
                        keyColor = Color.Black,
                        pressedColor = Color.DarkGray,
                        modifier = Modifier.fillMaxSize(),
                        notePlayer = notePlayer
                    )
                }
            }
        }
    }
}

/**
 * Composable function for a single piano key (white or black).
 */
@OptIn(ExperimentalMaterial3Api::class) // For InteractionSource
@Composable
fun PianoKey(
    note: Note,
    keyColor: Color,
    pressedColor: Color,
    modifier: Modifier = Modifier,
    notePlayer: NotePlayer
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState() // Observe press state for UI feedback

    val currentKeyColor = if (isPressed) pressedColor else keyColor
    val borderColor = if (keyColor == Color.White) Color.Black else Color.DarkGray

    Box(
        modifier = modifier
            .border(1.dp, borderColor) // Key border
            .background(currentKeyColor) // Key background color
            .clickable( // Basic clickable for accessibility and interaction source
                interactionSource = interactionSource,
                indication = null // No ripple effect for piano keys
            ) {}
            // Use pointerInput to detect precise press and release gestures for sound control
            .pointerInput(note) { // Re-launch if 'note' changes
                detectTapGestures(
                    onPress = {
                        // When a key is pressed down, start playing its note
                        notePlayer.startNote(note.baseFrequency)
                        // Launch a coroutine to wait for the release event
                        val job = launch { tryAwaitRelease() }
                        // When the release occurs (or the job is cancelled), stop the note
                        job.invokeOnCompletion {
                            notePlayer.stopNote()
                        }
                    }
                    // onRelease and onLongPress are not explicitly needed here as onPress handles the full press-release cycle
                )
            }
    ) {
        // Optional: display note name on the key
        Text(
            text = note.name.replace("s", "#"), // Replace 's' with '#' for display (e.g., 'Cs4' -> 'C#4')
            color = if (keyColor == Color.White) Color.Black else Color.White,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 4.dp)
        )
    }
}