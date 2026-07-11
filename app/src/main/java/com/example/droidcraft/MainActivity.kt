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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.math.sin
import kotlin.math.pow

// Constants for audio generation
private const val SAMPLE_RATE = 44100 // samples per second
private const val DURATION_MS_PER_CLICK = 300 // milliseconds
private const val AMPLITUDE_MAX = Short.MAX_VALUE.toFloat() * 0.7f // Max amplitude for 16-bit PCM, slightly reduced to avoid clipping

// Helper function to calculate frequency for a given MIDI note number
// A4 is MIDI note 69, 440 Hz
private fun midiNoteToFrequency(midiNote: Int): Float {
    return 440.0f * (2.0f.pow((midiNote - 69) / 12.0f))
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MainAppScreen()
        }
    }
}

@Composable
fun MainAppScreen() {
    // Flow to send note events to the sound synthesis coroutine
    val noteEventFlow = remember { MutableSharedFlow<Float>(extraBufferCapacity = 64) }

    // AudioTrack instance and coroutine management
    val coroutineScope = rememberCoroutineScope()
    var audioTrack: AudioTrack? by remember { mutableStateOf(null) }

    // Lifecycle observer to manage AudioTrack
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) { // Initialize on resume
                if (audioTrack == null) {
                    val minBufferSize = AudioTrack.getMinBufferSize(
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT
                    )
                    audioTrack = AudioTrack(
                        AudioManager.STREAM_MUSIC,
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        minBufferSize * 2, // Double buffer size for smoother streaming
                        AudioTrack.MODE_STREAM
                    ).apply { play() }

                    // Launch a coroutine to process note events and play sound
                    coroutineScope.launch(Dispatchers.IO) { // Use IO dispatcher for audio writing
                        noteEventFlow.collect { frequency ->
                            audioTrack?.let { track ->
                                val numSamples = (SAMPLE_RATE * DURATION_MS_PER_CLICK / 1000f).toInt()
                                val samples = ShortArray(numSamples)
                                // Generate sine wave samples for the given frequency
                                for (i in 0 until numSamples) {
                                    val angle = 2 * Math.PI * frequency * (i.toDouble() / SAMPLE_RATE)
                                    val sample = (AMPLITUDE_MAX * sin(angle)).toInt()
                                    samples[i] = sample.toShort()
                                }
                                // Write the samples to the AudioTrack
                                track.write(samples, 0, samples.size)
                            }
                        }
                    }
                }
            } else if (event == Lifecycle.Event.ON_PAUSE) { // Release on pause
                audioTrack?.stop()
                audioTrack?.release()
                audioTrack = null
                coroutineScope.cancel() // Cancel the coroutine scope when paused/destroyed
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
            coroutineScope.cancel()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("DroidCraft Piano", fontWeight = FontWeight.Bold) }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 8.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Tap a key to play a note!",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Piano Keyboard Layout
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                val whiteKeyWidth = 48.dp
                val whiteKeyHeight = 180.dp
                val blackKeyWidth = 32.dp
                val blackKeyHeight = 120.dp

                val totalWhiteKeys = 8 // C4-C5
                val totalWhiteKeyboardWidth = totalWhiteKeys * whiteKeyWidth
                val startOffsetForCentering = (maxWidth - totalWhiteKeyboardWidth) / 2

                // Layer 1: White Keys
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(x = startOffsetForCentering), // Offset to center the white keys
                    horizontalArrangement = Arrangement.Start // Start from the calculated offset
                ) {
                    val whiteKeyMidiNotes = listOf(60, 62, 64, 65, 67, 69, 71, 72) // C4, D4, E4, F4, G4, A4, B4, C5
                    whiteKeyMidiNotes.forEach { midiNote ->
                        val frequency = midiNoteToFrequency(midiNote)
                        PianoKey(
                            isBlack = false,
                            midiNote = midiNote,
                            width = whiteKeyWidth,
                            height = whiteKeyHeight,
                            onKeyPress = {
                                coroutineScope.launch { noteEventFlow.emit(frequency) }
                            }
                        )
                    }
                }

                // Layer 2: Black Keys
                val blackKeyMidiNotes = listOf(61, 63, 66, 68, 70) // C#4, D#4, F#4, G#4, A#4

                // Calculate horizontal offsets for black keys relative to the start of the first white key
                val blackKeyOffsets = listOf(
                    (whiteKeyWidth - blackKeyWidth / 2),                 // C# after C (index 0)
                    (whiteKeyWidth * 2 - blackKeyWidth / 2),             // D# after D (index 1)
                    (whiteKeyWidth * 4 - blackKeyWidth / 2),             // F# after F (index 3)
                    (whiteKeyWidth * 5 - blackKeyWidth / 2),             // G# after G (index 4)
                    (whiteKeyWidth * 6 - blackKeyWidth / 2)              // A# after A (index 5)
                )

                blackKeyMidiNotes.forEachIndexed { index, midiNote ->
                    val frequency = midiNoteToFrequency(midiNote)
                    Box(
                        modifier = Modifier
                            .offset(
                                x = startOffsetForCentering + blackKeyOffsets[index],
                                y = 0.dp // Black keys visually start at the same vertical origin as white keys
                            )
                    ) {
                        PianoKey(
                            isBlack = true,
                            midiNote = midiNote,
                            width = blackKeyWidth,
                            height = blackKeyHeight,
                            onKeyPress = {
                                coroutineScope.launch { noteEventFlow.emit(frequency) }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PianoKey(
    isBlack: Boolean,
    midiNote: Int,
    width: Dp,
    height: Dp,
    onKeyPress: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val keyColor = if (isBlack) Color.Black else Color.White
    val pressedColor = if (isBlack) Color.DarkGray else Color.LightGray

    val textColor = if (isBlack) Color.White.copy(alpha = 0.8f) else Color.DarkGray.copy(alpha = 0.8f)

    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .background(if (isPressed) pressedColor else keyColor, shape = RoundedCornerShape(4.dp))
            .border(1.dp, Color.DarkGray, shape = RoundedCornerShape(4.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null, // No ripple effect for piano keys
                onClick = onKeyPress
            )
    ) {
        val noteNames = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        val octave = (midiNote / 12) - 1 // MIDI note 60 (C4) -> octave 4
        val noteName = noteNames[midiNote % 12] + octave

        Text(
            text = noteName,
            color = textColor,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
}