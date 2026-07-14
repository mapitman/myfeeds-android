package io.pitman.myfeeds.home

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.pitman.myfeeds.ui.components.ListItemRow

@Composable
fun HomeScreen(modifier: Modifier = Modifier, viewModel: HomeViewModel = hiltViewModel()) {
    val categories by viewModel.categories.collectAsState()
    val feeds by viewModel.feeds.collectAsState()

    LazyColumn(modifier = modifier) {
        item {
            ListItemRow(title = viewModel.greeting, subtitle = "${categories.size} categories seeded", unreadCount = feeds.size)
        }
        categories.forEach { category ->
            item {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(feeds.filter { it.categoryId == category.id }) { feed ->
                ListItemRow(title = feed.title ?: feed.feedUrl.orEmpty())
            }
        }
    }
}
