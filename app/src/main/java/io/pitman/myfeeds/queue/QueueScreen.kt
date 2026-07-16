package io.pitman.myfeeds.queue

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import io.pitman.myfeeds.R
import io.pitman.myfeeds.data.local.QueuedEpisode
import io.pitman.myfeeds.playback.ExpandedPlayerBar
import io.pitman.myfeeds.playback.MiniPlayerViewModel
import kotlin.math.roundToInt

private val ROW_HEIGHT = 72.dp

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

        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            // Next Up (issue #106) is the screen most about "what's playing", so the currently
            // playing episode gets the full player -- with cover art and transport controls --
            // as the top of the list, rather than a plain pinned row.
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
                    onStop = miniPlayerViewModel::stop,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                )
            }
            ReorderableQueueList(
                modifier = Modifier.weight(1f),
                queue = queue,
                onReorder = viewModel::reorder,
                onRemove = viewModel::remove,
                onClick = { episode ->
                    viewModel.playNow(episode)
                    onEpisodeClick(episode.item.feedId, episode.item.id)
                },
            )
        }
    }
}

@Composable
private fun ReorderableQueueList(
    modifier: Modifier,
    queue: List<QueuedEpisode>,
    onReorder: (List<String>) -> Unit,
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
    val itemHeightPx = with(LocalDensity.current) { ROW_HEIGHT.toPx() }

    LaunchedEffect(queue) {
        if (draggedItemId == null) items = queue
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        itemsIndexed(items, key = { _, episode -> episode.item.id }) { _, episode ->
            val isDragged = episode.item.id == draggedItemId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ROW_HEIGHT)
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
                                    dragOffsetY = 0f
                                },
                                onDragEnd = {
                                    draggedItemId = null
                                    dragOffsetY = 0f
                                    onReorder(items.map { it.item.id })
                                },
                                onDragCancel = {
                                    draggedItemId = null
                                    dragOffsetY = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    offsetY += dragAmount.y
                                    dragOffsetY = offsetY

                                    val currentIndex = items.indexOfFirst { it.item.id == episode.item.id }
                                    if (currentIndex == -1) return@detectDragGestures
                                    val targetIndex = (currentIndex + (offsetY / itemHeightPx).roundToInt())
                                        .coerceIn(0, items.lastIndex)
                                    if (targetIndex != currentIndex) {
                                        val reordered = items.toMutableList()
                                        val moved = reordered.removeAt(currentIndex)
                                        reordered.add(targetIndex, moved)
                                        items = reordered
                                        offsetY -= (targetIndex - currentIndex) * itemHeightPx
                                        dragOffsetY = offsetY
                                    }
                                },
                            )
                        },
                )
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
                }
                IconButton(onClick = { onRemove(episode.item.id) }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_remove_from_queue))
                }
            }
        }
    }
}
