package io.pitman.myfeeds.data.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FirstImageExtractorTest {
    @Test
    fun extractFirstImageUrl_resolvesRelativeSrcAgainstBaseUrl() {
        val html = "<p>text</p><img src=\"/images/photo.jpg\" />"

        val result = FirstImageExtractor.extractFirstImageUrl(html, "https://example.com/article-1")

        assertEquals("https://example.com/images/photo.jpg", result)
    }

    @Test
    fun extractFirstImageUrl_keepsAbsoluteSrcAsIs() {
        val html = "<img src=\"https://cdn.example.com/photo.jpg\" />"

        val result = FirstImageExtractor.extractFirstImageUrl(html, "https://example.com/article-1")

        assertEquals("https://cdn.example.com/photo.jpg", result)
    }

    @Test
    fun extractFirstImageUrl_multipleImages_returnsFirstInDocumentOrder() {
        val html = """
            <img src="https://example.com/first.jpg" />
            <img src="https://example.com/second.jpg" />
        """.trimIndent()

        val result = FirstImageExtractor.extractFirstImageUrl(html, "https://example.com")

        assertEquals("https://example.com/first.jpg", result)
    }

    @Test
    fun extractFirstImageUrl_gifIsReturnedDirectly_noConversionProxy() {
        val html = "<img src=\"https://example.com/animated.gif\" />"

        val result = FirstImageExtractor.extractFirstImageUrl(html, "https://example.com")

        assertEquals("https://example.com/animated.gif", result)
    }

    @Test
    fun extractFirstImageUrl_noImgTags_returnsNull() {
        assertNull(FirstImageExtractor.extractFirstImageUrl("<p>just text</p>", "https://example.com"))
    }

    @Test
    fun extractFirstImageUrl_blankHtml_returnsNull() {
        assertNull(FirstImageExtractor.extractFirstImageUrl("", "https://example.com"))
    }
}
