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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme { // Use MaterialTheme for consistent styling
                PianoAppScreen()
            }
        }
    }
}

/**
 * SynthEngine handles custom sound synthesis and audio playback using AudioTrack.
 * It generates a sine wave for each active note, allowing polyphony.
 */
class SynthEngine {
    private val sampleRate = 44100 // Standard CD quality sample rate
    private val bufferSize = AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    ) * 2 // Double buffer for smoother playback and to avoid underruns

    private var audioTrack: AudioTrack? = null
    private var playbackThread: Thread? = null
    private var isPlaying = false

    // Stores active notes and their current phase to ensure continuous waveforms for polyphony.
    // Map: frequency (Hz) -> current phase (radians)
    private val activeNotes = ConcurrentHashMap<Double, Double>()

    /**
     * Starts the audio engine, initializing AudioTrack and the playback thread.
     */
    fun start() {
        if (isPlaying) return
        isPlaying = true

        audioTrack = AudioTrack(
            AudioManager.STREAM_MUSIC,
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize,
            AudioTrack.MODE_STREAM // Stream mode for continuous playback
        ).apply { play() }

        playbackThread = Thread {
            // Buffer to hold audio samples before writing to AudioTrack
            // bufferSize is in bytes, ShortArray is 2 bytes per element
            val buffer = ShortArray(bufferSize / 2)
            while (isPlaying) {
                var sampleIndex = 0
                while (sampleIndex < buffer.size) {
                    var mixedSample = 0.0

                    // Iterate over active notes to mix their waveforms
                    activeNotes.forEach { (frequency, currentPhase) ->
                        val phaseIncrement = 2 * PI * frequency / sampleRate
                        // Update phase for the next sample generation, keeping it within 0 to 2PI
                        val newPhase = (currentPhase + phaseIncrement) % (2 * PI)
                        activeNotes[frequency] = newPhase

                        // Generate a simple sine wave for the current note
                        mixedSample += sin(newPhase) * 0.2 // Amplitude scaling to avoid clipping with multiple notes
                    }

                    // Convert the mixed sample to 16-bit PCM short format
                    // Max_Value is used to scale to the full dynamic range of a short
                    buffer[sampleIndex] = (mixedSample * Short.MAX_VALUE).toShort()
                    sampleIndex++
                }
                // Write the generated buffer to the AudioTrack for playback
                audioTrack?.write(buffer, 0, buffer.size)
            }
        }.apply { start() } // Start the playback thread
    }

    /**
     * Stops the audio engine, releasing resources.
     */
    fun stop() {
        isPlaying = false
        playbackThread?.join(500) // Give the thread a little time to finish
        playbackThread = null
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
        activeNotes.clear() // Clear all active notes
    }

    /**
     * Adds a note to the list of currently playing notes.
     * If the note is already playing, it does nothing.
     */
    fun playNote(frequency: Double) {
        if (frequency > 0 && !activeNotes.containsKey(frequency)) {
            activeNotes[frequency] = 0.0 // Start note with phase 0
        }
    }

    /**
     * Removes a note from the list of currently playing notes.
     */
    fun stopNote(frequency: Double) {
        activeNotes.remove(frequency)
    }
}

/**
 * Calculates the frequency for a given MIDI note number.
 * A4 (MIDI note 69) is 440 Hz.
 */
fun midiNoteToFrequency(midiNote: Int): Double {
    return 440.0 * 2.0.pow((midiNote - 69) / 12.0)
}

/**
 * Main composable screen for the piano application.
 */
