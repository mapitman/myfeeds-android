package io.pitman.myfeeds.feedproperties

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.pitman.myfeeds.data.local.AppDatabase
import io.pitman.myfeeds.data.local.Feed
import io.pitman.myfeeds.data.repository.FeedRepository
import io.pitman.myfeeds.data.settings.SettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
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

/**
 * Config pins Robolectric to API 35 -- Robolectric 4.14 doesn't support compileSdk 36 yet.
 *
 * ViewModel is routed through a real ViewModelStore that's cleared in tearDown -- otherwise
 * nothing cancels its viewModelScope between tests since it's constructed directly rather than
 * via a ViewModelProvider (see the settings-screen PR for the full explanation).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FeedPropertiesViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var repository: FeedRepository
    private lateinit var settingsDataStore: SettingsDataStore
    private var feedId: Long = 0
    private val viewModelStore = ViewModelStore()

    private fun createViewModel(): FeedPropertiesViewModel =
        FeedPropertiesViewModel(
            savedStateHandle = SavedStateHandle(mapOf("feedId" to feedId)),
            feedRepository = repository,
            settingsDataStore = settingsDataStore,
        ).also { viewModelStore.put("feedProperties", it) }

    @Before
    fun setUp() = runTest(testDispatcher) {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repository = FeedRepository(db.feedDao(), db.feedItemDao(), db.queueDao())
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            produceFile = { File(tempFolder.newFolder(), "test.preferences_pb") },
        )
        settingsDataStore = SettingsDataStore(dataStore)

        feedId = repository.subscribe(Feed(title = "A Feed", feedUrl = "https://example.com/feed.xml"))
    }

    @After
    fun tearDown() {
        viewModelStore.clear()
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun uiState_defaultsToFetchedTitleAndGlobalMax() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        val state = viewModel.uiState.first { it.displayTitle == "A Feed" }

        assertNull(state.itemsToKeep)
        assertEquals(20, state.globalMaxArticles)
        assertEquals("https://example.com/feed.xml", state.feedUrl)
    }

    @Test
    fun setTitle_setsUserTitle() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        viewModel.uiState.first { it.displayTitle == "A Feed" }

        viewModel.setTitle("Custom Name")

        val feed = repository.observeFeed(feedId).first { it?.userTitle == "Custom Name" }
        assertEquals("Custom Name", feed?.userTitle)
    }

    @Test
    fun setTitle_blankOrOriginal_clearsUserTitle() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        viewModel.uiState.first { it.displayTitle == "A Feed" }
        viewModel.setTitle("Custom Name")
        repository.observeFeed(feedId).first { it?.userTitle == "Custom Name" }

        viewModel.setTitle("A Feed")

        val feed = repository.observeFeed(feedId).first { it?.userTitle == null }
        assertNull(feed?.userTitle)
    }

    @Test
    fun setItemsToKeep_persistsOverride() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        viewModel.uiState.first { it.displayTitle == "A Feed" }

        viewModel.setItemsToKeep(50)

        val state = viewModel.uiState.first { it.itemsToKeep == 50 }
        assertEquals(50, state.itemsToKeep)
    }

    @Test
    fun setAutoQueueEnabled_persists() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        viewModel.uiState.first { it.displayTitle == "A Feed" }

        viewModel.setAutoQueueEnabled(true)

        val state = viewModel.uiState.first { it.autoQueueEnabled }
        assertTrue(state.autoQueueEnabled)
    }

    @Test
    fun setAutoQueueMaxCount_persists() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        viewModel.uiState.first { it.displayTitle == "A Feed" }

        viewModel.setAutoQueueMaxCount(3)

        val state = viewModel.uiState.first { it.autoQueueMaxCount == 3 }
        assertEquals(3, state.autoQueueMaxCount)
    }

    @Test
    fun setPlaybackSpeed_persists() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        viewModel.uiState.first { it.displayTitle == "A Feed" }

        viewModel.setPlaybackSpeed(1.5f)

        val state = viewModel.uiState.first { it.playbackSpeed == 1.5f }
        assertEquals(1.5f, state.playbackSpeed)
    }

    @Test
    fun unsubscribe_deletesFeedAndReflectsInUiState() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        viewModel.uiState.first { it.displayTitle == "A Feed" }

        viewModel.unsubscribe()

        val state = viewModel.uiState.first { it.isUnsubscribed }
        assertTrue(state.isUnsubscribed)
        assertNull(db.feedDao().getById(feedId))
    }
}
