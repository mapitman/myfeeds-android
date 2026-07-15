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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class PlaybackUiState(
    val currentItemId: String? = null,
    val isPlaying: Boolean = false,
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

    private val _uiState = MutableStateFlow(PlaybackUiState())
    val uiState: StateFlow<PlaybackUiState> = _uiState.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            _uiState.value = PlaybackUiState(
                currentItemId = player.currentMediaItem?.mediaId,
                isPlaying = player.isPlaying,
                positionMs = player.currentPosition,
                durationMs = player.duration.coerceAtLeast(0L),
                isEnded = player.playbackState == Player.STATE_ENDED,
            )
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
                onConnected(mediaController)
            },
            MoreExecutors.directExecutor(),
        )
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

    fun stop() {
        controller?.stop()
    }

    fun release() {
        controller?.release()
        controller = null
    }
}
