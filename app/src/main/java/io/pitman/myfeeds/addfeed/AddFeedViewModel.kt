package io.pitman.myfeeds.addfeed

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.pitman.myfeeds.R
import io.pitman.myfeeds.data.directory.FeedDirectory
import io.pitman.myfeeds.data.directory.FeedDirectoryEntry
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class AddFeedViewModel @Inject constructor(
    private val feedFetcher: FeedFetcher,
    private val feedUpdateEngine: FeedUpdateEngine,
    private val feedRepository: FeedRepository,
    private val categoryDao: CategoryDao,
    private val opmlImporter: OpmlImporter,
    private val httpClient: OkHttpClient,
    private val feedDirectory: FeedDirectory,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val categories: StateFlow<List<Category>> =
        categoryDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _uiState = MutableStateFlow<AddFeedUiState>(AddFeedUiState.Idle)
    val uiState: StateFlow<AddFeedUiState> = _uiState

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val searchResults: StateFlow<List<FeedDirectoryEntry>> = _searchQuery
        .debounce(SEARCH_DEBOUNCE_MS)
        .flatMapLatest { query ->
            if (query.isBlank()) flowOf(emptyList()) else flowOf(query).mapLatest { feedDirectory.search(it) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun addFeedByUrl(url: String, categoryName: String) {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isEmpty()) {
            _uiState.value = AddFeedUiState.Error(context.getString(R.string.add_feed_enter_url_error))
            return
        }
        val trimmedCategory = categoryName.trim().ifEmpty { context.getString(R.string.add_feed_uncategorized) }

        viewModelScope.launch {
            _uiState.value = AddFeedUiState.Loading
            addFeed(normalizeUrl(trimmedUrl), trimmedCategory)
        }
    }

    fun addFromDirectory(entry: FeedDirectoryEntry) {
        viewModelScope.launch {
            _uiState.value = AddFeedUiState.Loading
            addFeed(entry.xmlUrl, entry.category)
        }
    }

    private suspend fun addFeed(url: String, categoryName: String) {
        when (val result = feedFetcher.fetchFeed(url)) {
            is FeedFetchResult.Failure -> _uiState.value = AddFeedUiState.Error(result.message)
            is FeedFetchResult.Success -> {
                val categoryId = categoryDao.getByName(categoryName)?.id
                    ?: categoryDao.insert(Category(name = categoryName))
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
                _uiState.value = AddFeedUiState.Success(context.getString(R.string.add_feed_added_message, result.feed.title))
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
            _uiState.value = AddFeedUiState.Error(context.getString(R.string.add_feed_enter_opml_url_error))
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
                _uiState.value = AddFeedUiState.Error(context.getString(R.string.add_feed_could_not_load_opml, trimmedUrl))
            } else {
                finishImport(document)
            }
        }
    }

    private suspend fun finishImport(document: OpmlDocument) {
        val importedCount = opmlImporter.import(document)
        _uiState.value = if (importedCount > 0) {
            AddFeedUiState.Success(context.getString(R.string.add_feed_imported_count, importedCount))
        } else {
            AddFeedUiState.Error(context.getString(R.string.add_feed_no_feeds_found_in_opml))
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

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}
