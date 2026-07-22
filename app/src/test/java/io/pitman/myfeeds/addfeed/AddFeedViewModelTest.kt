package io.pitman.myfeeds.addfeed

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.pitman.myfeeds.TrackedViewModelStore
import io.pitman.myfeeds.data.directory.FeedDirectory
import io.pitman.myfeeds.data.feed.FeedFetcher
import io.pitman.myfeeds.data.feed.FeedUpdateEngine
import io.pitman.myfeeds.data.local.AppDatabase
import io.pitman.myfeeds.data.opml.OpmlImporter
import io.pitman.myfeeds.data.repository.FeedRepository
import io.pitman.myfeeds.data.settings.SettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
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
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AddFeedViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    // Cleared *and joined* in tearDown so no ViewModel coroutine is still in flight when
    // Dispatchers.resetMain runs -- see TrackedViewModelStore's doc for the full leak mechanics
    // behind the #54/#60 flakiness this prevents.
    private val viewModelStore = TrackedViewModelStore()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var db: AppDatabase
    private lateinit var viewModel: AddFeedViewModel

    private val rssXml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0"><channel>
          <title>A New Feed</title>
          <link>https://example.com</link>
          <description>desc</description>
          <item>
            <title>An Item</title>
            <link>https://example.com/item-1</link>
            <guid>item-1</guid>
            <description>body</description>
            <pubDate>Mon, 03 Jun 2013 11:05:30 GMT</pubDate>
          </item>
        </channel></rss>
    """.trimIndent()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        server = MockWebServer()
        server.start()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val repository = FeedRepository(db.feedDao(), db.feedItemDao(), db.queueDao())
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            produceFile = { File(tempFolder.newFolder(), "test.preferences_pb") },
        )
        val settingsDataStore = SettingsDataStore(dataStore)
        val httpClient = OkHttpClient()
        viewModel = AddFeedViewModel(
            feedFetcher = FeedFetcher(httpClient),
            feedUpdateEngine = FeedUpdateEngine(FeedFetcher(httpClient), repository, settingsDataStore),
            feedRepository = repository,
            opmlImporter = OpmlImporter(db.feedDao()),
            httpClient = httpClient,
            feedDirectory = FeedDirectory(context),
            context = context,
        )
        viewModelStore.put("addFeed", viewModel)
    }

    @After
    fun tearDown() {
        // Inside runTest (same scheduler as Dispatchers.Main) so the scheduler keeps getting
        // pumped while clearAndJoin waits out in-flight ViewModel coroutines (issues #54/#60).
        runTest(testDispatcher) { viewModelStore.clearAndJoin() }
        server.shutdown()
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun awaitTerminalState(): AddFeedUiState =
        viewModel.uiState.first { it !is AddFeedUiState.Idle && it !is AddFeedUiState.Loading }

    @Test
    fun addFeedByUrl_subscribesAndPopulatesItems() = runTest(testDispatcher) {
        // addFeedByUrl fetches twice: once to validate/discover, once more inside
        // FeedUpdateEngine.updateFeed to actually persist items.
        server.enqueue(MockResponse().setResponseCode(200).setBody(rssXml))
        server.enqueue(MockResponse().setResponseCode(200).setBody(rssXml))

        viewModel.addFeedByUrl(server.url("/feed.xml").toString())
        val state = awaitTerminalState()

        assertTrue("expected Success but got $state", state is AddFeedUiState.Success)
        val feeds = db.feedDao().observeAll().first()
        assertEquals(1, feeds.size)
        assertEquals("A New Feed", feeds.single().title)
        val items = db.feedItemDao().observeByFeed(feeds.single().id).first()
        assertEquals(1, items.size)
    }

    @Test
    fun addFeedByUrl_blankUrl_showsErrorWithoutFetching() = runTest(testDispatcher) {
        viewModel.addFeedByUrl("  ")
        val state = awaitTerminalState()

        assertTrue(state is AddFeedUiState.Error)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun addFeedByUrl_fetchFailure_showsErrorAndDoesNotSubscribe() = runTest(testDispatcher) {
        server.enqueue(MockResponse().setResponseCode(404))

        viewModel.addFeedByUrl(server.url("/missing.xml").toString())
        val state = awaitTerminalState()

        assertTrue(state is AddFeedUiState.Error)
        assertEquals(0, db.feedDao().observeAll().first().size)
    }

    @Test
    fun importOpml_populatesFeeds() = runTest(testDispatcher) {
        val opml = """
            <?xml version="1.0" encoding="utf-8"?>
            <opml version="1.0"><body>
              <outline text="Imported">
                <outline text="Feed A" xmlUrl="https://a.example/feed" />
              </outline>
            </body></opml>
        """.trimIndent()

        viewModel.importOpml(opml.byteInputStream())
        val state = awaitTerminalState()

        assertTrue("expected Success but got $state", state is AddFeedUiState.Success)
        val feeds = db.feedDao().observeAll().first()
        assertEquals(listOf("Feed A"), feeds.map { it.title })
    }

    @Test
    fun importOpmlFromText_populatesFeeds() = runTest(testDispatcher) {
        val opml = """
            <?xml version="1.0" encoding="utf-8"?>
            <opml version="1.0"><body>
              <outline text="Imported">
                <outline text="Feed A" xmlUrl="https://a.example/feed" />
              </outline>
            </body></opml>
        """.trimIndent()

        viewModel.importOpmlFromText(opml)
        val state = awaitTerminalState()

        assertTrue("expected Success but got $state", state is AddFeedUiState.Success)
        val feeds = db.feedDao().observeAll().first()
        assertEquals(listOf("Feed A"), feeds.map { it.title })
    }

    @Test
    fun importOpmlFromText_blankText_showsErrorWithoutParsing() = runTest(testDispatcher) {
        viewModel.importOpmlFromText("   ")
        val state = awaitTerminalState()

        assertTrue(state is AddFeedUiState.Error)
        assertEquals(0, db.feedDao().observeAll().first().size)
    }
}
