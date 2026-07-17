package io.pitman.myfeeds.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.pitman.myfeeds.data.local.AppDatabase
import io.pitman.myfeeds.data.local.FeedDao
import io.pitman.myfeeds.data.local.FeedItemDao
import io.pitman.myfeeds.data.local.MIGRATION_1_2
import io.pitman.myfeeds.data.local.MIGRATION_2_3
import io.pitman.myfeeds.data.local.MIGRATION_3_4
import io.pitman.myfeeds.data.local.MIGRATION_4_5
import io.pitman.myfeeds.data.local.MIGRATION_5_6
import io.pitman.myfeeds.data.local.MIGRATION_6_7
import io.pitman.myfeeds.data.local.MIGRATION_7_8
import io.pitman.myfeeds.data.local.MIGRATION_8_9
import io.pitman.myfeeds.data.local.QueueDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
            )
            .build()

    @Provides
    fun provideFeedDao(database: AppDatabase): FeedDao = database.feedDao()

    @Provides
    fun provideFeedItemDao(database: AppDatabase): FeedItemDao = database.feedItemDao()

    @Provides
    fun provideQueueDao(database: AppDatabase): QueueDao = database.queueDao()
}
