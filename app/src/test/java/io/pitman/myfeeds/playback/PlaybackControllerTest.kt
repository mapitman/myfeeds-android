package io.pitman.myfeeds.playback

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.pitman.myfeeds.data.local.AppDatabase
import io.pitman.myfeeds.data.repository.FeedRepository
import io.pitman.myfeeds.data.settings.SettingsDataStore
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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
class PlaybackControllerTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var playbackController: PlaybackController

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            produceFile = { File(tempFolder.newFolder(), "test.preferences_pb") },
        )
        playbackController = PlaybackController(context, SettingsDataStore(dataStore), FeedRepository(db.feedDao(), db.feedItemDao()))
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun skipForwardAndSkipBackward_noActivePlayback_areNoOpsAndDoNotCrash() = runTest {
        playbackController.skipBackward()
        playbackController.skipForward()

        assertEquals(0L, playbackController.uiState.value.positionMs)
    }

    @Test
    fun uiState_defaultsToNormalSpeed() = runTest {
        assertEquals(1.0f, playbackController.uiState.value.speed)
    }

    @Test
    fun setSpeed_noActivePlayback_doesNotCrash() = runTest {
        playbackController.setSpeed(1.5f)

        assertEquals(1.0f, playbackController.uiState.value.speed)
    }
}
