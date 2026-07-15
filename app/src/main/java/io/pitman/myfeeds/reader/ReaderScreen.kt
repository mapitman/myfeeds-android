package io.pitman.myfeeds.reader

import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import io.pitman.myfeeds.R
import io.pitman.myfeeds.articlelist.ArticleDateFormatter
import io.pitman.myfeeds.data.local.FeedItem
import io.pitman.myfeeds.data.local.isPodcastEpisode
import io.pitman.myfeeds.data.settings.scaleFactor
import io.pitman.myfeeds.playback.PlaybackUiState
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    modifier: Modifier = Modifier,
    viewModel: ReaderViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val articleFontSize by viewModel.articleFontSize.collectAsState()
    val context = LocalContext.current

    if (uiState.items.isEmpty()) {
        Scaffold(modifier = modifier) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.reader_no_article_to_show))
            }
        }
        return
    }

    val pagerState = rememberPagerState(initialPage = uiState.initialIndex) { uiState.items.size }
    var zoomedImageUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pagerState, uiState.items) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                uiState.items.getOrNull(page)?.let { viewModel.markRead(it.id) }
            }
    }

    val currentItem = uiState.items.getOrNull(pagerState.currentPage)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.reader_page_position, pagerState.currentPage + 1, uiState.items.size))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val url = currentItem?.url ?: return@IconButton
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = stringResource(R.string.cd_open_in_browser))
                    }
                    IconButton(onClick = {
                        val item = currentItem ?: return@IconButton
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, item.title)
                            putExtra(Intent.EXTRA_TEXT, item.url)
                        }
                        context.startActivity(Intent.createChooser(sendIntent, null))
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.cd_share))
                    }
                },
            )
        },
    ) { innerPadding ->
        HorizontalPager(state = pagerState, modifier = Modifier.padding(innerPadding).fillMaxSize()) { page ->
            ArticlePage(
                item = uiState.items[page],
                onImageClick = { zoomedImageUrl = it },
                fontScale = articleFontSize.scaleFactor,
                playbackState = playbackState,
                onTogglePlayPause = { viewModel.togglePlayPause(uiState.items[page]) },
                onSeek = viewModel::seekTo,
                onDownload = { viewModel.downloadEnclosure(uiState.items[page]) },
                onDelete = { viewModel.deleteDownload(uiState.items[page]) },
            )
        }
    }

    zoomedImageUrl?.let { url ->
        ZoomableImageDialog(imageUrl = url, onDismiss = { zoomedImageUrl = null })
    }
}

@Composable
private fun ArticlePage(
    item: FeedItem,
    onImageClick: (String) -> Unit,
    fontScale: Float,
    playbackState: PlaybackUiState,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(
            text = item.title.orEmpty(),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(16.dp),
        )
        Text(
            text = ArticleDateFormatter.format(item.publishDate),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        if (item.isPodcastEpisode) {
            PodcastPlayerControls(
                isCurrentItem = playbackState.currentItemId == item.id,
                playbackState = playbackState,
                savedPositionMs = item.enclosurePosition?.let { (it * 1000).toLong() },
                savedDurationMs = item.enclosureDurationMs,
                downloadedFilePath = item.downloadedFilePath,
                downloadedBytes = item.downloadedBytes,
                enclosureLength = item.enclosureLength,
                onTogglePlayPause = onTogglePlayPause,
                onSeek = onSeek,
                onDownload = onDownload,
                onDelete = onDelete,
            )
        }
        val imageUrl = item.imageUrl
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clickable { onImageClick(imageUrl) },
            )
        }
        ArticleBody(html = item.description.orEmpty(), baseUrl = item.url, fontScale = fontScale)
    }
}

