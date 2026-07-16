package io.pitman.myfeeds.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Ported from MyFeeds.Data/FeedDataContext.cs Feed table. `isUpdating` from the original was
 * runtime-only (no [Column] attribute) and is intentionally not persisted here.
 */
@Entity(
    tableName = "feeds",
    indices = [Index("userTitle"), Index("title")],
)
data class Feed(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String? = null,
    val userTitle: String? = null,
    val description: String? = null,
    val feedUrl: String? = null,
    val siteUrl: String? = null,
    val imageUrl: String? = null,
    val displayMode: Int? = null,
    val itemsToKeep: Int? = null,
    val lastGet: Long? = null,
    val sortOrder: Int? = null,
    /** New in this port (issue #23) -- the original MyFeeds only supported manual downloads. */
    val autoDownloadEnabled: Boolean = false,
    /** New episodes auto-add to the Next Up queue (issue #68) when this feed refreshes. */
    val autoQueueEnabled: Boolean = false,
    /** Only enforced when [autoQueueEnabled]; null means unlimited (keep all auto-queued episodes). */
    val autoQueueMaxCount: Int? = null,
    /** Playback speed applied when starting an episode of this feed (issue #70). */
    val playbackSpeed: Float = 1.0f,
)
