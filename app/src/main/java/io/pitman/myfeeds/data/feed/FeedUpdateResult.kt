package io.pitman.myfeeds.data.feed

sealed interface FeedUpdateResult {
    data class Success(val newItemCount: Int, val evictedItemIds: List<String>) : FeedUpdateResult
    data class Failure(val message: String) : FeedUpdateResult
}
