package io.pitman.myfeeds.data.opml

import io.pitman.myfeeds.data.local.Feed
import io.pitman.myfeeds.data.local.FeedDao
import javax.inject.Inject

/**
 * Imports a parsed [OpmlDocument]'s flat feed list, subscribing to each feed not already
 * subscribed by [Feed.feedUrl] -- re-importing an OPML file that overlaps with existing
 * subscriptions used to insert an unconditional duplicate for every entry (issue #228).
 */
class OpmlImporter @Inject constructor(
    private val feedDao: FeedDao,
) {
    suspend fun import(document: OpmlDocument): Int {
        var importedCount = 0
        document.feeds.forEach { feed ->
            if (feedDao.findByFeedUrl(feed.xmlUrl) == null) {
                feedDao.insert(Feed(title = feed.title, feedUrl = feed.xmlUrl))
                importedCount++
            }
        }
        return importedCount
    }
}
