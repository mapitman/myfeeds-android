package io.pitman.myfeeds.refresh

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.pitman.myfeeds.data.feed.FeedUpdateEngine
import io.pitman.myfeeds.data.feed.FeedUpdateResult
import io.pitman.myfeeds.data.repository.FeedRepository
import io.pitman.myfeeds.download.EnclosureDownloadRepository
import io.pitman.myfeeds.widget.UnreadWidget
import kotlinx.coroutines.flow.first

/**
 * Periodic background refresh (issue #22). Runs [FeedUpdateEngine.updateFeeds] for every
 * subscribed feed; a per-feed failure doesn't fail the whole run since [FeedUpdateResult] already
 * carries success/failure per feed and the next scheduled run will simply retry.
 *
 * User-requested addition (issue #23): for feeds flagged with `autoDownloadEnabled`, newly
 * ingested items with an enclosure are queued for background download.
 *
 * Also refreshes the home-screen widget's unread counts (issue #24) once the run completes.
 */
@HiltWorker
class FeedRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val feedRepository: FeedRepository,
    private val feedUpdateEngine: FeedUpdateEngine,
    private val downloadRepository: EnclosureDownloadRepository,
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result {
        val feeds = feedRepository.observeAllFeeds().first()
        val feedsById = feeds.associateBy { it.id }
        val results = feedUpdateEngine.updateFeeds(feeds)

        results.filterIsInstance<FeedUpdateResult.Success>()
            .filter { feedsById[it.feedId]?.autoDownloadEnabled == true }
            .flatMap { it.newItemIds }
            .forEach { itemId ->
                val item = feedRepository.getItem(itemId) ?: return@forEach
                if (item.enclosureUrl != null) downloadRepository.startDownload(item)
            }

        UnreadWidget().updateAll(applicationContext)
        return Result.success()
    }
}
