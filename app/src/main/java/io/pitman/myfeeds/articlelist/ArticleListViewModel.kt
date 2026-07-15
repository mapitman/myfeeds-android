package io.pitman.myfeeds.articlelist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.pitman.myfeeds.data.local.FeedItem
import io.pitman.myfeeds.data.repository.FeedRepository
import io.pitman.myfeeds.data.settings.SettingsDataStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ArticleListUiState(
    val feedTitle: String = "",
    val showUnreadOnly: Boolean = true,
    val articles: List<FeedItem> = emptyList(),
    val unreadCount: Int = 0,
    val selectedIds: Set<String> = emptySet(),
) {
    val isSelectionMode: Boolean get() = selectedIds.isNotEmpty()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ArticleListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val feedRepository: FeedRepository,
    settingsDataStore: SettingsDataStore,
) : ViewModel() {
    private val feedId: Long = checkNotNull(savedStateHandle["feedId"])

    private val showUnreadOnly = MutableStateFlow(true)
    private val selectedIds = MutableStateFlow<Set<String>>(emptySet())
    private val feedTitle = MutableStateFlow("")

    val uiState: StateFlow<ArticleListUiState> = combine(
        feedTitle,
        showUnreadOnly,
        showUnreadOnly.flatMapLatest { unreadOnly ->
            if (unreadOnly) feedRepository.observeUnreadItems(feedId) else feedRepository.observeItems(feedId)
        },
        feedRepository.observeUnreadCount(feedId),
        selectedIds,
    ) { title, unreadOnly, articles, unreadCount, selected ->
        ArticleListUiState(title, unreadOnly, articles, unreadCount, selected)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArticleListUiState())

    init {
        viewModelScope.launch {
            val defaultToAllView = settingsDataStore.settings.first().defaultToAllArticleView
            showUnreadOnly.value = !defaultToAllView

            val feed = feedRepository.getFeed(feedId)
            feedTitle.value = feed?.userTitle ?: feed?.title.orEmpty()
        }
    }

    fun setShowUnreadOnly(unreadOnly: Boolean) {
        showUnreadOnly.value = unreadOnly
    }

    fun toggleSelection(itemId: String) {
        selectedIds.value = if (itemId in selectedIds.value) {
            selectedIds.value - itemId
        } else {
            selectedIds.value + itemId
        }
    }

    fun clearSelection() {
        selectedIds.value = emptySet()
    }

    fun markSelectedRead(isRead: Boolean) {
        val ids = selectedIds.value
        viewModelScope.launch {
            ids.forEach { feedRepository.markRead(it, isRead) }
            clearSelection()
        }
    }

    fun deleteSelected() {
        val ids = selectedIds.value
        viewModelScope.launch {
            val items = uiState.value.articles.filter { it.id in ids }
            feedRepository.deleteItems(items)
            clearSelection()
        }
    }
}
