package io.pitman.myfeeds.feedproperties

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedPropertiesScreen(
    modifier: Modifier = Modifier,
    viewModel: FeedPropertiesViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    var showUnsubscribeConfirm by remember { mutableStateOf(false) }
    var titleField by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uiState.isUnsubscribed) {
        if (uiState.isUnsubscribed) onBack()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Feed properties") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).padding(16.dp)) {
            OutlinedTextField(
                value = titleField ?: uiState.displayTitle,
                onValueChange = { titleField = it },
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Button(
                onClick = { titleField?.let(viewModel::setTitle) },
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text("Save title")
            }

            val useGlobalMax = uiState.itemsToKeep == null
            Text(
                text = "Use global max articles setting (${uiState.globalMaxArticles})",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 24.dp),
            )
            Switch(
                checked = useGlobalMax,
                onCheckedChange = { checked ->
                    viewModel.setItemsToKeep(if (checked) null else uiState.globalMaxArticles)
                },
            )
            if (!useGlobalMax) {
                val itemsToKeep = uiState.itemsToKeep ?: uiState.globalMaxArticles
                Text("Max articles for this feed: $itemsToKeep", style = MaterialTheme.typography.bodyLarge)
                Slider(
                    value = itemsToKeep.toFloat(),
                    onValueChange = { viewModel.setItemsToKeep(it.toInt()) },
                    valueRange = 5f..100f,
                    steps = 18,
                )
            }

            Button(
                onClick = { showUnsubscribeConfirm = true },
                modifier = Modifier.padding(top = 32.dp).fillMaxWidth(),
            ) {
                Text("Unsubscribe")
            }
        }
    }

    if (showUnsubscribeConfirm) {
        AlertDialog(
            onDismissRequest = { showUnsubscribeConfirm = false },
            title = { Text("Unsubscribe from this feed?") },
            text = { Text("This permanently deletes the feed and its articles.") },
            confirmButton = {
                TextButton(onClick = {
                    showUnsubscribeConfirm = false
                    viewModel.unsubscribe()
                }) {
                    Text("Unsubscribe")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnsubscribeConfirm = false }) { Text("Cancel") }
            },
        )
    }
}
