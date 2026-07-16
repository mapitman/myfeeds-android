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

/** Adds per-feed auto-queue settings (issue #68): opt-in auto-add of new episodes to the Next Up
 *  queue, with an optional cap on how many of that feed's episodes stay queued at once. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE feeds ADD COLUMN autoQueueEnabled INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE feeds ADD COLUMN autoQueueMaxCount INTEGER")
    }
}

/** Adds per-feed playback speed (issue #70), applied when starting an episode of that feed. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE feeds ADD COLUMN playbackSpeed REAL NOT NULL DEFAULT 1.0")
    }
}
