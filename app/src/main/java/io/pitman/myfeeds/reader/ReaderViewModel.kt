package io.pitman.myfeeds.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.pitman.myfeeds.data.local.FeedItem
import io.pitman.myfeeds.data.repository.FeedRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReaderUiState(
    val items: List<FeedItem> = emptyList(),
    val initialIndex: Int = 0,
)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val feedRepository: FeedRepository,
) : ViewModel() {
    private val feedId: Long = checkNotNull(savedStateHandle["feedId"])
    private val initialItemId: String = checkNotNull(savedStateHandle["itemId"])

    val uiState: StateFlow<ReaderUiState> = feedRepository.observeItems(feedId)
        .map { items ->
            val index = items.indexOfFirst { it.id == initialItemId }.coerceAtLeast(0)
            ReaderUiState(items = items, initialIndex = index)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReaderUiState())

    fun markRead(itemId: String) {
        viewModelScope.launch { feedRepository.markRead(itemId, true) }
    }
}
