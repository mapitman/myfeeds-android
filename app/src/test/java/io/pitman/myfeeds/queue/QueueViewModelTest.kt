package io.pitman.myfeeds.queue

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.pitman.myfeeds.data.local.AppDatabase
import io.pitman.myfeeds.data.local.Feed
import io.pitman.myfeeds.data.local.FeedItem
import io.pitman.myfeeds.data.repository.FeedRepository
import io.pitman.myfeeds.data.repository.QueueRepository
import io.pitman.myfeeds.data.settings.SettingsDataStore
import io.pitman.myfeeds.playback.PlaybackController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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

/** Config pins Robolectric to API 35 -- Robolectric 4.14 doesn't support compileSdk 36 yet. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class QueueViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val viewModelStore = ViewModelStore()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var feedRepository: FeedRepository
    private lateinit var queueRepository: QueueRepository
    private lateinit var playbackController: PlaybackController
    private lateinit var viewModel: QueueViewModel
    private var feedId: Long = 0

    @Before
    fun setUp() = runTest(testDispatcher) {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        feedRepository = FeedRepository(db.feedDao(), db.feedItemDao())
        queueRepository = QueueRepository(db.queueDao())
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            produceFile = { File(tempFolder.newFolder(), "test.preferences_pb") },
        )
        playbackController = PlaybackController(context, SettingsDataStore(dataStore), feedRepository, queueRepository)

        feedId = feedRepository.subscribe(Feed(title = "A Feed"))
        feedRepository.upsertItems(
            listOf(
                FeedItem(id = "ep-1", feedId = feedId, title = "Episode 1", itemGuid = "g1"),
                FeedItem(id = "ep-2", feedId = feedId, title = "Episode 2", itemGuid = "g2"),
            ),
        )
        queueRepository.addToEnd("ep-1")
        queueRepository.addToEnd("ep-2")

        viewModel = QueueViewModel(queueRepository, feedRepository, playbackController)
            .also { viewModelStore.put("queue", it) }
    }

    @After
    fun tearDown() {
        viewModelStore.clear()
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun queue_reflectsRepositoryOrder() = runTest(testDispatcher) {
        val state = viewModel.queue.first { it.size == 2 }

        assertEquals(listOf("ep-1", "ep-2"), state.map { it.item.id })
    }

    @Test
    fun reorder_updatesQueueOrder() = runTest(testDispatcher) {
        viewModel.queue.first { it.size == 2 }

        viewModel.reorder(listOf("ep-2", "ep-1"))

        val state = viewModel.queue.first { it.map { episode -> episode.item.id } == listOf("ep-2", "ep-1") }
        assertEquals(listOf("ep-2", "ep-1"), state.map { it.item.id })
    }

    @Test
    fun remove_dropsItemFromQueue() = runTest(testDispatcher) {
        viewModel.queue.first { it.size == 2 }

        viewModel.remove("ep-1")

        val state = viewModel.queue.first { it.size == 1 }
        assertEquals(listOf("ep-2"), state.map { it.item.id })
    }

    @Test
    fun playNow_removesEpisodeFromQueue() = runTest(testDispatcher) {
        val episode = viewModel.queue.first { it.size == 2 }.first()

        viewModel.playNow(episode)

        val state = viewModel.queue.first { it.size == 1 }
        assertEquals(listOf("ep-2"), state.map { it.item.id })
    }
}
