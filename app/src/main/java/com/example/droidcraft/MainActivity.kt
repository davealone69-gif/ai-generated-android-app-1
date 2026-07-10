package com.example.droidcraft

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.*
import kotlin.math.sin
import kotlin.math.PI

// Constants for audio synthesis
const val SAMPLE_RATE = 44100 // Hz
const val DURATION_SEC = 0.1 // Duration of each generated buffer segment to write
const val AMPLITUDE = Short.MAX_VALUE.toFloat() * 0.5f // Max amplitude for 16-bit PCM

// Enum for musical notes and their fundamental frequencies (in Hz)
enum class Note(val frequency: Double, val isSharp: Boolean = false) {
    C4(261.63), CSharp4(277.18, true), D4(293.66), DSharp4(311.13, true), E4(329.63), F4(349.23),
    FSharp4(369.99, true), G4(392.00), GSharp4(415.30, true), A4(440.00), ASharp4(466.16, true), B4(493.88),
    C5(523.25), CSharp5(554.37, true), D5(587.33), DSharp5(622.25, true), E5(659.25), F5(698.46),
    FSharp5(739.99, true), G5(783.99), GSharp5(830.61, true), A5(880.00), ASharp5(932.33, true), B5(987.77);

    // Helper to determine if a note is 'white' or 'black' based on its sharp property
    val isWhite: Boolean get() = !isSharp
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Apply MaterialTheme for consistent styling
            MaterialTheme {
                MainAppScreen()
            }
        }
    }
}

// Singleton object to manage AudioTrack lifecycle and sound playback
object SoundPlayer {
    private var audioTrack: AudioTrack? = null
    private val bufferSize = AudioTrack.getMinBufferSize(
        SAMPLE_RATE,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    private val playerScope = CoroutineScope(Dispatchers.IO) // Coroutine scope for audio playback
    private val activeNoteJobs = mutableMapOf<Note, Job>() // Tracks currently playing notes

    init {
        // Initialize AudioTrack for streaming PCM audio
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
            .setBufferSizeInBytes(bufferSize)
            .setMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.play() // Start the AudioTrack to prepare for playback
    }

    /**
     * Generates a short segment of a sine wave for a given frequency.
     */
    private fun generateSineWave(frequency: Double): ShortArray {
        val numSamples = (SAMPLE_RATE * DURATION_SEC).toInt()
        val samples = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val sample = (AMPLITUDE * sin(2 * PI * frequency * i / SAMPLE_RATE)).toInt()
            samples[i] = sample.toShort()
        }
        return samples
    }

    /**
     * Starts playing a note continuously until explicitly stopped.
     * Each note's playback runs in its own coroutine.
     */
    fun playNote(note: Note) {
        // If the note is already playing, do nothing to avoid multiple jobs for the same note
        if (activeNoteJobs.containsKey(note)) {
            return
        }

        val job = playerScope.launch {
            audioTrack?.apply {
                try {
                    val audioData = generateSineWave(note.frequency)
                    // Continuously write audio data while the coroutine is active
                    while (isActive) {
                        write(audioData, 0, audioData.size)
                    }
                } catch (e: Exception) {
                    // Handle potential audio track errors (e.g., track released)
                    e.printStackTrace()
                }
            }
        }
        activeNoteJobs[note] = job
    }

    /**
     * Stops the playback of a specific note.
     */
    fun stopNote(note: Note) {
        activeNoteJobs[note]?.cancel() // Cancel the coroutine for this note
        activeNoteJobs.remove(note)
    }

    /**
     * Releases all resources held by the SoundPlayer.
     * Should be called when the audio player is no longer needed (e.g., app exits).
     */
    fun release() {
        playerScope.cancel() // Cancel all ongoing playback jobs
        audioTrack?.stop() // Stop the audio track
        audioTrack?.release() // Release native resources
        audioTrack = null
    }
}

