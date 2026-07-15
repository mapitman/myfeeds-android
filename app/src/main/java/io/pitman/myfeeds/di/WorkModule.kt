package io.pitman.myfeeds.di

import android.content.Context
import androidx.work.WorkManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.pitman.myfeeds.download.DownloadManager
import io.pitman.myfeeds.download.DownloadScheduling
import io.pitman.myfeeds.refresh.FeedRefreshScheduler
import io.pitman.myfeeds.refresh.FeedRefreshScheduling
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WorkManagerModule {
    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class WorkBindingModule {
    @Binds
    abstract fun bindFeedRefreshScheduling(impl: FeedRefreshScheduler): FeedRefreshScheduling

    @Binds
    abstract fun bindDownloadScheduling(impl: DownloadManager): DownloadScheduling
}
