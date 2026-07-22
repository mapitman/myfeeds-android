package io.pitman.myfeeds.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.pitman.myfeeds.R
import io.pitman.myfeeds.data.opml.OpmlExporter
import io.pitman.myfeeds.data.opml.OpmlImporter
import io.pitman.myfeeds.data.opml.OpmlParser
import io.pitman.myfeeds.data.repository.FeedRepository
import io.pitman.myfeeds.data.settings.AppSettings
import io.pitman.myfeeds.data.settings.FontSize
import io.pitman.myfeeds.data.settings.SettingsDataStore
import io.pitman.myfeeds.refresh.FeedRefreshScheduling
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val feedRepository: FeedRepository,
    private val opmlImporter: OpmlImporter,
    private val opmlExporter: OpmlExporter,
    private val feedRefreshScheduler: FeedRefreshScheduling,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val settings: StateFlow<AppSettings> =
        settingsDataStore.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    /** One-shot "Add default feeds" result for a Snackbar; cleared via [consumeAddDefaultFeedsMessage]. */
    private val _addDefaultFeedsMessage = MutableStateFlow<String?>(null)
    val addDefaultFeedsMessage: StateFlow<String?> = _addDefaultFeedsMessage

    fun consumeAddDefaultFeedsMessage() {
        _addDefaultFeedsMessage.value = null
    }

    fun setUpdateIntervalMinutes(minutes: Long) {
        viewModelScope.launch {
            settingsDataStore.setUpdateIntervalMinutes(minutes)
            feedRefreshScheduler.schedule(minutes)
        }
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

    fun setFeedRefreshConcurrency(count: Int) {
        viewModelScope.launch { settingsDataStore.setFeedRefreshConcurrency(count) }
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

    fun setAutoDeleteFinishedDownloads(value: Boolean) {
        viewModelScope.launch { settingsDataStore.setAutoDeleteFinishedDownloads(value) }
    }

    fun setNotifyOnNewItems(value: Boolean) {
        viewModelScope.launch { settingsDataStore.setNotifyOnNewItems(value) }
    }

    fun clearPodcasts() {
        viewModelScope.launch { feedRepository.clearAllEnclosurePositions() }
    }

    fun addDefaultFeeds() {
        viewModelScope.launch {
            val document = try {
                context.assets.open("default_feeds.opml").use { OpmlParser.parse(it) }
            } catch (_: Exception) {
                null
            }
            _addDefaultFeedsMessage.value = if (document == null) {
                context.getString(R.string.add_feed_invalid_opml)
            } else {
                val importedCount = opmlImporter.import(document)
                if (importedCount > 0) {
                    context.getString(R.string.add_feed_imported_count, importedCount)
                } else {
                    context.getString(R.string.add_feed_no_feeds_found_in_opml)
                }
            }
        }
    }

    fun removeAllFeeds() {
        viewModelScope.launch { feedRepository.removeAllFeeds() }
    }

    fun resetSettings() {
        viewModelScope.launch { settingsDataStore.reset() }
    }

    /** Writes the OPML export to a cache file for the caller to share via [android.content.Intent.ACTION_SEND]. */
    suspend fun exportOpmlToFile(): File {
        val opml = opmlExporter.export()
        val file = File(context.cacheDir, "myfeeds-export.opml")
        file.writeText(opml)
        return file
    }

    /**
     * Writes the OPML export directly to a user-chosen destination (issue #151), e.g. from
     * [androidx.activity.result.contract.ActivityResultContracts.CreateDocument] -- no storage
     * permission needed since the system picker itself grants access to the chosen [uri].
     */
    suspend fun writeOpmlTo(uri: Uri) {
        val opml = opmlExporter.export()
        context.contentResolver.openOutputStream(uri)?.use { it.write(opml.toByteArray()) }
    }
}
