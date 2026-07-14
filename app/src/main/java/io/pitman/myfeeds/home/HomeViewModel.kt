package io.pitman.myfeeds.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.pitman.myfeeds.Greeter
import io.pitman.myfeeds.data.DefaultFeedsSeeder
import io.pitman.myfeeds.data.local.Category
import io.pitman.myfeeds.data.local.CategoryDao
import io.pitman.myfeeds.data.local.Feed
import io.pitman.myfeeds.data.repository.FeedRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    greeter: Greeter,
    private val seeder: DefaultFeedsSeeder,
    feedRepository: FeedRepository,
    categoryDao: CategoryDao,
) : ViewModel() {
    val greeting: String = greeter.greet("MyFeeds")

    val categories: StateFlow<List<Category>> = categoryDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val feeds: StateFlow<List<Feed>> = feedRepository.observeAllFeeds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { seeder.seedIfFirstRun() }
    }
}
