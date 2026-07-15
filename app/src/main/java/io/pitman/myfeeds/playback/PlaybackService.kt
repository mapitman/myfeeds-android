package io.pitman.myfeeds.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import io.pitman.myfeeds.MainActivity
import io.pitman.myfeeds.data.repository.FeedRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Ported from MyFeeds.AudioPlaybackAgent AudioPlayer.cs OnPlayStateChanged: while playing, saves
 * the current position every 5s (the original used 1s against isolated-storage LINQ-to-SQL; DB
 * writes here are cheap enough but 5s is plenty for resume granularity). On pause/stop it saves
 * once more; on completion it clears the saved position and marks the item read, mirroring
 * TrackEnded's `SavePosition(0)` + `SetItemAsRead`.
 */
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject
    lateinit var feedRepository: FeedRepository

    private lateinit var player: ExoPlayer
    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var positionSaveJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this).build()
        player.addListener(playerListener)

        val sessionActivityIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        positionSaveJob?.cancel()
        mediaSession?.let { session ->
            player.release()
            session.release()
            mediaSession = null
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                startPositionSaveLoop()
            } else {
                positionSaveJob?.cancel()
                // Skip the final save on end-of-track: onPlaybackStateChanged's STATE_ENDED
                // branch below already clears the position, and this callback can otherwise fire
                // after it and overwrite the clear with the last (near-duration) position.
                if (player.playbackState != Player.STATE_ENDED) {
                    saveCurrentPosition()
                }
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                val itemId = player.currentMediaItem?.mediaId ?: return
                serviceScope.launch {
                    feedRepository.setEnclosurePosition(itemId, null)
                    feedRepository.markRead(itemId, true)
                }
            }
        }
    }

    private fun startPositionSaveLoop() {
        positionSaveJob?.cancel()
        positionSaveJob = serviceScope.launch {
            while (isActive) {
                saveCurrentPosition()
                delay(5_000)
            }
        }
    }

    private fun saveCurrentPosition() {
        val itemId = player.currentMediaItem?.mediaId ?: return
        val positionSeconds = player.currentPosition / 1000.0
        serviceScope.launch { feedRepository.setEnclosurePosition(itemId, positionSeconds) }
    }
}
