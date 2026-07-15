package io.pitman.myfeeds.data.opml

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.pitman.myfeeds.data.local.AppDatabase
import io.pitman.myfeeds.data.local.Category
import io.pitman.myfeeds.data.local.Feed
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

/** Config pins Robolectric to API 35 -- Robolectric 4.14 doesn't support compileSdk 36 yet. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OpmlExporterTest {
    private lateinit var db: AppDatabase
    private lateinit var exporter: OpmlExporter

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        exporter = OpmlExporter(db.categoryDao(), db.feedDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun export_emptyDatabase_producesEmptyBody() = runTest {
        val opml = exporter.export()

        val parsed = OpmlParser.parse(ByteArrayInputStream(opml.toByteArray()))
        assertEquals(emptyList<OpmlCategory>(), parsed.categories)
    }

    @Test
    fun export_roundTripsThroughParser() = runTest {
        val categoryId = db.categoryDao().insert(Category(name = "Tech"))
        db.feedDao().insert(Feed(categoryId = categoryId, title = "Ars Technica", feedUrl = "https://arstechnica.com/feed"))
        db.feedDao().insert(
            Feed(categoryId = categoryId, title = "Original Title", userTitle = "My Feed", feedUrl = "https://example.com/feed"),
        )

        val opml = exporter.export()
        val parsed = OpmlParser.parse(ByteArrayInputStream(opml.toByteArray()))

        assertEquals(1, parsed.categories.size)
        val category = parsed.categories.single()
        assertEquals("Tech", category.name)
        assertEquals(
            setOf(
                OpmlFeed("Ars Technica", "https://arstechnica.com/feed"),
                OpmlFeed("My Feed", "https://example.com/feed"),
            ),
            category.feeds.toSet(),
        )
    }

    @Test
    fun export_escapesXmlSpecialCharacters() = runTest {
        val categoryId = db.categoryDao().insert(Category(name = "Tech & Science"))
        db.feedDao().insert(Feed(categoryId = categoryId, title = "A \"quoted\" <feed>", feedUrl = "https://example.com/feed?a=1&b=2"))

        val opml = exporter.export()
        val parsed = OpmlParser.parse(ByteArrayInputStream(opml.toByteArray()))

        assertEquals("Tech & Science", parsed.categories.single().name)
        assertEquals("A \"quoted\" <feed>", parsed.categories.single().feeds.single().title)
        assertEquals("https://example.com/feed?a=1&b=2", parsed.categories.single().feeds.single().xmlUrl)
    }
}
