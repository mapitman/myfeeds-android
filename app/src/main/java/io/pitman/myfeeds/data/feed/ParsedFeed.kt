package io.pitman.myfeeds.data.feed

import java.time.Instant

data class ParsedEnclosure(
    val url: String,
    val type: String = "",
    val length: Long = 0,
)

data class ParsedFeedItem(
    val title: String,
    val url: String,
    val description: String,
    val publishDate: Instant?,
    val itemGuid: String,
    val enclosure: ParsedEnclosure?,
)

data class ParsedFeed(
    val title: String,
    val siteUrl: String,
    val description: String,
    val imageUrl: String?,
    val items: List<ParsedFeedItem>,
)
