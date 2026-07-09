package com.example.droidcraft

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectPressAndRelease
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sin

/**
 * [AudioPlayer] class for custom sound synthesis using Android's AudioTrack.
 * It generates a sine wave at a specified frequency.
 */
class AudioPlayer {
    private var audioTrack: AudioTrack? = null
    @Volatile private var isPlaying = false
    @Volatile private var currentFrequency = 0.0

    private val sampleRate = 44100 // Samples per second (Hz)
    private val minBufferSize = AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )
    // Ensure buffer is large enough for continuous playback, at least 1/4 second of audio
    private val bufferSize = minBufferSize.coerceAtLeast(sampleRate / 4)

    /**
     * Plays a sine wave tone at the given frequency.
     * If a tone is already playing, it will stop it and start the new one,
     * unless the same frequency is requested.
     */
    fun playTone(frequency: Double) {
        // If the same tone is already playing, do nothing
        if (isPlaying && currentFrequency == frequency) return

        stopTone() // Stop any currently playing tone to avoid multiple audio streams

        currentFrequency = frequency
        isPlaying = true

        CoroutineScope(Dispatchers.Default).launch {
            audioTrack = AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
                AudioTrack.MODE_STREAM
            ).apply { play() } // Initialize and start playback

            val buffer = ShortArray(bufferSize / 2) // For 16-bit PCM, 2 bytes per sample means buffer.size / 2 samples
            var angle = 0.0 // Current phase angle for sine wave generation
            val twoPi = 2 * Math.PI

            // Loop to continuously generate and play audio samples
            while (isPlaying && currentFrequency == frequency) {
                for (i in buffer.indices) {
                    val sample = (sin(angle) * Short.MAX_VALUE).toInt().toShort()
                    buffer[i] = sample
                    // Increment angle based on frequency and sample rate
                    angle += twoPi * frequency / sampleRate
                    // Keep angle within 0 to 2PI to prevent precision issues over long periods
                    if (angle > twoPi) angle -= twoPi
                }
                audioTrack?.write(buffer, 0, buffer.size)
            }

            // Once the loop exits (isPlaying becomes false or frequency changes),
            // stop and release the AudioTrack resources
            withContext(Dispatchers.Main) {
                audioTrack?.stop()
                audioTrack?.release()
                audioTrack = null
            }
        }
    }

    /**
     * Stops the currently playing tone.
     */
    fun stopTone() {
        if (!isPlaying) return // No tone is playing
        isPlaying = false
        currentFrequency = 0.0 // Reset frequency
        // The active coroutine will detect `isPlaying = false` and terminate itself.
    }
}


/**
 * Main Activity for the DroidCraft Piano app.
 */
class MainActivity : ComponentActivity() {
    // Instantiate our custom audio player. This instance will manage sound synthesis.
    private val audioPlayer = AudioPlayer()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Apply MaterialTheme to the entire application for consistent styling
            MaterialTheme {
                MainAppScreen(audioPlayer)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Ensure audio stops and resources are released when the activity is destroyed
        audioPlayer.stopTone()
    }
}

/**
 * Enum defining standard piano notes with their frequencies and key type (white/black).
 * Frequencies are based on the equal-tempered scale, A4 = 440 Hz.
 */
enum class PianoNote(val frequency: Double, val isBlackKey: Boolean = false) {
    C4(261.63),
    Db4(277.18, true), // D-flat, a black key
    D4(293.66),
    Eb4(311.13, true), // E-flat, a black key
    E4(329.63),
    F4(349.23),
    Gb4(369.99, true), // G-flat, a black key
    G4(392.00),
    Ab4(415.30, true), // A-flat, a black key
    A4(440.00),
    Bb4(466.16, true), // B-flat, a black key
    B4(493.88)
}

/**
 * The main UI screen for the DroidCraft Piano application.
 * Displays a single octave piano keyboard.
 */
