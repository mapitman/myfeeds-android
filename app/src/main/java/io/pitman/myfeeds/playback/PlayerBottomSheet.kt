package io.pitman.myfeeds.playback

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.pitman.myfeeds.R
import io.pitman.myfeeds.data.local.QueuedEpisode
import io.pitman.myfeeds.queue.ReorderableQueueList

/**
 * Content of the persistent player bottom sheet (issue #195): [MiniPlayerBar] as a sticky header
 * -- it's all that's visible while the sheet sits at its collapsed peek height -- with the Next
 * Up queue revealed below it as the sheet is dragged open. The whole column has to be naturally
 * taller than the sheet's peek height (not conditionally sized to it) for
 * [androidx.compose.material3.BottomSheetScaffold] to compute a distinct "expanded" anchor at
 * all, which is what a fixed [Modifier.height] here is for -- letting [ReorderableQueueList]'s
 * [androidx.compose.foundation.lazy.LazyColumn] claim the remaining bounded space via `weight`.
 *
 * That height is the screen height minus the status bar inset, not the full screen -- edge-to-edge
 * means [androidx.compose.material3.BottomSheetScaffold] otherwise has no notion of the status bar
 * at all, so a plain `fillMaxHeight()` here let the sheet (and its drag handle) expand straight
 * into it.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun PlayerBottomSheetContent(
    playbackState: PlaybackUiState,
    queue: List<QueuedEpisode>,
    onOpenCurrentEpisode: () -> Unit,
    onQueueEpisodeClick: (QueuedEpisode) -> Unit,
    onReorder: (List<String>, onComplete: () -> Unit) -> Unit,
    onRemoveFromQueue: (String) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipBackward: () -> Unit,
    onSkipForward: () -> Unit,
    onNextChapter: () -> Unit,
    onPreviousChapter: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onStop: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
) {
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    Column(modifier = modifier.fillMaxWidth().height(screenHeight - statusBarHeight)) {
        if (playbackState.currentItemId != null) {
            MiniPlayerBar(
                playbackState = playbackState,
                onClick = onOpenCurrentEpisode,
                onTogglePlayPause = onTogglePlayPause,
                onSkipBackward = onSkipBackward,
                onSkipForward = onSkipForward,
                onNextChapter = onNextChapter,
                onPreviousChapter = onPreviousChapter,
                onSpeedChange = onSpeedChange,
                onStop = onStop,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
            )
        }
        Text(
            text = stringResource(R.string.queue_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (queue.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.queue_empty))
            }
        } else {
            ReorderableQueueList(
                modifier = Modifier.weight(1f),
                queue = queue,
                onReorder = onReorder,
                onRemove = onRemoveFromQueue,
                onClick = onQueueEpisodeClick,
            )
        }
    }
}
