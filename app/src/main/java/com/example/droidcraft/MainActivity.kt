package com.example.droidcraft

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectPressAndRelease
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.*
import kotlin.math.PI
import kotlin.math.sin

// --- Sound Synthesis Logic ---

const val SAMPLE_RATE = 44100 // Samples per second for audio generation
const val BITS_PER_SAMPLE = 16 // 16-bit PCM for audio quality
const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO // Mono audio output
const val AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT // PCM 16-bit encoding

/**
 * [SoundSynthesizer] handles the generation and playback of audio waveforms (sine waves)
 * for piano notes using Android's AudioTrack in streaming mode.
 */
class SoundSynthesizer(private val coroutineScope: CoroutineScope) {

    private var audioTrack: AudioTrack? = null
    private var playingJob: Job? = null // Manages the coroutine generating audio samples

    init {
        // Calculate the minimum buffer size required for AudioTrack
        val minBufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_ENCODING
        )
        // Initialize AudioTrack for streaming audio data
        audioTrack = AudioTrack(
            AudioManager.STREAM_MUSIC, // Use the music stream type
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_ENCODING,
            minBufferSize, // Set buffer size to the minimum required
            AudioTrack.MODE_STREAM // Enable streaming mode for continuous playback
        ).apply {
            play() // Start the AudioTrack so it's ready to receive data
        }
    }

    /**
     * Starts playing a note at the given frequency.
     * If another note is currently playing, it will be stopped before the new note begins.
     * This implements a monophonic (single-voice) synthesizer.
     */
    fun startNote(frequency: Double) {
        playingJob?.cancel() // Cancel any previously playing note to ensure only one note sounds at a time

        // Launch a new coroutine on Dispatchers.Default for CPU-bound sample generation.
        // This prevents blocking the main UI thread.
        playingJob = coroutineScope.launch(Dispatchers.Default) {
            val bufferSizeInShorts = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_ENCODING
            ) / (BITS_PER_SAMPLE / 8) // Convert bytes to shorts (2 bytes per short for 16-bit PCM)
            val buffer = ShortArray(bufferSizeInShorts)

            var angle = 0.0 // Current phase angle for sine wave generation
            val angleIncrement = (2 * PI * frequency) / SAMPLE_RATE // Increment for each sample

            try {
                while (isActive) { // Continue as long as the coroutine is active (not cancelled)
                    for (i in buffer.indices) {
                        // Generate a sine wave sample and scale it to 16-bit PCM range
                        buffer[i] = (sin(angle) * Short.MAX_VALUE).toShort()
                        angle += angleIncrement
                    }
                    // Write the generated buffer to AudioTrack. This call can block if the buffer is full.
                    audioTrack?.write(buffer, 0, buffer.size)
                }
            } catch (e: Exception) {
                // Handle exceptions (e.g., if AudioTrack fails).
                // In a real app, you might log this error.
            } finally {
                // When the coroutine is cancelled, it gracefully exits, stopping sample generation.
                // The AudioTrack remains in playing mode, ready for the next `startNote` call.
            }
        }
    }

    /**
     * Stops the currently playing note by cancelling its generation coroutine.
     */
    fun stopNote() {
        playingJob?.cancel() // Signal the coroutine to stop generating samples
        playingJob = null
    }

    /**
     * Releases all resources held by the AudioTrack.
     * This method must be called when the [SoundSynthesizer] is no longer needed to prevent resource leaks.
     */
    fun release() {
        playingJob?.cancel() // Ensure any active note generation is stopped
        playingJob = null
        audioTrack?.stop() // Stop playback on the AudioTrack
        audioTrack?.release() // Release native AudioTrack resources
        audioTrack = null
    }
}

// --- Piano Data Model ---

/**
 * Data class representing a single piano note with its properties.
 */
data class PianoNote(val name: String, val frequency: Double, val isWhite: Boolean, val midi: Int)

/**
 * Predefined list of piano notes for a single octave (C4 to B4), including both white and black keys.
 */
