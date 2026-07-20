package io.pitman.myfeeds.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import io.pitman.myfeeds.data.local.FeedItem
import io.pitman.myfeeds.data.repository.FeedRepository
import io.pitman.myfeeds.data.settings.SettingsDataStore
import kotlinx.coroutines.flow.first
import java.io.File

/** Everything a Media3 player needs to start [FeedItem], resolved outside the player itself. */
data class ResolvedPlaybackMedia(val mediaItem: MediaItem, val speed: Float, val startPositionMs: Long)

/** Key into [MediaMetadata.extras] carrying the episode's feed ID (issue #179): [PlaybackController]
 *  reads this back off the player's current item rather than tracking its own feedId field, since
 *  [PlaybackService] can also change the current item directly (backgrounded auto-advance) without
 *  going through [PlaybackController.loadMedia]. */
const val FEED_ID_EXTRA_KEY = "io.pitman.myfeeds.feedId"

/**
 * Resolves a [FeedItem] into playable Media3 pieces, shared by [PlaybackController] (playback
 * requests from the UI) and [PlaybackService] (issue #179: auto-advancing to the next Next Up
 * episode has to work with no UI/MediaController attached, so it can't go through
 * [PlaybackController]'s loadMedia).
 */
object PlaybackMediaItemFactory {
    /** Returns null without resolving anything if streaming is disallowed and nothing is downloaded. */
    suspend fun resolve(
        item: FeedItem,
        feedTitle: String?,
        feedRepository: FeedRepository,
        settingsDataStore: SettingsDataStore,
    ): ResolvedPlaybackMedia? {
        val allowStreaming = settingsDataStore.settings.first().allowPodcastStreaming
        val downloadedFilePath = item.downloadedFilePath?.takeIf { File(it).exists() }
        val uri = PlaybackUrlResolver.resolve(item, downloadedFilePath, allowStreaming = allowStreaming)
            ?: return null
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
                    .setExtras(Bundle().apply { putLong(FEED_ID_EXTRA_KEY, item.feedId) })
                    .build(),
            )
            .build()

        return ResolvedPlaybackMedia(
            mediaItem = mediaItem,
            speed = speed,
            startPositionMs = item.enclosurePosition?.let { (it * 1000).toLong() } ?: 0L,
        )
    }
}
