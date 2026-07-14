package io.pitman.myfeeds.data.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class FeedParserTest {
    private fun fixture(name: String) =
        javaClass.classLoader!!.getResourceAsStream("feed-fixtures/$name")
            ?: error("fixture $name not found")

    @Test
    fun parseRss_mapsChannelAndItemFields() {
        val feed = FeedParser.parse(fixture("rss.xml"))!!

        assertEquals("Ars Technica", feed.title)
        assertEquals("https://arstechnica.com", feed.siteUrl)
        assertEquals("https://arstechnica.com/logo.png", feed.imageUrl)
        assertEquals(2, feed.items.size)

        val first = feed.items[0]
        assertEquals("First Post & Things", first.title)
        assertEquals("urn:uuid:first-post", first.itemGuid)
        assertEquals("<p>Rich <b>content</b> body.</p>", first.description)
        assertEquals(Instant.parse("2013-06-03T11:05:30Z"), first.publishDate)
    }

    @Test
    fun parseRss_descriptionFallsBackWhenNoContentEncoded() {
        val feed = FeedParser.parse(fixture("rss.xml"))!!

        val second = feed.items[1]
        assertEquals("Only a plain description, no content:encoded.", second.description)
    }

    @Test
    fun parseRss_itemGuidFallsBackToLinkWhenGuidMissing() {
        val feed = FeedParser.parse(fixture("rss.xml"))!!

        val second = feed.items[1]
        assertEquals("https://arstechnica.com/second-post", second.itemGuid)
    }

    @Test
    fun parseRss_podcastEnclosureFieldsExtracted() {
        val feed = FeedParser.parse(fixture("rss-podcast.xml"))!!

        val enclosure = feed.items.single().enclosure
        assertNotNull(enclosure)
        assertEquals("https://cdn.twit.tv/windows-weekly-900.mp3", enclosure!!.url)
        assertEquals("audio/mpeg", enclosure.type)
        assertEquals(123456789L, enclosure.length)
    }

    @Test
    fun parseAtom_prefersContentOverSummaryInDocumentOrder() {
        val feed = FeedParser.parse(fixture("atom.xml"))!!

        assertEquals("A Blog", feed.title)
        assertEquals("https://example.com", feed.siteUrl)
        assertEquals("https://example.com/logo.png", feed.imageUrl)

        val first = feed.items[0]
        assertEquals("<p>Full content body.</p>", first.description)
        assertEquals("urn:uuid:entry-1", first.itemGuid)
        assertEquals(Instant.parse("2013-06-03T11:05:30Z"), first.publishDate)
    }

    @Test
    fun parseAtom_fallsBackToSummaryAndLinkWhenNoContentOrId() {
        val feed = FeedParser.parse(fixture("atom.xml"))!!

        val second = feed.items[1]
        assertEquals("Just a summary, no content element.", second.description)
        assertEquals("https://example.com/entry-2", second.itemGuid)
        assertEquals("https://example.com/entry-2", second.url)
    }

    @Test
    fun parseRdf_prefersContentEncodedAndFiltersNonAudioEnclosures() {
        val feed = FeedParser.parse(fixture("rdf.xml"))!!

        assertEquals("An RDF Feed", feed.title)
        assertEquals(2, feed.items.size)

        val first = feed.items[0]
        assertEquals("<p>Rich RDF content.</p>", first.description)
        assertEquals("https://example.com/rdf-item-1", first.itemGuid)
        assertNotNull(first.enclosure)
        assertEquals("audio/mpeg", first.enclosure!!.type)

        val second = feed.items[1]
        assertEquals("No content:encoded here.", second.description)
        assertNull(second.enclosure)
    }

    @Test
    fun parseRdf_itemGuidIsAlwaysTheLink() {
        val feed = FeedParser.parse(fixture("rdf.xml"))!!

        feed.items.forEach { item -> assertEquals(item.url, item.itemGuid) }
    }

    @Test
    fun parse_stripsControlCharsAndRetriesOnParseFailure() {
        val feed = FeedParser.parse(fixture("rss-control-chars.xml"))

        assertNotNull(feed)
        assertEquals("Feed With Control Chars", feed!!.title)
        assertTrue(feed.items.single().title.contains("Item"))
    }

    @Test
    fun parse_unrecognizedRootElement_returnsNull() {
        val feed = FeedParser.parse("<notafeed><thing/></notafeed>")

        assertNull(feed)
    }

    @Test
    fun parse_malformedXmlEvenAfterCleanup_returnsNull() {
        val feed = FeedParser.parse("<rss version=\"2.0\"><channel><title>Unclosed")

        assertNull(feed)
    }
}
