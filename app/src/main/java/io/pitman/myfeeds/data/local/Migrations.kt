package io.pitman.myfeeds.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Adds background-download support (issue #23): per-feed auto-download flag and per-item progress/path. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE feeds ADD COLUMN autoDownloadEnabled INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE feed_items ADD COLUMN downloadedBytes INTEGER")
        db.execSQL("ALTER TABLE feed_items ADD COLUMN downloadedFilePath TEXT")
    }
}
