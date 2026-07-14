package io.pitman.myfeeds.home

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.pitman.myfeeds.Greeter
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    greeter: Greeter,
) : ViewModel() {
    val greeting: String = greeter.greet("MyFeeds")
}
