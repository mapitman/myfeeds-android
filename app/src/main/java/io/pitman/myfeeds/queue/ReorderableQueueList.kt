package io.pitman.myfeeds.queue

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import io.pitman.myfeeds.R
import io.pitman.myfeeds.articlelist.ArticleDateFormatter
import io.pitman.myfeeds.data.local.QueuedEpisode
import kotlin.math.roundToInt

val QUEUE_ROW_HEIGHT = 84.dp
private val THUMBNAIL_SIZE = 48.dp

/** The Next Up queue as a drag-to-reorder list (issue #106), shared by wherever it's shown. */
@Composable
fun ReorderableQueueList(
    modifier: Modifier,
    queue: List<QueuedEpisode>,
    onReorder: (List<String>, onComplete: () -> Unit) -> Unit,
    onRemove: (String) -> Unit,
    onClick: (QueuedEpisode) -> Unit,
) {
    // Local, optimistic ordering while a drag is in progress -- committed to the repository (and
    // therefore reflected back through `queue`) only on drag end, via `onReorder`. Reassigned from
    // `queue` whenever nothing is being dragged so remote changes (e.g. auto-queue, issue #68) stay
    // reflected too.
    var items by remember { mutableStateOf(queue) }
    var draggedItemId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    // Separate from `draggedItemId`: that one drives the row's *visual* drag state and must
    // clear the instant a finger lifts, or the row keeps its elevated zIndex/highlight into the
    // settled layout and visibly overlaps its neighbor. This one guards `items` against an
    // unrelated `queue` re-emission landing before the (async, fire-and-forget) reorder write
    // completes and clobbering the just-dropped order with the stale pre-reorder one.
    var isReordering by remember { mutableStateOf(false) }
    val itemHeightPx = with(LocalDensity.current) { QUEUE_ROW_HEIGHT.toPx() }

    LaunchedEffect(queue) {
        if (!isReordering) items = queue
    }

    // fillMaxWidth only, not fillMaxSize -- callers that want this to shrink-to-fit a short queue
    // (issue #197) rather than always claim a forced/weighted height pass a bounded heightIn(max=)
    // instead, which a plain LazyColumn already respects by sizing to its actual content within it.
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        itemsIndexed(items, key = { _, episode -> episode.item.id }) { _, episode ->
            val isDragged = episode.item.id == draggedItemId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(QUEUE_ROW_HEIGHT)
                    // Clips any content that overflows this row's fixed height (e.g. at larger
                    // system font scales) to its own bounds -- otherwise the dragged row's
                    // elevated zIndex below makes that overflow paint visibly on top of the row
                    // underneath it instead of being hidden beneath it as it normally would be.
                    .clipToBounds()
                    .zIndex(if (isDragged) 1f else 0f)
                    .graphicsLayer { translationY = if (isDragged) dragOffsetY else 0f }
                    .then(
                        if (isDragged) {
                            Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        } else {
                            Modifier
                        },
                    )
                    .clickable { onClick(episode) }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.DragHandle,
                    contentDescription = stringResource(R.string.cd_reorder_queue_item),
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .pointerInput(episode.item.id) {
                            var offsetY = 0f
                            // Dragging starts immediately here (no long-press) -- unlike a
                            // draggable row with no separate handle, this icon isn't also a tap
                            // target, so there's nothing to disambiguate from.
                            detectDragGestures(
                                onDragStart = {
                                    offsetY = 0f
                                    draggedItemId = episode.item.id
                                    isReordering = true
                                    dragOffsetY = 0f
                                },
                                onDragEnd = {
                                    draggedItemId = null
                                    dragOffsetY = 0f
                                    onReorder(items.map { it.item.id }) { isReordering = false }
                                },
                                onDragCancel = {
                                    draggedItemId = null
                                    isReordering = false
                                    dragOffsetY = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    offsetY += dragAmount.y

                                    val currentIndex = items.indexOfFirst { it.item.id == episode.item.id }
                                    if (currentIndex == -1) return@detectDragGestures
                                    val rawTargetIndex = currentIndex + (offsetY / itemHeightPx).roundToInt()
                                    val targetIndex = rawTargetIndex.coerceIn(0, items.lastIndex)
                                    if (targetIndex != currentIndex) {
                                        val reordered = items.toMutableList()
                                        val moved = reordered.removeAt(currentIndex)
                                        reordered.add(targetIndex, moved)
                                        items = reordered
                                        offsetY -= (targetIndex - currentIndex) * itemHeightPx
                                    } else if (rawTargetIndex != targetIndex) {
                                        // Pinned at the top/bottom of the list -- there's no row
                                        // above index 0 (or below the last index) left to swap
                                        // places with, so don't allow ANY further overshoot past
                                        // the row's own slot in the pinned direction. A previous
                                        // version of this clamp allowed up to half a row's height
                                        // of overshoot here, which seemed harmless but wasn't:
                                        // LazyColumn clips its content to its own viewport bounds,
                                        // and translationY moves a row's paint position but not its
                                        // layout position -- the index-0 row is still laid out at
                                        // the very top of the LazyColumn's viewport, so any negative
                                        // translationY on it draws above that viewport's top edge
                                        // and gets visibly truncated there. That's exactly the
                                        // "clipped to roughly half a row's height" sliver stuck
                                        // under the app bar (issue #163) -- clamping the overshoot
                                        // to zero means the pinned row simply comes to rest at its
                                        // slot instead of poking out past it.
                                        offsetY = if (rawTargetIndex < 0) {
                                            offsetY.coerceAtLeast(0f)
                                        } else {
                                            offsetY.coerceAtMost(0f)
                                        }
                                    }
                                    dragOffsetY = offsetY
                                },
                            )
                        },
                )
                val thumbnailUrl = episode.item.imageUrl ?: episode.feedImageUrl
                if (thumbnailUrl != null) {
                    AsyncImage(
                        model = thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(THUMBNAIL_SIZE)
                            .clip(RoundedCornerShape(8.dp))
                            // Isolates the thumbnail into its own compositing layer so dragging a
                            // row (which redraws it every frame via graphicsLayer{translationY=..})
                            // doesn't force Coil's image draw to be re-evaluated as part of the
                            // parent Row's drawing, which was visibly choppy mid-drag.
                            .graphicsLayer(),
                    )
                }
                androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = episode.item.title.orEmpty(),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                    )
                    if (episode.feedTitle != null) {
                        Text(
                            text = episode.feedTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    Text(
                        text = ArticleDateFormatter.format(episode.item.publishDate),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                IconButton(onClick = { onRemove(episode.item.id) }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_remove_from_queue))
                }
            }
        }
    }
}
