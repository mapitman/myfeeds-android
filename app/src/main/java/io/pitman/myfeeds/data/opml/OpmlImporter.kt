package io.pitman.myfeeds.data.opml

import io.pitman.myfeeds.data.local.Feed
import io.pitman.myfeeds.data.local.FeedDao
import javax.inject.Inject

/** Imports a parsed [OpmlDocument]'s flat feed list, subscribing to each feed. */
class OpmlImporter @Inject constructor(
    private val feedDao: FeedDao,
) {
    suspend fun import(document: OpmlDocument): Int {
        document.feeds.forEach { feed ->
            feedDao.insert(Feed(title = feed.title, feedUrl = feed.xmlUrl))
        }
        return document.feeds.size
    }
}
