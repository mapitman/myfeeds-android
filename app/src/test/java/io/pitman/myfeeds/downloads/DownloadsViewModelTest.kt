package io.pitman.myfeeds.downloads

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.pitman.myfeeds.data.local.AppDatabase
import io.pitman.myfeeds.data.local.Feed
import io.pitman.myfeeds.data.local.FeedItem
import io.pitman.myfeeds.data.repository.FeedRepository
import io.pitman.myfeeds.data.settings.SettingsDataStore
import io.pitman.myfeeds.download.DownloadScheduling
import io.pitman.myfeeds.download.EnclosureDownloadRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Config pins Robolectric to API 35 -- Robolectric 4.14 doesn't support compileSdk 36 yet. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DownloadsViewModelTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var repository: FeedRepository
    private lateinit var viewModel: DownloadsViewModel
    private var feedId: Long = 0

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repository = FeedRepository(db.feedDao(), db.feedItemDao(), db.queueDao())
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            produceFile = { File(tempFolder.newFolder(), "test.preferences_pb") },
        )
        val downloadRepository = EnclosureDownloadRepository(
            feedRepository = repository,
            downloadScheduling = object : DownloadScheduling {
                override fun enqueueDownload(itemId: String, allowCellular: Boolean, allowOnBattery: Boolean) {}
                override fun cancelDownload(itemId: String) {}
            },
            settingsDataStore = SettingsDataStore(dataStore),
        )
        viewModel = DownloadsViewModel(repository, downloadRepository)

        feedId = repository.subscribe(Feed(title = "A Podcast"))
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun uiState_completedDownload_usesFileSizeOnDisk() = runTest {
        val file = tempFolder.newFile("episode.mp3").apply { writeBytes(ByteArray(1024)) }
        repository.insertItems(
            listOf(FeedItem(id = "ep-1", feedId = feedId, title = "Episode 1", itemGuid = "g1", downloadedFilePath = file.absolutePath)),
        )

        val state = viewModel.uiState.first { it.episodes.isNotEmpty() }

        val row = state.episodes.single()
        assertFalse(row.isInProgress)
        assertEquals(1024L, row.sizeBytes)
        assertEquals(1024L, state.totalBytes)
    }

    @Test
    fun uiState_inProgressDownload_usesDownloadedBytes() = runTest {
        repository.insertItems(
            listOf(FeedItem(id = "ep-1", feedId = feedId, title = "Episode 1", itemGuid = "g1", downloadedBytes = 512L)),
        )

        val state = viewModel.uiState.first { it.episodes.isNotEmpty() }

        val row = state.episodes.single()
        assertTrue(row.isInProgress)
        assertEquals(512L, row.sizeBytes)
    }

    @Test
    fun delete_removesDownloadAndClearsState() = runTest {
        val file = tempFolder.newFile("episode.mp3").apply { writeBytes(ByteArray(1024)) }
        repository.insertItems(
            listOf(FeedItem(id = "ep-1", feedId = feedId, title = "Episode 1", itemGuid = "g1", downloadedFilePath = file.absolutePath)),
        )
        val item = viewModel.uiState.first { it.episodes.isNotEmpty() }.episodes.single().item

        viewModel.delete(item)

        assertTrue(repository.observeDownloadedItems().first().isEmpty())
        assertFalse(file.exists())
    }
}
