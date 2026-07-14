package io.pitman.myfeeds.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.pitman.myfeeds.data.local.AppDatabase
import io.pitman.myfeeds.data.settings.SettingsDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Config pins Robolectric to API 35 -- Robolectric 4.14 doesn't support compileSdk 36 yet. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DefaultFeedsSeederTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var seeder: DefaultFeedsSeeder

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        settingsDataStore = SettingsDataStore(
            PreferenceDataStoreFactory.create(
                produceFile = { File(tempFolder.newFolder(), "test.preferences_pb") },
            ),
        )
        seeder = DefaultFeedsSeeder(context, db.categoryDao(), db.feedDao(), settingsDataStore)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun seedIfFirstRun_populatesCategoriesAndFeedsFromBundledOpml() = runTest {
        seeder.seedIfFirstRun()

        val categories = db.categoryDao().observeAll().first()
        assertEquals(setOf("Tech", "Mobile", "News"), categories.map { it.name }.toSet())

        categories.forEach { category ->
            val feeds = db.feedDao().observeByCategory(category.id).first()
            assertEquals(4, feeds.size)
            feeds.forEach { feed -> assertTrue(feed.feedUrl?.startsWith("http") == true) }
        }
    }

    @Test
    fun seedIfFirstRun_clearsIsFirstRunFlag() = runTest {
        assertTrue(settingsDataStore.settings.first().isFirstRun)

        seeder.seedIfFirstRun()

        assertFalse(settingsDataStore.settings.first().isFirstRun)
    }

    @Test
    fun seedIfFirstRun_isNoOpOnSecondCall() = runTest {
        seeder.seedIfFirstRun()
        seeder.seedIfFirstRun()

        val categories = db.categoryDao().observeAll().first()
        assertEquals(3, categories.size)
    }
}
