package io.pitman.myfeeds.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class SettingsDataStore @Inject constructor(private val dataStore: DataStore<Preferences>) {
    val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            updateIntervalMinutes = prefs[Keys.UPDATE_INTERVAL_MINUTES] ?: AppSettings().updateIntervalMinutes,
            isFirstRun = prefs[Keys.IS_FIRST_RUN] ?: AppSettings().isFirstRun,
            listFontSize = prefs[Keys.LIST_FONT_SIZE]?.let { FontSize.entries[it] } ?: AppSettings().listFontSize,
            feedListFontSize = prefs[Keys.FEED_LIST_FONT_SIZE]?.let { FontSize.entries[it] }
                ?: AppSettings().feedListFontSize,
            articleFontSize = prefs[Keys.ARTICLE_FONT_SIZE]?.let { FontSize.entries[it] }
                ?: AppSettings().articleFontSize,
            enableImageDisplay = prefs[Keys.ENABLE_IMAGE_DISPLAY] ?: AppSettings().enableImageDisplay,
            maxArticles = prefs[Keys.MAX_ARTICLES] ?: AppSettings().maxArticles,
            defaultToAllArticleView = prefs[Keys.DEFAULT_TO_ALL_ARTICLE_VIEW]
                ?: AppSettings().defaultToAllArticleView,
            allowPodcastDownloadOnBattery = prefs[Keys.ALLOW_PODCAST_DOWNLOAD_ON_BATTERY]
                ?: AppSettings().allowPodcastDownloadOnBattery,
            allowPodcastDownloadOnCellular = prefs[Keys.ALLOW_PODCAST_DOWNLOAD_ON_CELLULAR]
                ?: AppSettings().allowPodcastDownloadOnCellular,
            allowPodcastStreaming = prefs[Keys.ALLOW_PODCAST_STREAMING] ?: AppSettings().allowPodcastStreaming,
            notifyOnNewItems = prefs[Keys.NOTIFY_ON_NEW_ITEMS] ?: AppSettings().notifyOnNewItems,
            lastImportUrl = prefs[Keys.LAST_IMPORT_URL],
            lastFeedUpdateEpochMillis = prefs[Keys.LAST_FEED_UPDATE_EPOCH_MILLIS],
        )
    }

    suspend fun setUpdateIntervalMinutes(minutes: Long) {
        dataStore.edit { it[Keys.UPDATE_INTERVAL_MINUTES] = minutes }
    }

    suspend fun setFirstRunComplete() {
        dataStore.edit { it[Keys.IS_FIRST_RUN] = false }
    }

    suspend fun setListFontSize(size: FontSize) {
        dataStore.edit { it[Keys.LIST_FONT_SIZE] = size.ordinal }
    }

    suspend fun setFeedListFontSize(size: FontSize) {
        dataStore.edit { it[Keys.FEED_LIST_FONT_SIZE] = size.ordinal }
    }

    suspend fun setArticleFontSize(size: FontSize) {
        dataStore.edit { it[Keys.ARTICLE_FONT_SIZE] = size.ordinal }
    }

    suspend fun setEnableImageDisplay(enabled: Boolean) {
        dataStore.edit { it[Keys.ENABLE_IMAGE_DISPLAY] = enabled }
    }

    suspend fun setMaxArticles(count: Int) {
        dataStore.edit { it[Keys.MAX_ARTICLES] = count }
    }

    suspend fun setDefaultToAllArticleView(value: Boolean) {
        dataStore.edit { it[Keys.DEFAULT_TO_ALL_ARTICLE_VIEW] = value }
    }

    suspend fun setAllowPodcastDownloadOnBattery(value: Boolean) {
        dataStore.edit { it[Keys.ALLOW_PODCAST_DOWNLOAD_ON_BATTERY] = value }
    }

    suspend fun setAllowPodcastDownloadOnCellular(value: Boolean) {
        dataStore.edit { it[Keys.ALLOW_PODCAST_DOWNLOAD_ON_CELLULAR] = value }
    }

    suspend fun setAllowPodcastStreaming(value: Boolean) {
        dataStore.edit { it[Keys.ALLOW_PODCAST_STREAMING] = value }
    }

    suspend fun setNotifyOnNewItems(value: Boolean) {
        dataStore.edit { it[Keys.NOTIFY_ON_NEW_ITEMS] = value }
    }

    suspend fun setLastImportUrl(url: String?) {
        dataStore.edit {
            if (url == null) it.remove(Keys.LAST_IMPORT_URL) else it[Keys.LAST_IMPORT_URL] = url
        }
    }

    suspend fun setLastFeedUpdateEpochMillis(epochMillis: Long) {
        dataStore.edit { it[Keys.LAST_FEED_UPDATE_EPOCH_MILLIS] = epochMillis }
    }

    /** Mirrors the original SettingsViewModel.Reset(): clears all settings back to defaults. */
    suspend fun reset() {
        dataStore.edit { it.clear() }
    }

    private object Keys {
        val UPDATE_INTERVAL_MINUTES = longPreferencesKey("update_interval_minutes")
        val IS_FIRST_RUN = booleanPreferencesKey("is_first_run")
        val LIST_FONT_SIZE = intPreferencesKey("list_font_size")
        val FEED_LIST_FONT_SIZE = intPreferencesKey("feed_list_font_size")
        val ARTICLE_FONT_SIZE = intPreferencesKey("article_font_size")
        val ENABLE_IMAGE_DISPLAY = booleanPreferencesKey("enable_image_display")
        val MAX_ARTICLES = intPreferencesKey("max_articles")
        val DEFAULT_TO_ALL_ARTICLE_VIEW = booleanPreferencesKey("default_to_all_article_view")
        val ALLOW_PODCAST_DOWNLOAD_ON_BATTERY = booleanPreferencesKey("allow_podcast_download_on_battery")
        val ALLOW_PODCAST_DOWNLOAD_ON_CELLULAR = booleanPreferencesKey("allow_podcast_download_on_cellular")
        val ALLOW_PODCAST_STREAMING = booleanPreferencesKey("allow_podcast_streaming")
        val NOTIFY_ON_NEW_ITEMS = booleanPreferencesKey("notify_on_new_items")
        val LAST_IMPORT_URL = stringPreferencesKey("last_import_url")
        val LAST_FEED_UPDATE_EPOCH_MILLIS = longPreferencesKey("last_feed_update_epoch_millis")
    }
}
