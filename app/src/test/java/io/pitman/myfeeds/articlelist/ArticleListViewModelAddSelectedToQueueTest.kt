package io.pitman.myfeeds.articlelist

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.pitman.myfeeds.TrackedViewModelStore
import io.pitman.myfeeds.data.feed.AutoQueueAndDownloadEnforcer
import io.pitman.myfeeds.data.feed.FeedFetcher
import io.pitman.myfeeds.data.feed.FeedUpdateEngine
import io.pitman.myfeeds.data.local.AppDatabase
import io.pitman.myfeeds.data.local.Feed
import io.pitman.myfeeds.data.local.FeedItem
import io.pitman.myfeeds.data.repository.FeedRepository
import io.pitman.myfeeds.data.repository.QueueRepository
import io.pitman.myfeeds.data.settings.SettingsDataStore
import io.pitman.myfeeds.download.DownloadScheduling
import io.pitman.myfeeds.download.EnclosureDownloadRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Config pins Robolectric to API 35 -- Robolectric 4.14 doesn't support compileSdk 36 yet.
 *
 * Split out of ArticleListViewModelTest (issue #215): this single test hangs for the full
 * runTest timeout whenever it runs after certain other tests in that class, in a JVM-static
 * cross-test way that TrackedViewModelStore's quiescent teardown doesn't fully cover (a
 * timing-dependent "lost wakeup", not a deadlock -- see #215 for the diagnostic writeup). Giving
 * it a dedicated class combined with Gradle's `forkEvery = 1` isolates it into its own JVM, with
 * no preceding test in the same process to leave behind whatever's causing the corruption.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ArticleListViewModelAddSelectedToQueueTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var repository: FeedRepository
    private lateinit var queueRepository: QueueRepository
    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var feedUpdateEngine: FeedUpdateEngine
    private lateinit var autoQueueAndDownloadEnforcer: AutoQueueAndDownloadEnforcer
    private lateinit var context: android.content.Context
    private var feedId: Long = 0
    private var nextViewModelKey = 0

    // Cleared *and joined* in tearDown so no ViewModel coroutine is still in flight when
    // Dispatchers.resetMain runs -- see TrackedViewModelStore's doc for the full leak mechanics
    // behind the #54/#60 flakiness this prevents.
    private val viewModelStore = TrackedViewModelStore()

    private fun createViewModel(): ArticleListViewModel =
        ArticleListViewModel(
            savedStateHandle = SavedStateHandle(mapOf("feedId" to feedId)),
            feedRepository = repository,
            feedUpdateEngine = feedUpdateEngine,
            autoQueueAndDownloadEnforcer = autoQueueAndDownloadEnforcer,
            queueRepository = queueRepository,
            settingsDataStore = settingsDataStore,
            context = context,
        ).also { viewModelStore.put("articleList-${nextViewModelKey++}", it) }

    @Before
    fun setUp() = runTest(testDispatcher) {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repository = FeedRepository(db.feedDao(), db.feedItemDao(), db.queueDao())
        queueRepository = QueueRepository(db.queueDao())
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            produceFile = { File(tempFolder.newFolder(), "test.preferences_pb") },
        )
        settingsDataStore = SettingsDataStore(dataStore)
        feedUpdateEngine = FeedUpdateEngine(FeedFetcher(OkHttpClient()), repository, settingsDataStore)
        val downloadRepository = EnclosureDownloadRepository(
            feedRepository = repository,
            downloadScheduling = object : DownloadScheduling {
                override fun enqueueDownload(itemId: String, allowCellular: Boolean, allowOnBattery: Boolean) {}
                override fun cancelDownload(itemId: String) {}
            },
            settingsDataStore = settingsDataStore,
        )
        autoQueueAndDownloadEnforcer = AutoQueueAndDownloadEnforcer(repository, downloadRepository, queueRepository)

        feedId = repository.subscribe(Feed(title = "A Feed"))
        repository.insertItems(
            listOf(
                FeedItem(id = "unread-1", feedId = feedId, title = "Unread One", itemGuid = "g1", isRead = false),
                FeedItem(id = "read-1", feedId = feedId, title = "Read One", itemGuid = "g2", isRead = true),
            ),
        )
    }

    @After
    fun tearDown() {
        // Inside runTest (same scheduler as Dispatchers.Main) so the scheduler keeps getting
        // pumped while clearAndJoin waits out in-flight ViewModel coroutines (issues #54/#60).
        runTest(testDispatcher) { viewModelStore.clearAndJoin() }
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun addSelectedToQueue_queuesOnlyPodcastEpisodesAndClearsSelection() = runTest(testDispatcher) {
        // issue #159: selection mode isn't podcast-specific, so a plain article ("read-1", no
        // enclosure) mixed into the selection should be silently skipped rather than queued.
        repository.insertItems(
            listOf(
                FeedItem(
                    id = "episode-1",
                    feedId = feedId,
                    title = "Episode One",
                    itemGuid = "g-episode-1",
                    enclosureUrl = "https://example.com/ep1.mp3",
                    enclosureType = "audio/mpeg",
                ),
                FeedItem(
                    id = "episode-2",
                    feedId = feedId,
                    title = "Episode Two",
                    itemGuid = "g-episode-2",
                    enclosureUrl = "https://example.com/ep2.mp3",
                    enclosureType = "audio/mpeg",
                ),
            ),
        )
        val viewModel = createViewModel()
        viewModel.setShowUnreadOnly(false)
        viewModel.uiState.first { !it.showUnreadOnly && it.articles.size == 4 }
        viewModel.toggleSelection("episode-1")
        viewModel.toggleSelection("episode-2")
        viewModel.toggleSelection("read-1")
        viewModel.uiState.first { it.selectedIds.size == 3 }

        viewModel.addSelectedToQueue()
        viewModel.uiState.first { !it.isSelectionMode }

        val queue = queueRepository.observeQueue().first { it.size == 2 }
        assertEquals(setOf("episode-1", "episode-2"), queue.map { it.item.id }.toSet())
    }
}
