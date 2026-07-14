package io.pitman.myfeeds.home

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import io.pitman.myfeeds.ui.components.ListItemRow
import io.pitman.myfeeds.ui.components.ReaderText

@Composable
fun HomeScreen(modifier: Modifier = Modifier, viewModel: HomeViewModel = hiltViewModel()) {
    Column(modifier = modifier) {
        ListItemRow(title = viewModel.greeting, subtitle = "MyFeeds design system scaffold", unreadCount = 5)
        ListItemRow(title = "Windows Phone Blog", subtitle = "Official Windows Phone Blog", unreadCount = 15)
        ListItemRow(title = "Already read article", isRead = true)
        ReaderText(text = "Sample reader body text in the ported typography scale.")
    }
}
