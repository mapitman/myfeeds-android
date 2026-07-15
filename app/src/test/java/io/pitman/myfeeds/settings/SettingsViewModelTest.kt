package io.pitman.myfeeds.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.pitman.myfeeds.data.local.AppDatabase
import io.pitman.myfeeds.data.local.Category
import io.pitman.myfeeds.data.local.Feed
import io.pitman.myfeeds.data.local.FeedItem
import io.pitman.myfeeds.data.opml.OpmlExporter
import io.pitman.myfeeds.data.opml.OpmlImporter
import io.pitman.myfeeds.data.repository.FeedRepository
import io.pitman.myfeeds.data.settings.FontSize
import io.pitman.myfeeds.data.settings.SettingsDataStore
import io.pitman.myfeeds.refresh.FeedRefreshScheduling
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
import kotlin.time.Duration.Companion.seconds

/**
 * Config pins Robolectric to API 35 -- Robolectric 4.14 doesn't support compileSdk 36 yet.
 *
 * The test dispatcher is shared between setMain and runTest so runTest's automatic
 * child-coroutine cleanup also covers the ViewModel's viewModelScope children (see the
 * article-reader PR for the full explanation of the flakiness this avoids).
 *
 * runTest's default 60s dispatch timeout is raised to 120s: this file is the flakiest in the
 * suite on CI's slower/more contended runners, and observed failures consistently timed out at
 * exactly the default -- a sign of slow-runner contention rather than a genuine hang.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var repository: FeedRepository
    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var viewModel: SettingsViewModel

    // ViewModels here are constructed directly (not via a real ViewModelProvider), so nothing
    // would otherwise call ViewModel.clear() to cancel their viewModelScope between tests. A
    // leaked WhileSubscribed collector then outlives the test method and races the next test's
    // Dispatchers.setMain/resetMain. Routing creation through a real ViewModelStore and clearing
    // it in tearDown cancels those coroutines properly, the same way the Android framework does.
    private val viewModelStore = ViewModelStore()

    @Before
    fun setUp() = runTest(testDispatcher, timeout = 120.seconds) {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repository = FeedRepository(db.feedDao(), db.feedItemDao())
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            produceFile = { File(tempFolder.newFolder(), "test.preferences_pb") },
        )
        settingsDataStore = SettingsDataStore(dataStore)
        // Real WorkManager deadlocked when touched from Robolectric-hosted ViewModel tests (see
        // the scheduled-refresh PR description), so SettingsViewModel depends on the
        // FeedRefreshScheduling interface and this test uses a no-op fake instead.
        viewModel = SettingsViewModel(
            settingsDataStore = settingsDataStore,
            feedRepository = repository,
            opmlImporter = OpmlImporter(db.categoryDao(), db.feedDao()),
            opmlExporter = OpmlExporter(db.categoryDao(), db.feedDao()),
            feedRefreshScheduler = object : FeedRefreshScheduling {
                override fun schedule(intervalMinutes: Long) {}
            },
            context = context,
        )
        viewModelStore.put("settings", viewModel)
    }

    @After
    fun tearDown() {
        viewModelStore.clear()
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun setUpdateIntervalMinutes_persistsAndReflectsInSettings() = runTest(testDispatcher, timeout = 120.seconds) {
        viewModel.setUpdateIntervalMinutes(60)

        val settings = viewModel.settings.first { it.updateIntervalMinutes == 60L }
        assertEquals(60L, settings.updateIntervalMinutes)
    }

    @Test
    fun setArticleFontSize_persists() = runTest(testDispatcher, timeout = 120.seconds) {
        viewModel.setArticleFontSize(FontSize.LARGE)

        val settings = viewModel.settings.first { it.articleFontSize == FontSize.LARGE }
        assertEquals(FontSize.LARGE, settings.articleFontSize)
    }

    @Test
    fun addDefaultFeeds_importsBundledOpml() = runTest(testDispatcher, timeout = 120.seconds) {
        viewModel.addDefaultFeeds()

        val categories = db.categoryDao().observeAll().first { it.size == 3 }
        assertEquals(setOf("Tech", "Mobile", "News"), categories.map { it.name }.toSet())
        val feeds = db.feedDao().observeAll().first { it.size == 12 }
        assertEquals(12, feeds.size)
    }

    @Test
    fun removeAllFeeds_deletesAllFeedsAndCascadesItems() = runTest(testDispatcher, timeout = 120.seconds) {
        val categoryId = db.categoryDao().insert(Category(name = "Tech"))
        val feedId = repository.subscribe(Feed(categoryId = categoryId, title = "A Feed"))
        repository.upsertItems(listOf(FeedItem(id = "item-1", feedId = feedId, itemGuid = "g1")))

        viewModel.removeAllFeeds()

        val feeds = db.feedDao().observeAll().first { it.isEmpty() }
        assertTrue(feeds.isEmpty())
        assertTrue(db.feedItemDao().observeByFeed(feedId).first().isEmpty())
    }

    @Test
    fun clearPodcasts_clearsEnclosurePositions() = runTest(testDispatcher, timeout = 120.seconds) {
        val categoryId = db.categoryDao().insert(Category(name = "Tech"))
        val feedId = repository.subscribe(Feed(categoryId = categoryId, title = "A Feed"))
        repository.upsertItems(
            listOf(FeedItem(id = "item-1", feedId = feedId, itemGuid = "g1", enclosurePosition = 42.0)),
        )

        viewModel.clearPodcasts()

        val item = db.feedItemDao().observeByFeed(feedId).first { it.first().enclosurePosition == null }.first()
        assertNull(item.enclosurePosition)
    }

    @Test
    fun resetSettings_restoresDefaultsWithoutTouchingFeeds() = runTest(testDispatcher, timeout = 120.seconds) {
        val categoryId = db.categoryDao().insert(Category(name = "Tech"))
        repository.subscribe(Feed(categoryId = categoryId, title = "A Feed"))
        viewModel.setMaxArticles(99)
        viewModel.settings.first { it.maxArticles == 99 }

        viewModel.resetSettings()

        val settings = viewModel.settings.first { it.maxArticles != 99 }
        assertEquals(20, settings.maxArticles)
        assertEquals(1, db.feedDao().observeAll().first().size)
    }
}
