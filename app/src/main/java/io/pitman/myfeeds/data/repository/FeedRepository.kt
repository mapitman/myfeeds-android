package io.pitman.myfeeds.data.repository

import io.pitman.myfeeds.data.local.Feed
import io.pitman.myfeeds.data.local.FeedDao
import io.pitman.myfeeds.data.local.FeedItem
import io.pitman.myfeeds.data.local.FeedItemDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Feed subscription and article state, over [FeedDao]/[FeedItemDao]. Feed update/parsing
 * (issue #11) is a separate concern; this repository only owns persistence.
 */
class FeedRepository @Inject constructor(
    private val feedDao: FeedDao,
    private val feedItemDao: FeedItemDao,
) {
    fun observeAllFeeds(): Flow<List<Feed>> = feedDao.observeAll()

    suspend fun getFeed(feedId: Long): Feed? = feedDao.getById(feedId)

    fun observeFeed(feedId: Long): Flow<Feed?> = feedDao.observeById(feedId)

    suspend fun subscribe(feed: Feed): Long = feedDao.insert(feed)

    suspend fun unsubscribe(feed: Feed) = feedDao.delete(feed)

    suspend fun updateFeed(feed: Feed) = feedDao.update(feed)

    fun observeItems(feedId: Long): Flow<List<FeedItem>> = feedItemDao.observeByFeed(feedId)

    fun observeUnreadItems(feedId: Long): Flow<List<FeedItem>> = feedItemDao.observeUnreadByFeed(feedId)

    fun observeItems(feedIds: List<Long>): Flow<List<FeedItem>> = feedItemDao.observeByFeeds(feedIds)

    fun observeUnreadItems(feedIds: List<Long>): Flow<List<FeedItem>> = feedItemDao.observeUnreadByFeeds(feedIds)

    fun observeUnreadCount(feedId: Long): Flow<Int> = feedItemDao.observeUnreadCountForFeed(feedId)

    fun observeUnreadCount(feedIds: List<Long>): Flow<Int> = feedItemDao.observeUnreadCountForFeeds(feedIds)

    fun observeTotalUnreadCount(): Flow<Int> = feedItemDao.observeTotalUnreadCount()

    fun observeUnreadCountsByFeed(): Flow<Map<Long, Int>> =
        feedItemDao.observeUnreadCountsByFeed().map { counts -> counts.associate { it.feedId to it.count } }

    fun observePodcastFeedIds(): Flow<Set<Long>> = feedItemDao.observePodcastFeedIds().map { it.toSet() }

    suspend fun upsertItems(items: List<FeedItem>) = feedItemDao.insertAll(items)

    suspend fun findByItemGuid(feedId: Long, itemGuid: String): FeedItem? =
        feedItemDao.findByItemGuid(feedId, itemGuid)

    suspend fun getItem(itemId: String): FeedItem? = feedItemDao.getById(itemId)

    suspend fun setEnclosurePosition(itemId: String, position: Double?) =
        feedItemDao.setEnclosurePosition(itemId, position)

    suspend fun markRead(itemId: String, isRead: Boolean = true) = feedItemDao.setRead(itemId, isRead)

    suspend fun markAllRead(feedId: Long) = feedItemDao.markAllReadForFeed(feedId)

    suspend fun deleteItems(items: List<FeedItem>) = feedItemDao.deleteAll(items)

    suspend fun removeAllFeeds() = feedDao.deleteAll()

    /** Clears saved podcast resume positions. Does not touch downloaded files/state (see [io.pitman.myfeeds.download.DownloadManager]). */
    suspend fun clearAllEnclosurePositions() = feedItemDao.clearAllEnclosurePositions()

    suspend fun setDownloadedBytes(itemId: String, bytes: Long?) = feedItemDao.setDownloadedBytes(itemId, bytes)

    suspend fun setDownloadedFilePath(itemId: String, path: String?) = feedItemDao.setDownloadedFilePath(itemId, path)

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
