package io.pitman.myfeeds.data.repository

import io.pitman.myfeeds.data.local.QueueDao
import io.pitman.myfeeds.data.local.QueueEntry
import io.pitman.myfeeds.data.local.QueuedEpisode
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** The "Next Up" playback queue (issue #67), over [QueueDao]. */
class QueueRepository @Inject constructor(
    private val queueDao: QueueDao,
) {
    fun observeQueue(): Flow<List<QueuedEpisode>> = queueDao.observeQueue()

    suspend fun isQueued(itemId: String): Boolean = queueDao.findItemId(itemId) != null

    /** No-op if already queued -- an episode can only be queued once. Returns whether it was added. */
    suspend fun addToEnd(itemId: String): Boolean {
        if (queueDao.findItemId(itemId) != null) return false
        queueDao.insert(QueueEntry(itemId, position = queueDao.maxPosition() + 1, addedAt = System.currentTimeMillis()))
        return true
    }

    /** No-op if already queued -- an episode can only be queued once. */
    suspend fun addToFront(itemId: String) {
        if (queueDao.findItemId(itemId) != null) return
        queueDao.insert(QueueEntry(itemId, position = queueDao.minPosition() - 1, addedAt = System.currentTimeMillis()))
    }

    suspend fun remove(itemId: String) = queueDao.remove(itemId)

    /** Renumbers positions to match [orderedItemIds] exactly, for drag-to-reorder in the queue screen. */
    suspend fun reorder(orderedItemIds: List<String>) {
        orderedItemIds.forEachIndexed { index, itemId -> queueDao.setPosition(itemId, index) }
    }

    /** Removes and returns the item at the front of the queue, or null if the queue is empty. */
    suspend fun popNext(): String? {
        val next = queueDao.firstItemId() ?: return null
        queueDao.remove(next)
        return next
    }

    /**
     * Evicts this feed's oldest-queued episodes (earliest added, not earliest published) down to
     * [maxCount] (issue #68) -- applies to the whole queue for that feed, auto- and manually-queued
     * alike, since [QueueEntry] doesn't distinguish how an entry got there. Only removes from the
     * queue; does not touch the episode, its download, or read state.
     */
    suspend fun enforceFeedCap(feedId: Long, maxCount: Int) {
        val ordered = queueDao.orderedItemIdsForFeed(feedId)
        val excess = ordered.size - maxCount
        if (excess <= 0) return
        ordered.take(excess).forEach { queueDao.remove(it) }
    }
}
