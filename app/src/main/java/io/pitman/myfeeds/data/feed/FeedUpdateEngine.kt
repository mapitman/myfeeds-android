package io.pitman.myfeeds.data.feed

import io.pitman.myfeeds.data.local.Feed
import io.pitman.myfeeds.data.local.FeedItem
import io.pitman.myfeeds.data.repository.FeedRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
        var newItemCount = 0

        parsed.items.forEach { parsedItem ->
            val itemGuid = parsedItem.itemGuid.ifBlank { parsedItem.url }
            if (itemGuid.isBlank()) return@forEach

            val existing = feedRepository.findByItemGuid(feed.id, itemGuid)
            val imageUrl = FirstImageExtractor.extractFirstImageUrl(parsedItem.description, parsedItem.url)

            val entity = FeedItem(
                id = existing?.id ?: UUID.randomUUID().toString(),
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
            )
            feedRepository.upsertItems(listOf(entity))
            if (existing == null) newItemCount++
        }

        feedRepository.updateFeed(feed.copy(lastGet = Instant.now().toEpochMilli()))
        val evicted = feedRepository.trimToItemsToKeep(feed.id)

        return FeedUpdateResult.Success(newItemCount = newItemCount, evictedItemIds = evicted.map { it.id })
    }

    companion object {
        private const val MAX_CONCURRENT_UPDATES = 2
    }
}
