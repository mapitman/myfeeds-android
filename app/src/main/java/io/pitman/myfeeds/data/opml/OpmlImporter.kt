package io.pitman.myfeeds.data.opml

import io.pitman.myfeeds.data.local.Category
import io.pitman.myfeeds.data.local.CategoryDao
import io.pitman.myfeeds.data.local.Feed
import io.pitman.myfeeds.data.local.FeedDao
import javax.inject.Inject

/**
 * Imports a parsed [OpmlDocument] into Category/Feed, reusing an existing category by name
 * rather than creating a duplicate (unlike [io.pitman.myfeeds.data.DefaultFeedsSeeder], which
 * only ever runs once against an empty DB on first run).
 */
class OpmlImporter @Inject constructor(
    private val categoryDao: CategoryDao,
    private val feedDao: FeedDao,
) {
    suspend fun import(document: OpmlDocument): Int {
        var importedFeedCount = 0

        document.categories.forEach { category ->
            val categoryId = categoryDao.getByName(category.name)?.id
                ?: categoryDao.insert(Category(name = category.name))

            category.feeds.forEach { feed ->
                feedDao.insert(Feed(categoryId = categoryId, title = feed.title, feedUrl = feed.xmlUrl))
                importedFeedCount++
            }
        }

        return importedFeedCount
    }
}
