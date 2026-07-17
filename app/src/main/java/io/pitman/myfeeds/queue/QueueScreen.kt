package io.pitman.myfeeds.queue

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import io.pitman.myfeeds.R
import io.pitman.myfeeds.articlelist.ArticleDateFormatter
import io.pitman.myfeeds.data.local.QueuedEpisode
import io.pitman.myfeeds.playback.ExpandedPlayerBar
import io.pitman.myfeeds.playback.MiniPlayerViewModel
import kotlin.math.roundToInt

private val ROW_HEIGHT = 84.dp
private val THUMBNAIL_SIZE = 48.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun QueueScreen(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
    viewModel: QueueViewModel = hiltViewModel(),
    miniPlayerViewModel: MiniPlayerViewModel = hiltViewModel(),
    onEpisodeClick: (Long, String) -> Unit = { _, _ -> },
    onBack: () -> Unit = {},
) {
    val queue by viewModel.queue.collectAsState()
    val playbackState by miniPlayerViewModel.playbackState.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.queue_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        if (queue.isEmpty() && playbackState.currentItemId == null) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.queue_empty))
            }
            return@Scaffold
        }

        // issue #158: measuring available width vs. height (rather than checking
        // Configuration.orientation) also does the right thing in split-screen/multi-window and on
        // foldables, where the window can be wider than it is tall without the device itself being
        // held sideways.
        BoxWithConstraints(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            val isLandscape = maxWidth > maxHeight
            if (isLandscape) {
                Row(modifier = Modifier.fillMaxSize()) {
                    if (playbackState.currentItemId != null) {
                        ExpandedPlayerBar(
                            playbackState = playbackState,
                            onClick = {
                                val feedId = playbackState.feedId
                                val itemId = playbackState.currentItemId
                                if (feedId != null && itemId != null) onEpisodeClick(feedId, itemId)
                            },
                            onSeek = miniPlayerViewModel::seekTo,
                            onTogglePlayPause = miniPlayerViewModel::togglePlayPause,
                            onSkipBackward = miniPlayerViewModel::skipBackward,
                            onSkipForward = miniPlayerViewModel::skipForward,
                            onNextChapter = miniPlayerViewModel::nextChapter,
                            onPreviousChapter = miniPlayerViewModel::previousChapter,
                            onStop = miniPlayerViewModel::stop,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                    ReorderableQueueList(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        queue = queue,
                        onReorder = { ids, onComplete -> viewModel.reorder(ids, onComplete) },
                        onRemove = viewModel::remove,
                        onClick = { episode ->
                            viewModel.playNow(episode)
                            onEpisodeClick(episode.item.feedId, episode.item.id)
                        },
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Next Up (issue #106) is the screen most about "what's playing", so the
                    // currently playing episode gets the full player -- with cover art and
                    // transport controls -- as the top of the list, rather than a plain pinned row.
                    if (playbackState.currentItemId != null) {
                        ExpandedPlayerBar(
                            playbackState = playbackState,
                            onClick = {
                                val feedId = playbackState.feedId
                                val itemId = playbackState.currentItemId
                                if (feedId != null && itemId != null) onEpisodeClick(feedId, itemId)
                            },
                            onSeek = miniPlayerViewModel::seekTo,
                            onTogglePlayPause = miniPlayerViewModel::togglePlayPause,
                            onSkipBackward = miniPlayerViewModel::skipBackward,
                            onSkipForward = miniPlayerViewModel::skipForward,
                            onNextChapter = miniPlayerViewModel::nextChapter,
                            onPreviousChapter = miniPlayerViewModel::previousChapter,
                            onStop = miniPlayerViewModel::stop,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                        )
                    }
                    ReorderableQueueList(
                        modifier = Modifier.weight(1f),
                        queue = queue,
                        onReorder = { ids, onComplete -> viewModel.reorder(ids, onComplete) },
                        onRemove = viewModel::remove,
                        onClick = { episode ->
                            viewModel.playNow(episode)
                            onEpisodeClick(episode.item.feedId, episode.item.id)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReorderableQueueList(
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
    val itemHeightPx = with(LocalDensity.current) { ROW_HEIGHT.toPx() }

    LaunchedEffect(queue) {
        if (!isReordering) items = queue
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        itemsIndexed(items, key = { _, episode -> episode.item.id }) { _, episode ->
            val isDragged = episode.item.id == draggedItemId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ROW_HEIGHT)
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
                                        // Pinned at the top/bottom of the list -- clamp so continuing
                                        // to drag past the boundary doesn't keep sliding the row's
                                        // visual position further from its slot with nothing to
                                        // compensate it (it would otherwise end up dragged up behind
                                        // the top bar, or below the last row, with no swap left to
                                        // reset the offset).
                                        offsetY = offsetY.coerceIn(-itemHeightPx / 2f, itemHeightPx / 2f)
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
                Column(modifier = Modifier.weight(1f)) {
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
