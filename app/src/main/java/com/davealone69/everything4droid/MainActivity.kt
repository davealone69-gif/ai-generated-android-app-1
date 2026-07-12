package com.davealone69.everything4droid

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MainAppScreen()
        }
    }
}

enum class Waveform {
    SINE, TRIANGLE, SQUARE, SAWTOOTH
}

class AudioSynthesizer {
    private val sampleRate = 22050

    fun playTone(frequency: Double, waveform: Waveform, sustain: Float, volume: Float) {
        Thread {
            val durationMs = (sustain * 400).toInt().coerceIn(150, 3000)
            val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
            val generatedSnd = ShortArray(numSamples)
            val decayFactor = 5.0 / sustain
            val attackTime = 0.005 // 5ms smooth attack

            for (i in 0 until numSamples) {
                val t = i.toDouble() / sampleRate
                
                // Attack & Decay Envelope
                val envelope = if (t < attackTime) {
                    t / attackTime
                } else {
                    exp(-decayFactor * (t - attackTime))
                }

                val angle = 2 * PI * frequency * t
                val rawSample = when (waveform) {
                    Waveform.SINE -> {
                        sin(angle) + 0.3 * sin(2 * angle) + 0.1 * sin(3 * angle)
                    }
                    Waveform.TRIANGLE -> {
                        val cycle = t * frequency
                        2.0 * abs(2.0 * (cycle - floor(cycle + 0.5))) - 1.0
                    }
                    Waveform.SQUARE -> {
                        if (sin(angle) >= 0.0) 0.4 else -0.4
                    }
                    Waveform.SAWTOOTH -> {
                        val cycle = t * frequency
                        2.0 * (cycle - floor(cycle + 0.5))
                    }
                }

                val value = rawSample * envelope * volume
                val valShort = (value * 32767).toInt().coerceIn(-32768, 32767)
                generatedSnd[i] = valShort.toShort()
            }

            try {
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
                    .setBufferSizeInBytes(generatedSnd.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                audioTrack.write(generatedSnd, 0, generatedSnd.size)
                audioTrack.play()
                Thread.sleep(durationMs.toLong() + 30)
                audioTrack.stop()
                audioTrack.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }
}

data class WhiteKey(val name: String, val freq: Double, val index: Int)
data class BlackKey(val name: String, val freq: Double, val afterWhiteIndex: Int)

@Composable
fun MainAppScreen() {
    val synth = remember { AudioSynthesizer() }
    val coroutineScope = rememberCoroutineScope()

    var waveform by remember { mutableStateOf(Waveform.SINE) }
    var sustain by remember { mutableFloatStateOf(3.0f) }
    var volume by remember { mutableFloatStateOf(0.7f) }
    var lastPlayedNote by remember { mutableStateOf("Tap keys to play") }

    val activePlayingNotes = remember { mutableStateMapOf<String, Boolean>() }
    var isPlayingDemo by remember { mutableStateOf(false) }

    val whiteKeys = listOf(
        WhiteKey("C4", 261.63, 0),
        WhiteKey("D4", 293.66, 1),
        WhiteKey("E4", 329.63, 2),
        WhiteKey("F4", 349.23, 3),
        WhiteKey("G4", 392.00, 4),
        WhiteKey("A4", 440.00, 5),
        WhiteKey("B4", 493.88, 6),
        WhiteKey("C5", 523.25, 7),
        WhiteKey("D5", 587.33, 8),
        WhiteKey("E5", 659.25, 9),
        WhiteKey("F5", 698.46, 10),
        WhiteKey("G5", 783.99, 11),
        WhiteKey("A5", 880.00, 12),
        WhiteKey("B5", 987.77, 13),
        WhiteKey("C6", 1046.50, 14)
    )

    val blackKeys = listOf(
        BlackKey("C#4", 277.18, 0),
        BlackKey("D#4", 311.13, 1),
        BlackKey("F#4", 369.99, 3),
        BlackKey("G#4", 415.30, 4),
        BlackKey("A#4", 466.16, 5),
        BlackKey("C#5", 554.37, 7),
        BlackKey("D#5", 622.25, 8),
        BlackKey("F#5", 739.99, 10),
        BlackKey("G#5", 830.61, 11),
        BlackKey("A#5", 932.33, 12)
    )

    fun triggerTone(noteName: String, frequency: Double) {
        lastPlayedNote = noteName
        activePlayingNotes[noteName] = true
        synth.playTone(frequency, waveform, sustain, volume)
        coroutineScope.launch {
            delay(280)
            activePlayingNotes[noteName] = false
        }
    }

    fun playDemoSong() {
        if (isPlayingDemo) return
        isPlayingDemo = true
        coroutineScope.launch {
            // Ode to Joy theme
            val demoNotes = listOf(
                Pair("E5", 659.25), Pair("E5", 659.25), Pair("F5", 698.46), Pair("G5", 783.99),
                Pair("G5", 783.99), Pair("F5", 698.46), Pair("E5", 659.25), Pair("D5", 587.33),
                Pair("C5", 523.25), Pair("C5", 523.25), Pair("D5", 587.33), Pair("E5", 659.25),
                Pair("E5", 659.25), Pair("D5", 587.33), Pair("D5", 587.33)
            )
            for (note in demoNotes) {
                triggerTone(note.first, note.second)
                delay(350)
            }
            isPlayingDemo = false
        }
    }

    // Interactive visualization phase animator
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2 * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF121214)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header & Visualizer Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E24)),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.35f)
                    .padding(bottom = 8.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "SYNTH PIANO",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE2E2E9),
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "Current Note: $lastPlayedNote",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF29B6F6),
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Button(
                            onClick = { playDemoSong() },
                            enabled = !isPlayingDemo,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF00E676),
                                disabledContainerColor = Color(0xFF1B5E20)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play Demo",
                                tint = Color.Black
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Demo Song", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Live Oscilloscope Visualizer
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0D0D11))
                            .border(1.dp, Color(0xFF33333F), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val width = size.width
                            val height = size.height
                            val middleY = height / 2
                            val path = Path()

                            path.moveTo(0f, middleY)
                            for (x in 0..width.toInt() step 4) {
                                val normalizedX = x / width
                                val waveValue = when (waveform) {
                                    Waveform.SINE -> {
                                        sin(normalizedX * 4 * PI + wavePhase)
                                    }
                                    Waveform.TRIANGLE -> {
                                        val cycle = normalizedX * 2 + (wavePhase / (2 * PI))
                                        2.0 * abs(2.0 * (cycle - floor(cycle + 0.5))) - 1.0
                                    }
                                    Waveform.SQUARE -> {
                                        if (sin(normalizedX * 4 * PI + wavePhase) >= 0.0) 0.6 else -0.6
                                    }
                                    Waveform.SAWTOOTH -> {
                                        val cycle = normalizedX * 2 + (wavePhase / (2 * PI))
                                        2.0 * (cycle - floor(cycle + 0.5))
                                    }
                                }
                                val activeMultiplier = if (activePlayingNotes.values.any { it }) 1.0 else 0.15
                                val y = middleY + (waveValue * (height * 0.4) * activeMultiplier).toFloat()
                                path.lineTo(x.toFloat(), y)
                            }

                            drawPath(
                                path = path,
                                color = if (activePlayingNotes.values.any { it }) Color(0xFF00E676) else Color(0xFF29B6F6),
                                style = Stroke(width = 3.dp.toPx())
                            )
                        }
                    }

                    // Waveform Selector Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Waveform.values().forEach { type ->
                            val isSelected = waveform == type
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 2.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) Color(0xFF29B6F6) else Color(0xFF2C2C35))
                                    .clickable { waveform = type }
                                    .padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = type.name,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.Black else Color.White
                                )
                            }
                        }
                    }
                }
            }

            // Synthesizer Settings Panel
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E24)),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.25f)
                    .padding(bottom = 12.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    // Sustain Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SUSTAIN",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF8E8E9F),
                            modifier = Modifier.width(64.dp)
                        )
                        Slider(
                            value = sustain,
                            onValueChange = { sustain = it },
                            valueRange = 1.0f..6.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF00E676),
                                activeTrackColor = Color(0xFF00E676)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = String.format("%.1fs", sustain * 0.4),
                            fontSize = 12.sp,
                            color = Color.White,
                            modifier = Modifier.width(40.dp)
                        )
                    }

                    // Volume Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "VOLUME",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF8E8E9F),
                            modifier = Modifier.width(64.dp)
                        )
                        Slider(
                            value = volume,
                            onValueChange = { volume = it },
                            valueRange = 0.1f..1.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF29B6F6),
                                activeTrackColor = Color(0xFF29B6F6)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${(volume * 100).toInt()}%",
                            fontSize = 12.sp,
                            color = Color.White,
                            modifier = Modifier.width(40.dp)
                        )
                    }
                }
            }

            // Piano Keyboard Section
            val keyboardScrollState = rememberScrollState()
            val whiteKeyWidth = 64.dp
            val whiteKeyHeight = 220.dp
            val blackKeyWidth = 38.dp
            val blackKeyHeight = 130.dp

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.4f)
                    .horizontalScroll(keyboardScrollState)
            ) {
                // White Keys
                Row(
                    modifier = Modifier.fillMaxHeight()
                ) {
                    whiteKeys.forEach { key ->
                        val isActive = activePlayingNotes[key.name] == true
                        val keyBg = if (isActive) {
                            Brush.verticalGradient(listOf(Color(0xFF80DEEA), Color(0xFF00ACC1)))
                        } else {
                            Brush.verticalGradient(listOf(Color(0xFFFAFAFA), Color(0xFFE0E0E0)))
                        }

                        Box(
                            modifier = Modifier
                                .width(whiteKeyWidth)
                                .fillMaxHeight()
                                .padding(horizontal = 1.dp)
                                .clip(RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp))
                                .background(keyBg)
                                .clickable { triggerTone(key.name, key.freq) },
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Text(
                                text = key.name,
                                color = Color.DarkGray,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        }
                    }
                }

                // Black Keys overlays
                blackKeys.forEach { key ->
                    val isActive = activePlayingNotes[key.name] == true
                    val keyBg = if (isActive) {
                        Brush.verticalGradient(listOf(Color(0xFFFF7043), Color(0xFFD84315)))
                    } else {
                        Brush.verticalGradient(listOf(Color(0xFF424242), Color(0xFF151515)))
                    }

                    // Calculate horizontal offset
                    val leftOffset = (key.afterWhiteIndex + 1) * whiteKeyWidth.value - (blackKeyWidth.value / 2)

                    Box(
                        modifier = Modifier
                            .offset(x = leftOffset.dp)
                            .width(blackKeyWidth)
                            .height(blackKeyHeight)
                            .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                            .background(keyBg)
                            .border(1.dp, Color.Black, RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                            .clickable { triggerTone(key.name, key.freq) },
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Text(
                            text = key.name,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 8.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }
            }
        }
    }
}