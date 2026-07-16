package io.pitman.myfeeds.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The "Next Up" playback queue (issue #67): an ordered, cross-feed list of episodes independent
 * of any single feed's article list. `itemId` is the primary key -- an episode can only be queued
 * once, re-queuing is a no-op (see [io.pitman.myfeeds.data.repository.QueueRepository]).
 */
@Entity(
    tableName = "queue_entries",
    foreignKeys = [
        ForeignKey(
            entity = FeedItem::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("position")],
)
data class QueueEntry(
    @PrimaryKey val itemId: String,
    val position: Int,
    val addedAt: Long,
)
