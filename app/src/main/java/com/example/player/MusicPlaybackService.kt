package com.example.player

import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class MusicPlaybackService : MediaSessionService() {

    companion object {
        private const val TAG = "MusicPlaybackService"
    }

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        try {
            val player = ExoPlayer.Builder(this).build()
            mediaSession = MediaSession.Builder(this, player).build()
            Log.d(TAG, "Background MediaSessionService created successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing background MediaSession", e)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onBind(intent: Intent?): IBinder? {
        Log.d(TAG, "Binding music service.")
        return super.onBind(intent)
    }

    override fun onDestroy() {
        try {
            mediaSession?.run {
                player.release()
                release()
                mediaSession = null
            }
            Log.d(TAG, "Background MediaSessionService destroyed cleanly.")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy", e)
        }
        super.onDestroy()
    }
}
