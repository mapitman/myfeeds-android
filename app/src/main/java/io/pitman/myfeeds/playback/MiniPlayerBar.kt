package io.pitman.myfeeds.playback

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.pitman.myfeeds.R

/**
 * Persistent "now playing" bar (issue #66) shown across the app whenever [PlaybackController] has
 * an episode loaded, so playback stays visible/controllable after navigating away from the reader.
 */
@Composable
fun MiniPlayerBar(
    playbackState: PlaybackUiState,
    onClick: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipBackward: () -> Unit,
    onSkipForward: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (playbackState.artworkUrl != null) {
                    AsyncImage(
                        model = playbackState.artworkUrl,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)),
                    )
                }
                Text(
                    text = playbackState.title.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                )
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