@Composable
fun MainAppScreen(audioPlayer: AudioPlayer) {
    // List of white keys in a single octave
    val whiteKeys = listOf(
        PianoNote.C4, PianoNote.D4, PianoNote.E4, PianoNote.F4,
        PianoNote.G4, PianoNote.A4, PianoNote.B4
    )
    // List representing the layout of black keys relative to white keys.
    // `null` entries denote a gap where no black key exists (e.g., between E and F).
    val blackKeyNotesLayout = listOf(
        PianoNote.Db4, // Black key after C4
        PianoNote.Eb4, // Black key after D4
        null,          // No black key after E4 (E-F interval)
        PianoNote.Gb4, // Black key after F4
        PianoNote.Ab4, // Black key after G4
        PianoNote.Bb4, // Black key after A4
        null           // No black key after B4 (B-C interval)
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "DroidCraft Piano",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Use a Box to layer white and black keys on top of each other
        Box(
            modifier = Modifier
                .fillMaxWidth(0.95f) // Piano occupies 95% of the screen width
                .aspectRatio(3.5f) // Define the overall aspect ratio of the keyboard area
                .align(Alignment.CenterHorizontally)
        ) {
            // Layer 1: White keys, laid out horizontally
            Row(modifier = Modifier.fillMaxSize()) {
                whiteKeys.forEach { note ->
                    PianoKey(
                        note = note,
                        audioPlayer = audioPlayer,
                        modifier = Modifier
                            .weight(1f) // Each white key takes equal horizontal space
                            .fillMaxHeight()
                            .border(1.dp, Color.LightGray) // Visual border for white keys
                    )
                }
            }

            // Layer 2: Black keys, positioned on top of the white keys
            // BoxWithConstraints is used to dynamically calculate key sizes and positions
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize()
            ) {
                val whiteKeyWidth = maxWidth / whiteKeys.size // Calculated width of a single white key
                val blackKeyWidth = whiteKeyWidth * 0.6f      // Black keys are 60% of white key width
                val blackKeyHeight = maxHeight * 0.6f         // Black keys are 60% of total keyboard height

                // Iterate through the black key layout to place each black key
                blackKeyNotesLayout.forEachIndexed { index, note ->
                    if (note != null) {
                        // Calculate the horizontal offset for each black key.
                        // It should be centered over the division line between `whiteKeys[index]` and `whiteKeys[index+1]`.
                        // `(index + 1) * whiteKeyWidth` gives the position of the right edge of `whiteKeys[index]`.
                        // Subtracting half the `blackKeyWidth` centers it over that edge.
                        val offsetFromLeft = (index + 1) * whiteKeyWidth - (blackKeyWidth / 2)

                        Box(
                            modifier = Modifier
                                .offset(x = offsetFromLeft, y = 0.dp) // Position from left and top
                                .width(blackKeyWidth)
                                .height(blackKeyHeight)
                                .zIndex(1f) // Ensure black keys are rendered on top of white keys
                        ) {
                            PianoKey(
                                note = note,
                                audioPlayer = audioPlayer,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .border(1.dp, Color.DarkGray) // Visual border for black keys
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Composable function for a single piano key (either white or black).
 * Handles touch input to play and stop tones.
 */
@Composable
fun PianoKey(note: PianoNote, audioPlayer: AudioPlayer, modifier: Modifier) {
    val keyColor = if (note.isBlackKey) Color.Black else Color.White
    // Visual feedback color when the key is pressed
    val pressedColor = if (note.isBlackKey) Color.DarkGray else Color.LightGray.copy(alpha = 0.5f)

    // State to track if the key is currently being pressed
    var isPressed by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .pointerInput(Unit) { // Use pointerInput to detect press and release gestures
                detectPressAndRelease(
                    onPress = {
                        isPressed = true
                        audioPlayer.playTone(note.frequency)
                    },
                    onRelease = {
                        isPressed = false
                        audioPlayer.stopTone()
                    },
                    onCancel = { // Handle cases where touch is cancelled (e.g., finger slides off)
                        isPressed = false
                        audioPlayer.stopTone()
                    }
                )
            },
        shape = MaterialTheme.shapes.extraSmall, // Slightly rounded corners for keys
        color = keyColor // Base color of the key
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Apply a semi-transparent overlay when the key is pressed for visual feedback
                .background(if (isPressed) pressedColor else Color.Transparent)
        ) {
            // Display note name on white keys for identification
            if (!note.isBlackKey) {
                Text(
                    text = note.name.replace("4", ""), // Display "C" instead of "C4"
                    color = Color.DarkGray,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.BottomCenter) // Position text at the bottom center
                        .padding(bottom = 4.dp)
                )
            }
        }
    }
}