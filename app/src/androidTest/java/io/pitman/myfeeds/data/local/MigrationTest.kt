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

    companion object {
        private const val TEST_DB = "migration-test"
    }
}
