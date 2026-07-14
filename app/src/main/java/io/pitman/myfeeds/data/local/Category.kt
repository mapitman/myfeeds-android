package io.pitman.myfeeds.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Ported from MyFeeds.Data/FeedDataContext.cs Category table.
 */
@Entity(tableName = "categories", indices = [Index("name")])
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sortOrder: Int? = null,
)
