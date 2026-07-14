package io.pitman.myfeeds.data.opml

import org.w3c.dom.Element
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

data class OpmlFeed(val title: String, val xmlUrl: String)

data class OpmlCategory(val name: String, val feeds: List<OpmlFeed>)

data class OpmlDocument(val categories: List<OpmlCategory>)

/**
 * Ported rule from MyFeeds/Opml.cs: an outline without `xmlUrl` is a category, a nested outline
 * with `xmlUrl` is a feed within it, and a feed outline directly under `<body>` (no parent
 * category) falls into "Uncategorized".
 */
object OpmlParser {
    private const val UNCATEGORIZED = "Uncategorized"

    fun parse(input: InputStream): OpmlDocument {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(input)
        val body = document.getElementsByTagName("body").item(0) as? Element
            ?: return OpmlDocument(emptyList())

        val categories = mutableListOf<OpmlCategory>()
        val uncategorizedFeeds = mutableListOf<OpmlFeed>()

        for (outline in body.childElements("outline")) {
            val xmlUrl = outline.getAttribute("xmlUrl")
            if (xmlUrl.isNullOrBlank()) {
                val feeds = outline.childElements("outline")
                    .mapNotNull { it.toOpmlFeedOrNull() }
                categories += OpmlCategory(name = outline.outlineTitle(), feeds = feeds)
            } else {
                uncategorizedFeeds += OpmlFeed(title = outline.outlineTitle(), xmlUrl = xmlUrl)
            }
        }

        if (uncategorizedFeeds.isNotEmpty()) {
            categories += OpmlCategory(name = UNCATEGORIZED, feeds = uncategorizedFeeds)
        }

        return OpmlDocument(categories)
    }

    private fun Element.childElements(tagName: String): List<Element> {
        val result = mutableListOf<Element>()
        val children = childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node is Element && node.tagName == tagName) result += node
        }
        return result
    }

    private fun Element.toOpmlFeedOrNull(): OpmlFeed? {
        val xmlUrl = getAttribute("xmlUrl")
        if (xmlUrl.isNullOrBlank()) return null
        return OpmlFeed(title = outlineTitle(), xmlUrl = xmlUrl)
    }

    private fun Element.outlineTitle(): String =
        getAttribute("text").ifBlank { getAttribute("title") }
}
