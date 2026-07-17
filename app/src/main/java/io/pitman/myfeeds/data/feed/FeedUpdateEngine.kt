package io.pitman.myfeeds.data.feed

import io.pitman.myfeeds.data.local.Feed
import io.pitman.myfeeds.data.local.FeedItem
import io.pitman.myfeeds.data.repository.FeedRepository
import io.pitman.myfeeds.data.settings.SettingsDataStore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/**
 * Ported from FeedManager.UpdateFeed's persistence half (fetch/parse is FeedFetcher, #10):
 * dedup incoming items by itemGuid (existing items keep their id/isRead/enclosurePosition, new
 * items get a fresh UUID), extract each item's first image, trim to itemsToKeep, and update the
 * feed's lastGet. Multiple feeds refresh with bounded concurrency, mirroring the original's
 * intent to not hammer the network/DB with unlimited parallel requests.
 */
class FeedUpdateEngine @Inject constructor(
    private val feedFetcher: FeedFetcher,
    private val feedRepository: FeedRepository,
    private val settingsDataStore: SettingsDataStore,
) {
    suspend fun updateFeed(feed: Feed): FeedUpdateResult {
        val feedUrl = feed.feedUrl
        if (feedUrl.isNullOrBlank()) return FeedUpdateResult.Failure("Feed has no URL")

        return when (val result = feedFetcher.fetchFeed(feedUrl)) {
            is FeedFetchResult.Failure -> FeedUpdateResult.Failure(result.message)
            is FeedFetchResult.Success -> persist(feed, result.feed)
        }
    }

    suspend fun updateFeeds(feeds: List<Feed>): List<FeedUpdateResult> = coroutineScope {
        val semaphore = Semaphore(MAX_CONCURRENT_UPDATES)
        feeds.map { feed -> async { semaphore.withPermit { updateFeed(feed) } } }.awaitAll()
    }

    private suspend fun persist(feed: Feed, parsed: ParsedFeed): FeedUpdateResult {
        // A feed's first-ever successful fetch is the only time it's safe to change auto-queue
        // defaults without risking overwriting a value the user has since set themselves via Feed
        // Properties (issue #137) -- lastGet is only ever null before that first fetch completes.
        val isFirstFetch = feed.lastGet == null
        val newItemIds = mutableListOf<String>()
        var hasPodcastEpisode = false

        parsed.items.forEach { parsedItem ->
            val itemGuid = parsedItem.itemGuid.ifBlank { parsedItem.url }
            if (itemGuid.isBlank()) return@forEach

            if (parsedItem.enclosure?.type?.startsWith("audio/", ignoreCase = true) == true) {
                hasPodcastEpisode = true
            }

            val existing = feedRepository.findByItemGuid(feed.id, itemGuid)
            val imageUrl = FirstImageExtractor.extractFirstImageUrl(parsedItem.description, parsedItem.url)
            val id = existing?.id ?: UUID.randomUUID().toString()

            val entity = FeedItem(
                id = id,
                feedId = feed.id,
                title = parsedItem.title,
                description = parsedItem.description,
                url = parsedItem.url,
                imageUrl = imageUrl,
                itemGuid = itemGuid,
                publishDate = parsedItem.publishDate?.toEpochMilli(),
                isRead = existing?.isRead ?: false,
                enclosureUrl = parsedItem.enclosure?.url,
                enclosureType = parsedItem.enclosure?.type,
                enclosureLength = parsedItem.enclosure?.length,
                enclosurePosition = existing?.enclosurePosition,
                enclosureDurationMs = parsedItem.durationMs,
            )
            feedRepository.upsertItems(listOf(entity))
            if (existing == null) newItemIds += id
        }

        var updatedFeed = feed.copy(lastGet = Instant.now().toEpochMilli(), imageUrl = parsed.imageUrl ?: feed.imageUrl)
        // New podcast subscriptions default to auto-queuing, capped at a small number of episodes
        // rather than unlimited (issue #137), so Next Up doesn't get flooded by a feed's entire
        // back catalog on the very first fetch.
        if (isFirstFetch && hasPodcastEpisode && !feed.autoQueueEnabled) {
            updatedFeed = updatedFeed.copy(autoQueueEnabled = true, autoQueueMaxCount = NEW_PODCAST_AUTO_QUEUE_CAP)
        }
        feedRepository.updateFeed(updatedFeed)
        val defaultItemsToKeep = settingsDataStore.settings.first().maxArticles
        val evicted = feedRepository.trimToItemsToKeep(feed.id, defaultItemsToKeep)

        return FeedUpdateResult.Success(feedId = feed.id, newItemIds = newItemIds, evictedItemIds = evicted.map { it.id })
    }

    companion object {
        private const val MAX_CONCURRENT_UPDATES = 2
        private const val NEW_PODCAST_AUTO_QUEUE_CAP = 5
    }
}
