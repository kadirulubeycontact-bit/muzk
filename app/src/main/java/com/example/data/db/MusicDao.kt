package com.example.data.db

import androidx.room.*
import com.example.data.model.Track
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {

    // --- TRACK QUERIES ---
    @Query("SELECT * FROM tracks ORDER BY title ASC")
    fun getAllTracksFlow(): Flow<List<Track>>

    @Query("SELECT * FROM tracks")
    suspend fun getAllTracks(): List<Track>

    @Query("SELECT * FROM tracks WHERE id = :id LIMIT 1")
    suspend fun getTrackById(id: String): Track?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTracks(tracks: List<Track>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: Track)

    @Query("UPDATE tracks SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: String, isFavorite: Boolean)

    @Query("UPDATE tracks SET playCount = playCount + 1, lastPlayed = :timestamp WHERE id = :id")
    suspend fun incrementPlayStats(id: String, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM tracks WHERE path NOT LIKE 'synth:%'")
    suspend fun clearCachedMediaStoreTracks()

    // --- FAVORITE QUERIES ---
    @Query("SELECT * FROM tracks WHERE isFavorite = 1 ORDER BY title ASC")
    fun getFavoriteTracksFlow(): Flow<List<Track>>

    // --- RECENTLY PLAYED QUERIES ---
    @Query("SELECT * FROM tracks WHERE playCount > 0 ORDER BY lastPlayed DESC LIMIT 50")
    fun getRecentlyPlayedFlow(): Flow<List<Track>>

    // --- PLAYLIST QUERIES ---
    @Query("SELECT * FROM playlists ORDER BY name ASC")
    fun getAllPlaylistsFlow(): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTrack(playlistTrackCrossRef: PlaylistTrackCrossRef)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: String)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun removeAllTracksFromPlaylist(playlistId: Long)

    @Query("""
        SELECT t.* FROM tracks t 
        INNER JOIN playlist_tracks pt ON t.id = pt.trackId 
        WHERE pt.playlistId = :playlistId 
        ORDER BY pt.addedAt DESC
    """)
    fun getTracksInPlaylistFlow(playlistId: Long): Flow<List<Track>>

    @Query("""
        SELECT t.* FROM tracks t 
        INNER JOIN playlist_tracks pt ON t.id = pt.trackId 
        WHERE pt.playlistId = :playlistId
    """)
    suspend fun getTracksInPlaylist(playlistId: Long): List<Track>
}
