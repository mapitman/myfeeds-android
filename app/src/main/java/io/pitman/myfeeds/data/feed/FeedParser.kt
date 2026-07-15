package io.pitman.myfeeds.data.feed

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.InputStream
import java.io.StringReader
import java.nio.charset.Charset
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Ported from MyFeeds.Syndication/FeedUpdater.cs (+ MyFeeds/FeedManager.cs): dispatch by root
 * element name (rss / feed / RDF), per-format field mapping with the same element precedence
 * rules and itemGuid fallback chains as the original. First-image extraction is a separate step
 * (issue #12), not part of parsing.
 */
object FeedParser {
    fun parse(input: InputStream, charset: Charset = Charsets.UTF_8): ParsedFeed? =
        parse(input.readBytes().toString(charset))

    fun parse(xml: String): ParsedFeed? {
        val document = parseDocument(xml) ?: parseDocument(cleanControlChars(xml)) ?: return null
        val root = document.documentElement ?: return null

        return when (root.localName()) {
            "rss" -> root.firstChildElement("channel")?.let(::parseRssChannel)
            "feed" -> parseAtomFeed(root)
            "RDF" -> parseRdfDocument(root)
            else -> null
        }
    }

    /** Ported from FeedUpdater.CleanFeedString: strips NUL/SOH, the only chars it strips. */
    private fun cleanControlChars(xml: String): String =
        xml.filter { it.code != 0x00 && it.code != 0x01 }

    private fun parseDocument(xml: String): Document? = try {
        DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = false }
            .newDocumentBuilder()
            .parse(InputSource(StringReader(xml)))
    } catch (_: Exception) {
        null
    }

    // ---- RSS 2.0 ----

    private fun parseRssChannel(channel: Element): ParsedFeed {
        val title = channel.textOf("title")
        val items = channel.childElements("item").map(::parseRssItem)
        return ParsedFeed(
            title = title,
            siteUrl = channel.textOf("link"),
            description = channel.textOf("description").ifBlank { title },
            imageUrl = channel.firstChildElement("image")?.textOf("url")?.ifBlank { null },
            items = items,
        )
    }

    private fun parseRssItem(item: Element): ParsedFeedItem {
        val url = item.textOf("link")
        val guid = item.textOf("guid")
        val enclosure = item.firstChildElement("enclosure")?.let {
            ParsedEnclosure(
                url = it.getAttribute("url"),
                type = it.getAttribute("type"),
                length = it.getAttribute("length").toLongOrNull() ?: 0,
            )
        }
        return ParsedFeedItem(
            title = item.textOf("title"),
            url = url,
            description = item.firstLocalNameOrNull("encoded")?.ifBlank { null } ?: item.textOf("description"),
            publishDate = FeedDateParser.parse(item.textOf("pubDate")),
            itemGuid = guid.ifBlank { url },
            enclosure = enclosure,
            durationMs = item.firstLocalNameOrNull("duration")?.let(::parseItunesDurationMs),
        )
    }

    /** `itunes:duration` is either plain seconds or `[HH:]MM:SS`. */
    private fun parseItunesDurationMs(raw: String): Long? {
        val parts = raw.trim().split(":").map { it.toIntOrNull() ?: return null }
        if (parts.isEmpty() || parts.size > 3) return null
        val seconds = parts.fold(0) { acc, part -> acc * 60 + part }
        return seconds * 1000L
    }

    // ---- Atom ----

    private fun parseAtomFeed(root: Element): ParsedFeed {
        val title = root.textOf("title")
        val items = root.childElements("entry").map(::parseAtomEntry)
        return ParsedFeed(
            title = title,
            siteUrl = root.pickAtomLink(rel = "alternate", preferNoType = true),
            description = root.textOf("subtitle").ifBlank { title },
            imageUrl = root.firstChildElement("logo")?.textContent?.trim()?.ifBlank { null }
                ?: root.firstChildElement("icon")?.textContent?.trim()?.ifBlank { null },
            items = items,
        )
    }

    private fun parseAtomEntry(entry: Element): ParsedFeedItem {
        val url = entry.pickAtomLink(rel = "alternate", preferNoType = true)
        val id = entry.textOf("id")
        val enclosureLink = entry.childElements("link").firstOrNull { it.getAttribute("rel") == "enclosure" }
        val enclosure = enclosureLink?.let {
            ParsedEnclosure(
                url = it.getAttribute("href"),
                type = it.getAttribute("type"),
                length = it.getAttribute("length").toLongOrNull() ?: 0,
            )
        }
        return ParsedFeedItem(
            title = entry.textOf("title"),
            url = url,
            description = entry.firstLocalNameOrNull("content", "summary").orEmpty(),
            publishDate = FeedDateParser.parse(entry.textOf("updated")),
            itemGuid = id.ifBlank { url },
            enclosure = enclosure,
        )
    }

    /** `rel="alternate" && type="text/html"`, or any link with no `type` attribute. */
    private fun Element.pickAtomLink(rel: String, preferNoType: Boolean): String =
        childElements("link").firstOrNull { link ->
            val linkRel = link.getAttribute("rel")
            val type = link.getAttribute("type")
            (linkRel == rel && type == "text/html") || (preferNoType && type.isBlank())
        }?.getAttribute("href").orEmpty()

    // ---- RDF / RSS 1.0 ----

    private fun parseRdfDocument(root: Element): ParsedFeed {
        val channel = root.firstChildElement("channel")
        val title = channel?.textOf("title").orEmpty()
        val items = root.childElements("item").map(::parseRdfItem)
        return ParsedFeed(
            title = title,
            siteUrl = channel?.textOf("link").orEmpty(),
            description = channel?.textOf("description")?.ifBlank { title } ?: title,
            imageUrl = channel?.firstChildElement("image")?.textOf("url")?.ifBlank { null },
            items = items,
        )
    }

    private fun parseRdfItem(item: Element): ParsedFeedItem {
        val url = item.textOf("link")
        val enclosure = item.firstChildElement("enclosure")
            ?.takeIf { it.getAttribute("type") == "audio/mpeg" }
            ?.let { ParsedEnclosure(url = it.getAttribute("url"), type = "audio/mpeg") }
        return ParsedFeedItem(
            title = item.textOf("title"),
            url = url,
            description = item.firstLocalNameOrNull("encoded")?.ifBlank { null } ?: item.textOf("description"),
            publishDate = FeedDateParser.parse(item.firstLocalNameOrNull("date")),
            itemGuid = url,
            enclosure = enclosure,
        )
    }

    // ---- shared DOM helpers (prefix-stripped local-name matching, like the original's
    // namespace-by-prefix resolution but without needing exact namespace URIs to line up) ----

    private fun Element.localName(): String = tagName.substringAfterLast(':')

    private fun Element.childElements(): List<Element> {
        val result = mutableListOf<Element>()
        val children = childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node is Element) result += node
        }
        return result
    }

    private fun Element.childElements(localName: String): List<Element> =
        childElements().filter { it.localName() == localName }

    private fun Element.firstChildElement(localName: String): Element? =
        childElements().firstOrNull { it.localName() == localName }

    private fun Element.textOf(localName: String): String =
        firstChildElement(localName)?.textContent?.trim().orEmpty()

    private fun Element.firstLocalNameOrNull(vararg localNames: String): String? =
        childElements().firstOrNull { it.localName() in localNames }?.textContent?.trim()
}
