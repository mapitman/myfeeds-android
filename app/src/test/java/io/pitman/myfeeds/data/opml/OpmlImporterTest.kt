package io.pitman.myfeeds.data.opml

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.pitman.myfeeds.data.local.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Config pins Robolectric to API 35 -- Robolectric 4.14 doesn't support compileSdk 36 yet. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OpmlImporterTest {
    private lateinit var db: AppDatabase
    private lateinit var importer: OpmlImporter

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        importer = OpmlImporter(db.feedDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun import_createsFeeds() = runTest {
        val document = OpmlDocument(
            folders = listOf(
                OpmlFolder("Tech", listOf(OpmlFeed("Ars Technica", "https://arstechnica.com/feed"))),
            ),
        )

        val count = importer.import(document)

        assertEquals(1, count)
        val feeds = db.feedDao().observeAll().first()
        assertEquals(listOf("Ars Technica"), feeds.map { it.title })
    }

    @Test
    fun import_multipleFolders_returnsTotalFeedCount() = runTest {
        val document = OpmlDocument(
            folders = listOf(
                OpmlFolder("Tech", listOf(OpmlFeed("A", "https://a.example/feed"), OpmlFeed("B", "https://b.example/feed"))),
                OpmlFolder("News", listOf(OpmlFeed("C", "https://c.example/feed"))),
            ),
        )

        val count = importer.import(document)

        assertEquals(3, count)
    }

    @Test
    fun import_emptyDocument_returnsZero() = runTest {
        val count = importer.import(OpmlDocument(folders = emptyList()))

        assertEquals(0, count)
    }

    @Test
    fun import_skipsFeedsAlreadySubscribedByFeedUrl() = runTest {
        // issue #228: re-importing an OPML file that overlaps with existing subscriptions used to
        // insert an unconditional duplicate Feed row for every entry.
        val document = OpmlDocument(
            folders = listOf(
                OpmlFolder("Tech", listOf(OpmlFeed("Ars Technica", "https://arstechnica.com/feed"))),
            ),
        )
        importer.import(document)

        val secondImportCount = importer.import(document)

        assertEquals(0, secondImportCount)
        val feeds = db.feedDao().observeAll().first()
        assertEquals(1, feeds.size)
    }

    @Test
    fun import_onlyImportsTheNewFeedsWhenSomeAlreadySubscribed() = runTest {
        importer.import(
            OpmlDocument(folders = listOf(OpmlFolder("Tech", listOf(OpmlFeed("A", "https://a.example/feed"))))),
        )

        val count = importer.import(
            OpmlDocument(
                folders = listOf(
                    OpmlFolder(
                        "Tech",
                        listOf(OpmlFeed("A", "https://a.example/feed"), OpmlFeed("B", "https://b.example/feed")),
                    ),
                ),
            ),
        )

        assertEquals(1, count)
        val feeds = db.feedDao().observeAll().first()
        assertEquals(listOf("A", "B"), feeds.map { it.title })
    }
}
