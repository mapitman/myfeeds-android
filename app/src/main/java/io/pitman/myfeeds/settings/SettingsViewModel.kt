package io.pitman.myfeeds.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.pitman.myfeeds.data.opml.OpmlImporter
import io.pitman.myfeeds.data.opml.OpmlParser
import io.pitman.myfeeds.data.repository.FeedRepository
import io.pitman.myfeeds.data.settings.AppSettings
import io.pitman.myfeeds.data.settings.FontSize
import io.pitman.myfeeds.data.settings.SettingsDataStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val feedRepository: FeedRepository,
    private val opmlImporter: OpmlImporter,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val settings: StateFlow<AppSettings> =
        settingsDataStore.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setUpdateIntervalMinutes(minutes: Long) {
        viewModelScope.launch { settingsDataStore.setUpdateIntervalMinutes(minutes) }
    }

    fun setEnableImageDisplay(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setEnableImageDisplay(enabled) }
    }

    fun setDefaultToAllArticleView(value: Boolean) {
        viewModelScope.launch { settingsDataStore.setDefaultToAllArticleView(value) }
    }

    fun setMaxArticles(count: Int) {
        viewModelScope.launch { settingsDataStore.setMaxArticles(count) }
    }

    fun setArticleFontSize(size: FontSize) {
        viewModelScope.launch { settingsDataStore.setArticleFontSize(size) }
    }

    fun setListFontSize(size: FontSize) {
        viewModelScope.launch { settingsDataStore.setListFontSize(size) }
    }

    fun setFeedListFontSize(size: FontSize) {
        viewModelScope.launch { settingsDataStore.setFeedListFontSize(size) }
    }

    fun setAllowPodcastDownloadOnBattery(value: Boolean) {
        viewModelScope.launch { settingsDataStore.setAllowPodcastDownloadOnBattery(value) }
    }

    fun setAllowPodcastDownloadOnCellular(value: Boolean) {
        viewModelScope.launch { settingsDataStore.setAllowPodcastDownloadOnCellular(value) }
    }

    fun setAllowPodcastStreaming(value: Boolean) {
        viewModelScope.launch { settingsDataStore.setAllowPodcastStreaming(value) }
    }

    fun clearPodcasts() {
        viewModelScope.launch { feedRepository.clearAllEnclosurePositions() }
    }

    fun addDefaultFeeds() {
        viewModelScope.launch {
            val document = context.assets.open("default_feeds.opml").use { OpmlParser.parse(it) }
            opmlImporter.import(document)
        }
    }

    fun removeAllFeeds() {
        viewModelScope.launch { feedRepository.removeAllFeeds() }
    }

    fun resetSettings() {
        viewModelScope.launch { settingsDataStore.reset() }
    }
}
