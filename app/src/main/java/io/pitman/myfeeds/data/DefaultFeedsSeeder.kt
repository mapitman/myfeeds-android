package io.pitman.myfeeds.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.pitman.myfeeds.data.local.Category
import io.pitman.myfeeds.data.local.CategoryDao
import io.pitman.myfeeds.data.local.Feed
import io.pitman.myfeeds.data.local.FeedDao
import io.pitman.myfeeds.data.opml.OpmlParser
import io.pitman.myfeeds.data.settings.SettingsDataStore
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Loads the bundled default_feeds.opml (refreshed from the WinPhone app's stale DefaultFeeds.xml
 * -- see MyFeeds/DefaultFeeds.xml) into Category/Feed on first run.
 */
class DefaultFeedsSeeder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val categoryDao: CategoryDao,
    private val feedDao: FeedDao,
    private val settingsDataStore: SettingsDataStore,
) {
    suspend fun seedIfFirstRun() {
        if (!settingsDataStore.settings.first().isFirstRun) return

        val opml = context.assets.open(DEFAULT_FEEDS_ASSET).use { OpmlParser.parse(it) }
        opml.categories.forEachIndexed { categoryIndex, category ->
            val categoryId = categoryDao.insert(Category(name = category.name, sortOrder = categoryIndex))
            category.feeds.forEachIndexed { feedIndex, feed ->
                feedDao.insert(
                    Feed(categoryId = categoryId, title = feed.title, feedUrl = feed.xmlUrl, sortOrder = feedIndex),
                )
            }
        }

        settingsDataStore.setFirstRunComplete()
    }

    companion object {
        private const val DEFAULT_FEEDS_ASSET = "default_feeds.opml"
    }
}
