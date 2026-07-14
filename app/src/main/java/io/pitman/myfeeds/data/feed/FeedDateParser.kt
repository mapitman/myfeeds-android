package io.pitman.myfeeds.data.feed

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Ported from DateTimeUtils.ParseDate: tries a lenient parse first, then a couple of common
 * feed date variants. The original never throws on failure -- it returns null -- and does a
 * lot of RFC822 military-timezone-letter normalization we don't reproduce here since it's
 * vanishingly rare in real-world feeds; anything that doesn't match a standard format is
 * treated as unparseable (item still ingested, just without a publish date).
 */
object FeedDateParser {
    private val FORMATTERS = listOf(
        DateTimeFormatter.RFC_1123_DATE_TIME,
        DateTimeFormatter.ISO_OFFSET_DATE_TIME,
        DateTimeFormatter.ISO_ZONED_DATE_TIME,
        DateTimeFormatter.ISO_INSTANT,
        DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss zzz"),
        DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss zzz"),
    )

    fun parse(raw: String?): Instant? {
        val value = raw?.trim()
        if (value.isNullOrEmpty()) return null

        for (formatter in FORMATTERS) {
            try {
                return OffsetDateTime.parse(value, formatter).toInstant()
            } catch (_: Exception) {
                // try the next formatter
            }
        }

        return try {
            LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME).toInstant(ZoneOffset.UTC)
        } catch (_: Exception) {
            try {
                LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay().toInstant(ZoneOffset.UTC)
            } catch (_: Exception) {
                null
            }
        }
    }
}
