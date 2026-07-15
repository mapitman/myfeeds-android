package io.pitman.myfeeds.articlelist

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.pitman.myfeeds.data.local.AppDatabase
import io.pitman.myfeeds.data.local.Category
import io.pitman.myfeeds.data.local.Feed
import io.pitman.myfeeds.data.local.FeedItem
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
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ArticleListViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var repository: FeedRepository
    private lateinit var settingsDataStore: SettingsDataStore
    private var feedId: Long = 0
    private var nextViewModelKey = 0

    // ViewModels here are constructed directly (not via a real ViewModelProvider), so nothing
    // would otherwise call ViewModel.clear() to cancel their viewModelScope between tests. A
    // leaked WhileSubscribed collector then outlives the test method and races the next test's
    // Dispatchers.setMain/resetMain. Routing creation through a real ViewModelStore and clearing
    // it in tearDown cancels those coroutines properly, the same way the Android framework does.
    private val viewModelStore = ViewModelStore()

    private fun createViewModel(): ArticleListViewModel =
        ArticleListViewModel(
            savedStateHandle = SavedStateHandle(mapOf("feedId" to feedId)),
            feedRepository = repository,
            settingsDataStore = settingsDataStore,
        ).also { viewModelStore.put("articleList-${nextViewModelKey++}", it) }

    @Before
    fun setUp() = runTest(testDispatcher) {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repository = FeedRepository(db.feedDao(), db.feedItemDao())
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            produceFile = { File(tempFolder.newFolder(), "test.preferences_pb") },
        )
        settingsDataStore = SettingsDataStore(dataStore)

        val categoryId = db.categoryDao().insert(Category(name = "Tech"))
        feedId = repository.subscribe(Feed(categoryId = categoryId, title = "A Feed"))
        repository.upsertItems(
            listOf(
                FeedItem(id = "unread-1", feedId = feedId, title = "Unread One", itemGuid = "g1", isRead = false),
                FeedItem(id = "read-1", feedId = feedId, title = "Read One", itemGuid = "g2", isRead = true),
            ),
        )
    }

    @After
    fun tearDown() {
        viewModelStore.clear()
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun defaultState_showsUnreadOnlyByDefault() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        val state = viewModel.uiState.first { it.feedTitle == "A Feed" }

        assertTrue(state.showUnreadOnly)
        assertEquals(listOf("unread-1"), state.articles.map { it.id })
        assertEquals(1, state.unreadCount)
    }

    @Test
    fun defaultToAllArticleViewSetting_showsAllByDefault() = runTest(testDispatcher) {
        settingsDataStore.setDefaultToAllArticleView(true)
        val viewModel = createViewModel()

        val state = viewModel.uiState.first { it.feedTitle == "A Feed" }

        assertFalse(state.showUnreadOnly)
        assertEquals(2, state.articles.size)
    }

    @Test
    fun setShowUnreadOnly_switchesArticleList() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        viewModel.uiState.first { it.feedTitle == "A Feed" }

        viewModel.setShowUnreadOnly(false)
        val state = viewModel.uiState.first { !it.showUnreadOnly && it.articles.size == 2 }

        assertEquals(2, state.articles.size)
    }

    @Test
    fun toggleSelection_entersAndExitsSelectionMode() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        viewModel.uiState.first { it.feedTitle == "A Feed" }

        viewModel.toggleSelection("unread-1")
        val selected = viewModel.uiState.first { it.isSelectionMode }
        assertEquals(setOf("unread-1"), selected.selectedIds)

        viewModel.toggleSelection("unread-1")
        viewModel.uiState.first { !it.isSelectionMode }
    }

    @Test
    fun markSelectedRead_updatesItemsAndClearsSelection() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        viewModel.uiState.first { it.feedTitle == "A Feed" }
        viewModel.toggleSelection("unread-1")
        viewModel.uiState.first { it.isSelectionMode }

        viewModel.markSelectedRead(true)
        viewModel.uiState.first { !it.isSelectionMode }

        val items = repository.observeItems(feedId).first { items -> items.all { it.isRead } }
        assertTrue(items.first { it.id == "unread-1" }.isRead)
        assertEquals(0, repository.observeUnreadCount(feedId).first())
    }

    @Test
    fun deleteSelected_removesItemsAndClearsSelection() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        viewModel.setShowUnreadOnly(false)
        viewModel.uiState.first { !it.showUnreadOnly && it.articles.size == 2 }
        viewModel.toggleSelection("read-1")
        viewModel.uiState.first { it.isSelectionMode }

        viewModel.deleteSelected()
        viewModel.uiState.first { !it.isSelectionMode }

        val items = repository.observeItems(feedId).first { it.size == 1 }
        assertEquals(listOf("unread-1"), items.map { it.id })
    }
}
