package com.example.player

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.data.model.Track
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class MusicPlaybackManager(private val context: Context) {

    companion object {
        private const val TAG = "PlaybackManager"
        const val REPEAT_MODE_NONE = 0
        const val REPEAT_MODE_ALL = 1
        const val REPEAT_MODE_ONE = 2
    }

    private val exoPlayer: ExoPlayer by lazy {
        ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    _duration.value = duration.coerceAtLeast(0)
                    if (state == Player.STATE_ENDED) {
                        handlePlaybackEnded()
                    }
                }

                override fun onIsPlayingChanged(playing: Boolean) {
                    _isPlaying.value = playing
                    if (playing) {
                        startProgressTicker()
                    } else {
                        stopProgressTicker()
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.e(TAG, "ExoPlayer raised error on playback", error)
                    _errorState.value = "Şarkı oynatılamadı. Dosya silinmiş veya bozuk olabilir."
                    // Safeguard: Move to next track automatically to prevent infinite crash / loading loop!
                    handleFilePlaybackFailure()
                }
            })
        }
    }

    private val synthPlayer = ProgrammaticSynthesizer()

    // Active state flows
    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(REPEAT_MODE_NONE)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _playbackQueue = MutableStateFlow<List<Track>>(emptyList())
    val playbackQueue: StateFlow<List<Track>> = _playbackQueue.asStateFlow()

    private val _errorState = MutableStateFlow<String?>(null)
    val errorState: StateFlow<String?> = _errorState.asStateFlow()

    private var currentTrackIndex = -1
    private var originalQueue = listOf<Track>() // to recover when shuffle turns off

    // Threading
    private var progressJob: Job? = null
    private val managerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Equalizer, Bass Boost, & Virtualizer hardware components
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null

    init {
        initAudioEffects()
    }

    private fun initAudioEffects() {
        try {
            val audioSessionId = exoPlayer.audioSessionId
            if (audioSessionId != 0) {
                equalizer = Equalizer(0, audioSessionId).apply { enabled = true }
                bassBoost = BassBoost(0, audioSessionId).apply { enabled = true }
                virtualizer = Virtualizer(0, audioSessionId).apply { enabled = true }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Hardware audio effects initialization failed", e)
        }
    }

    fun setQueue(tracks: List<Track>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        originalQueue = tracks.toList()
        
        if (_shuffleEnabled.value) {
            val current = tracks[startIndex]
            val mutable = tracks.toMutableList()
            mutable.removeAt(startIndex)
            mutable.shuffle()
            // Keep current playing at top
            val shuffled = mutableListOf(current)
            shuffled.addAll(mutable)
            _playbackQueue.value = shuffled
            currentTrackIndex = 0
        } else {
            _playbackQueue.value = originalQueue
            currentTrackIndex = startIndex.coerceIn(0, originalQueue.lastIndex)
        }

        playTrack(_playbackQueue.value[currentTrackIndex])
    }

    fun playTrack(track: Track) {
        // Reset old tracks
        stopPlayers()

        _currentTrack.value = track
        _errorState.value = null

        if (track.isSynthTrack) {
            _duration.value = track.duration
            _isPlaying.value = true
            
            synthPlayer.start(track.path) { pos ->
                _currentPosition.value = pos
                if (pos >= track.duration) {
                    handlePlaybackEnded()
                }
            }
        } else {
            // Physical file check prior to sending items to media stream to block blank app terminations
            val file = File(track.path)
            if (!file.exists()) {
                Log.e(TAG, "File not found at physical disk location: ${track.path}")
                _errorState.value = "Dosya bulunamadı: ${track.title}"
                handleFilePlaybackFailure()
                return
            }

            try {
                val mediaItem = MediaItem.fromUri(Uri.fromFile(file))
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                exoPlayer.play()
                // Duration will emit on ExoPlayer state updates
            } catch (e: Exception) {
                Log.e(TAG, "Failed launching exoplayer for URI", e)
                _errorState.value = "Dosya oynatılamadı."
                handleFilePlaybackFailure()
            }
        }
    }

    fun togglePlayPause() {
        val track = _currentTrack.value ?: return
        val currentlyPlaying = _isPlaying.value

        if (currentlyPlaying) {
            if (track.isSynthTrack) {
                synthPlayer.pause()
            } else {
                exoPlayer.pause()
            }
            _isPlaying.value = false
            stopProgressTicker()
        } else {
            if (track.isSynthTrack) {
                synthPlayer.resume()
                _isPlaying.value = true
            } else {
                exoPlayer.play()
                _isPlaying.value = true
                startProgressTicker()
            }
        }
    }

    fun skipToNext() {
        val queue = _playbackQueue.value
        if (queue.isEmpty()) return

        currentTrackIndex = (currentTrackIndex + 1) % queue.size
        playTrack(queue[currentTrackIndex])
    }

    fun skipToPrevious() {
        val queue = _playbackQueue.value
        if (queue.isEmpty()) return

        currentTrackIndex = if (currentTrackIndex - 1 < 0) {
            queue.lastIndex
        } else {
            currentTrackIndex - 1
        }
        playTrack(queue[currentTrackIndex])
    }

    fun seekTo(positionMs: Long) {
        val track = _currentTrack.value ?: return
        val boundPosition = positionMs.coerceIn(0L, _duration.value)

        if (track.isSynthTrack) {
            synthPlayer.seekTo(boundPosition)
            _currentPosition.value = boundPosition
        } else {
            exoPlayer.seekTo(boundPosition)
            _currentPosition.value = boundPosition
        }
    }

    fun toggleShuffle() {
        val newValue = !_shuffleEnabled.value
        _shuffleEnabled.value = newValue

        val current = _currentTrack.value
        val queue = _playbackQueue.value

        if (newValue && queue.isNotEmpty()) {
            val currentInQueue = current ?: queue[currentTrackIndex]
            val mutable = queue.toMutableList()
            mutable.remove(currentInQueue)
            mutable.shuffle()
            val newQueue = mutableListOf(currentInQueue)
            newQueue.addAll(mutable)
            _playbackQueue.value = newQueue
            currentTrackIndex = 0
        } else if (!newValue) {
            // Restore regular queue order
            val index = originalQueue.indexOf(current)
            _playbackQueue.value = originalQueue
            if (index != -1) {
                currentTrackIndex = index
            }
        }
    }

    fun toggleRepeatMode() {
        _repeatMode.value = when (_repeatMode.value) {
            REPEAT_MODE_NONE -> REPEAT_MODE_ALL
            REPEAT_MODE_ALL -> REPEAT_MODE_ONE
            else -> REPEAT_MODE_NONE
        }
    }

    fun playQueueTrackAt(index: Int) {
        val queue = _playbackQueue.value
        if (index in queue.indices) {
            currentTrackIndex = index
            playTrack(queue[index])
        }
    }

    // --- EFFECT API WRAPPERS ---

    fun getEqualizerPresetNames(): List<String> {
        val result = mutableListOf<String>()
        equalizer?.let { eq ->
            try {
                for (i in 0 until eq.numberOfPresets) {
                    result.add(eq.getPresetName(i.toShort()) ?: "Preset $i")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed query preset sizes", e)
            }
        }
        if (result.isEmpty()) {
            result.addAll(listOf("Düz (Flat)", "Bass Boost", "Klasik", "Pop", "Rock", "Akustik"))
        }
        return result
    }

    fun applyEqualizerPreset(presetIndex: Short) {
        try {
            equalizer?.usePreset(presetIndex)
        } catch (e: Exception) {
            Log.e(TAG, "Failed applying custom preset", e)
        }
    }

    fun setBassBoostStrength(strength: Short) {
        try {
            if (bassBoost?.strengthSupported == true) {
                bassBoost?.setStrength(strength)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed setting bass boost", e)
        }
    }

    fun setVirtualizerStrength(strength: Short) {
        try {
            if (virtualizer?.strengthSupported == true) {
                virtualizer?.setStrength(strength)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed setting virtualizer", e)
        }
    }

    fun getEqualizerBandColors(): List<Short> {
        val result = mutableListOf<Short>()
        equalizer?.let { eq ->
            try {
                for (b in 0 until eq.numberOfBands) {
                    result.add(eq.getBandLevel(b.toShort()))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching bands", e)
            }
        }
        return result
    }

    fun setEqualizerBandLevel(band: Short, level: Short) {
        try {
            equalizer?.setBandLevel(band, level)
        } catch (e: Exception) {
            Log.e(TAG, "Error writing custom EQ band", e)
        }
    }

    // --- INTERNAL SYSTEMS ---

    private fun handlePlaybackEnded() {
        val currentRepeat = _repeatMode.value
        val queue = _playbackQueue.value

        if (queue.isEmpty()) return

        when (currentRepeat) {
            REPEAT_MODE_ONE -> {
                // Loop same item
                _currentTrack.value?.let { playTrack(it) }
            }
            REPEAT_MODE_ALL -> {
                // Advance or loop index around queue boundary
                currentTrackIndex = (currentTrackIndex + 1) % queue.size
                playTrack(queue[currentTrackIndex])
            }
            REPEAT_MODE_NONE -> {
                if (currentTrackIndex < queue.lastIndex) {
                    currentTrackIndex++
                    playTrack(queue[currentTrackIndex])
                } else {
                    // Queue finished naturally
                    _isPlaying.value = false
                    stopPlayers()
                }
            }
        }
    }

    private fun handleFilePlaybackFailure() {
        // Prevent looping infinite playback errors: skip to next file if possible
        val queue = _playbackQueue.value
        if (queue.size > 1) {
            Handler(Looper.getMainLooper()).postDelayed({
                skipToNext()
            }, 1500)
        } else {
            _isPlaying.value = false
            stopPlayers()
        }
    }

    private fun startProgressTicker() {
        stopProgressTicker()
        progressJob = managerScope.launch {
            while (isActive) {
                _currentPosition.value = exoPlayer.currentPosition
                delay(150)
            }
        }
    }

    private fun stopProgressTicker() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun stopPlayers() {
        stopProgressTicker()
        try {
            exoPlayer.stop()
        } catch (e: Exception) {
            Log.e(TAG, "ExoPlayer stop failure", e)
        }
        try {
            synthPlayer.stop()
        } catch (e: Exception) {
            Log.e(TAG, "SynthPlayer stop failure", e)
        }
        _isPlaying.value = false
        _currentPosition.value = 0L
    }

    fun shutdown() {
        stopPlayers()
        managerScope.cancel()
        try {
            exoPlayer.release()
        } catch (e: Exception) {
            Log.e(TAG, "ExoPlayer release failed", e)
        }
        try {
            equalizer?.release()
            bassBoost?.release()
            virtualizer?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Effects release failed", e)
        }
    }
}
