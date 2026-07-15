package io.pitman.myfeeds.articlelist

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
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
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var repository: FeedRepository
    private lateinit var settingsDataStore: SettingsDataStore
    private var feedId: Long = 0

    private fun createViewModel(): ArticleListViewModel = ArticleListViewModel(
        savedStateHandle = SavedStateHandle(mapOf("feedId" to feedId)),
        feedRepository = repository,
        settingsDataStore = settingsDataStore,
    )

    @Before
    fun setUp() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
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
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun defaultState_showsUnreadOnlyByDefault() = runTest {
        val viewModel = createViewModel()

        val state = viewModel.uiState.first { it.feedTitle == "A Feed" }

        assertTrue(state.showUnreadOnly)
        assertEquals(listOf("unread-1"), state.articles.map { it.id })
        assertEquals(1, state.unreadCount)
    }

    @Test
    fun defaultToAllArticleViewSetting_showsAllByDefault() = runTest {
        settingsDataStore.setDefaultToAllArticleView(true)
        val viewModel = createViewModel()

        val state = viewModel.uiState.first { it.feedTitle == "A Feed" }

        assertFalse(state.showUnreadOnly)
        assertEquals(2, state.articles.size)
    }

    @Test
    fun setShowUnreadOnly_switchesArticleList() = runTest {
        val viewModel = createViewModel()
        viewModel.uiState.first { it.feedTitle == "A Feed" }

        viewModel.setShowUnreadOnly(false)
        val state = viewModel.uiState.first { !it.showUnreadOnly && it.articles.size == 2 }

        assertEquals(2, state.articles.size)
    }

    @Test
    fun toggleSelection_entersAndExitsSelectionMode() = runTest {
        val viewModel = createViewModel()
        viewModel.uiState.first { it.feedTitle == "A Feed" }

        viewModel.toggleSelection("unread-1")
        assertTrue(viewModel.uiState.value.isSelectionMode)
        assertEquals(setOf("unread-1"), viewModel.uiState.value.selectedIds)

        viewModel.toggleSelection("unread-1")
        assertFalse(viewModel.uiState.value.isSelectionMode)
    }

    @Test
    fun markSelectedRead_updatesItemsAndClearsSelection() = runTest {
        val viewModel = createViewModel()
        viewModel.uiState.first { it.feedTitle == "A Feed" }
        viewModel.toggleSelection("unread-1")

        viewModel.markSelectedRead(true)
        viewModel.uiState.first { !it.isSelectionMode }

        val items = repository.observeItems(feedId).first()
        assertTrue(items.first { it.id == "unread-1" }.isRead)
        assertEquals(0, repository.observeUnreadCount(feedId).first())
    }

    @Test
    fun deleteSelected_removesItemsAndClearsSelection() = runTest {
        val viewModel = createViewModel()
        viewModel.setShowUnreadOnly(false)
        viewModel.uiState.first { !it.showUnreadOnly && it.articles.size == 2 }
        viewModel.toggleSelection("read-1")

        viewModel.deleteSelected()
        viewModel.uiState.first { !it.isSelectionMode }

        val items = repository.observeItems(feedId).first()
        assertEquals(listOf("unread-1"), items.map { it.id })
    }
}
