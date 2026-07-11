package com.example.droidcraft

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import android.media.AudioTrack
import android.media.AudioFormat
import android.media.AudioManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlin.math.sin
import kotlin.math.PI
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.gestures.detectPressAndRelease
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

// Define some constants for audio synthesis
private const val SAMPLE_RATE = 44100 // samples per second
private val BUFFER_SIZE = AudioTrack.getMinBufferSize(
    SAMPLE_RATE,
    AudioFormat.CHANNEL_OUT_MONO,
    AudioFormat.ENCODING_PCM_16BIT
)

// A simple sine wave generator engine
class AudioEngine {
    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    private var currentFrequency: Double = 0.0
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    init {
        // Initialize AudioTrack
        if (BUFFER_SIZE == AudioTrack.ERROR_BAD_VALUE || BUFFER_SIZE == AudioTrack.ERROR) {
             // Handle error, e.g., log it, show a toast, or disable audio features
             // For this example, we'll just allow audioTrack to be null.
        } else {
            audioTrack = AudioTrack(
                AudioManager.STREAM_MUSIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                BUFFER_SIZE,
                AudioTrack.MODE_STREAM
            )
            // Start the audio track so it's ready to receive data.
            audioTrack?.play()
        }
    }

    fun playNote(frequency: Double) {
        if (audioTrack == null || audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
            // AudioTrack not initialized or invalid, cannot play
            return
        }
        if (isPlaying && currentFrequency == frequency) return // Already playing this note
        
        stopNote() // Stop any currently playing note before starting a new one

        currentFrequency = frequency
        isPlaying = true
        job = scope.launch {
            var phase = 0.0
            val samples = ShortArray(BUFFER_SIZE / 2) // For 16-bit PCM, 2 bytes per sample
            while (isPlaying && currentFrequency == frequency && audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) {
                for (i in samples.indices) {
                    val sampleValue = (sin(phase) * Short.MAX_VALUE).toInt().toShort()
                    samples[i] = sampleValue
                    phase += (2 * PI * currentFrequency / SAMPLE_RATE)
                    if (phase > 2 * PI) phase -= (2 * PI) // Keep phase within 0 to 2PI
                }
                audioTrack?.write(samples, 0, samples.size)
            }
        }
    }

    fun stopNote() {
        isPlaying = false
        currentFrequency = 0.0
        job?.cancel() // Cancel the coroutine responsible for playing the note
        job = null
    }

    fun release() {
        stopNote()
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
        scope.cancel() // Cancel the coroutine scope to clean up any ongoing coroutines
    }
}

// Frequencies for a basic octave (C4 to B4)
val PIANO_NOTES = mapOf(
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
    "B4" to 493.88
)

class MainActivity : ComponentActivity() {
    // AudioEngine is created once with the activity and managed through its lifecycle
    private val audioEngine = AudioEngine()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // A MaterialTheme must wrap the content for Material3 components to work correctly
            // and pick up default styles/colors.
            MaterialTheme {
                MainAppScreen(audioEngine = audioEngine)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioEngine.release() // Release AudioTrack resources when activity is destroyed
    }
}

@Composable
fun MainAppScreen(audioEngine: AudioEngine) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "DroidCraft Piano",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            PianoKeyboard(audioEngine = audioEngine)
        }
    }
}

