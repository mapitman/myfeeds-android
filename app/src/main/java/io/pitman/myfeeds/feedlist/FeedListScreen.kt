package io.pitman.myfeeds.feedlist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.pitman.myfeeds.R
import io.pitman.myfeeds.ui.components.ListItemRow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedListScreen(
    modifier: Modifier = Modifier,
    viewModel: FeedListViewModel = hiltViewModel(),
    onFeedClick: (Long) -> Unit = {},
    onAddFeedClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onFeedLongClick: (Long) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.app_name))
                        Text(
                            text = stringResource(R.string.feed_list_total_unread, uiState.totalUnread),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.cd_settings))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddFeedClick) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.cd_add_feed))
            }
        },
    ) { innerPadding ->
        if (uiState.categories.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.feed_list_no_feeds_yet))
            }
            return@Scaffold
        }

        val pagerState = rememberPagerState(pageCount = { uiState.categories.size })
        val scope = rememberCoroutineScope()

        Column(modifier = Modifier.padding(innerPadding)) {
            ScrollableTabRow(selectedTabIndex = pagerState.currentPage) {
                uiState.categories.forEachIndexed { index, section ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(section.category.name) },
                    )
                }
            }
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                CategoryFeedList(
                    section = uiState.categories[page],
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = viewModel::refresh,
                    onFeedClick = onFeedClick,
                    onFeedLongClick = onFeedLongClick,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryFeedList(
    section: CategorySectionUiState,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onFeedClick: (Long) -> Unit,
    onFeedLongClick: (Long) -> Unit,
) {
    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Text(
                    text = stringResource(R.string.feed_list_section_unread, section.totalUnread),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(section.feeds, key = { it.feed.id }) { item ->
                ListItemRow(
                    title = item.feed.userTitle ?: item.feed.title.orEmpty(),
                    subtitle = item.feed.description,
                    imageUrl = item.feed.imageUrl,
                    unreadCount = item.unreadCount,
                    onClick = { onFeedClick(item.feed.id) },
                    onLongClick = { onFeedLongClick(item.feed.id) },
                )
            }
        }
    }
}
