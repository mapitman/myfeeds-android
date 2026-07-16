package io.pitman.myfeeds.feedproperties

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.pitman.myfeeds.data.repository.FeedRepository
import io.pitman.myfeeds.data.settings.SettingsDataStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FeedPropertiesUiState(
    val title: String = "",
    val displayTitle: String = "",
    val itemsToKeep: Int? = null,
    val globalMaxArticles: Int = 20,
    val autoDownloadEnabled: Boolean = false,
    val autoQueueEnabled: Boolean = false,
    val autoQueueMaxCount: Int? = null,
    val playbackSpeed: Float = 1.0f,
    val isUnsubscribed: Boolean = false,
)

@HiltViewModel
class FeedPropertiesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val feedRepository: FeedRepository,
    settingsDataStore: SettingsDataStore,
) : ViewModel() {
    private val feedId: Long = checkNotNull(savedStateHandle["feedId"])

    val uiState: StateFlow<FeedPropertiesUiState> = combine(
        feedRepository.observeFeed(feedId),
        settingsDataStore.settings,
    ) { feed, settings ->
        if (feed == null) {
            FeedPropertiesUiState(isUnsubscribed = true)
        } else {
            FeedPropertiesUiState(
                title = feed.userTitle ?: feed.title.orEmpty(),
                displayTitle = feed.userTitle ?: feed.title.orEmpty(),
                itemsToKeep = feed.itemsToKeep,
                globalMaxArticles = settings.maxArticles,
                autoDownloadEnabled = feed.autoDownloadEnabled,
                autoQueueEnabled = feed.autoQueueEnabled,
                autoQueueMaxCount = feed.autoQueueMaxCount,
                playbackSpeed = feed.playbackSpeed,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FeedPropertiesUiState())

    fun setTitle(title: String) {
        viewModelScope.launch {
            val feed = feedRepository.getFeed(feedId) ?: return@launch
            val trimmed = title.trim()
            val userTitle = if (trimmed.isBlank() || trimmed == feed.title) null else trimmed
            feedRepository.updateFeed(feed.copy(userTitle = userTitle))
        }
    }

    fun setItemsToKeep(itemsToKeep: Int?) {
        viewModelScope.launch {
            val feed = feedRepository.getFeed(feedId) ?: return@launch
            feedRepository.updateFeed(feed.copy(itemsToKeep = itemsToKeep))
        }
    }

    fun setAutoDownloadEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val feed = feedRepository.getFeed(feedId) ?: return@launch
            feedRepository.updateFeed(feed.copy(autoDownloadEnabled = enabled))
        }
    }

    fun setAutoQueueEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val feed = feedRepository.getFeed(feedId) ?: return@launch
            feedRepository.updateFeed(feed.copy(autoQueueEnabled = enabled))
        }
    }

    fun setAutoQueueMaxCount(maxCount: Int?) {
        viewModelScope.launch {
            val feed = feedRepository.getFeed(feedId) ?: return@launch
            feedRepository.updateFeed(feed.copy(autoQueueMaxCount = maxCount))
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        viewModelScope.launch {
            val feed = feedRepository.getFeed(feedId) ?: return@launch
            feedRepository.updateFeed(feed.copy(playbackSpeed = speed))
        }
    }

    fun unsubscribe() {
        viewModelScope.launch {
            val feed = feedRepository.getFeed(feedId) ?: return@launch
            feedRepository.unsubscribe(feed)
        }
    }
}
