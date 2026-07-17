package io.pitman.myfeeds.data.feed

import org.jsoup.Jsoup

/**
 * Ported from MyFeeds/HtmlUtility.cs::FindImages: scan an item's description/content HTML for
 * `<img src>` tags in document order, resolve relative URLs against the item's link, and use the
 * first one as the item's image.
 *
 * The original also proxied `.gif` URLs through a personal `Gif2Jpg.aspx` conversion service,
 * because Silverlight's Image control couldn't render animated GIFs. That service is long gone
 * and unnecessary here -- Coil (this port's image loader, see plan) renders GIFs natively -- so
 * this is intentionally dropped rather than ported.
 */
object FirstImageExtractor {
    fun extractFirstImageUrl(html: String, baseUrl: String): String? {
        if (html.isBlank()) return null

        val document = try {
            Jsoup.parse(html, baseUrl)
        } catch (_: Exception) {
            return null
        }

        return document.select("img[src]").firstOrNull()?.attr("abs:src")?.ifBlank { null }?.upgradeToHttps()
    }
}