val pianoNotes = listOf(
    // White keys (C4 to B4)
    PianoNote("C4", 261.63, true, 60),
    PianoNote("D4", 293.66, true, 62),
    PianoNote("E4", 329.63, true, 64),
    PianoNote("F4", 349.23, true, 65),
    PianoNote("G4", 392.00, true, 67),
    PianoNote("A4", 440.00, true, 69),
    PianoNote("B4", 493.88, true, 71),

    // Black keys (C#4, D#4, F#4, G#4, A#4)
    PianoNote("C#4", 277.18, false, 61),
    PianoNote("D#4", 311.13, false, 63),
    PianoNote("F#4", 369.99, false, 66),
    PianoNote("G#4", 415.30, false, 68),
    PianoNote("A#4", 466.16, false, 70)
)

// --- Composable UI Components ---

/**
 * A Composable that represents a single piano key, handling visual feedback and touch events.
 */
@Composable
fun PianoKey(
    note: PianoNote,
    modifier: Modifier = Modifier,
    onPress: (PianoNote) -> Unit,
    onRelease: (PianoNote) -> Unit
) {
    var isPressed by remember { mutableStateOf(false) } // State to track if the key is currently pressed

    // Determine key color based on its type (white/black) and press state
    val keyColor = when {
        isPressed && note.isWhite -> Color(0xFFCCCCCC) // Lighter grey when white key pressed
        isPressed && !note.isWhite -> Color(0xFF333333) // Darker grey when black key pressed
        note.isWhite -> Color.White // Default white key color
        else -> Color.Black // Default black key color
    }
    val borderColor = if (note.isWhite) Color.DarkGray else Color.Black // Border color
    // Define rounded corner shapes for keys
    val cornerShape = if (note.isWhite) RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
                      else RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp)

    Card(
        modifier = modifier
            // Apply pointerInput to detect press and release gestures
            .pointerInput(note) { // Keyed by note to ensure each key has its own state
                detectPressAndRelease(
                    onPress = {
                        isPressed = true
                        onPress(note)
                    },
                    onRelease = {
                        isPressed = false
                        onRelease(note)
                    }
                )
            }
            // Add a border to the key
            .border(1.dp, borderColor, cornerShape),
        colors = CardDefaults.cardColors(containerColor = keyColor), // Set background color
        shape = cornerShape // Apply rounded corners
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            // Display note label only on white keys for better aesthetics
            if (note.isWhite) {
                Text(
                    text = note.name.substring(0, 1), // Display just the letter (e.g., "C", "D")
                    color = Color.DarkGray,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }
    }
}

// --- Main App Screen ---

/**
 * The main UI screen for the DroidCraft Piano app.
 * It sets up the piano keyboard and manages the sound synthesizer's lifecycle.
 */
