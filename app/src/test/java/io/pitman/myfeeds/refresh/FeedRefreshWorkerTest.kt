package io.pitman.myfeeds.refresh

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import io.pitman.myfeeds.data.feed.FeedFetcher
import io.pitman.myfeeds.data.feed.FeedUpdateEngine
import io.pitman.myfeeds.data.local.AppDatabase
import io.pitman.myfeeds.data.local.Category
import io.pitman.myfeeds.data.local.Feed
import io.pitman.myfeeds.data.repository.FeedRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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

private const val FEED_XML = """
    <?xml version="1.0" encoding="UTF-8"?>
    <rss version="2.0"><channel>
      <title>Test Feed</title>
      <link>https://example.com</link>
      <description>desc</description>
      <item>
        <title>Item One</title>
        <link>https://example.com/1</link>
        <guid>guid-1</guid>
        <description>Body</description>
        <pubDate>Mon, 03 Jun 2013 11:05:30 GMT</pubDate>
      </item>
    </channel></rss>
"""

/** Config pins Robolectric to API 35 -- Robolectric 4.14 doesn't support compileSdk 36 yet. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FeedRefreshWorkerTest {
    private lateinit var server: MockWebServer
    private lateinit var db: AppDatabase
    private lateinit var repository: FeedRepository
    private var feedId: Long = 0

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repository = FeedRepository(db.feedDao(), db.feedItemDao())
    }

    @After
    fun tearDown() {
        server.shutdown()
        db.close()
    }

    @Test
    fun doWork_updatesAllSubscribedFeedsAndSucceeds() = runTest {
        server.enqueue(MockResponse().setBody(FEED_XML.trimIndent().trim()))
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val categoryId = db.categoryDao().insert(Category(name = "Tech"))
        feedId = repository.subscribe(
            Feed(categoryId = categoryId, title = "A Feed", feedUrl = server.url("/feed.xml").toString()),
        )
        val engine = FeedUpdateEngine(FeedFetcher(OkHttpClient()), repository)

        val worker = TestListenableWorkerBuilder<FeedRefreshWorker>(context)
            .setWorkerFactory(TestWorkerFactory(repository, engine))
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        val items = repository.observeItems(feedId).first()
        assertTrue(items.any { it.title == "Item One" })
    }

    private class TestWorkerFactory(
        private val repository: FeedRepository,
        private val engine: FeedUpdateEngine,
    ) : WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
        ) = FeedRefreshWorker(appContext, workerParameters, repository, engine)
    }
}
