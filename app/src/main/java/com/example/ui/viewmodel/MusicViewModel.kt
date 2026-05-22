package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.MusicDatabase
import com.example.data.model.Track
import com.example.data.repository.MusicRepository
import com.example.player.MusicPlaybackManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class SortOption(val TurkishLabel: String) {
    TITLE_ASC("Alfabetik (A-Z)"),
    ARTIST_ASC("Sanatçıya Göre"),
    DURATION_DESC("Süreye Göre (En Uzun)"),
    MOST_PLAYED_DESC("En Çok Dinlenen"),
    NEWEST_FIRST("En Son Çalınan"),
    OLDEST_FIRST("En Eski Dinlenen"),
    DATE_ADDED_DESC("Tarihe Göre (En Yeni)")
}

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val musicDb = MusicDatabase.getDatabase(application)
    private val musicDao = musicDb.musicDao()
    private val repository = MusicRepository(application, musicDao)
    private val playbackManager = MusicPlaybackManager(application)

    companion object {
        private const val TAG = "MusicViewModel"
    }

    // Connect flow states from playback manager
    val currentTrack: StateFlow<Track?> = playbackManager.currentTrack
    val isPlaying: StateFlow<Boolean> = playbackManager.isPlaying
    val currentPosition: StateFlow<Long> = playbackManager.currentPosition
    val duration: StateFlow<Long> = playbackManager.duration
    val shuffleEnabled: StateFlow<Boolean> = playbackManager.shuffleEnabled
    val repeatMode: StateFlow<Int> = playbackManager.repeatMode
    val playbackQueue: StateFlow<List<Track>> = playbackManager.playbackQueue
    val errorState: StateFlow<String?> = playbackManager.errorState

    // Connected repository flows
    val allTracks: StateFlow<List<Track>> = repository.allTracksFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        
    val favoriteTracks: StateFlow<List<Track>> = repository.favoriteTracksFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentlyPlayed: StateFlow<List<Track>> = repository.recentlyPlayedFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<com.example.data.db.PlaylistEntity>> = repository.playlistsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI state for search and sorting
    val searchQuery = MutableStateFlow("")
    val sortOption = MutableStateFlow(SortOption.TITLE_ASC)

    // Dynamic filtering matching search text & ordering algorithm
    val filteredTracks: StateFlow<List<Track>> = combine(
        allTracks,
        searchQuery,
        sortOption
    ) { tracks, query, sort ->
        val trimmedQuery = query.trim().lowercase()
        val filtered = if (trimmedQuery.isEmpty()) {
            tracks
        } else {
            tracks.filter { track ->
                val folderName = try {
                    java.io.File(track.path).parentFile?.name ?: ""
                } catch (e: Exception) {
                    ""
                }
                track.title.lowercase().contains(trimmedQuery) ||
                track.artist.lowercase().contains(trimmedQuery) ||
                track.album.lowercase().contains(trimmedQuery) ||
                folderName.lowercase().contains(trimmedQuery)
            }
        }

        when (sort) {
            SortOption.TITLE_ASC -> filtered.sortedBy { it.title.lowercase() }
            SortOption.ARTIST_ASC -> filtered.sortedBy { it.artist.lowercase() }
            SortOption.DURATION_DESC -> filtered.sortedByDescending { it.duration }
            SortOption.MOST_PLAYED_DESC -> filtered.sortedByDescending { it.playCount }
            SortOption.NEWEST_FIRST -> filtered.sortedByDescending { it.lastPlayed }
            SortOption.OLDEST_FIRST -> filtered.sortedBy { if (it.lastPlayed == 0L) Long.MAX_VALUE else it.lastPlayed }
            SortOption.DATE_ADDED_DESC -> filtered.sortedByDescending {
                try {
                    it.id.toLong()
                } catch (e: Exception) {
                    0L
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Equalizer UI controller variables
    val equalizerPresets = playbackManager.getEqualizerPresetNames()
    
    private val _bassStrength = MutableStateFlow<Short>(0)
    val bassStrength: StateFlow<Short> = _bassStrength.asStateFlow()

    private val _virtualizerStrength = MutableStateFlow<Short>(0)
    val virtualizerStrength: StateFlow<Short> = _virtualizerStrength.asStateFlow()

    init {
        // Run a default scan for synthesizer files right away so UI isn't blank
        scanMusicFiles(false)

        // Observe currentTrack playback states to increment statistics counter reactively on database record
        viewModelScope.launch {
            currentTrack.collect { track ->
                if (track != null) {
                    repository.recordIncrementTrackPlay(track.id)
                }
            }
        }
    }

    /**
     * Start scan of MediaStore for physical music files.
     */
    fun scanMusicFiles(hasPermission: Boolean = true) {
        viewModelScope.launch {
            Log.d(TAG, "Triggering scan with storage status: $hasPermission")
            repository.scanAndStoreMusicFiles(hasPermission)
        }
    }

    // Playback modifiers
    fun playQueue(tracks: List<Track>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        playbackManager.setQueue(tracks, startIndex)
    }

    fun playSingleTrack(track: Track) {
        playbackManager.playTrack(track)
    }

    fun togglePlayPause() {
        playbackManager.togglePlayPause()
    }

    fun skipToNext() {
        playbackManager.skipToNext()
    }

    fun skipToPrevious() {
        playbackManager.skipToPrevious()
    }

    fun seekTo(positionMs: Long) {
        playbackManager.seekTo(positionMs)
    }

    fun toggleShuffle() {
        playbackManager.toggleShuffle()
    }

    fun toggleRepeatMode() {
        playbackManager.toggleRepeatMode()
    }

    fun playQueueAt(index: Int) {
        playbackManager.playQueueTrackAt(index)
    }

    // Audio effects modifiers
    fun applyEqPreset(index: Short) {
        playbackManager.applyEqualizerPreset(index)
    }

    fun updateBassStrength(value: Short) {
        _bassStrength.value = value
        playbackManager.setBassBoostStrength(value)
    }

    fun updateVirtualizerStrength(value: Short) {
        _virtualizerStrength.value = value
        playbackManager.setVirtualizerStrength(value)
    }

    // Favoriting state tracking
    fun toggleFavorite(track: Track) {
        viewModelScope.launch {
            repository.toggleFavorite(track.id, track.isFavorite)
        }
    }

    // Playlist systems
    fun createPlaylist(name: String) {
        viewModelScope.launch {
            repository.createPlaylist(name)
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            repository.removePlaylist(playlistId)
        }
    }

    fun addTrackToPlaylist(playlistId: Long, trackId: String) {
        viewModelScope.launch {
            repository.addTrackToPlaylist(playlistId, trackId)
        }
    }

    fun removeTrackFromPlaylist(playlistId: Long, trackId: String) {
        viewModelScope.launch {
            repository.removeTrackFromPlaylist(playlistId, trackId)
        }
    }

    fun getTracksInPlaylist(playlistId: Long): Flow<List<Track>> {
        return repository.getTracksInPlaylist(playlistId)
    }

    override fun onCleared() {
        playbackManager.shutdown()
        super.onCleared()
    }
}
