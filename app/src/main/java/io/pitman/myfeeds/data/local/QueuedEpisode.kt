package io.pitman.myfeeds.data.local

import androidx.room.Embedded

/** A queued episode joined with its parent feed's display title, in queue order. */
data class QueuedEpisode(
    @Embedded val item: FeedItem,
    val feedTitle: String?,
)
