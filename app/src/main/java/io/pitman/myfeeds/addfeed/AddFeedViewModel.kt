package io.pitman.myfeeds.addfeed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.pitman.myfeeds.data.feed.FeedFetchResult
import io.pitman.myfeeds.data.feed.FeedFetcher
import io.pitman.myfeeds.data.feed.FeedUpdateEngine
import io.pitman.myfeeds.data.local.Category
import io.pitman.myfeeds.data.local.CategoryDao
import io.pitman.myfeeds.data.local.Feed
import io.pitman.myfeeds.data.opml.OpmlDocument
import io.pitman.myfeeds.data.opml.OpmlImporter
import io.pitman.myfeeds.data.opml.OpmlParser
import io.pitman.myfeeds.data.repository.FeedRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject

@HiltViewModel
class AddFeedViewModel @Inject constructor(
    private val feedFetcher: FeedFetcher,
    private val feedUpdateEngine: FeedUpdateEngine,
    private val feedRepository: FeedRepository,
    private val categoryDao: CategoryDao,
    private val opmlImporter: OpmlImporter,
    private val httpClient: OkHttpClient,
) : ViewModel() {
    val categories: StateFlow<List<Category>> =
        categoryDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _uiState = MutableStateFlow<AddFeedUiState>(AddFeedUiState.Idle)
    val uiState: StateFlow<AddFeedUiState> = _uiState

    fun addFeedByUrl(url: String, categoryName: String) {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isEmpty()) {
            _uiState.value = AddFeedUiState.Error("Enter a feed or site URL")
            return
        }
        val trimmedCategory = categoryName.trim().ifEmpty { "Uncategorized" }

        viewModelScope.launch {
            _uiState.value = AddFeedUiState.Loading
            when (val result = feedFetcher.fetchFeed(normalizeUrl(trimmedUrl))) {
                is FeedFetchResult.Failure -> _uiState.value = AddFeedUiState.Error(result.message)
                is FeedFetchResult.Success -> {
                    val categoryId = categoryDao.getByName(trimmedCategory)?.id
                        ?: categoryDao.insert(Category(name = trimmedCategory))
                    val feedId = feedRepository.subscribe(
                        Feed(
                            categoryId = categoryId,
                            title = result.feed.title,
                            feedUrl = result.resolvedUrl,
                            siteUrl = result.feed.siteUrl,
                            description = result.feed.description,
                            imageUrl = result.feed.imageUrl,
                        ),
                    )
                    val feed = feedRepository.getFeed(feedId)
                    if (feed != null) feedUpdateEngine.updateFeed(feed)
                    _uiState.value = AddFeedUiState.Success("Added ${result.feed.title}")
                }
            }
        }
    }

    fun importOpml(input: InputStream) {
        viewModelScope.launch {
            _uiState.value = AddFeedUiState.Loading
            val document = withContext(Dispatchers.IO) {
                input.use { OpmlParser.parse(it) }
            }
            finishImport(document)
        }
    }

    fun importOpmlFromUrl(url: String) {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isEmpty()) {
            _uiState.value = AddFeedUiState.Error("Enter an OPML URL")
            return
        }

        viewModelScope.launch {
            _uiState.value = AddFeedUiState.Loading
            val document = withContext(Dispatchers.IO) {
                try {
                    val request = Request.Builder().url(normalizeUrl(trimmedUrl)).build()
                    httpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@withContext null
                        response.body?.byteStream()?.use { OpmlParser.parse(it) }
                    }
                } catch (_: IOException) {
                    null
                }
            }
            if (document == null) {
                _uiState.value = AddFeedUiState.Error("Could not load OPML from $trimmedUrl")
            } else {
                finishImport(document)
            }
        }
    }

    private suspend fun finishImport(document: OpmlDocument) {
        val importedCount = opmlImporter.import(document)
        _uiState.value = if (importedCount > 0) {
            AddFeedUiState.Success("Imported $importedCount feeds")
        } else {
            AddFeedUiState.Error("No feeds found in OPML")
        }
    }

    fun resetState() {
        _uiState.value = AddFeedUiState.Idle
    }

    private fun normalizeUrl(input: String): String =
        if (input.startsWith("http://", ignoreCase = true) || input.startsWith("https://", ignoreCase = true)) {
            input
        } else {
            "https://$input"
        }
}
