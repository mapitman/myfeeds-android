package io.pitman.myfeeds.data.repository

import io.pitman.myfeeds.data.local.Feed
import io.pitman.myfeeds.data.local.FeedDao
import io.pitman.myfeeds.data.local.FeedItem
import io.pitman.myfeeds.data.local.FeedItemDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Feed subscription and article state, over [FeedDao]/[FeedItemDao]. Feed update/parsing
 * (issue #11) is a separate concern; this repository only owns persistence.
 */
class FeedRepository @Inject constructor(
    private val feedDao: FeedDao,
    private val feedItemDao: FeedItemDao,
) {
    fun observeFeedsByCategory(categoryId: Long): Flow<List<Feed>> = feedDao.observeByCategory(categoryId)

    fun observeAllFeeds(): Flow<List<Feed>> = feedDao.observeAll()

    suspend fun getFeed(feedId: Long): Feed? = feedDao.getById(feedId)

    suspend fun subscribe(feed: Feed): Long = feedDao.insert(feed)

    suspend fun unsubscribe(feed: Feed) = feedDao.delete(feed)

    suspend fun updateFeed(feed: Feed) = feedDao.update(feed)

    fun observeItems(feedId: Long): Flow<List<FeedItem>> = feedItemDao.observeByFeed(feedId)

    fun observeUnreadItems(feedId: Long): Flow<List<FeedItem>> = feedItemDao.observeUnreadByFeed(feedId)

    fun observeUnreadCount(feedId: Long): Flow<Int> = feedItemDao.observeUnreadCountForFeed(feedId)

    fun observeTotalUnreadCount(): Flow<Int> = feedItemDao.observeTotalUnreadCount()

    suspend fun upsertItems(items: List<FeedItem>) = feedItemDao.insertAll(items)

    suspend fun findByItemGuid(feedId: Long, itemGuid: String): FeedItem? =
        feedItemDao.findByItemGuid(feedId, itemGuid)

    suspend fun markRead(itemId: String, isRead: Boolean = true) = feedItemDao.setRead(itemId, isRead)

    suspend fun markAllRead(feedId: Long) = feedItemDao.markAllReadForFeed(feedId)

    /**
     * Deletes the oldest items beyond the feed's `itemsToKeep`, mirroring the original
     * FeedUpdater's trim-by-publish-date behavior. Returns the evicted items so callers can clean
     * up associated enclosure files (issue #12).
     */
    suspend fun trimToItemsToKeep(feedId: Long): List<FeedItem> {
        val itemsToKeep = feedDao.getById(feedId)?.itemsToKeep ?: return emptyList()
        val items = feedItemDao.observeByFeed(feedId).first()
        if (items.size <= itemsToKeep) return emptyList()

        val evicted = items.drop(itemsToKeep)
        feedItemDao.deleteAll(evicted)
        return evicted
    }
}