@Composable
fun MainAppScreen() {
    val scope = rememberCoroutineScope() // Coroutine scope for launching sound generation jobs
    // Remember and initialize the SoundSynthesizer, ensuring it's tied to the composable's lifecycle
    val synthesizer = remember { SoundSynthesizer(scope) }

    // Use DisposableEffect to release synthesizer resources when this composable leaves the composition
    DisposableEffect(Unit) {
        onDispose {
            synthesizer.release()
        }
    }

    val whiteNotes = pianoNotes.filter { it.isWhite }
    val blackNotes = pianoNotes.filter { !it.isWhite }

    // Define key dimensions
    val whiteKeyHeight = 200.dp
    val blackKeyHeight = 120.dp // Black keys are typically shorter
    val blackKeyWidthRatio = 0.6f // Black keys are narrower, e.g., 60% of white key width
    val keyGap = 2.dp // Visual gap between keys

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface) // Use Material3 surface color
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween // Distribute content vertically
    ) {
        // App title
        Text(
            text = "DroidCraft Piano",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp),
            color = MaterialTheme.colorScheme.onSurface
        )

        // Piano keyboard layout container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(whiteKeyHeight) // Set overall height for the keyboard
                .padding(horizontal = 8.dp), // Horizontal padding for the entire keyboard
            contentAlignment = Alignment.BottomCenter // Align keyboard to the bottom of the box
        ) {
            var singleWhiteKeyCalculatedWidth by remember { mutableStateOf(0.dp) }
            val density = LocalDensity.current

            // Layer for White Keys
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    // Use onGloballyPositioned to measure the actual width of a single white key
                    .onGloballyPositioned { coordinates ->
                        val totalWhiteKeysRowWidthPx = coordinates.size.width
                        val totalGapWidthPx = with(density) { (keyGap * (whiteNotes.size - 1)).toPx() }
                        val singleWhiteKeyWidthPx = (totalWhiteKeysRowWidthPx - totalGapWidthPx) / whiteNotes.size
                        singleWhiteKeyCalculatedWidth = with(density) { singleWhiteKeyWidthPx.toDp() }
                    },
                horizontalArrangement = Arrangement.spacedBy(keyGap) // Add space between white keys
            ) {
                whiteNotes.forEach { note ->
                    PianoKey(
                        note = note,
                        modifier = Modifier
                            .weight(1f) // Distribute white keys evenly
                            .fillMaxHeight(),
                        onPress = { synthesizer.startNote(it.frequency) },
                        onRelease = { synthesizer.stopNote() }
                    )
                }
            }

            // Layer for Black Keys - overlayed on top of white keys, only if white key width is calculated
            if (singleWhiteKeyCalculatedWidth > 0.dp) {
                val calculatedBlackKeyWidth = singleWhiteKeyCalculatedWidth * blackKeyWidthRatio

                // Map black notes to the index of the white key they are typically placed 'after'
                // Used for calculating their horizontal position.
                val blackKeyWhiteKeyIndexMap = mapOf(
                    pianoNotes.first { it.name == "C#4" } to 0, // C#4 after C4
                    pianoNotes.first { it.name == "D#4" } to 1, // D#4 after D4
                    pianoNotes.first { it.name == "F#4" } to 3, // F#4 after F4
                    pianoNotes.first { it.name == "G#4" } to 4, // G#4 after G4
                    pianoNotes.first { it.name == "A#4" } to 5  // A#4 after A4
                )

                blackKeyWhiteKeyIndexMap.forEach { (note, whiteKeyIndex) ->
                    // Calculate the x-offset for the black key's left edge.
                    // It's positioned centered over the seam between the whiteKeyIndex and whiteKeyIndex+1.
                    // Calculation: (cumulative width of preceding white keys + their gaps)
                    //             + (width of the current white key - half black key width + half gap)
                    val xOffset = (whiteKeyIndex * (singleWhiteKeyCalculatedWidth + keyGap)) +
                                  singleWhiteKeyCalculatedWidth - // Right edge of the preceding white key
                                  (calculatedBlackKeyWidth / 2) + // Shift left by half black key width
                                  (keyGap / 2) // Shift right by half gap to center it on the seam

                    Box(
                        modifier = Modifier
                            .offset(x = xOffset) // Apply the calculated horizontal offset
                            .width(calculatedBlackKeyWidth) // Set black key width
                            .height(blackKeyHeight) // Set black key height
                            .align(Alignment.BottomStart) // Align to the bottom-left of the parent Box
                    ) {
                        PianoKey(
                            note = note,
                            modifier = Modifier.fillMaxSize(),
                            onPress = { synthesizer.startNote(it.frequency) },
                            onRelease = { synthesizer.stopNote() }
                        )
                    }
                }
            }
        }
    }
}

// --- MainActivity and Preview ---

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Apply a basic Material3 theme to the app.
            // In a real app, this would typically be a custom theme (e.g., DroidcraftTheme).
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainAppScreen()
                }
            }
        }
    }
}

/**
 * Preview Composable for [MainAppScreen] to visualize the UI in Android Studio.
 */
@Preview(showBackground = true, widthDp = 700, heightDp = 300)
@Composable
fun PreviewMainAppScreen() {
    MaterialTheme {
        Surface {
            MainAppScreen()
        }
    }
}