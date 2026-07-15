package io.pitman.myfeeds

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MyFeedsApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            NEW_ITEMS_CHANNEL_ID,
            "New articles",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "Notifies when a feed refresh finds new articles" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val NEW_ITEMS_CHANNEL_ID = "new_items"
    }
}
