package com.davealone69.everything4droid

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class WaveType { SINE, SQUARE, TRIANGLE, SAWTOOTH }

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
    val coroutineScope = rememberCoroutineScope()
    
    // Synth Parameters
    var waveType by remember { mutableStateOf(WaveType.SINE) }
    var attack by remember { mutableFloatStateOf(0.05f) }
    var decay by remember { mutableFloatStateOf(0.8f) }
    var volume by remember { mutableFloatStateOf(0.7f) }
    var octaveShift by remember { mutableIntStateOf(0) } // Offset in octaves

    // Track active playing keys for visual feedback
    val activeKeys = remember { mutableStateMapOf<Int, Boolean>() }

    // Piano key mapping details (10 white keys starting from standard C)
    // C, D, E, F, G, A, B, C, D, E
    val whiteKeyOffsets = listOf(0, 2, 4, 5, 7, 9, 11, 12, 14, 16)
    val whiteKeyNames = listOf("C", "D", "E", "F", "G", "A", "B", "C", "D", "E")

    // Black keys positioned relative to white keys (MIDI offsets)
    // C#, D#, F#, G#, A#, C#, D#
    val blackKeys = listOf(
        BlackKeyData(midiOffset = 1, leftNeighborIndex = 0, label = "C#"),
        BlackKeyData(midiOffset = 3, leftNeighborIndex = 1, label = "D#"),
        BlackKeyData(midiOffset = 6, leftNeighborIndex = 3, label = "F#"),
        BlackKeyData(midiOffset = 8, leftNeighborIndex = 4, label = "G#"),
        BlackKeyData(midiOffset = 10, leftNeighborIndex = 5, label = "A#"),
        BlackKeyData(midiOffset = 13, leftNeighborIndex = 7, label = "C#"),
        BlackKeyData(midiOffset = 15, leftNeighborIndex = 8, label = "D#")
    )

    // Base MIDI note is Middle C (60)
    val baseMidiNote = 60 + (octaveShift * 12)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF121214)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "RetroSynth Piano",
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    color = Color(0xFFBB86FC)
                )
                Text(
                    text = "Real-time Native Sound Synthesis Engine",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Waveform Visualizer Screen
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E1E24))
                    .border(1.dp, Color(0xFF3700B3), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                WaveformVisualizer(waveType = waveType, attack = attack, decay = decay)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Synth Controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E1E24), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                // Wave selector Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    WaveType.values().forEach { type ->
                        val selected = waveType == type
                        Button(
                            onClick = { waveType = type },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selected) Color(0xFFBB86FC) else Color(0xFF2C2C35),
                                contentColor = if (selected) Color.Black else Color.White
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            Text(text = type.name, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ADSR / Vol / Octave sliders
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = "Attack: ${"%.2f".format(attack)}s",
                            fontSize = 11.sp,
                            color = Color.LightGray
                        )
                        Slider(
                            value = attack,
                            onValueChange = { attack = it },
                            valueRange = 0.01f..0.8f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFBB86FC),
                                activeTrackColor = Color(0xFFBB86FC)
                            )
                        )
                    }
                    Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(
                            text = "Decay: ${"%.2f".format(decay)}s",
                            fontSize = 11.sp,
                            color = Color.LightGray
                        )
                        Slider(
                            value = decay,
                            onValueChange = { decay = it },
                            valueRange = 0.1f..2.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFBB86FC),
                                activeTrackColor = Color(0xFFBB86FC)
                            )
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1.2f).padding(end = 8.dp)) {
                        Text(
                            text = "Volume: ${"%.1f".format(volume * 100)}%",
                            fontSize = 11.sp,
                            color = Color.LightGray
                        )
                        Slider(
                            value = volume,
                            onValueChange = { volume = it },
                            valueRange = 0.0f..1.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF03DAC6),
                                activeTrackColor = Color(0xFF03DAC6)
                            )
                        )
                    }

                    // Octave Shift Buttons
                    Column(
                        modifier = Modifier.weight(0.8f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "Octave", fontSize = 11.sp, color = Color.LightGray)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            IconButton(
                                onClick = { if (octaveShift > -2) octaveShift-- },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Text("<", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = if (octaveShift >= 0) "+$octaveShift" else "$octaveShift",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp)
                            )
                            IconButton(
                                onClick = { if (octaveShift < 3) octaveShift++ },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Text(">", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Piano Keyboard Layout
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black, RoundedCornerShape(8.dp))
                    .padding(4.dp)
            ) {
                val totalWhiteKeys = whiteKeyOffsets.size
                val keyWidth = maxWidth / totalWhiteKeys
                val keyHeight = maxHeight

                // 1. Draw White Keys Row
                Row(modifier = Modifier.fillMaxSize()) {
                    whiteKeyOffsets.forEachIndexed { index, offset ->
                        val note = baseMidiNote + offset
                        val isPressed = activeKeys[note] == true
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(horizontal = 1.5.dp)
                                .clip(RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp))
                                .background(
                                    if (isPressed) Color(0xFFE0E0E0) else Color.White
                                )
                                .pointerInput(note, waveType, attack, decay, volume) {
                                    detectTapGestures(
                                        onPress = {
                                            activeKeys[note] = true
                                            playSynthesizedNote(
                                                midiNote = note,
                                                waveType = waveType,
                                                attackSec = attack,
                                                decaySec = decay,
                                                volume = volume,
                                                coroutineScope = coroutineScope
                                            )
                                            try {
                                                awaitRelease()
                                            } finally {
                                                activeKeys[note] = false
                                            }
                                        }
                                    )
                                },
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Text(
                                text = whiteKeyNames[index],
                                color = Color.DarkGray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                    }
                }

                // 2. Overlay Black Keys
                val blackKeyWidth = keyWidth * 0.65f
                val blackKeyHeight = keyHeight * 0.6f

                blackKeys.forEach { keyData ->
                    val note = baseMidiNote + keyData.midiOffset
                    val isPressed = activeKeys[note] == true
                    
                    // Center of black key sits between leftNeighbor white key and the next white key
                    val leftOffset = (keyData.leftNeighborIndex + 1) * keyWidth - (blackKeyWidth / 2)

                    Box(
                        modifier = Modifier
                            .offset(x = leftOffset)
                            .size(width = blackKeyWidth, height = blackKeyHeight)
                            .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                            .background(
                                if (isPressed) Color(0xFF424242) else Color(0xFF151515)
                            )
                            .border(1.dp, Color.Black, RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                            .pointerInput(note, waveType, attack, decay, volume) {
                                detectTapGestures(
                                    onPress = {
                                        activeKeys[note] = true
                                        playSynthesizedNote(
                                            midiNote = note,
                                            waveType = waveType,
                                            attackSec = attack,
                                            decaySec = decay,
                                            volume = volume,
                                            coroutineScope = coroutineScope
                                        )
                                        try {
                                            awaitRelease()
                                        } finally {
                                            activeKeys[note] = false
                                        }
                                    }
                                )
                            },
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Text(
                            text = keyData.label,
                            color = Color.LightGray,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

// Data holder class for black keys placement
data class BlackKeyData(
    val midiOffset: Int,
    val leftNeighborIndex: Int,
    val label: String
)

@Composable
fun WaveformVisualizer(waveType: WaveType, attack: Float, decay: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val path = Path()

        val points = 250
        val cycles = 3f // how many wave cycles to show

        for (i in 0..points) {
            val t = i.toFloat() / points
            val x = t * width

            // Synthesis calculations for envelope visualization shape
            val envelope = if (t < (attack / (attack + decay))) {
                t / (attack / (attack + decay))
            } else {
                1f - (t - (attack / (attack + decay))) / (1f - (attack / (attack + decay)))
            }

            // Wave cycles scaling
            val angle = 2.0 * Math.PI * cycles * t
            val waveVal = when (waveType) {
                WaveType.SINE -> Math.sin(angle)
                WaveType.SQUARE -> Math.signum(Math.sin(angle))
                WaveType.TRIANGLE -> Math.asin(Math.sin(angle)) / (Math.PI / 2.0)
                WaveType.SAWTOOTH -> 2.0 * (t * cycles - Math.floor(0.5 + t * cycles))
            }

            val y = centerY + (waveVal * envelope * (height * 0.35f)).toFloat()

            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        drawPath(
            path = path,
            color = Color(0xFF03DAC6),
            style = Stroke(width = 4.dp.toPx())
        )
    }
}

// Custom PCM Wave synthesis Played over low-level AudioTrack APIs for pure latency-free real-time synthesis
fun playSynthesizedNote(
    midiNote: Int,
    waveType: WaveType,
    attackSec: Float,
    decaySec: Float,
    volume: Float,
    coroutineScope: CoroutineScope
) {
    coroutineScope.launch(Dispatchers.Default) {
        val sampleRate = 44100
        val frequency = 440.0 * Math.pow(2.0, (midiNote - 69) / 12.0)

        val totalDuration = attackSec + decaySec
        val totalSamples = (sampleRate * totalDuration).toInt().coerceAtLeast(100)
        val buffer = ShortArray(totalSamples)

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate

            // Envelope calculations
            val amplitude = if (t < attackSec) {
                t / attackSec
            } else {
                val decayTime = t - attackSec
                Math.max(0.0, 1.0 - (decayTime / decaySec))
            }

            val angle = 2.0 * Math.PI * frequency * t
            val rawValue = when (waveType) {
                WaveType.SINE -> Math.sin(angle)
                WaveType.SQUARE -> Math.signum(Math.sin(angle))
                WaveType.TRIANGLE -> Math.asin(Math.sin(angle)) / (Math.PI / 2.0)
                WaveType.SAWTOOTH -> 2.0 * (t * frequency - Math.floor(0.5 + t * frequency))
            }

            val sample = (rawValue * amplitude * volume * Short.MAX_VALUE).toInt()
            buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        try {
            val minBufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = Math.max(buffer.size * 2, minBufferSize)

            val audioTrack = AudioTrack.Builder()
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
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            audioTrack.write(buffer, 0, buffer.size)
            audioTrack.play()

            // Safe Release after playback finishes
            delay((totalDuration * 1000).toLong() + 100)
            audioTrack.stop()
            audioTrack.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}