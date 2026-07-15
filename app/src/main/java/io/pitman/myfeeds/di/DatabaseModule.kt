package io.pitman.myfeeds.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.pitman.myfeeds.data.local.AppDatabase
import io.pitman.myfeeds.data.local.CategoryDao
import io.pitman.myfeeds.data.local.FeedDao
import io.pitman.myfeeds.data.local.FeedItemDao
import io.pitman.myfeeds.data.local.MIGRATION_1_2
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides
    fun provideCategoryDao(database: AppDatabase): CategoryDao = database.categoryDao()

    @Provides
    fun provideFeedDao(database: AppDatabase): FeedDao = database.feedDao()

    @Provides
    fun provideFeedItemDao(database: AppDatabase): FeedItemDao = database.feedItemDao()
}
