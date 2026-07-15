package io.pitman.myfeeds.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.pitman.myfeeds.data.settings.AppSettings
import io.pitman.myfeeds.data.settings.FontSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val settings by viewModel.settings.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionHeader("General")
            UpdateIntervalSetting(settings, viewModel)
            SwitchRow("Show images", settings.enableImageDisplay, viewModel::setEnableImageDisplay)
            SwitchRow("Default to all articles", settings.defaultToAllArticleView, viewModel::setDefaultToAllArticleView)
            MaxArticlesSetting(settings, viewModel)

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            SectionHeader("Fonts")
            FontSizeRow("Article font size", settings.articleFontSize, viewModel::setArticleFontSize)
            FontSizeRow("Article list font size", settings.listFontSize, viewModel::setListFontSize)
            FontSizeRow("Feed list font size", settings.feedListFontSize, viewModel::setFeedListFontSize)

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            SectionHeader("Podcasts")
            SwitchRow(
                "Download on battery",
                settings.allowPodcastDownloadOnBattery,
                viewModel::setAllowPodcastDownloadOnBattery,
            )
            SwitchRow(
                "Download on cellular",
                settings.allowPodcastDownloadOnCellular,
                viewModel::setAllowPodcastDownloadOnCellular,
            )
            SwitchRow("Allow streaming", settings.allowPodcastStreaming, viewModel::setAllowPodcastStreaming)

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            SectionHeader("Actions")
            ActionsSection(viewModel)

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            SectionHeader("About")
            Text("MyFeeds", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 4.dp))
            Text(
                "An RSS/Atom feed reader and podcast client.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun UpdateIntervalSetting(settings: AppSettings, viewModel: SettingsViewModel) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text("Update interval: ${settings.updateIntervalMinutes} min", style = MaterialTheme.typography.bodyLarge)
        Row {
            listOf(15L, 30L, 60L, 120L).forEach { minutes ->
                FilterChip(
                    selected = settings.updateIntervalMinutes == minutes,
                    onClick = { viewModel.setUpdateIntervalMinutes(minutes) },
                    label = { Text("${minutes}m") },
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun MaxArticlesSetting(settings: AppSettings, viewModel: SettingsViewModel) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text("Max articles per feed: ${settings.maxArticles}", style = MaterialTheme.typography.bodyLarge)
        Slider(
            value = settings.maxArticles.toFloat(),
            onValueChange = { viewModel.setMaxArticles(it.toInt()) },
            valueRange = 5f..100f,
            steps = 18,
        )
    }
}

@Composable
private fun FontSizeRow(label: String, selected: FontSize, onSelect: (FontSize) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Row(modifier = Modifier.padding(top = 4.dp)) {
            FontSize.entries.forEach { size ->
                FilterChip(
                    selected = selected == size,
                    onClick = { onSelect(size) },
                    label = { Text(size.name) },
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ActionsSection(viewModel: SettingsViewModel) {
    var confirmAction by remember { mutableStateOf<ConfirmableAction?>(null) }

    Column {
        OutlinedButton(onClick = { confirmAction = ConfirmableAction.ClearPodcasts }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text("Clear podcasts")
        }
        OutlinedButton(onClick = { confirmAction = ConfirmableAction.AddDefaultFeeds }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text("Add default feeds")
        }
        Button(onClick = { confirmAction = ConfirmableAction.RemoveAllFeeds }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text("Remove all feeds")
        }
        Button(onClick = { confirmAction = ConfirmableAction.Reset }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text("Reset settings")
        }
    }

    confirmAction?.let { action ->
        AlertDialog(
            onDismissRequest = { confirmAction = null },
            title = { Text(action.title) },
            text = { Text(action.message) },
            confirmButton = {
                TextButton(onClick = {
                    when (action) {
                        ConfirmableAction.ClearPodcasts -> viewModel.clearPodcasts()
                        ConfirmableAction.AddDefaultFeeds -> viewModel.addDefaultFeeds()
                        ConfirmableAction.RemoveAllFeeds -> viewModel.removeAllFeeds()
                        ConfirmableAction.Reset -> viewModel.resetSettings()
                    }
                    confirmAction = null
                }) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmAction = null }) { Text("Cancel") }
            },
        )
    }
}

private enum class ConfirmableAction(val title: String, val message: String) {
    ClearPodcasts("Clear podcasts?", "This clears saved playback positions for downloaded episodes."),
    AddDefaultFeeds("Add default feeds?", "This adds the bundled starter feeds to your subscriptions."),
    RemoveAllFeeds("Remove all feeds?", "This permanently deletes every subscribed feed and its articles."),
    Reset("Reset settings?", "This restores all settings to their defaults. Feeds are not affected."),
}
