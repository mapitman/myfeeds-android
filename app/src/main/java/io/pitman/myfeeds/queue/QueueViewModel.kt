package io.pitman.myfeeds.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.pitman.myfeeds.data.local.QueuedEpisode
import io.pitman.myfeeds.data.repository.FeedRepository
import io.pitman.myfeeds.data.repository.QueueRepository
import io.pitman.myfeeds.playback.PlaybackController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QueueViewModel @Inject constructor(
    private val queueRepository: QueueRepository,
    private val feedRepository: FeedRepository,
    private val playbackController: PlaybackController,
) : ViewModel() {
    val queue: StateFlow<List<QueuedEpisode>> = queueRepository.observeQueue()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Renumbers the queue to match [orderedItemIds] exactly, for drag-to-reorder. [onComplete]
     * runs once the write lands -- the caller uses it to release its own optimistic-ordering
     * guard, so an unrelated `queue` re-emission racing this write can't overwrite the drag's
     * result with the stale pre-reorder order before it's persisted.
     */
    fun reorder(orderedItemIds: List<String>, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            queueRepository.reorder(orderedItemIds)
            onComplete()
        }
    }

    fun remove(itemId: String) {
        viewModelScope.launch { queueRepository.remove(itemId) }
    }

    /** Plays this episode immediately, removing it from the queue since it's no longer "up next". */
    fun playNow(episode: QueuedEpisode) {
        viewModelScope.launch {
            if (playbackController.uiState.value.currentItemId == episode.item.id) return@launch
            queueRepository.remove(episode.item.id)
            val feed = feedRepository.getFeed(episode.item.feedId)
            playbackController.play(episode.item, feed?.userTitle ?: feed?.title)
        }
    }
}
