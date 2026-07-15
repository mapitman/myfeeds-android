package io.pitman.myfeeds.feedlist

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.pitman.myfeeds.R
import io.pitman.myfeeds.data.DefaultFeedsSeeder
import io.pitman.myfeeds.data.feed.FeedUpdateEngine
import io.pitman.myfeeds.data.feed.FeedUpdateResult
import io.pitman.myfeeds.data.local.Category
import io.pitman.myfeeds.data.local.CategoryDao
import io.pitman.myfeeds.data.local.Feed
import io.pitman.myfeeds.data.repository.FeedRepository
import io.pitman.myfeeds.data.settings.FontSize
import io.pitman.myfeeds.data.settings.SettingsDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FeedListItemUiState(val feed: Feed, val unreadCount: Int)

data class CategorySectionUiState(val category: Category, val feeds: List<FeedListItemUiState>) {
    val totalUnread: Int get() = feeds.sumOf { it.unreadCount }
}

data class FeedListUiState(
    val categories: List<CategorySectionUiState> = emptyList(),
    val totalUnread: Int = 0,
    val isRefreshing: Boolean = false,
)

@HiltViewModel
class FeedListViewModel @Inject constructor(
    private val seeder: DefaultFeedsSeeder,
    categoryDao: CategoryDao,
    private val feedRepository: FeedRepository,
    private val feedUpdateEngine: FeedUpdateEngine,
    settingsDataStore: SettingsDataStore,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val isRefreshing = MutableStateFlow(false)
    private val _refreshError = MutableStateFlow<String?>(null)

    /** One-shot refresh-failure message for a Snackbar; cleared via [consumeRefreshError]. */
    val refreshError: StateFlow<String?> = _refreshError

    val feedListFontSize: StateFlow<FontSize> = settingsDataStore.settings
        .map { it.feedListFontSize }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FontSize.LARGE)

    val uiState: StateFlow<FeedListUiState> = combine(
        categoryDao.observeAll(),
        feedRepository.observeAllFeeds(),
        feedRepository.observeUnreadCountsByFeed(),
        feedRepository.observeTotalUnreadCount(),
        isRefreshing,
    ) { categories, feeds, unreadCounts, totalUnread, refreshing ->
        val sections = categories.map { category ->
            val categoryFeeds = feeds
                .filter { it.categoryId == category.id }
                .map { feed -> FeedListItemUiState(feed, unreadCounts[feed.id] ?: 0) }
            CategorySectionUiState(category, categoryFeeds)
        }
        FeedListUiState(categories = sections, totalUnread = totalUnread, isRefreshing = refreshing)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FeedListUiState())

    init {
        viewModelScope.launch { seeder.seedIfFirstRun() }
    }

    fun refresh() {
        viewModelScope.launch {
            isRefreshing.value = true
            val feeds = feedRepository.observeAllFeeds().first()
            val results = feedUpdateEngine.updateFeeds(feeds)
            if (results.any { it is FeedUpdateResult.Failure }) {
                _refreshError.value = context.getString(R.string.feed_list_refresh_error)
            }
            isRefreshing.value = false
        }
    }

    fun consumeRefreshError() {
        _refreshError.value = null
    }
}