@Composable
fun MainAppScreen() {
    val coroutineScope = rememberCoroutineScope() // Scope for launching suspend functions

    // Ensure audio resources are released when the MainAppScreen leaves composition
    DisposableEffect(Unit) {
        onDispose {
            SoundPlayer.release()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "DroidCraft Piano",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(16.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Composable for the piano keyboard layout
        PianoKeyboard(coroutineScope)
    }
}

@Composable
fun PianoKeyboard(coroutineScope: CoroutineScope) {
    // Define the white notes to be displayed on the keyboard
    val whiteNotes = listOf(
        Note.C4, Note.D4, Note.E4, Note.F4, Note.G4, Note.A4, Note.B4,
        Note.C5, Note.D5, Note.E5, Note.F5, Note.G5, Note.A5, Note.B5
    )

    // Define the black notes
    val blackNotes = listOf(
        Note.CSharp4, Note.DSharp4,
        Note.FSharp4, Note.GSharp4, Note.ASharp4,
        Note.CSharp5, Note.DSharp5,
        Note.FSharp5, Note.GSharp5, Note.ASharp5
    )

    Box(
        modifier = Modifier
            .fillMaxWidth(0.9f) // Occupy 90% of screen width
            .height(200.dp) // Fixed height for the keyboard
            .border(2.dp, Color.Black, RoundedCornerShape(8.dp))
            .background(Color.LightGray, RoundedCornerShape(8.dp))
    ) {
        // Layer 1: White keys
        Row(modifier = Modifier.fillMaxSize()) {
            whiteNotes.forEach { note ->
                PianoKey(
                    note = note,
                    modifier = Modifier
                        .weight(1f) // Each white key takes equal horizontal space
                        .fillMaxHeight(),
                    coroutineScope = coroutineScope
                )
            }
        }

        // Layer 2: Black keys (positioned using BoxWithConstraints and offset)
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val totalWidth = maxWidth
            val whiteKeyWidth = totalWidth / whiteNotes.size.toFloat() // Calculate individual white key width
            val blackKeyWidth = whiteKeyWidth * 0.6f // Black keys are narrower than white keys

            // Map black notes to their preceding white note for positioning logic
            val blackKeyPositions = mapOf(
                Note.CSharp4 to Note.C4,
                Note.DSharp4 to Note.D4,
                Note.FSharp4 to Note.F4,
                Note.GSharp4 to Note.G4,
                Note.ASharp4 to Note.A4,
                Note.CSharp5 to Note.C5,
                Note.DSharp5 to Note.D5,
                Note.FSharp5 to Note.F5,
                Note.GSharp5 to Note.G5,
                Note.ASharp5 to Note.A5
            )

            blackKeyPositions.forEach { (blackNote, whiteNoteBefore) ->
                val whiteKeyIndex = whiteNotes.indexOf(whiteNoteBefore)
                if (whiteKeyIndex != -1) {
                    // Calculate x-offset: centered over the gap between whiteNoteBefore and the next white note
                    // A physical piano usually shifts black keys slightly right of the exact center of the gap.
                    // This approximation places the left edge of the black key at (whiteKeyIndex + 0.75) * whiteKeyWidth
                    // minus half the black key width to center it visually.
                    val xOffset = (whiteKeyIndex + 0.75f) * whiteKeyWidth - (blackKeyWidth / 2)

                    PianoKey(
                        note = blackNote,
                        modifier = Modifier
                            .offset(x = xOffset) // Apply horizontal offset
                            .width(blackKeyWidth) // Set black key's width
                            .fillMaxHeight(0.6f) // Black keys are typically shorter
                            .zIndex(2f), // Ensure black keys are rendered above white keys
                        coroutineScope = coroutineScope
                    )
                }
            }
        }
    }
}

@Composable
fun PianoKey(
    note: Note,
    modifier: Modifier = Modifier,
    coroutineScope: CoroutineScope // Coroutine scope for LaunchedEffect
) {
    val interactionSource = remember { MutableInteractionSource() }
    // Collect the press state to detect when a key is pressed down or released
    val isPressed by interactionSource.collectIsPressedAsState()

    // Use LaunchedEffect to react to changes in the pressed state
    LaunchedEffect(isPressed) {
        if (isPressed) {
            SoundPlayer.playNote(note) // Start playing the note
        } else {
            SoundPlayer.stopNote(note) // Stop playing the note
        }
    }

    // Determine key color based on note type and pressed state
    val keyColor = if (note.isWhite) {
        if (isPressed) Color.LightGray else Color.White
    } else {
        if (isPressed) Color.DarkGray else Color.Black
    }
    val borderColor = Color.Black // All keys have black borders

    Surface(
        modifier = modifier
            // Add slight padding for white keys to create visual separation
            .padding(if (note.isWhite) 0.5.dp else 0.dp)
            .background(keyColor, RoundedCornerShape(2.dp))
            .border(0.5.dp, borderColor, RoundedCornerShape(2.dp))
            // Make the Surface clickable and associate with interactionSource
            .clickable(
                interactionSource = interactionSource,
                indication = null // Disable ripple effect for piano keys
            ) {},
        color = keyColor // Set the background color of the Surface itself
    ) {
        // No explicit content inside the key, the color and interaction are sufficient.
        // You could add Text for note labels here if desired.
    }
}