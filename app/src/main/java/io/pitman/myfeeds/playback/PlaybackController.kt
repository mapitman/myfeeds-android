package io.pitman.myfeeds.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import io.pitman.myfeeds.data.local.FeedItem
import io.pitman.myfeeds.data.repository.FeedRepository
import io.pitman.myfeeds.data.repository.QueueRepository
import io.pitman.myfeeds.data.settings.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val SKIP_FORWARD_MS = 30_000L
private const val SKIP_BACKWARD_MS = 15_000L
private const val POSITION_TICK_MS = 500L

data class PlaybackUiState(
    val currentItemId: String? = null,
    val feedId: Long? = null,
    val title: String? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isEnded: Boolean = false,
    val speed: Float = 1.0f,
    val artworkUrl: String? = null,
)

/**
 * UI-facing entry point for playback (used by the in-article player, issue #20): connects to
 * [PlaybackService] via a [MediaController] and exposes its state as a [StateFlow]. Building the
 * [MediaItem] here (rather than in the service) keeps the streaming-gate check
 * ([PlaybackUrlResolver]) next to the caller, which needs to know whether the request was denied.
 */
@Singleton
class PlaybackController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsDataStore: SettingsDataStore,
    private val feedRepository: FeedRepository,
    private val queueRepository: QueueRepository,
) {
    private var controller: MediaController? = null

    // Media3's MediaItem/MediaMetadata have no first-class "feedId" field, so it's tracked
    // alongside the controller and folded into PlaybackUiState -- the mini-player (issue #66)
    // needs it to navigate to the reader route ("reader/{feedId}/{itemId}") on tap.
    private var currentFeedId: Long? = null

    // Tracked separately from uiState.currentItemId (which only updates once the player listener
    // fires) so loadMedia can synchronously tell what was playing right before a switch, to
    // re-queue it (issue #106).
    private var currentItemId: String? = null

    private val _uiState = MutableStateFlow(PlaybackUiState())
    val uiState: StateFlow<PlaybackUiState> = _uiState.asStateFlow()

    // Player.Listener.onEvents only fires on discrete state changes (play/pause/seek/media item
    // transition/etc.), never on a timer -- without this, positionMs (and the progress bar/elapsed
    // time driven by it, issue #75) would sit frozen at wherever it was during ongoing playback.
    private val positionTickerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var positionTickerJob: Job? = null

    private fun snapshotState(player: Player) = PlaybackUiState(
        currentItemId = player.currentMediaItem?.mediaId,
        feedId = currentFeedId,
        title = player.currentMediaItem?.mediaMetadata?.title?.toString(),
        isPlaying = player.isPlaying,
        isBuffering = player.playbackState == Player.STATE_BUFFERING,
        positionMs = player.currentPosition,
        durationMs = player.duration.coerceAtLeast(0L),
        isEnded = player.playbackState == Player.STATE_ENDED,
        speed = player.playbackParameters.speed,
        artworkUrl = player.currentMediaItem?.mediaMetadata?.artworkUri?.toString(),
    )

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (player.playbackState == Player.STATE_ENDED) {
                handlePlaybackEnded(player)
            } else {
                _uiState.value = snapshotState(player)
            }
        }
    }

    /**
     * On completion the episode is no longer "current" (issue #107): position resets, the item
     * stops being treated as playing, and the mini-player disappears -- the same as an explicit
     * [stop], just triggered by reaching the end instead of the user tapping close. Then, if
     * anything is up next, playback continues automatically (issue #106).
     */
    private fun handlePlaybackEnded(player: Player) {
        player.clearMediaItems()
        currentFeedId = null
        currentItemId = null
        _uiState.value = PlaybackUiState()
        positionTickerJob?.cancel()
        // Left on the scope's default Main.immediate context (not dispatched to IO) because
        // playNextQueued(), if the queue isn't empty, ends up calling into the MediaController --
        // which, like all Media3 controller methods, must be called on the app's main thread.
        positionTickerScope.launch {
            settingsDataStore.setLastPlayingItem(null, null)
            playNextQueued()
        }
    }

    /** Auto-advances to whatever's next in "Next Up" when an episode finishes (issue #106). */
    private suspend fun playNextQueued() {
        val itemId = queueRepository.popNext() ?: return
        val item = feedRepository.getItem(itemId) ?: return
        val feed = feedRepository.getFeed(item.feedId)
        loadMedia(item, feed?.userTitle ?: feed?.title, autoPlay = true)
    }

    private fun connect(onConnected: (MediaController) -> Unit) {
        controller?.let {
            onConnected(it)
            return
        }
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        future.addListener(
            {
                val mediaController = future.get()
                controller = mediaController
                mediaController.addListener(playerListener)
                startPositionTicker(mediaController)
                onConnected(mediaController)
            },
            MoreExecutors.directExecutor(),
        )
    }

    private fun startPositionTicker(player: Player) {
        positionTickerJob?.cancel()
        positionTickerJob = positionTickerScope.launch {
            while (isActive) {
                delay(POSITION_TICK_MS)
                if (player.isPlaying) _uiState.value = snapshotState(player)
            }
        }
    }

    /** Returns false without starting playback if streaming is disallowed and nothing is downloaded. */
    suspend fun play(item: FeedItem, feedTitle: String?): Boolean = loadMedia(item, feedTitle, autoPlay = true)

    /**
     * Restores the episode the player had loaded before the process died (issue #108), so the
     * mini-player reappears ready to resume without auto-starting playback. A no-op if something
     * is already loaded (e.g. this raced with the user starting playback some other way) or if
     * nothing was persisted, already finished, or has since been removed from the feed.
     */
    suspend fun restoreLastPlayingItem() {
        if (currentFeedId != null) return
        val settings = settingsDataStore.settings.first()
        val feedId = settings.lastPlayingFeedId ?: return
        val itemId = settings.lastPlayingItemId ?: return
        val item = feedRepository.getItem(itemId)?.takeIf { it.feedId == feedId && !it.isRead } ?: return
        val feed = feedRepository.getFeed(feedId)
        loadMedia(item, feed?.userTitle ?: feed?.title, autoPlay = false)
    }

    private suspend fun loadMedia(item: FeedItem, feedTitle: String?, autoPlay: Boolean): Boolean {
        val allowStreaming = settingsDataStore.settings.first().allowPodcastStreaming
        val downloadedFilePath = item.downloadedFilePath?.takeIf { File(it).exists() }
        val uri = PlaybackUrlResolver.resolve(item, downloadedFilePath, allowStreaming = allowStreaming)
            ?: return false
        val feed = feedRepository.getFeed(item.feedId)
        val speed = feed?.playbackSpeed ?: 1.0f
        val artworkUrl = item.imageUrl ?: feed?.imageUrl

        val mediaItem = MediaItem.Builder()
            .setMediaId(item.id)
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(item.title)
                    .setArtist(feedTitle)
                    .setArtworkUri(artworkUrl?.let(Uri::parse))
                    .build(),
            )
            .build()

        val previousItemId = currentItemId
        if (previousItemId != null && previousItemId != item.id) {
            requeuePreviousEpisode(previousItemId)
        }

        currentFeedId = item.feedId
        currentItemId = item.id
        connect { controller ->
            controller.setMediaItem(mediaItem, item.enclosurePosition?.let { (it * 1000).toLong() } ?: 0L)
            controller.setPlaybackSpeed(speed)
            controller.prepare()
            if (autoPlay) controller.play()
        }
        settingsDataStore.setLastPlayingItem(item.feedId, item.id)
        return true
    }

    /**
     * Whatever was playing before a switch stays in "Next Up" as long as it wasn't finished
     * (issue #106) -- mirrors [restoreLastPlayingItem]'s `!isRead` check for "not completed".
     * A no-op if it's already queued (e.g. the user played straight from the queue).
     */
    private suspend fun requeuePreviousEpisode(itemId: String) {
        val previous = feedRepository.getItem(itemId)?.takeIf { !it.isRead } ?: return
        queueRepository.addToFront(previous.id)
    }

    fun pause() {
        controller?.pause()
    }

    fun resume() {
        controller?.play()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    /**
     * Changes speed for the current playback session and persists it as the playing episode's
     * feed's default (issue #70), so future episodes from that feed start at the chosen speed.
     */
    fun setSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed)
        val feedId = currentFeedId ?: return
        positionTickerScope.launch(Dispatchers.IO) {
            feedRepository.getFeed(feedId)?.let { feedRepository.updateFeed(it.copy(playbackSpeed = speed)) }
        }
    }

    // Shared by the reader's inline controls and the mini-player (issue #66) so both skip by the
    // same amount rather than each view re-implementing the offset/clamping logic.
    fun skipForward() {
        val playback = uiState.value
        seekTo((playback.positionMs + SKIP_FORWARD_MS).coerceAtMost(playback.durationMs))
    }

    fun skipBackward() {
        val playback = uiState.value
        seekTo((playback.positionMs - SKIP_BACKWARD_MS).coerceAtLeast(0L))
    }

    fun stop() {
        // Player.stop() alone halts playback but retains currentMediaItem, which would leave
        // the mini-player (issue #66) stuck on-screen -- clearMediaItems() is what actually
        // drops it so currentItemId (and therefore mini-player visibility) goes back to null.
        controller?.stop()
        controller?.clearMediaItems()
        currentFeedId = null
        currentItemId = null
        positionTickerScope.launch(Dispatchers.IO) { settingsDataStore.setLastPlayingItem(null, null) }
    }

    fun release() {
        positionTickerJob?.cancel()
        controller?.release()
        controller = null
    }
}
