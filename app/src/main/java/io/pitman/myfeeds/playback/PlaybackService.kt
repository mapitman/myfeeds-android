package io.pitman.myfeeds.playback

import android.app.PendingIntent
import android.content.Intent
import android.media.audiofx.LoudnessEnhancer
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import io.pitman.myfeeds.MainActivity
import io.pitman.myfeeds.R
import io.pitman.myfeeds.data.repository.FeedRepository
import io.pitman.myfeeds.data.repository.QueueRepository
import io.pitman.myfeeds.data.settings.SettingsDataStore
import io.pitman.myfeeds.download.EnclosureDownloadRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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

    @Inject
    lateinit var downloadRepository: EnclosureDownloadRepository

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    @Inject
    lateinit var queueRepository: QueueRepository

    private lateinit var player: ExoPlayer
    private var mediaSession: MediaSession? = null

    // Boosts loudness beyond ExoPlayer's unity-gain volume cap (issue #199), keyed to the
    // player's audio session ID which is fixed for the lifetime of this ExoPlayer instance. Some
    // devices/OEMs don't ship the underlying effect, so creation is best-effort.
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var positionSaveJob: Job? = null

    // Guards against onPlaybackStateChanged(STATE_ENDED) firing again (issue #125/#127's original
    // failure mode, now guarded here instead of PlaybackController -- issue #179) before the
    // coroutine handling the first call has advanced the player off STATE_ENDED.
    private var advancingFromEnded = false

    @OptIn(markerClass = [UnstableApi::class])
    override fun onCreate() {
        super.onCreate()
        // ExoPlayer.Builder defaults to *not* managing audio focus at all, so without this,
        // playback talks straight over things like a navigation app's turn-by-turn announcements
        // instead of pausing for them and resuming after (issue #180). AudioAttributes.DEFAULT's
        // content type is AUDIO_CONTENT_TYPE_UNKNOWN, under which Media3's AudioFocusManager only
        // ducks (lowers volume) rather than pauses on a transient "may duck" focus loss -- the
        // request type nav apps use for spoken prompts -- since its willPauseWhenDucked() only
        // returns true for AUDIO_CONTENT_TYPE_SPEECH. Podcast speech talking under a nav prompt at
        // reduced volume is still unintelligible, so mark the content as speech to get an outright
        // pause/resume instead of a duck.
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                /* handleAudioFocus= */ true,
            )
            .build()
        player.addListener(playerListener)
        loudnessEnhancer = runCatching { LoudnessEnhancer(player.audioSessionId) }.getOrNull()

        // Without an explicit small icon, DefaultMediaNotificationProvider falls back to the
        // app's adaptive launcher icon (android.R.attr.icon), which the system can't flatten
        // into the monochrome silhouette a notification/lock-screen icon needs, so it renders
        // blank. Reuse the same pre-flattened monochrome icon FeedRefreshWorker already uses for
        // its notifications. (issue #116)
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply {
                setSmallIcon(R.drawable.ic_notification)
            },
        )

        val sessionActivityIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityIntent)
            .setCallback(mediaSessionCallback)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        positionSaveJob?.cancel()
        loudnessEnhancer?.release()
        loudnessEnhancer = null
        mediaSession?.let { session ->
            player.release()
            session.release()
            mediaSession = null
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    /** Grants [CUSTOM_COMMAND_SET_VOLUME_BOOST] to controllers (issue #202) and applies it live to
     *  [loudnessEnhancer], since it's not a standard [MediaSession]/[Player] command. */
    private val mediaSessionCallback = object : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                .add(SessionCommand(CUSTOM_COMMAND_SET_VOLUME_BOOST, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.accept(sessionCommands, MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS)
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == CUSTOM_COMMAND_SET_VOLUME_BOOST) {
                applyVolumeBoost(args.getInt(EXTRA_VOLUME_BOOST_MILLIBELS, 0))
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val millibels = mediaItem?.mediaMetadata?.extras?.getInt(VOLUME_BOOST_EXTRA_KEY, 0) ?: 0
            applyVolumeBoost(millibels)
        }

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
            if (playbackState != Player.STATE_ENDED || advancingFromEnded) return
            val itemId = player.currentMediaItem?.mediaId ?: return
            advancingFromEnded = true
            serviceScope.launch {
                try {
                    feedRepository.setEnclosurePosition(itemId, null)
                    feedRepository.markRead(itemId, true)
                    // Storage cap / auto-cleanup (issue #71): only ever deletes an episode that
                    // just finished playing in full, never one still in progress or unplayed.
                    if (settingsDataStore.settings.first().autoDeleteFinishedDownloads) {
                        feedRepository.getItem(itemId)
                            ?.takeIf { it.downloadedFilePath != null }
                            ?.let { downloadRepository.deleteDownload(it) }
                    }
                    settingsDataStore.setLastPlayingItem(null, null)
                    playNextQueued()
                } finally {
                    advancingFromEnded = false
                }
            }
        }
    }

    /**
     * Auto-advances to whatever's next in Next Up when an episode finishes (issue #106), acting
     * directly on [player] rather than through a [androidx.media3.session.MediaController] --
     * issue #179: this has to keep working even with no UI/MediaController attached (backgrounded
     * or screen off), and this service is the one guaranteed to still be running when that happens.
     */
    private suspend fun playNextQueued() {
        val itemId = queueRepository.popNext()
        if (itemId == null) {
            player.clearMediaItems()
            return
        }
        val item = feedRepository.getItem(itemId) ?: return
        val feed = feedRepository.getFeed(item.feedId)
        val resolved = PlaybackMediaItemFactory.resolve(item, feed?.userTitle ?: feed?.title, feedRepository, settingsDataStore)
            ?: return
        player.setMediaItem(resolved.mediaItem, resolved.startPositionMs)
        player.setPlaybackSpeed(resolved.speed)
        player.prepare()
        player.play()
        settingsDataStore.setLastPlayingItem(item.feedId, item.id)
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

    /** Sets [loudnessEnhancer]'s live gain -- called both with the boost baked into a newly loaded
     *  media item (issue #199) and with a live override pushed by [PlaybackController] via
     *  [CUSTOM_COMMAND_SET_VOLUME_BOOST] while the same episode keeps playing (issue #202). */
    private fun applyVolumeBoost(millibels: Int) {
        val enhancer = loudnessEnhancer ?: return
        runCatching {
            enhancer.setTargetGain(millibels)
            enhancer.enabled = millibels > 0
        }
    }

    private fun saveCurrentPosition() {
        val itemId = player.currentMediaItem?.mediaId ?: return
        val positionSeconds = player.currentPosition / 1000.0
        serviceScope.launch { feedRepository.setEnclosurePosition(itemId, positionSeconds) }
    }
}
