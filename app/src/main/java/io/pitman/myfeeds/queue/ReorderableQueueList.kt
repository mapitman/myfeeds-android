package io.pitman.myfeeds.queue

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

val QUEUE_ROW_HEIGHT = 84.dp
private val THUMBNAIL_SIZE = 48.dp

/**
 * The Next Up queue as a drag-to-reorder list (issue #106), shared by wherever it's shown.
 *
 * The backing [items] list is never mutated mid-drag (issue #209: continuously splicing it on
 * every row-threshold crossing -- the previous approach -- left whichever row a drag settled on
 * rendering at a fraction of [QUEUE_ROW_HEIGHT], covered by the row below it, a state that
 * persisted even after leaving and re-entering the screen). Instead, only the actively-dragged
 * row moves freely (via `graphicsLayer { translationY }`, following the finger 1:1); every other
 * row between its origin and the current target slot animates out of the way by exactly one row
 * height to open a gap, purely visually. The real reorder is computed and committed to
 * [onReorder] exactly once, at drop.
 */
@Composable
fun ReorderableQueueList(
    modifier: Modifier,
    queue: List<QueuedEpisode>,
    onReorder: (List<String>, onComplete: () -> Unit) -> Unit,
    onRemove: (String) -> Unit,
    onClick: (QueuedEpisode) -> Unit,
) {
    // Reassigned from `queue` whenever nothing is being dragged so remote changes (e.g.
    // auto-queue, issue #68) stay reflected too. Never spliced mid-drag -- see the class doc.
    var items by remember { mutableStateOf(queue) }
    var draggedItemId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    // The slot the dragged row would land in if dropped right now, recomputed every onDrag frame
    // from dragOffsetY. -1 (no drag in progress) so the "make room" displacement below is a no-op.
    var targetIndex by remember { mutableStateOf(-1) }
    // The item-id order just committed to the repository at drop, until `queue` catches up to it.
    // `QueueRepository.reorder` writes one position per item as separate, sequential DB updates
    // (not one transaction), so `observeQueue()` can emit several partially-reordered intermediate
    // states while that loop is still running -- a plain "reordering in progress" boolean flag
    // guarding those out isn't enough, because it gets cleared (the write coroutine returns) before
    // Room's invalidation-driven re-query for the *final* write has necessarily reached this
    // composable, letting one of those stale intermediates land right after the guard opens and
    // silently revert the just-dropped order (issue #209). Comparing against the exact order we
    // committed -- instead of trusting timing -- means only a `queue` value that actually matches
    // is ever accepted while a commit is pending.
    var pendingOrder by remember { mutableStateOf<List<String>?>(null) }
    val itemHeightPx = with(LocalDensity.current) { QUEUE_ROW_HEIGHT.toPx() }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(queue) {
        val pending = pendingOrder
        if (pending == null) {
            items = queue
        } else if (queue.map { it.item.id } == pending) {
            items = queue
            pendingOrder = null
        }
    }

    // fillMaxWidth only, not fillMaxSize -- callers that want this to shrink-to-fit a short queue
    // (issue #197) rather than always claim a forced/weighted height pass a bounded heightIn(max=)
    // instead, which a plain LazyColumn already respects by sizing to its actual content within it.
    //
    // userScrollEnabled is off for the duration of a drag: the drag handle's own pointerInput
    // consumes move events for our custom reordering, but LazyColumn's built-in scroll gesture
    // detection still picks up the same finger movement (a nested-scrolling conflict) and scrolls
    // its own content underneath -- so a row correctly pinned at index 0 by the clamp above still
    // ends up pushed above the viewport as the list itself scrolls out from under it (issue #209).
    LazyColumn(modifier = modifier.fillMaxWidth(), state = listState, userScrollEnabled = draggedItemId == null) {
        itemsIndexed(items, key = { _, episode -> episode.item.id }) { index, episode ->
            val isDragged = episode.item.id == draggedItemId
            val originIndex = if (draggedItemId != null) items.indexOfFirst { it.item.id == draggedItemId } else -1
            // Non-dragged rows between the drag's origin and its current target slot shift out of
            // the way by one row height, in whichever direction makes room -- purely visual, the
            // backing list order doesn't actually change until drop.
            val rawDisplacement = if (!isDragged && originIndex != -1) {
                when {
                    originIndex < targetIndex && index in (originIndex + 1)..targetIndex -> -itemHeightPx
                    originIndex > targetIndex && index in targetIndex until originIndex -> itemHeightPx
                    else -> 0f
                }
            } else {
                0f
            }
            // Snapped, not spring-animated: the dragged row's own translationY (below) tracks the
            // finger continuously every frame, but an animated displacement here eases into place
            // over ~200-300ms and can't keep pace with a normal-speed drag -- the growing gap
            // between where the dragged row currently is and where a still-easing neighbor hasn't
            // caught up to yet is exactly the "everything I drag over disappears" bug (issue #209).
            val displacement = rawDisplacement
            // Clamped to how far the dragged row can actually go (its origin slot to index 0, or to
            // the last index) -- without this, a fast/long drag keeps following the finger past the
            // top or bottom of the list into the space of whatever's laid out above/below it (e.g.
            // sliding up under MiniPlayerBar, issue #209/#163), instead of coming to rest pinned at
            // its slot like every other reorderable list.
            val clampedDragOffsetY = if (isDragged && originIndex != -1) {
                dragOffsetY.coerceIn(-originIndex * itemHeightPx, (items.lastIndex - originIndex) * itemHeightPx)
            } else {
                dragOffsetY
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(QUEUE_ROW_HEIGHT)
                    .zIndex(if (isDragged) 1f else 0f)
                    .graphicsLayer { translationY = if (isDragged) clampedDragOffsetY else displacement }
                    // Clips any content that overflows this row's fixed height (e.g. at larger
                    // system font scales) to its own bounds -- otherwise the dragged row's
                    // elevated zIndex below makes that overflow paint visibly on top of the row
                    // underneath it instead of being hidden beneath it as it normally would be.
                    // Must come *after* graphicsLayer above, not before: clipToBounds() clips to
                    // this node's own local bounds, and a clip applied before (outside) the
                    // translation stays anchored to the row's untranslated position instead of
                    // moving with it -- once translationY moves the row further than its own
                    // height, none of the translated content overlaps that stale clip rect at all
                    // and the whole row vanishes (issue #209).
                    .clipToBounds()
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
                            // Dragging starts immediately here (no long-press) -- unlike a
                            // draggable row with no separate handle, this icon isn't also a tap
                            // target, so there's nothing to disambiguate from.
                            detectDragGestures(
                                onDragStart = {
                                    draggedItemId = episode.item.id
                                    dragOffsetY = 0f
                                    targetIndex = items.indexOfFirst { it.item.id == episode.item.id }
                                },
                                onDragEnd = {
                                    val originIndex = items.indexOfFirst { it.item.id == episode.item.id }
                                    val drop = targetIndex
                                    draggedItemId = null
                                    dragOffsetY = 0f
                                    targetIndex = -1
                                    if (originIndex != -1 && drop != -1 && drop != originIndex) {
                                        val reordered = items.toMutableList()
                                        val moved = reordered.removeAt(originIndex)
                                        reordered.add(drop, moved)
                                        items = reordered
                                        pendingOrder = reordered.map { it.item.id }
                                        onReorder(reordered.map { it.item.id }) {}
                                        // The list's own scroll position doesn't track a moved
                                        // item to its new slot on its own -- without this, dropping
                                        // near the top/bottom can leave the row just-dropped above
                                        // or below the visible viewport, looking like it vanished
                                        // (issue #209).
                                        coroutineScope.launch { listState.scrollToItem(drop) }
                                    }
                                },
                                onDragCancel = {
                                    draggedItemId = null
                                    dragOffsetY = 0f
                                    targetIndex = -1
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffsetY += dragAmount.y
                                    val originIndex = items.indexOfFirst { it.item.id == episode.item.id }
                                    if (originIndex == -1) return@detectDragGestures
                                    targetIndex = (originIndex + (dragOffsetY / itemHeightPx).roundToInt())
                                        .coerceIn(0, items.lastIndex)
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
