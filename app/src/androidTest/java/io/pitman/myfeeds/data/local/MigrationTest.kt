package io.pitman.myfeeds.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Schema migration coverage (Room exports schema JSON to app/schemas via room.schemaLocation). */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun version1Schema_opensSuccessfully() {
        helper.createDatabase(TEST_DB, 1).close()
    }

    @Test
    fun migrate1To2_addsDownloadColumnsWithoutDataLoss() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL("INSERT INTO categories (id, name, sortOrder) VALUES (1, 'Tech', NULL)")
            execSQL(
                "INSERT INTO feeds (id, categoryId, title, userTitle, description, feedUrl, siteUrl, " +
                    "imageUrl, displayMode, itemsToKeep, lastGet, sortOrder) " +
                    "VALUES (1, 1, 'A Feed', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        migrated.query("SELECT autoDownloadEnabled FROM feeds WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        migrated.close()
    }

    @Test
    fun migrate2To3_addsEnclosureDurationColumnWithoutDataLoss() {
        helper.createDatabase(TEST_DB, 2).apply {
            execSQL("INSERT INTO categories (id, name, sortOrder) VALUES (1, 'Tech', NULL)")
            execSQL(
                "INSERT INTO feeds (id, categoryId, title, userTitle, description, feedUrl, siteUrl, " +
                    "imageUrl, displayMode, itemsToKeep, lastGet, sortOrder, autoDownloadEnabled) " +
                    "VALUES (1, 1, 'A Feed', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0)",
            )
            execSQL(
                "INSERT INTO feed_items (id, feedId, title, isRead) VALUES ('item-1', 1, 'An Item', 0)",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3)

        migrated.query("SELECT enclosureDurationMs FROM feed_items WHERE id = 'item-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.isNull(0))
        }
        migrated.close()
    }

    @Test
    fun migrate3To4_createsQueueEntriesTable() {
        helper.createDatabase(TEST_DB, 3).apply {
            execSQL("INSERT INTO categories (id, name, sortOrder) VALUES (1, 'Tech', NULL)")
            execSQL(
                "INSERT INTO feeds (id, categoryId, title, userTitle, description, feedUrl, siteUrl, " +
                    "imageUrl, displayMode, itemsToKeep, lastGet, sortOrder, autoDownloadEnabled) " +
                    "VALUES (1, 1, 'A Feed', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0)",
            )
            execSQL(
                "INSERT INTO feed_items (id, feedId, title, isRead) VALUES ('item-1', 1, 'An Item', 0)",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 4, true, MIGRATION_3_4)

        migrated.execSQL("INSERT INTO queue_entries (itemId, position, addedAt) VALUES ('item-1', 0, 1000)")
        migrated.query("SELECT position FROM queue_entries WHERE itemId = 'item-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        migrated.close()
    }

    @Test
    fun migrate4To5_addsAutoQueueColumnsWithoutDataLoss() {
        helper.createDatabase(TEST_DB, 4).apply {
            execSQL("INSERT INTO categories (id, name, sortOrder) VALUES (1, 'Tech', NULL)")
            execSQL(
                "INSERT INTO feeds (id, categoryId, title, userTitle, description, feedUrl, siteUrl, " +
                    "imageUrl, displayMode, itemsToKeep, lastGet, sortOrder, autoDownloadEnabled) " +
                    "VALUES (1, 1, 'A Feed', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0)",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 5, true, MIGRATION_4_5)

        migrated.query("SELECT autoQueueEnabled, autoQueueMaxCount FROM feeds WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
            assertTrue(cursor.isNull(1))
        }
        migrated.close()
    }

    @Test
    fun migrate5To6_addsPlaybackSpeedColumnWithoutDataLoss() {
        helper.createDatabase(TEST_DB, 5).apply {
            execSQL("INSERT INTO categories (id, name, sortOrder) VALUES (1, 'Tech', NULL)")
            execSQL(
                "INSERT INTO feeds (id, categoryId, title, userTitle, description, feedUrl, siteUrl, " +
                    "imageUrl, displayMode, itemsToKeep, lastGet, sortOrder, autoDownloadEnabled, " +
                    "autoQueueEnabled, autoQueueMaxCount) " +
                    "VALUES (1, 1, 'A Feed', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, NULL)",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 6, true, MIGRATION_5_6)

        migrated.query("SELECT playbackSpeed FROM feeds WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1.0, cursor.getDouble(0), 0.0001)
        }
        migrated.close()
    }

    companion object {
        private const val TEST_DB = "migration-test"
    }
}
