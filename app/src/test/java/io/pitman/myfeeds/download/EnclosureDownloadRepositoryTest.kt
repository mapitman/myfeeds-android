package io.pitman.myfeeds.download

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.pitman.myfeeds.data.local.AppDatabase
import io.pitman.myfeeds.data.local.Category
import io.pitman.myfeeds.data.local.Feed
import io.pitman.myfeeds.data.local.FeedItem
import io.pitman.myfeeds.data.repository.FeedRepository
import io.pitman.myfeeds.data.settings.SettingsDataStore
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
class EnclosureDownloadRepositoryTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var repository: FeedRepository
    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var downloadRepository: EnclosureDownloadRepository
    private val enqueuedCalls = mutableListOf<Triple<String, Boolean, Boolean>>()
    private val cancelledCalls = mutableListOf<String>()
    private var feedId: Long = 0

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repository = FeedRepository(db.feedDao(), db.feedItemDao())
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            produceFile = { File(tempFolder.newFolder(), "test.preferences_pb") },
        )
        settingsDataStore = SettingsDataStore(dataStore)
        downloadRepository = EnclosureDownloadRepository(
            feedRepository = repository,
            downloadScheduling = object : DownloadScheduling {
                override fun enqueueDownload(itemId: String, allowCellular: Boolean, allowOnBattery: Boolean) {
                    enqueuedCalls += Triple(itemId, allowCellular, allowOnBattery)
                }

                override fun cancelDownload(itemId: String) {
                    cancelledCalls += itemId
                }
            },
            settingsDataStore = settingsDataStore,
        )
        feedId = 0
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seedFeedAndItem(enclosureUrl: String?, enclosureType: String? = "audio/mpeg"): Long {
        val categoryId = db.categoryDao().insert(Category(name = "Tech"))
        val id = repository.subscribe(Feed(categoryId = categoryId, title = "A Feed"))
        repository.upsertItems(
            listOf(
                FeedItem(
                    id = "item-1",
                    feedId = id,
                    itemGuid = "g1",
                    enclosureUrl = enclosureUrl,
                    enclosureType = enclosureUrl?.let { enclosureType },
                ),
            ),
        )
        return id
    }

    @Test
    fun startDownload_passesCurrentSettingsToScheduler() = runTest {
        seedFeedAndItem("https://example.com/episode.mp3")
        settingsDataStore.setAllowPodcastDownloadOnCellular(true)
        settingsDataStore.setAllowPodcastDownloadOnBattery(false)
        val item = repository.getItem("item-1")!!

        downloadRepository.startDownload(item)

        assertEquals(listOf(Triple("item-1", true, false)), enqueuedCalls)
    }

    @Test
    fun startDownload_noEnclosureUrl_doesNothing() = runTest {
        seedFeedAndItem(enclosureUrl = null)
        val item = repository.getItem("item-1")!!

        downloadRepository.startDownload(item)

        assertTrue(enqueuedCalls.isEmpty())
    }

    @Test
    fun startDownload_nonAudioEnclosure_doesNothing() = runTest {
        // e.g. a featured-image enclosure on a plain article, not a podcast episode.
        seedFeedAndItem("https://example.com/cover.jpg", enclosureType = "image/jpeg")
        val item = repository.getItem("item-1")!!

        downloadRepository.startDownload(item)

        assertTrue(enqueuedCalls.isEmpty())
    }

    @Test
    fun deleteDownload_cancelsWorkDeletesFileAndClearsPath() = runTest {
        seedFeedAndItem("https://example.com/episode.mp3")
        val file = tempFolder.newFile("downloaded.mp3")
        repository.setDownloadedFilePath("item-1", file.absolutePath)
        val item = repository.getItem("item-1")!!

        downloadRepository.deleteDownload(item)

        assertEquals(listOf("item-1"), cancelledCalls)
        assertFalse(file.exists())
        assertNull(repository.getItem("item-1")?.downloadedFilePath)
    }
}
