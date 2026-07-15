package io.pitman.myfeeds.refresh

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.pitman.myfeeds.data.feed.FeedUpdateEngine
import io.pitman.myfeeds.data.feed.FeedUpdateResult
import io.pitman.myfeeds.data.repository.FeedRepository
import kotlinx.coroutines.flow.first

/**
 * Periodic background refresh (issue #22). Runs [FeedUpdateEngine.updateFeeds] for every
 * subscribed feed; a per-feed failure doesn't fail the whole run since [FeedUpdateResult] already
 * carries success/failure per feed and the next scheduled run will simply retry.
 */
@HiltWorker
class FeedRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val feedRepository: FeedRepository,
    private val feedUpdateEngine: FeedUpdateEngine,
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result {
        val feeds = feedRepository.observeAllFeeds().first()
        feedUpdateEngine.updateFeeds(feeds)
        return Result.success()
    }
}
