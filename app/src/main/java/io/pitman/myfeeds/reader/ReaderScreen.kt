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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import io.pitman.myfeeds.articlelist.ArticleDateFormatter
import io.pitman.myfeeds.data.local.FeedItem
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    modifier: Modifier = Modifier,
    viewModel: ReaderViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    if (uiState.items.isEmpty()) {
        Scaffold(modifier = modifier) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No article to show")
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
                    Text("${pagerState.currentPage + 1} of ${uiState.items.size}")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val url = currentItem?.url ?: return@IconButton
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Open in browser")
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
                        Icon(Icons.Filled.Share, contentDescription = "Share")
                    }
                },
            )
        },
    ) { innerPadding ->
        HorizontalPager(state = pagerState, modifier = Modifier.padding(innerPadding).fillMaxSize()) { page ->
            ArticlePage(
                item = uiState.items[page],
                onImageClick = { zoomedImageUrl = it },
            )
        }
    }

    zoomedImageUrl?.let { url ->
        ZoomableImageDialog(imageUrl = url, onDismiss = { zoomedImageUrl = null })
    }
}

@Composable
private fun ArticlePage(item: FeedItem, onImageClick: (String) -> Unit) {
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
        ArticleBody(html = item.description.orEmpty(), baseUrl = item.url)
    }
}

@Composable
private fun ArticleBody(html: String, baseUrl: String?) {
    val context = LocalContext.current
    val sanitized = remember(html) { HtmlSanitizer.sanitize(html) }
    val textColor = MaterialTheme.colorScheme.onSurface
    val backgroundColor = MaterialTheme.colorScheme.surface

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
                  body { color: $textColorHex; background-color: $backgroundColorHex; font-family: sans-serif; font-size: 16px; }
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
