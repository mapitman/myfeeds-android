package io.pitman.myfeeds.articlelist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import io.pitman.myfeeds.R
import io.pitman.myfeeds.data.local.FeedItem

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ArticleListScreen(
    modifier: Modifier = Modifier,
    viewModel: ArticleListViewModel = hiltViewModel(),
    onArticleClick: (String) -> Unit = {},
    onBack: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = {
            if (uiState.isSelectionMode) {
                TopAppBar(
                    title = { Text(stringResource(R.string.article_list_selected, uiState.selectedIds.size)) },
                    navigationIcon = {
                        IconButton(onClick = viewModel::clearSelection) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_clear_selection))
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.markSelectedRead(true) }) {
                            Icon(Icons.Filled.Done, contentDescription = stringResource(R.string.cd_mark_read))
                        }
                        IconButton(onClick = { viewModel.markSelectedRead(false) }) {
                            Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.cd_mark_unread))
                        }
                        IconButton(onClick = viewModel::deleteSelected) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.cd_delete))
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(uiState.feedTitle)
                            Text(
                                text = stringResource(R.string.article_list_unread_count, uiState.unreadCount),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            val selectedTab = if (uiState.showUnreadOnly) 0 else 1
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { viewModel.setShowUnreadOnly(true) },
                    text = { Text(stringResource(R.string.article_list_tab_unread)) },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { viewModel.setShowUnreadOnly(false) },
                    text = { Text(stringResource(R.string.article_list_tab_all)) },
                )
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(uiState.articles, key = { it.id }) { article ->
                    ArticleRow(
                        article = article,
                        selected = article.id in uiState.selectedIds,
                        selectionMode = uiState.isSelectionMode,
                        onClick = {
                            if (uiState.isSelectionMode) viewModel.toggleSelection(article.id) else onArticleClick(article.id)
                        },
                        onLongClick = { viewModel.toggleSelection(article.id) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArticleRow(
    article: FeedItem,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            Checkbox(checked = selected, onCheckedChange = { onClick() })
        }
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = article.title.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (article.isRead) FontWeight.Normal else FontWeight.Bold,
                color = if (article.isRead) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                text = ArticleDateFormatter.format(article.publishDate),
                style = MaterialTheme.typography.bodySmall,
                color = if (article.isRead) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        }
        if (article.imageUrl != null) {
            AsyncImage(
                model = article.imageUrl,
                contentDescription = null,
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
            )
        }
    }
}