@Composable
private fun PodcastPlayerControls(
    isCurrentItem: Boolean,
    playbackState: PlaybackUiState,
    savedPositionMs: Long?,
    savedDurationMs: Long?,
    downloadedFilePath: String?,
    downloadedBytes: Long?,
    enclosureLength: Long?,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    // While actively loaded *and* the player has a real duration, show the live player position.
    // Otherwise -- not yet played this session, still buffering, or the mini-player was dismissed
    // (issue #75) -- fall back to the saved resume position/duration (itunes:duration, where the
    // feed provides it) so progress doesn't visually reset to 0:00. If duration truly isn't known
    // (feed has no itunes:duration and playback hasn't buffered in), stay at 0 rather than
    // overflowing the slider's fallback range with a positionMs the duration can't yet bound.
    val hasLiveDuration = isCurrentItem && playbackState.durationMs > 0
    val durationMs = when {
        hasLiveDuration -> playbackState.durationMs
        savedDurationMs != null && savedDurationMs > 0 -> savedDurationMs
        else -> 0L
    }
    val positionMs = when {
        hasLiveDuration -> playbackState.positionMs
        durationMs > 0 -> (savedPositionMs ?: 0L).coerceIn(0L, durationMs)
        else -> 0L
    }
    val isPlaying = isCurrentItem && playbackState.isPlaying
    val isDownloaded = downloadedFilePath != null
    val isDownloading = !isDownloaded && downloadedBytes != null

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Slider(
            value = positionMs.toFloat(),
            onValueChange = { onSeek(it.toLong()) },
            valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
            enabled = isCurrentItem,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        ) {
            Text(formatDuration(positionMs), style = MaterialTheme.typography.bodySmall)
            Text(formatDuration((durationMs - positionMs).coerceAtLeast(0L)), style = MaterialTheme.typography.bodySmall)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onTogglePlayPause) {
                if (isCurrentItem && playbackState.isBuffering) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = stringResource(if (isPlaying) R.string.cd_pause else R.string.cd_play),
                    )
                }
            }
            when {
                isDownloading -> {
                    val progress = if (enclosureLength != null && enclosureLength > 0) {
                        (downloadedBytes!!.toFloat() / enclosureLength).coerceIn(0f, 1f)
                    } else {
                        null
                    }
                    Box(modifier = Modifier.padding(8.dp)) {
                        if (progress != null) {
                            CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(24.dp))
                        } else {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                }
                isDownloaded -> {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.cd_delete_download))
                    }
                }
                else -> {
                    IconButton(onClick = onDownload) {
                        Icon(Icons.Filled.Download, contentDescription = stringResource(R.string.cd_download_episode))
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

@Composable
private fun ArticleBody(html: String, baseUrl: String?, fontScale: Float) {
    val context = LocalContext.current
    val sanitized = remember(html) { HtmlSanitizer.sanitize(html) }
    val textColor = MaterialTheme.colorScheme.onSurface
    val backgroundColor = MaterialTheme.colorScheme.surface
    val bodyFontSizePx = (16 * fontScale).toInt()

    AndroidView(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = false
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                        if (url == null) return false
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        return true
                    }
                }
            }
        },
        update = { webView ->
            val textColorHex = String.format("#%06X", 0xFFFFFF and textColor.toArgb())
            val backgroundColorHex = String.format("#%06X", 0xFFFFFF and backgroundColor.toArgb())
            val styledHtml = """
                <html><head><meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                  body { color: $textColorHex; background-color: $backgroundColorHex; font-family: sans-serif; font-size: ${bodyFontSizePx}px; }
                  img { max-width: 100%; height: auto; }
                  a { color: $textColorHex; }
                </style></head><body>$sanitized</body></html>
            """.trimIndent()
            webView.loadDataWithBaseURL(baseUrl, styledHtml, "text/html", "UTF-8", null)
        },
    )
}

@Composable
private fun ZoomableImageDialog(imageUrl: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        var scale by remember { mutableStateOf(1f) }
        var offsetX by remember { mutableStateOf(0f) }
        var offsetY by remember { mutableStateOf(0f) }

        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        offsetX += pan.x
                        offsetY += pan.y
                    }
                }
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY,
                ),
        )
    }
}
