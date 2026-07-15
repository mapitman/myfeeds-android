package io.pitman.myfeeds.addfeed

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.pitman.myfeeds.data.feed.FeedFetcher
import io.pitman.myfeeds.data.feed.FeedUpdateEngine
import io.pitman.myfeeds.data.local.AppDatabase
import io.pitman.myfeeds.data.opml.OpmlImporter
import io.pitman.myfeeds.data.repository.FeedRepository
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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Config pins Robolectric to API 35 -- Robolectric 4.14 doesn't support compileSdk 36 yet. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AddFeedViewModelTest {
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
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server = MockWebServer()
        server.start()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val repository = FeedRepository(db.feedDao(), db.feedItemDao())
        val httpClient = OkHttpClient()
        viewModel = AddFeedViewModel(
            feedFetcher = FeedFetcher(httpClient),
            feedUpdateEngine = FeedUpdateEngine(FeedFetcher(httpClient), repository),
            feedRepository = repository,
            categoryDao = db.categoryDao(),
            opmlImporter = OpmlImporter(db.categoryDao(), db.feedDao()),
            httpClient = httpClient,
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun awaitTerminalState(): AddFeedUiState =
        viewModel.uiState.first { it !is AddFeedUiState.Idle && it !is AddFeedUiState.Loading }

    @Test
    fun addFeedByUrl_subscribesAndPopulatesItems() = runTest {
        // addFeedByUrl fetches twice: once to validate/discover, once more inside
        // FeedUpdateEngine.updateFeed to actually persist items.
        server.enqueue(MockResponse().setResponseCode(200).setBody(rssXml))
        server.enqueue(MockResponse().setResponseCode(200).setBody(rssXml))

        viewModel.addFeedByUrl(server.url("/feed.xml").toString(), "Tech")
        val state = awaitTerminalState()

        assertTrue("expected Success but got $state", state is AddFeedUiState.Success)
        val categories = db.categoryDao().observeAll().first()
        assertEquals(listOf("Tech"), categories.map { it.name })
        val feeds = db.feedDao().observeByCategory(categories.single().id).first()
        assertEquals(1, feeds.size)
        assertEquals("A New Feed", feeds.single().title)
        val items = db.feedItemDao().observeByFeed(feeds.single().id).first()
        assertEquals(1, items.size)
    }

    @Test
    fun addFeedByUrl_blankUrl_showsErrorWithoutFetching() = runTest {
        viewModel.addFeedByUrl("  ", "Tech")
        val state = awaitTerminalState()

        assertTrue(state is AddFeedUiState.Error)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun addFeedByUrl_fetchFailure_showsErrorAndDoesNotSubscribe() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))

        viewModel.addFeedByUrl(server.url("/missing.xml").toString(), "Tech")
        val state = awaitTerminalState()

        assertTrue(state is AddFeedUiState.Error)
        assertEquals(0, db.feedDao().observeAll().first().size)
    }

    @Test
    fun addFeedByUrl_blankCategory_fallsBackToUncategorized() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(rssXml))
        server.enqueue(MockResponse().setResponseCode(200).setBody(rssXml))

        viewModel.addFeedByUrl(server.url("/feed.xml").toString(), "")
        awaitTerminalState()

        val categories = db.categoryDao().observeAll().first()
        assertEquals(listOf("Uncategorized"), categories.map { it.name })
    }

    @Test
    fun importOpml_populatesCategoriesAndFeeds() = runTest {
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
        val categories = db.categoryDao().observeAll().first()
        assertEquals(listOf("Imported"), categories.map { it.name })
    }
}
