package io.pitman.myfeeds.playback

import io.pitman.myfeeds.data.repository.FeedRepository
import io.pitman.myfeeds.data.repository.QueueRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Advances playback to the next queued episode when the current one finishes (issue #67),
 * replacing the old `ReaderViewModel.advanceToNextItem` index+1-within-feed logic. Runs app-scoped
 * (not tied to any screen's ViewModel) since the point of a queue is that it keeps advancing even
 * after the reader is closed -- eagerly instantiated from [io.pitman.myfeeds.MyFeedsApp.onCreate]
 * since nothing else injects it directly.
 */
@Singleton
class QueuePlaybackCoordinator @Inject constructor(
    private val playbackController: PlaybackController,
    private val queueRepository: QueueRepository,
    private val feedRepository: FeedRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        scope.launch {
            playbackController.uiState
                .map { it.isEnded to it.currentItemId }
                .distinctUntilChanged()
                .collect { (isEnded, itemId) ->
                    if (isEnded && itemId != null) advanceFromQueue()
                }
        }
    }

    private suspend fun advanceFromQueue() {
        val nextItemId = queueRepository.popNext() ?: return
        val item = feedRepository.getItem(nextItemId) ?: return
        val feed = feedRepository.getFeed(item.feedId)
        playbackController.play(item, feed?.userTitle ?: feed?.title)
    }
}
