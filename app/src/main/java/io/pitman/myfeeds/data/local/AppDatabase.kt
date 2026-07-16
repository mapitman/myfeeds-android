package io.pitman.myfeeds.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [Category::class, Feed::class, FeedItem::class, QueueEntry::class],
    version = 6,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun feedDao(): FeedDao
    abstract fun feedItemDao(): FeedItemDao
    abstract fun queueDao(): QueueDao

    companion object {
        const val NAME = "myfeeds.db"
    }
}
