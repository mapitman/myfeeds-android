package io.pitman.myfeeds

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import io.pitman.myfeeds.playback.QueuePlaybackCoordinator
import javax.inject.Inject

@HiltAndroidApp
class MyFeedsApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    // Hilt singletons are created lazily on first injection; nothing else injects this directly,
    // so it's referenced here purely to force it to start observing playback at app launch
    // (issue #67) rather than never existing at all.
    @Inject
    lateinit var queuePlaybackCoordinator: QueuePlaybackCoordinator

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            NEW_ITEMS_CHANNEL_ID,
            getString(R.string.notification_new_items_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = getString(R.string.notification_new_items_channel_description) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val NEW_ITEMS_CHANNEL_ID = "new_items"
    }
}
