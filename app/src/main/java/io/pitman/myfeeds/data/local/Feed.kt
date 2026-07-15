package io.pitman.myfeeds.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Ported from MyFeeds.Data/FeedDataContext.cs Feed table. `isUpdating` from the original was
 * runtime-only (no [Column] attribute) and is intentionally not persisted here.
 */
@Entity(
    tableName = "feeds",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("userTitle"), Index("title"), Index("categoryId")],
)
data class Feed(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
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
)
