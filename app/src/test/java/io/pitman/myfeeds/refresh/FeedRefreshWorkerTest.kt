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
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Config pins Robolectric to API 35 -- Robolectric 4.14 doesn't support compileSdk 36 yet.
 *
 * Deliberately doesn't use MockWebServer/a real HTTP round trip (unlike FeedUpdateEngineTest,
 * which already covers fetch/parse thoroughly): this worker's own job is only to call
 * FeedUpdateEngine.updateFeeds for every subscribed feed and tolerate per-feed failures, which an
 * unreachable URL exercises just as well without the extra OkHttpClient/socket/thread overhead of
 * a live listener in every Robolectric-hosted run.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FeedRefreshWorkerTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: FeedRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repository = FeedRepository(db.feedDao(), db.feedItemDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun doWork_toleratesPerFeedFailureAndStillSucceeds() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val categoryId = db.categoryDao().insert(Category(name = "Tech"))
        repository.subscribe(Feed(categoryId = categoryId, title = "A Feed", feedUrl = "http://localhost:1/feed.xml"))
        val engine = FeedUpdateEngine(FeedFetcher(OkHttpClient()), repository)

        val worker = TestListenableWorkerBuilder<FeedRefreshWorker>(context)
            .setWorkerFactory(TestWorkerFactory(repository, engine))
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
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
