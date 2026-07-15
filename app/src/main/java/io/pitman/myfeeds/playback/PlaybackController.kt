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
) {
    private var controller: MediaController? = null

    // Media3's MediaItem/MediaMetadata have no first-class "feedId" field, so it's tracked
    // alongside the controller and folded into PlaybackUiState -- the mini-player (issue #66)
    // needs it to navigate to the reader route ("reader/{feedId}/{itemId}") on tap.
    private var currentFeedId: Long? = null

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
    )

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            _uiState.value = snapshotState(player)
        }
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
    suspend fun play(item: FeedItem, feedTitle: String?): Boolean {
        val allowStreaming = settingsDataStore.settings.first().allowPodcastStreaming
        val downloadedFilePath = item.downloadedFilePath?.takeIf { File(it).exists() }
        val uri = PlaybackUrlResolver.resolve(item, downloadedFilePath, allowStreaming = allowStreaming)
            ?: return false

        val mediaItem = MediaItem.Builder()
            .setMediaId(item.id)
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(item.title)
                    .setArtist(feedTitle)
                    .setArtworkUri(item.imageUrl?.let(Uri::parse))
                    .build(),
            )
            .build()

        currentFeedId = item.feedId
        connect { controller ->
            controller.setMediaItem(mediaItem, item.enclosurePosition?.let { (it * 1000).toLong() } ?: 0L)
            controller.prepare()
            controller.play()
        }
        return true
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
    }

    fun release() {
        positionTickerJob?.cancel()
        controller?.release()
        controller = null
    }
}
