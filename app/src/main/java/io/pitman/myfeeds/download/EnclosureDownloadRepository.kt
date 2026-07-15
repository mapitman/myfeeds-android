package io.pitman.myfeeds.download

import io.pitman.myfeeds.data.local.FeedItem
import io.pitman.myfeeds.data.local.isPodcastEpisode
import io.pitman.myfeeds.data.repository.FeedRepository
import io.pitman.myfeeds.data.settings.SettingsDataStore
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject

/** UI-facing entry point for starting/cancelling a single episode's download (issue #23). */
class EnclosureDownloadRepository @Inject constructor(
    private val feedRepository: FeedRepository,
    private val downloadScheduling: DownloadScheduling,
    private val settingsDataStore: SettingsDataStore,
) {
    suspend fun startDownload(item: FeedItem) {
        if (!item.isPodcastEpisode) return
        val settings = settingsDataStore.settings.first()
        downloadScheduling.enqueueDownload(
            itemId = item.id,
            allowCellular = settings.allowPodcastDownloadOnCellular,
            allowOnBattery = settings.allowPodcastDownloadOnBattery,
        )
    }

    suspend fun deleteDownload(item: FeedItem) {
        downloadScheduling.cancelDownload(item.id)
        item.downloadedFilePath?.let { File(it).delete() }
        feedRepository.setDownloadedFilePath(item.id, null)
    }
}
