package io.pitman.myfeeds.feedlist

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.pitman.myfeeds.R
import io.pitman.myfeeds.data.DefaultFeedsSeeder
import io.pitman.myfeeds.data.feed.AutoQueueAndDownloadEnforcer
import io.pitman.myfeeds.data.feed.FeedUpdateEngine
import io.pitman.myfeeds.data.feed.FeedUpdateResult
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

enum class FeedListSection { PODCASTS, FEEDS }

data class FeedListSectionUiState(
    val section: FeedListSection,
    val feeds: List<FeedListItemUiState>,
) {
    val totalUnread: Int get() = feeds.sumOf { it.unreadCount }
}

data class FeedListUiState(
    val sections: List<FeedListSectionUiState> = emptyList(),
    val totalUnread: Int = 0,
    val isRefreshing: Boolean = false,
)

private data class FeedListSourceData(
    val feeds: List<Feed>,
    val unreadCounts: Map<Long, Int>,
    val totalUnread: Int,
    val refreshing: Boolean,
)

@HiltViewModel
class FeedListViewModel @Inject constructor(
    private val seeder: DefaultFeedsSeeder,
    private val feedRepository: FeedRepository,
    private val feedUpdateEngine: FeedUpdateEngine,
    private val autoQueueAndDownloadEnforcer: AutoQueueAndDownloadEnforcer,
    settingsDataStore: SettingsDataStore,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val isRefreshing = MutableStateFlow(false)
    private val _refreshError = MutableStateFlow<String?>(null)

    /** One-shot refresh-failure message for a Snackbar; cleared via [consumeRefreshError]. */
    val refreshError: StateFlow<String?> = _refreshError

    val uiState: StateFlow<FeedListUiState> = combine(
        feedRepository.observeAllFeeds(),
        feedRepository.observeUnreadCountsByFeed(),
        feedRepository.observeTotalUnreadCount(),
        isRefreshing,
    ) { feeds, unreadCounts, totalUnread, refreshing ->
        FeedListSourceData(feeds, unreadCounts, totalUnread, refreshing)
    }.combine(feedRepository.observePodcastFeedIds()) { source, podcastFeedIds ->
        // Podcast-ness (issue #65) splits the flat feed list into two fixed sections (issue #118):
        // "Podcasts" (feeds with at least one audio-enclosure item) and "Feeds" (everything else).
        val podcastFeeds = source.feeds
            .filter { it.id in podcastFeedIds }
            .map { feed -> FeedListItemUiState(feed, source.unreadCounts[feed.id] ?: 0) }
        val otherFeeds = source.feeds
            .filterNot { it.id in podcastFeedIds }
            .map { feed -> FeedListItemUiState(feed, source.unreadCounts[feed.id] ?: 0) }
        FeedListUiState(
            sections = listOf(
                FeedListSectionUiState(FeedListSection.PODCASTS, podcastFeeds),
                FeedListSectionUiState(FeedListSection.FEEDS, otherFeeds),
            ),
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
            autoQueueAndDownloadEnforcer.apply(feeds, results)
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
