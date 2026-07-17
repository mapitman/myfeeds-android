package io.pitman.myfeeds.data.feed

import io.pitman.myfeeds.data.local.Feed
import io.pitman.myfeeds.data.local.isPodcastEpisode
import io.pitman.myfeeds.data.repository.FeedRepository
import io.pitman.myfeeds.data.repository.QueueRepository
import io.pitman.myfeeds.download.EnclosureDownloadRepository
import javax.inject.Inject

/**
 * Applies auto-download (`autoDownloadEnabled`, issue #23) and auto-queue (`autoQueueEnabled`,
 * issue #68) to a batch of [FeedUpdateResult]s. Extracted out of [io.pitman.myfeeds.refresh.FeedRefreshWorker]
 * (issue #88) so manual pull-to-refresh -- both `FeedListViewModel.refresh()` and
 * `ArticleListViewModel.refresh()` -- can trigger the same behavior the background worker does,
 * instead of only seeing new episodes auto-download/auto-queue on the next scheduled run.
 */
class AutoQueueAndDownloadEnforcer @Inject constructor(
    private val feedRepository: FeedRepository,
    private val downloadRepository: EnclosureDownloadRepository,
    private val queueRepository: QueueRepository,
) {
    suspend fun apply(feeds: List<Feed>, results: List<FeedUpdateResult>) {
        val feedsById = feeds.associateBy { it.id }
        val successes = results.filterIsInstance<FeedUpdateResult.Success>()

        successes
            .filter { feedsById[it.feedId]?.autoDownloadEnabled == true }
            .flatMap { it.newItemIds }
            .forEach { itemId ->
                val item = feedRepository.getItem(itemId) ?: return@forEach
                if (item.isPodcastEpisode) downloadRepository.startDownload(item)
            }

        // Per feed (not flattened, since cap eviction needs to run once per feed after all of
        // that feed's new items for this run have been queued) -- see QueueRepository#enforceFeedCap.
        successes.forEach { success ->
            val feed = feedsById[success.feedId] ?: return@forEach
            if (!feed.autoQueueEnabled) return@forEach
            success.newItemIds.forEach { itemId ->
                val item = feedRepository.getItem(itemId) ?: return@forEach
                if (item.isPodcastEpisode) queueRepository.addToEnd(itemId, autoQueued = true)
            }
            feed.autoQueueMaxCount?.let { queueRepository.enforceFeedCap(feed.id, it) }
        }
    }
}