@Composable
fun PianoAppScreen() {
    // Remember and manage the lifecycle of the SynthEngine
    val synthEngine = remember { SynthEngine() }

    // Use DisposableEffect to start and stop the engine with the Composable's lifecycle
    DisposableEffect(Unit) {
        synthEngine.start()
        onDispose {
            synthEngine.stop()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "DroidCraft Piano",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        PianoKeyboard(synthEngine)
    }
}

/**
 * Composable for rendering the piano keyboard.
 */
@Composable
fun PianoKeyboard(synthEngine: SynthEngine) {
    // Define the range of notes for the keyboard (e.g., C4 to C5/B5)
    // C4 = MIDI 60, C5 = MIDI 72 (inclusive of C5 for a full octave + C5)
    val startMidiNote = 60 // C4
    val endMidiNote = 72  // C5

    // A state to keep track of which keys are currently pressed for visual feedback
    // Stores the frequencies of currently pressed notes.
    val pressedKeys = remember { mutableStateListOf<Double>() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(horizontal = 8.dp)
            .background(Color.DarkGray)
            .border(2.dp, Color.Gray)
    ) {
        // White Keys Layer
        Row(modifier = Modifier.fillMaxSize()) {
            // Filter notes to get only white keys
            (startMidiNote..endMidiNote).filter { midiNote ->
                val noteName = midiToNoteName(midiNote)
                !noteName.contains("#") // White keys do not have '#'
            }.forEach { midiNote ->
                val frequency = midiNoteToFrequency(midiNote)
                PianoKey(
                    noteName = midiToNoteName(midiNote),
                    frequency = frequency,
                    isBlackKey = false,
                    isPressed = pressedKeys.contains(frequency),
                    onPress = {
                        synthEngine.playNote(frequency)
                        if (!pressedKeys.contains(frequency)) pressedKeys.add(frequency)
                    },
                    onRelease = {
                        synthEngine.stopNote(frequency)
                        pressedKeys.remove(frequency)
                    }
                )
            }
        }

        // Black Keys Layer (positioned on top)
        Row(
            modifier = Modifier
                .fillMaxSize()
                .offset(x = 18.dp) // Adjust based on key width to center black keys
        ) {
            var whiteKeyIndex = 0
            // Iterate through white keys to correctly position black keys above them
            (startMidiNote..endMidiNote).filter { midiNote ->
                val noteName = midiToNoteName(midiNote)
                !noteName.contains("#")
            }.forEach { midiNote ->
                // Check if the current white key should have a black key to its right
                val currentNoteName = midiToNoteName(midiNote)
                val hasBlackKey = currentNoteName.endsWith("C") || currentNoteName.endsWith("D") ||
                                  currentNoteName.endsWith("F") || currentNoteName.endsWith("G") ||
                                  currentNoteName.endsWith("A")

                if (hasBlackKey) {
                    val nextMidiNote = midiNote + 1 // C# is C + 1 semitone
                    // Ensure the black key is within our defined range
                    if (nextMidiNote <= endMidiNote) {
                        val frequency = midiNoteToFrequency(nextMidiNote)
                        PianoKey(
                            noteName = midiToNoteName(nextMidiNote),
                            frequency = frequency,
                            isBlackKey = true,
                            isPressed = pressedKeys.contains(frequency),
                            onPress = {
                                synthEngine.playNote(frequency)
                                if (!pressedKeys.contains(frequency)) pressedKeys.add(frequency)
                            },
                            onRelease = {
                                synthEngine.stopNote(frequency)
                                pressedKeys.remove(frequency)
                            },
                            modifier = Modifier
                                .offset(x = (-whiteKeyIndex * 40).dp) // Offset for previous white keys
                                .offset(x = (whiteKeyIndex * 40 + 20).dp) // Position relative to white keys
                        )
                    }
                }
                whiteKeyIndex++ // Increment for correct black key positioning
            }
        }
    }
}

/**
 * Composable for an individual piano key (white or black).
 */
@OptIn(ExperimentalMaterial3Api::class) // For detectPressAndRelease
@Composable
fun RowScope.PianoKey(
    noteName: String,
    frequency: Double,
    isBlackKey: Boolean,
    isPressed: Boolean,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when {
        isPressed && isBlackKey -> Color(0xFF333333) // Darker gray when black key pressed
        isPressed && !isBlackKey -> Color(0xFFCCCCCC) // Lighter gray when white key pressed
        isBlackKey -> Color.Black
        else -> Color.White
    }

    val textColor = if (isBlackKey) Color.White else Color.Black
    val borderColor = if (isBlackKey) Color.DarkGray else Color.Gray

    // Determine dimensions and Z-index for layering
    val keyModifier = if (isBlackKey) {
        Modifier
            .width(40.dp)
            .height(120.dp)
            .offset(x = (-20).dp) // Shift left to overlap white keys
            .zIndex(1f) // Ensure black keys are on top of white keys
    } else {
        Modifier
            .weight(1f) // White keys take equal horizontal space
            .fillMaxHeight()
            .zIndex(0f) // White keys are below black keys
    }

    Surface(
        color = backgroundColor,
        modifier = modifier
            .then(keyModifier) // Apply key specific modifiers
            .border(0.5.dp, borderColor)
            .pointerInput(key1 = noteName) { // Use noteName as key for pointerInput to ensure recomposition doesn't break input
                detectPressAndRelease(
                    onPress = { onPress() },
                    onRelease = { onRelease() }
                )
            }
    ) {
        // Optional: display note name on white keys for clarity
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            if (!isBlackKey) {
                Text(
                    text = noteName,
                    color = textColor,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }
    }
}

/**
 * Helper function to convert MIDI note number to common note name (e.g., C4, C#4).
 */
fun midiToNoteName(midiNote: Int): String {
    val noteNames = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    val octave = (midiNote / 12) - 1 // MIDI 0 is C-1, so C4 is MIDI 60 -> octave (60/12)-1 = 5-1 = 4
    val noteIndex = midiNote % 12
    return "${noteNames[noteIndex]}$octave"
}