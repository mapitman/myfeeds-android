package io.pitman.myfeeds.data.opml

import io.pitman.myfeeds.data.local.CategoryDao
import io.pitman.myfeeds.data.local.FeedDao
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Mirror image of [OpmlParser]/[OpmlImporter], ported from MyFeeds/Opml.cs GenerateOpml: one
 * category-outline per Category (no xmlUrl attribute), with nested feed-outlines (text + xmlUrl)
 * underneath. Exports userTitle when set, falling back to the fetched title.
 */
class OpmlExporter @Inject constructor(
    private val categoryDao: CategoryDao,
    private val feedDao: FeedDao,
) {
    suspend fun export(): String {
        val categories = categoryDao.observeAll().first()
        val feeds = feedDao.observeAll().first()
        val feedsByCategory = feeds.groupBy { it.categoryId }

        return buildString {
            append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
            append("<opml version=\"1.0\">\n")
            append("  <head>\n")
            append("    <title>MyFeeds Exported Feeds</title>\n")
            append("  </head>\n")
            append("  <body>\n")
            categories.forEach { category ->
                val categoryFeeds = feedsByCategory[category.id].orEmpty()
                append("    <outline text=\"${category.name.xmlEscape()}\">\n")
                categoryFeeds.forEach { feed ->
                    val title = (feed.userTitle ?: feed.title).orEmpty()
                    append(
                        "      <outline text=\"${title.xmlEscape()}\" xmlUrl=\"${feed.feedUrl.orEmpty().xmlEscape()}\" />\n",
                    )
                }
                append("    </outline>\n")
            }
            append("  </body>\n")
            append("</opml>\n")
        }
    }

    private fun String.xmlEscape(): String =
        replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
}
