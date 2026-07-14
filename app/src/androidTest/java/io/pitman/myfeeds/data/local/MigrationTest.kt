package io.pitman.myfeeds.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Infra for future schema migrations (Room exports schema JSON to app/schemas, wired via
 * ksp { arg("room.schemaLocation", ...) } in build.gradle.kts). No migrations exist yet at
 * version 1 -- this test establishes that MigrationTestHelper can open the exported v1 schema,
 * so a real Migration(1, 2) test can be added here once the schema next changes.
 */
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

    companion object {
        private const val TEST_DB = "migration-test"
    }
}
