package com.example.data.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.example.data.db.MusicDao
import com.example.data.db.PlaylistEntity
import com.example.data.db.PlaylistTrackCrossRef
import com.example.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class MusicRepository(
    private val context: Context,
    private val musicDao: MusicDao
) {

    companion object {
        private const val TAG = "MusicRepository"

        // Beautiful default synthesizer tracks that run anywhere without storage permission
        val SYNTH_TRACKS = listOf(
            Track(
                id = "synth_cosmic_retro",
                title = "Cosmic Retro Wave",
                artist = "Masiva Synth",
                album = "Space Odyssey",
                duration = 142000, // 2:22
                path = "synth:cosmic_retro",
                artUri = "android.resource://com.example/drawable/ic_launcher_foreground"
            ),
            Track(
                id = "synth_ambient_lofi",
                title = "Ambient Lofi Night",
                artist = "Masiva Synth",
                album = "Dreamscape",
                duration = 180000, // 3:00
                path = "synth:ambient_lofi",
                artUri = null
            ),
            Track(
                id = "synth_neon_pulse",
                title = "Deep Neon Bass",
                artist = "Masiva Pulse",
                album = "Cyber Punk",
                duration = 120000, // 2:00
                path = "synth:neon_pulse",
                artUri = null
            ),
            Track(
                id = "synth_chiptune_echo",
                title = "Chiptune Echoes",
                artist = "8-Bit Hero",
                album = "Arcade Legacy",
                duration = 96000, // 1:36
                path = "synth:chiptune_echo",
                artUri = null
            ),
            Track(
                id = "synth_theta_frequency",
                title = "Theta Wave Meditation",
                artist = "Binaural Mind",
                album = "Deep Sleep",
                duration = 300000, // 5:00
                path = "synth:theta_frequency",
                artUri = null
            )
        )
    }

    // Streams of data directly from Room Database (reactive flows)
    val allTracksFlow: Flow<List<Track>> = musicDao.getAllTracksFlow()
    val favoriteTracksFlow: Flow<List<Track>> = musicDao.getFavoriteTracksFlow()
    val recentlyPlayedFlow: Flow<List<Track>> = musicDao.getRecentlyPlayedFlow()
    val playlistsFlow: Flow<List<PlaylistEntity>> = musicDao.getAllPlaylistsFlow()

    /**
     * Scans MediaStore for local audio tracks, validates them, and caches them in Room.
     * Also guarantees that fallback synthesizer tracks are preloaded.
     */
    suspend fun scanAndStoreMusicFiles(hasStoragePermission: Boolean): Unit = withContext(Dispatchers.IO) {
        try {
            val localTracksList = mutableListOf<Track>()

            if (hasStoragePermission) {
                val contentResolver = context.contentResolver
                val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
                val projection = arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.DATA,
                    MediaStore.Audio.Media.ALBUM_ID
                )

                contentResolver.query(uri, projection, selection, null, null)?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                    val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

                    while (cursor.moveToNext()) {
                        try {
                            val id = cursor.getLong(idCol).toString()
                            val title = cursor.getString(titleCol) ?: "Bilinmeyen Başlık"
                            val artist = cursor.getString(artistCol) ?: "Bilinmeyen Sanatçı"
                            val album = cursor.getString(albumCol) ?: "Bilinmeyen Albüm"
                            val duration = cursor.getLong(durationCol)
                            val path = cursor.getString(dataCol) ?: ""
                            val albumId = cursor.getLong(albumIdCol)

                            if (path.isNotEmpty() && duration > 500) { // filter out ultra short utility system sounds
                                val albumArtUri = Uri.parse("content://media/external/audio/albumart")
                                val trackArtUri = ContentUris.withAppendedId(albumArtUri, albumId).toString()

                                localTracksList.add(
                                    Track(
                                        id = id,
                                        title = title,
                                        artist = artist,
                                        album = album,
                                        duration = duration,
                                        path = path,
                                        artUri = trackArtUri
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed reading track from cursor position", e)
                        }
                    }
                }
            }

            // Sync with DB
            // 1. Clear old media store cache to reflect actual files on device (without removing synth files or modified stats!)
            val existingInDb = musicDao.getAllTracks().associateBy { it.id }
            musicDao.clearCachedMediaStoreTracks()

            // 2. Re-insert scan results merged with favorites and history states
            val tracksToSave = mutableListOf<Track>()

            // Insert matching scanned local tracks preserving isFavorite and play count
            localTracksList.forEach { local ->
                val cached = existingInDb[local.id]
                tracksToSave.add(
                    local.copy(
                        isFavorite = cached?.isFavorite ?: false,
                        playCount = cached?.playCount ?: 0,
                        lastPlayed = cached?.lastPlayed ?: 0
                    )
                )
            }

            // Always add the synthesizer fallback files so the app remains interactive!
            SYNTH_TRACKS.forEach { synth ->
                val cached = existingInDb[synth.id]
                tracksToSave.add(
                    synth.copy(
                        isFavorite = cached?.isFavorite ?: false,
                        playCount = cached?.playCount ?: 0,
                        lastPlayed = cached?.lastPlayed ?: 0
                    )
                )
            }

            musicDao.insertTracks(tracksToSave)
            Log.d(TAG, "Successfully synced ${tracksToSave.size} total tracks to DB.")

        } catch (e: Exception) {
            Log.e(TAG, "Error in scanAndStoreMusicFiles", e)
        }
    }

    // Track state modifications
    suspend fun toggleFavorite(trackId: String, currentStatus: Boolean) {
        musicDao.updateFavorite(trackId, !currentStatus)
    }

    suspend fun recordIncrementTrackPlay(trackId: String) {
        musicDao.incrementPlayStats(trackId)
    }

    // Playlist CRUD operations
    suspend fun createPlaylist(name: String): Long {
        if (name.isBlank()) return -1
        return musicDao.insertPlaylist(PlaylistEntity(name = name.trim()))
    }

    suspend fun removePlaylist(id: Long) {
        musicDao.deletePlaylist(id)
        musicDao.removeAllTracksFromPlaylist(id)
    }

    suspend fun addTrackToPlaylist(playlistId: Long, trackId: String) {
        musicDao.insertPlaylistTrack(PlaylistTrackCrossRef(playlistId = playlistId, trackId = trackId))
    }

    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: String) {
        musicDao.removeTrackFromPlaylist(playlistId, trackId)
    }

    fun getTracksInPlaylist(playlistId: Long): Flow<List<Track>> {
        return musicDao.getTracksInPlaylistFlow(playlistId)
    }

    suspend fun getTracksInPlaylistSnapshot(playlistId: Long): List<Track> {
        return musicDao.getTracksInPlaylist(playlistId)
    }

    suspend fun getTrackById(trackId: String): Track? {
        return musicDao.getTrackById(trackId)
    }
}
