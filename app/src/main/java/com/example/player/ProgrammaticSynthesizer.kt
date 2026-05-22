package com.example.player

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.*
import kotlin.math.sin

class ProgrammaticSynthesizer {

    companion object {
        private const val TAG = "ProgSynthesizer"
        private const val SAMPLE_RATE = 22050 // budget friendly stable sample rate
    }

    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    private var isPaused = false
    private var synthJob: Job? = null
    private val synthScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Frequencies (Hz) for notes
    private val C4 = 261.63f
    private val D4 = 293.66f
    private val E4 = 329.63f
    private val F4 = 349.23f
    private val G4 = 392.00f
    private val A4 = 440.00f
    private val B4 = 493.88f
    private val C5 = 523.25f
    private val E5 = 659.25f
    private val G5 = 783.99f

    // Sequences
    private val retroWaveMelody = listOf(A4, C5, E5, G5, E5, C5, A4, G4)
    private val lofiMelody = listOf(C4, E4, G4, B4, A4, F4, G4, E4)
    private val neonPulseMelody = listOf(D4, D4, D4, F4, G4, G4, C5, A4)
    private val chiptuneMelody = listOf(E5, G5, C5, E5, G5, E5, C5, G4)
    private val thetaMelody = listOf(100.0f, 104.3f, 100.0f, 106.0f) // Binaural sleep beat

    private var currentPositionMs = 0L
    private var trackLengthMs = 180000L // default length

    fun start(synthId: String, onPositionChanged: (Long) -> Unit) {
        stop()
        isPlaying = true
        isPaused = false

        trackLengthMs = when (synthId) {
            "synth:cosmic_retro" -> 142000L
            "synth:ambient_lofi" -> 180000L
            "synth:neon_pulse" -> 120000L
            "synth:chiptune_echo" -> 96000L
            "synth:theta_frequency" -> 300000L
            else -> 120000L
        }

        synthJob = synthScope.launch {
            try {
                val minBufferSize = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                
                // Safety clamp buffer size
                val bufferSize = if (minBufferSize > 0) minBufferSize * 2 else 4096

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
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                audioTrack?.play()

                val buffer = ShortArray(1024)
                var phase = 0.0
                var noteIndex = 0
                var samplesPerNote = SAMPLE_RATE / 2 // half a second per note
                var sampleCounter = 0

                val melody = when (synthId) {
                    "synth:cosmic_retro" -> retroWaveMelody
                    "synth:ambient_lofi" -> lofiMelody
                    "synth:neon_pulse" -> neonPulseMelody
                    "synth:chiptune_echo" -> chiptuneMelody
                    else -> thetaMelody
                }

                while (isPlaying && currentPositionMs < trackLengthMs) {
                    if (isPaused) {
                        delay(50)
                        continue
                    }

                    val currentFreq = melody[noteIndex % melody.size]
                    val isChiptune = (synthId == "synth:chiptune_echo")
                    val isRetro = (synthId == "synth:cosmic_retro")

                    for (i in buffer.indices) {
                        val t = phase / SAMPLE_RATE
                        
                        var sampleVal = 0.0
                        if (synthId == "synth:theta_frequency") {
                            // Binaural beats: slow modulation
                            sampleVal = sin(2.0 * Math.PI * currentFreq * t) * 0.4
                        } else if (isChiptune) {
                            // Square wave for retro 8bit crunch (either premium high or low)
                            val cycle = sin(2.0 * Math.PI * currentFreq * t)
                            sampleVal = if (cycle > 0) 0.15 else -0.15
                        } else if (isRetro) {
                            // Sawtooth wave for cosmic fat analog lead
                            val period = 1.0 / currentFreq
                            val progress = (t % period) / period
                            sampleVal = (2.0 * progress - 1.0) * 0.22
                        } else {
                            // Pure sine chord wave (relaxed)
                            sampleVal = sin(2.0 * Math.PI * currentFreq * t) * 0.35
                            // Add sub-octave
                            sampleVal += sin(2.0 * Math.PI * (currentFreq / 2f) * t) * 0.15
                        }

                        // Apply soft envelope decay to avoid clicks
                        val noteProgress = (sampleCounter % samplesPerNote).toFloat() / samplesPerNote
                        val envelope = if (noteProgress < 0.05f) {
                            noteProgress / 0.05f // quick attack
                        } else {
                            1.0f - noteProgress // decay to zero
                        }

                        buffer[i] = (sampleVal * envelope * Short.MAX_VALUE).toInt().toShort()

                        phase += 1.0
                        sampleCounter++
                        if (sampleCounter >= samplesPerNote) {
                            noteIndex++
                            sampleCounter = 0
                        }
                    }

                    audioTrack?.write(buffer, 0, buffer.size)

                    // Increment timing
                    val elapsedMs = (buffer.size.toFloat() / SAMPLE_RATE * 1000).toLong()
                    currentPositionMs += elapsedMs
                    withContext(Dispatchers.Main) {
                        onPositionChanged(currentPositionMs.coerceAtMost(trackLengthMs))
                    }
                }

                // If track ends naturally, loop or trigger finish
                if (currentPositionMs >= trackLengthMs) {
                    currentPositionMs = 0
                }

            } catch (e: Exception) {
                Log.e(TAG, "Synth rendering thread crashed/interrupted", e)
            } finally {
                releaseTrack()
            }
        }
    }

    fun pause() {
        if (isPlaying) {
            isPaused = true
            try {
                audioTrack?.pause()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to pause AudioTrack", e)
            }
        }
    }

    fun resume() {
        if (isPlaying) {
            isPaused = false
            try {
                audioTrack?.play()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to resume AudioTrack", e)
            }
        }
    }

    fun seekTo(positionMs: Long) {
        currentPositionMs = positionMs.coerceIn(0L, trackLengthMs)
    }

    fun stop() {
        isPlaying = false
        isPaused = false
        synthJob?.cancel()
        synthJob = null
        currentPositionMs = 0L
        releaseTrack()
    }

    private fun releaseTrack() {
        try {
            audioTrack?.apply {
                if (state == AudioTrack.STATE_INITIALIZED) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing audio track", e)
        } finally {
            audioTrack = null
        }
    }
}