@Composable
fun PianoKeyboard(audioEngine: AudioEngine) {
    // This Box acts as the container for all keys, allowing for stacking (white keys below, black keys above)
    Box(
        modifier = Modifier
            .fillMaxWidth(0.95f) // Adjust keyboard width
            .height(200.dp) // Fixed height for the keyboard
            .background(Color.Transparent)
    ) {
        // White keys row - takes full width of the Box, height will be adjusted by PianoKey
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceAround // Distributes space between keys
        ) {
            val whiteKeys = listOf("C4", "D4", "E4", "F4", "G4", "A4", "B4")
            whiteKeys.forEach { noteName ->
                PianoKey(
                    noteName = noteName,
                    frequency = PIANO_NOTES[noteName]!!,
                    audioEngine = audioEngine,
                    isBlack = false,
                    modifier = Modifier.weight(1f) // Each white key takes equal weight
                )
            }
        }

        // Black keys row - positioned over the white keys.
        // Needs careful weight distribution to align correctly above the gaps in white keys.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp) // Black keys are shorter
                .align(Alignment.TopCenter) // Align to the top of the parent Box
        ) {
            // These spacers are designed to visually position the black keys correctly.
            // Weights are approximate for a visually balanced layout.
            
            // Spacer before C#4
            Spacer(modifier = Modifier.weight(0.58f)) 
            PianoKey(
                noteName = "C#4",
                frequency = PIANO_NOTES["C#4"]!!,
                audioEngine = audioEngine,
                isBlack = true,
                modifier = Modifier.weight(0.40f) // Black key width
            )
            // Spacer between C#4 and D#4
            Spacer(modifier = Modifier.weight(0.48f)) 
            PianoKey(
                noteName = "D#4",
                frequency = PIANO_NOTES["D#4"]!!,
                audioEngine = audioEngine,
                isBlack = true,
                modifier = Modifier.weight(0.40f) // Black key width
            )
            // Spacer for E-F gap (no E# or B#)
            Spacer(modifier = Modifier.weight(1.00f)) 
            PianoKey(
                noteName = "F#4",
                frequency = PIANO_NOTES["F#4"]!!,
                audioEngine = audioEngine,
                isBlack = true,
                modifier = Modifier.weight(0.40f)
            )
            // Spacer between F#4 and G#4
            Spacer(modifier = Modifier.weight(0.48f)) 
            PianoKey(
                noteName = "G#4",
                frequency = PIANO_NOTES["G#4"]!!,
                audioEngine = audioEngine,
                isBlack = true,
                modifier = Modifier.weight(0.40f)
            )
            // Spacer between G#4 and A#4
            Spacer(modifier = Modifier.weight(0.48f)) 
            PianoKey(
                noteName = "A#4",
                frequency = PIANO_NOTES["A#4"]!!,
                audioEngine = audioEngine,
                isBlack = true,
                modifier = Modifier.weight(0.40f)
            )
            // Spacer after A#4
            Spacer(modifier = Modifier.weight(0.58f)) 
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PianoKey(
    noteName: String,
    frequency: Double,
    audioEngine: AudioEngine,
    isBlack: Boolean,
    modifier: Modifier = Modifier
) {
    val keyColor = if (isBlack) Color.Black else Color.White
    val textColor = if (isBlack) Color.White else Color.Black
    val pressedColor = if (isBlack) Color.DarkGray else Color(0xFFE0E0E0) // Lighter gray for white pressed

    var isPressed by remember { mutableStateOf(false) }

    val actualModifier = modifier
        .fillMaxHeight(if (isBlack) 0.65f else 1f) // Black keys are shorter than white keys
        .padding(
            horizontal = if (isBlack) 0.dp else 2.dp, // No horizontal padding for black keys
            vertical = 2.dp
        )
        // Add accessibility semantics
        .semantics(mergeDescendants = true) {
            contentDescription = "$noteName piano key"
            role = Role.Button
        }
        // Detect press and release events for continuous sound
        .pointerInput(noteName) { // Use noteName as key to restart detector if note definition changes
            detectPressAndRelease(
                onPress = { offset: Offset ->
                    isPressed = true
                    audioEngine.playNote(frequency)
                },
                onRelease = {
                    isPressed = false
                    audioEngine.stopNote()
                }
            )
        }

    Surface(
        shape = MaterialTheme.shapes.extraSmall, // Sharper corners for a key look
        color = if (isPressed) pressedColor else keyColor,
        border = if (!isBlack) ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp) else null, // Border for white keys
        shadowElevation = if (isPressed) 2.dp else 4.dp, // Subtle shadow for depth
        modifier = actualModifier
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Text(
                text = noteName.replace("#", "♯"), // Use sharp symbol for better display
                color = textColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    }
}