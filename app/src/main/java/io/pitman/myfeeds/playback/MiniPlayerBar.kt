package io.pitman.myfeeds.playback

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.pitman.myfeeds.R

/** Shared-element keys (issue #112) matching across [MiniPlayerBar], [ExpandedPlayerBar], and the
 * reader's hero image -- only one of those three is ever on-screen carrying a given key at once
 * (except mid-transition, which is exactly when Compose is meant to animate between them). */
const val PLAYER_CONTAINER_KEY = "player-container"
const val PLAYER_ARTWORK_KEY = "player-artwork"

/**
 * Persistent "now playing" bar (issue #66) shown across the app whenever [PlaybackController] has
 * an episode loaded, so playback stays visible/controllable after navigating away from the reader.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MiniPlayerBar(
    playbackState: PlaybackUiState,
    onClick: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipBackward: () -> Unit,
    onSkipForward: () -> Unit,
    onStop: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
) {
    with(sharedTransitionScope) {
        Surface(
            modifier = modifier.fillMaxWidth().clickable(onClick = onClick)
                .sharedBounds(
                    rememberSharedContentState(key = PLAYER_CONTAINER_KEY),
                    animatedVisibilityScope = animatedVisibilityScope,
                ),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            tonalElevation = 3.dp,
        ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            val progress = if (playbackState.durationMs > 0) {
                (playbackState.positionMs.toFloat() / playbackState.durationMs).coerceIn(0f, 1f)
            } else {
                0f
            }
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            ) {
                Text(formatDuration(playbackState.positionMs), style = MaterialTheme.typography.labelSmall)
                Text(
                    formatDuration((playbackState.durationMs - playbackState.positionMs).coerceAtLeast(0L)),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (playbackState.artworkUrl != null) {
                    AsyncImage(
                        model = playbackState.artworkUrl,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(4.dp))
                            .sharedElement(
                                rememberSharedContentState(key = PLAYER_ARTWORK_KEY),
                                animatedVisibilityScope = animatedVisibilityScope,
                            ),
                    )
                }
                Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(
                        text = playbackState.title.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (playbackState.feedTitle != null) {
                        Text(
                            text = playbackState.feedTitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onSkipBackward) {
                    Icon(Icons.Filled.FastRewind, contentDescription = stringResource(R.string.cd_rewind))
                }
                IconButton(onClick = onTogglePlayPause) {
                    if (playbackState.isBuffering) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            if (playbackState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = stringResource(if (playbackState.isPlaying) R.string.cd_pause else R.string.cd_play),
                        )
                    }
                }
                IconButton(onClick = onSkipForward) {
                    Icon(Icons.Filled.FastForward, contentDescription = stringResource(R.string.cd_forward))
                }
                IconButton(onClick = onStop) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_stop_playback))
                }
            }
        }
        }
    }
}

/**
 * A taller now-playing player, swapped in for [MiniPlayerBar] on the Next Up screen (issue #106)
 * -- it's the screen most about "what's playing/coming up next", so it gets the full transport
 * controls and cover art background rather than the compact bar shown everywhere else.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ExpandedPlayerBar(
    playbackState: PlaybackUiState,
    onClick: () -> Unit,
    onSeek: (Long) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipBackward: () -> Unit,
    onSkipForward: () -> Unit,
    onStop: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
) {
    with(sharedTransitionScope) {
    Surface(
        modifier = modifier.fillMaxWidth()
            .sharedBounds(
                rememberSharedContentState(key = PLAYER_CONTAINER_KEY),
                animatedVisibilityScope = animatedVisibilityScope,
            ),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 3.dp,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (playbackState.artworkUrl != null) {
                AsyncImage(
                    model = playbackState.artworkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize().blur(24.dp).alpha(0.6f),
                )
                Box(
                    modifier = Modifier.matchParentSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)),
                )
            }
            Column(modifier = Modifier.navigationBarsPadding().padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
                ) {
                    if (playbackState.artworkUrl != null) {
                        AsyncImage(
                            model = playbackState.artworkUrl,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp))
                                .sharedElement(
                                    rememberSharedContentState(key = PLAYER_ARTWORK_KEY),
                                    animatedVisibilityScope = animatedVisibilityScope,
                                ),
                        )
                    }
                    Text(
                        text = playbackState.title.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    )
                }
                Slider(
                    value = playbackState.positionMs.toFloat(),
                    onValueChange = { onSeek(it.toLong()) },
                    valueRange = 0f..playbackState.durationMs.coerceAtLeast(1L).toFloat(),
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
                    Text(formatDuration(playbackState.positionMs), style = MaterialTheme.typography.bodySmall)
                    Text(
                        formatDuration((playbackState.durationMs - playbackState.positionMs).coerceAtLeast(0L)),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onSkipBackward) {
                        Icon(Icons.Filled.FastRewind, contentDescription = stringResource(R.string.cd_rewind))
                    }
                    IconButton(onClick = onTogglePlayPause) {
                        if (playbackState.isBuffering) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                if (playbackState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = stringResource(if (playbackState.isPlaying) R.string.cd_pause else R.string.cd_play),
                            )
                        }
                    }
                    IconButton(onClick = onSkipForward) {
                        Icon(Icons.Filled.FastForward, contentDescription = stringResource(R.string.cd_forward))
                    }
                    IconButton(onClick = onStop) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_stop_playback))
                    }
                }
            }
        }
    }
    }
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
