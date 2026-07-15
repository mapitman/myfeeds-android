package io.pitman.myfeeds.feedlist

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.pitman.myfeeds.data.DefaultFeedsSeeder
import io.pitman.myfeeds.data.feed.FeedFetcher
import io.pitman.myfeeds.data.feed.FeedUpdateEngine
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
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
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
class FeedListViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var repository: FeedRepository
    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var viewModel: FeedListViewModel

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
        // Skips DefaultFeedsSeeder's bundled-OPML seeding so the test only sees fixture data
        // inserted below, not the app's default Tech/Mobile/News starter feeds.
        settingsDataStore.setFirstRunComplete()

        viewModel = FeedListViewModel(
            seeder = DefaultFeedsSeeder(context, db.categoryDao(), db.feedDao(), settingsDataStore),
            categoryDao = db.categoryDao(),
            feedRepository = repository,
            feedUpdateEngine = FeedUpdateEngine(FeedFetcher(OkHttpClient()), repository),
            settingsDataStore = settingsDataStore,
            context = context,
        )
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun uiState_podcastFeedAppearsInBothItsCategoryAndPodcastsSection() = runTest(testDispatcher) {
        val categoryId = db.categoryDao().insert(Category(name = "Tech"))
        val podcastFeedId = repository.subscribe(Feed(categoryId = categoryId, title = "Podcast Feed"))
        val articleFeedId = repository.subscribe(Feed(categoryId = categoryId, title = "Article Feed"))
        repository.upsertItems(
            listOf(
                FeedItem(id = "ep-1", feedId = podcastFeedId, itemGuid = "g1", enclosureUrl = "https://example.com/ep1.mp3"),
                FeedItem(id = "art-1", feedId = articleFeedId, itemGuid = "g2"),
            ),
        )

        val state = viewModel.uiState.first { it.categories.isNotEmpty() }

        val techSection = state.categories.first { !it.isPodcastsSection }
        assertEquals(setOf(podcastFeedId, articleFeedId), techSection.feeds.map { it.feed.id }.toSet())

        val podcastsSection = state.categories.first { it.isPodcastsSection }
        assertEquals(listOf(podcastFeedId), podcastsSection.feeds.map { it.feed.id })
    }

    @Test
    fun uiState_podcastsSectionPresentEvenWithNoPodcastFeeds() = runTest(testDispatcher) {
        val categoryId = db.categoryDao().insert(Category(name = "Tech"))
        repository.subscribe(Feed(categoryId = categoryId, title = "Article Feed"))

        val state = viewModel.uiState.first { it.categories.isNotEmpty() }

        val podcastsSection = state.categories.first { it.isPodcastsSection }
        assertTrue(podcastsSection.feeds.isEmpty())
    }
}
