package io.pitman.myfeeds.data.opml

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.pitman.myfeeds.data.local.AppDatabase
import io.pitman.myfeeds.data.local.Category
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
        importer = OpmlImporter(db.categoryDao(), db.feedDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun import_createsNewCategoriesAndFeeds() = runTest {
        val document = OpmlDocument(
            categories = listOf(
                OpmlCategory("Tech", listOf(OpmlFeed("Ars Technica", "https://arstechnica.com/feed"))),
            ),
        )

        val count = importer.import(document)

        assertEquals(1, count)
        val categories = db.categoryDao().observeAll().first()
        assertEquals(listOf("Tech"), categories.map { it.name })
        val feeds = db.feedDao().observeByCategory(categories.single().id).first()
        assertEquals(listOf("Ars Technica"), feeds.map { it.title })
    }

    @Test
    fun import_reusesExistingCategoryByName() = runTest {
        val existingCategoryId = db.categoryDao().insert(Category(name = "Tech"))

        importer.import(
            OpmlDocument(
                categories = listOf(
                    OpmlCategory("Tech", listOf(OpmlFeed("Engadget", "https://engadget.com/feed"))),
                ),
            ),
        )

        val categories = db.categoryDao().observeAll().first()
        assertEquals(1, categories.size)
        assertEquals(existingCategoryId, categories.single().id)
    }

    @Test
    fun import_multipleCategories_returnsTotalFeedCount() = runTest {
        val document = OpmlDocument(
            categories = listOf(
                OpmlCategory("Tech", listOf(OpmlFeed("A", "https://a.example/feed"), OpmlFeed("B", "https://b.example/feed"))),
                OpmlCategory("News", listOf(OpmlFeed("C", "https://c.example/feed"))),
            ),
        )

        val count = importer.import(document)

        assertEquals(3, count)
    }

    @Test
    fun import_emptyDocument_returnsZero() = runTest {
        val count = importer.import(OpmlDocument(categories = emptyList()))

        assertEquals(0, count)
    }
}
