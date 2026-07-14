package io.pitman.myfeeds.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Ported from MyFeeds.Data/FeedDataContext.cs FeedItem table. `id` is a client-generated UUID
 * (matches the original's `UniqueIdentifier` PK); dedup on ingest is by `itemGuid` (fallback
 * chain guid -> id -> link, applied in the feed update engine, not here).
 *
 * `enclosureBytesDownloaded`/`isEnclosureDownloading` from the original were runtime-only (no
 * [Column] attribute) and `enclosureRequestId` was a WP BackgroundTransferService request id with
 * no Android equivalent (WorkManager tracks its own requests) — none are persisted here.
 */
@Entity(
    tableName = "feed_items",
    foreignKeys = [
        ForeignKey(
            entity = Feed::class,
            parentColumns = ["id"],
            childColumns = ["feedId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("publishDate"), Index("feedId")],
)
data class FeedItem(
    @PrimaryKey val id: String,
    val feedId: Long,
    val title: String? = null,
    val description: String? = null,
    val url: String? = null,
    val imageUrl: String? = null,
    val itemGuid: String? = null,
    val publishDate: Long? = null,
    val isRead: Boolean = false,
    val enclosureUrl: String? = null,
    val enclosureType: String? = null,
    val enclosureLength: Long? = null,
    val enclosurePosition: Double? = null,
)
