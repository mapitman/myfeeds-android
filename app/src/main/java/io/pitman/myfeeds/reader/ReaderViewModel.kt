package io.pitman.myfeeds.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.pitman.myfeeds.data.local.FeedItem
import io.pitman.myfeeds.data.repository.FeedRepository
import io.pitman.myfeeds.playback.PlaybackController
import io.pitman.myfeeds.playback.PlaybackUiState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReaderUiState(
    val items: List<FeedItem> = emptyList(),
    val initialIndex: Int = 0,
    val feedTitle: String? = null,
)

private const val SKIP_FORWARD_MS = 30_000L
private const val SKIP_BACKWARD_MS = 15_000L

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val feedRepository: FeedRepository,
    private val playbackController: PlaybackController,
) : ViewModel() {
    private val feedId: Long = checkNotNull(savedStateHandle["feedId"])
    private val initialItemId: String = checkNotNull(savedStateHandle["itemId"])

    val uiState: StateFlow<ReaderUiState> = combine(
        feedRepository.observeItems(feedId),
        feedRepository.observeFeed(feedId),
    ) { items, feed ->
        val index = items.indexOfFirst { it.id == initialItemId }.coerceAtLeast(0)
        ReaderUiState(items = items, initialIndex = index, feedTitle = feed?.userTitle ?: feed?.title)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReaderUiState())

    val playbackState: StateFlow<PlaybackUiState> = playbackController.uiState

    fun markRead(itemId: String) {
        viewModelScope.launch { feedRepository.markRead(itemId, true) }
    }

    fun togglePlayPause(item: FeedItem) {
        val playback = playbackState.value
        when {
            playback.currentItemId == item.id && playback.isPlaying -> playbackController.pause()
            playback.currentItemId == item.id && !playback.isPlaying -> playbackController.resume()
            else -> viewModelScope.launch { playbackController.play(item, uiState.value.feedTitle) }
        }
    }

    fun seekTo(positionMs: Long) {
        playbackController.seekTo(positionMs)
    }

    fun skipForward() {
        val playback = playbackState.value
        playbackController.seekTo((playback.positionMs + SKIP_FORWARD_MS).coerceAtMost(playback.durationMs))
    }

    fun skipBackward() {
        val playback = playbackState.value
        playbackController.seekTo((playback.positionMs - SKIP_BACKWARD_MS).coerceAtLeast(0L))
    }
}
