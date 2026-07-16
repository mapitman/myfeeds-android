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

/** Stores parsed `itunes:duration` per episode (issue #75), so the reader can show a saved resume
 *  position proportionally without needing to buffer the episode first. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE feed_items ADD COLUMN enclosureDurationMs INTEGER")
    }
}

/** Adds the "Next Up" playback queue (issue #67): an ordered, cross-feed list of episode ids. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS queue_entries (" +
                "itemId TEXT NOT NULL PRIMARY KEY, " +
                "position INTEGER NOT NULL, " +
                "addedAt INTEGER NOT NULL, " +
                "FOREIGN KEY(itemId) REFERENCES feed_items(id) ON DELETE CASCADE)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_queue_entries_position ON queue_entries(position)")
    }
}
