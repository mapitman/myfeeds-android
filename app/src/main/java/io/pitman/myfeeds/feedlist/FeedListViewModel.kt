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

data class CategorySectionUiState(
    val category: Category,
    val feeds: List<FeedListItemUiState>,
    val isPodcastsSection: Boolean = false,
) {
    val totalUnread: Int get() = feeds.sumOf { it.unreadCount }
}

data class FeedListUiState(
    val categories: List<CategorySectionUiState> = emptyList(),
    val totalUnread: Int = 0,
    val isRefreshing: Boolean = false,
)

/** Synthetic, never-persisted category backing the cross-category "Podcasts" tab (issue #65). */
private val PODCASTS_SECTION_CATEGORY = Category(id = -1L, name = "", sortOrder = Int.MAX_VALUE)

private data class FeedListSourceData(
    val categories: List<Category>,
    val feeds: List<Feed>,
    val unreadCounts: Map<Long, Int>,
    val totalUnread: Int,
    val refreshing: Boolean,
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

    val uiState: StateFlow<FeedListUiState> = combine(
        categoryDao.observeAll(),
        feedRepository.observeAllFeeds(),
        feedRepository.observeUnreadCountsByFeed(),
        feedRepository.observeTotalUnreadCount(),
        isRefreshing,
    ) { categories, feeds, unreadCounts, totalUnread, refreshing ->
        FeedListSourceData(categories, feeds, unreadCounts, totalUnread, refreshing)
    }.combine(feedRepository.observePodcastFeedIds()) { source, podcastFeedIds ->
        val categorySections = source.categories.map { category ->
            val categoryFeeds = source.feeds
                .filter { it.categoryId == category.id }
                .map { feed -> FeedListItemUiState(feed, source.unreadCounts[feed.id] ?: 0) }
            CategorySectionUiState(category, categoryFeeds)
        }
        // Podcast feeds keep their normal category membership above *and* always also appear
        // here, cross-category, as a dedicated tab (issue #65) -- additive, not a replacement.
        val podcastFeeds = source.feeds
            .filter { it.id in podcastFeedIds }
            .map { feed -> FeedListItemUiState(feed, source.unreadCounts[feed.id] ?: 0) }
        val podcastsSection = CategorySectionUiState(
            category = PODCASTS_SECTION_CATEGORY,
            feeds = podcastFeeds,
            isPodcastsSection = true,
        )
        FeedListUiState(
            categories = categorySections + podcastsSection,
            totalUnread = source.totalUnread,
            isRefreshing = source.refreshing,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FeedListUiState())

    init {
        viewModelScope.launch { seeder.seedIfFirstRun() }
    }

    val feedListFontSize: StateFlow<FontSize> = settingsDataStore.settings
        .map { it.feedListFontSize }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FontSize.LARGE)

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
