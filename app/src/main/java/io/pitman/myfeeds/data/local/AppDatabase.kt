package io.pitman.myfeeds.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [Category::class, Feed::class, FeedItem::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun feedDao(): FeedDao
    abstract fun feedItemDao(): FeedItemDao

    companion object {
        const val NAME = "myfeeds.db"
    }
}
