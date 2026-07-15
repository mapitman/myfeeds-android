package io.pitman.myfeeds.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.pitman.myfeeds.data.local.FeedItem
import io.pitman.myfeeds.data.local.isPodcastEpisode
import io.pitman.myfeeds.data.repository.FeedRepository
import io.pitman.myfeeds.data.settings.FontSize
import io.pitman.myfeeds.data.settings.SettingsDataStore
import io.pitman.myfeeds.download.EnclosureDownloadRepository
import io.pitman.myfeeds.playback.PlaybackController
import io.pitman.myfeeds.playback.PlaybackUiState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReaderUiState(
    val items: List<FeedItem> = emptyList(),
    val initialIndex: Int = 0,
    val feedTitle: String? = null,
)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val feedRepository: FeedRepository,
    private val playbackController: PlaybackController,
    private val downloadRepository: EnclosureDownloadRepository,
    settingsDataStore: SettingsDataStore,
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

    init {
        // Ported from AudioPlayer.cs TrackEnded, which advanced to PlayList.GetNextTrack(): when
        // the currently playing item in this reader session finishes, start the next one in the
        // list (already ordered newest-first, matching the article pager).
        viewModelScope.launch {
            playbackState
                .map { it.isEnded to it.currentItemId }
                .distinctUntilChanged()
                .collect { (isEnded, itemId) ->
                    if (isEnded && itemId != null) advanceToNextItem(itemId)
                }
        }
    }

    val articleFontSize: StateFlow<FontSize> = settingsDataStore.settings
        .map { it.articleFontSize }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FontSize.NORMAL)

    private fun advanceToNextItem(finishedItemId: String) {
        val items = uiState.value.items
        val index = items.indexOfFirst { it.id == finishedItemId }
        if (index == -1) return
        val next = items.getOrNull(index + 1) ?: return
        if (!next.isPodcastEpisode) return
        viewModelScope.launch { playbackController.play(next, uiState.value.feedTitle) }
    }

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

    fun downloadEnclosure(item: FeedItem) {
        viewModelScope.launch { downloadRepository.startDownload(item) }
    }

    fun deleteDownload(item: FeedItem) {
        viewModelScope.launch { downloadRepository.deleteDownload(item) }
    }
}
