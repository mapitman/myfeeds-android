package io.pitman.myfeeds.playback

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.pitman.myfeeds.data.local.AppDatabase
import io.pitman.myfeeds.data.local.Feed
import io.pitman.myfeeds.data.local.FeedItem
import io.pitman.myfeeds.data.repository.FeedRepository
import io.pitman.myfeeds.data.settings.SettingsDataStore
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
class PlaybackMediaItemFactoryTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var feedRepository: FeedRepository
    private lateinit var settingsDataStore: SettingsDataStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        feedRepository = FeedRepository(db.feedDao(), db.feedItemDao(), db.queueDao())
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            produceFile = { File(tempFolder.newFolder(), "test.preferences_pb") },
        )
        settingsDataStore = SettingsDataStore(dataStore)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun resolve_streamingAllowed_buildsMediaItemFromFeedAndItem() = runTest {
        val feedId = feedRepository.subscribe(Feed(title = "A Feed", playbackSpeed = 1.5f))
        val item = FeedItem(
            id = "episode-1",
            feedId = feedId,
            title = "Episode One",
            itemGuid = "g1",
            enclosureUrl = "https://example.com/ep1.mp3",
            enclosureType = "audio/mpeg",
            enclosurePosition = 30.0,
        )
        feedRepository.insertItems(listOf(item))

        val resolved = PlaybackMediaItemFactory.resolve(item, "A Feed", feedRepository, settingsDataStore)

        requireNotNull(resolved)
        assertEquals("episode-1", resolved.mediaItem.mediaId)
        assertEquals("https://example.com/ep1.mp3", resolved.mediaItem.localConfiguration?.uri?.toString())
        assertEquals("Episode One", resolved.mediaItem.mediaMetadata.title?.toString())
        assertEquals("A Feed", resolved.mediaItem.mediaMetadata.artist?.toString())
        assertEquals(1.5f, resolved.speed)
        assertEquals(30_000L, resolved.startPositionMs)
    }

    @Test
    fun resolve_streamingDisallowedAndNotDownloaded_returnsNull() = runTest {
        settingsDataStore.setAllowPodcastStreaming(false)
        val feedId = feedRepository.subscribe(Feed(title = "A Feed"))
        val item = FeedItem(
            id = "episode-1",
            feedId = feedId,
            title = "Episode One",
            itemGuid = "g1",
            enclosureUrl = "https://example.com/ep1.mp3",
            enclosureType = "audio/mpeg",
        )
        feedRepository.insertItems(listOf(item))

        val resolved = PlaybackMediaItemFactory.resolve(item, "A Feed", feedRepository, settingsDataStore)

        assertNull(resolved)
    }
}
