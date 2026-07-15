package io.pitman.myfeeds.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.pitman.myfeeds.data.local.AppDatabase
import io.pitman.myfeeds.data.local.Category
import io.pitman.myfeeds.data.local.Feed
import io.pitman.myfeeds.data.local.FeedItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Config pins Robolectric to API 35 -- Robolectric 4.14 doesn't support compileSdk 36 yet. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FeedRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: FeedRepository
    private var categoryId: Long = 0

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = FeedRepository(db.feedDao(), db.feedItemDao())
        categoryId = db.categoryDao().insert(Category(name = "Tech"))
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun subscribe_addsFeedVisibleUnderItsCategory() = runTest {
        val feedId = repository.subscribe(Feed(categoryId = categoryId, title = "A Feed"))

        val feeds = repository.observeFeedsByCategory(categoryId).first()

        assertEquals(listOf(feedId), feeds.map { it.id })
    }

    @Test
    fun unsubscribe_removesFeedAndCascadesItems() = runTest {
        val feed = Feed(categoryId = categoryId, title = "A Feed")
        val feedId = repository.subscribe(feed)
        repository.upsertItems(listOf(FeedItem(id = "item-1", feedId = feedId, itemGuid = "guid-1")))

        repository.unsubscribe(feed.copy(id = feedId))

        assertNull(repository.getFeed(feedId))
        assertEquals(0, repository.observeItems(feedId).first().size)
    }

    @Test
    fun markRead_updatesUnreadCounts() = runTest {
        val feedId = repository.subscribe(Feed(categoryId = categoryId, title = "A Feed"))
        repository.upsertItems(
            listOf(
                FeedItem(id = "item-1", feedId = feedId, itemGuid = "guid-1"),
                FeedItem(id = "item-2", feedId = feedId, itemGuid = "guid-2"),
            ),
        )

        repository.markRead("item-1", true)

        assertEquals(1, repository.observeUnreadCount(feedId).first())
        assertEquals(1, repository.observeTotalUnreadCount().first())
    }

    @Test
    fun markAllRead_clearsUnreadCountForFeed() = runTest {
        val feedId = repository.subscribe(Feed(categoryId = categoryId, title = "A Feed"))
        repository.upsertItems(
            listOf(
                FeedItem(id = "item-1", feedId = feedId, itemGuid = "guid-1"),
                FeedItem(id = "item-2", feedId = feedId, itemGuid = "guid-2"),
            ),
        )

        repository.markAllRead(feedId)

        assertEquals(0, repository.observeUnreadCount(feedId).first())
    }

    @Test
    fun findByItemGuid_locatesExistingItemForDedup() = runTest {
        val feedId = repository.subscribe(Feed(categoryId = categoryId, title = "A Feed"))
        repository.upsertItems(listOf(FeedItem(id = "item-1", feedId = feedId, itemGuid = "guid-1")))

        val found = repository.findByItemGuid(feedId, "guid-1")

        assertEquals("item-1", found?.id)
        assertNull(repository.findByItemGuid(feedId, "missing-guid"))
    }

    @Test
    fun trimToItemsToKeep_deletesOldestBeyondLimitAndReturnsEvicted() = runTest {
        val feedId = repository.subscribe(Feed(categoryId = categoryId, title = "A Feed", itemsToKeep = 2))
        repository.upsertItems(
            listOf(
                FeedItem(id = "oldest", feedId = feedId, itemGuid = "g1", publishDate = 1L),
                FeedItem(id = "middle", feedId = feedId, itemGuid = "g2", publishDate = 2L),
                FeedItem(id = "newest", feedId = feedId, itemGuid = "g3", publishDate = 3L),
            ),
        )

        val evicted = repository.trimToItemsToKeep(feedId)

        assertEquals(listOf("oldest"), evicted.map { it.id })
        val remaining = repository.observeItems(feedId).first().map { it.id }
        assertTrue(remaining.containsAll(listOf("middle", "newest")))
        assertEquals(2, remaining.size)
    }

    @Test
    fun setEnclosurePosition_persistsAndClears() = runTest {
        val feedId = repository.subscribe(Feed(categoryId = categoryId, title = "A Feed"))
        repository.upsertItems(listOf(FeedItem(id = "item-1", feedId = feedId, itemGuid = "g1")))

        repository.setEnclosurePosition("item-1", 42.5)
        assertEquals(42.5, repository.getItem("item-1")?.enclosurePosition)

        repository.setEnclosurePosition("item-1", null)
        assertNull(repository.getItem("item-1")?.enclosurePosition)
    }

    @Test
    fun observePodcastFeedIds_includesOnlyFeedsWithAudioEnclosures() = runTest {
        val podcastFeedId = repository.subscribe(Feed(categoryId = categoryId, title = "Podcast Feed"))
        val articleFeedId = repository.subscribe(Feed(categoryId = categoryId, title = "Article Feed"))
        // e.g. Windows Central/Sky News: an ordinary article whose feed sets <enclosure> on a
        // featured image, not an audio episode -- shouldn't count as a podcast feed.
        val imageEnclosureFeedId = repository.subscribe(Feed(categoryId = categoryId, title = "Image Enclosure Feed"))
        repository.upsertItems(
            listOf(
                FeedItem(
                    id = "ep-1",
                    feedId = podcastFeedId,
                    itemGuid = "g1",
                    enclosureUrl = "https://example.com/ep1.mp3",
                    enclosureType = "audio/mpeg",
                ),
                FeedItem(id = "art-1", feedId = articleFeedId, itemGuid = "g2"),
                FeedItem(
                    id = "img-1",
                    feedId = imageEnclosureFeedId,
                    itemGuid = "g3",
                    enclosureUrl = "https://example.com/cover.jpg",
                    enclosureType = "image/jpeg",
                ),
            ),
        )

        assertEquals(setOf(podcastFeedId), repository.observePodcastFeedIds().first())
    }

    @Test
    fun trimToItemsToKeep_noOpWhenUnderLimit() = runTest {
        val feedId = repository.subscribe(Feed(categoryId = categoryId, title = "A Feed", itemsToKeep = 10))
        repository.upsertItems(listOf(FeedItem(id = "item-1", feedId = feedId, itemGuid = "g1")))

        val evicted = repository.trimToItemsToKeep(feedId)

        assertEquals(0, evicted.size)
    }
}
