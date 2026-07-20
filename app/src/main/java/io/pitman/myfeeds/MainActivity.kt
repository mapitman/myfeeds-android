package io.pitman.myfeeds

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dagger.hilt.android.AndroidEntryPoint
import io.pitman.myfeeds.addfeed.AddFeedScreen
import io.pitman.myfeeds.articlelist.ArticleListScreen
import io.pitman.myfeeds.data.settings.SettingsDataStore
import io.pitman.myfeeds.downloads.DownloadsScreen
import io.pitman.myfeeds.feedlist.FeedListScreen
import io.pitman.myfeeds.feedproperties.FeedPropertiesScreen
import io.pitman.myfeeds.feedriver.FeedRiverScreen
import io.pitman.myfeeds.playback.MiniPlayerBar
import io.pitman.myfeeds.playback.MiniPlayerViewModel
import io.pitman.myfeeds.queue.QueueScreen
import io.pitman.myfeeds.reader.ReaderScreen
import io.pitman.myfeeds.refresh.FeedRefreshScheduler
import io.pitman.myfeeds.settings.SettingsScreen
import io.pitman.myfeeds.ui.theme.MyFeedsTheme
import io.pitman.myfeeds.widget.UnreadWidget
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalSharedTransitionApi::class)
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var feedRefreshScheduler: FeedRefreshScheduler

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // (Re)schedules the periodic refresh worker for the current interval on every app launch
        // (issue #22) -- kept off Application.onCreate() since that also runs for every
        // Robolectric-hosted unit test, where touching WorkManager off the simulated main thread
        // is unsafe. Interval changes made later are rescheduled directly from SettingsViewModel.
        lifecycleScope.launch {
            feedRefreshScheduler.schedule(settingsDataStore.settings.first().updateIntervalMinutes)
        }

        // Refreshes the home-screen widget's unread counts on every app launch (issue #24); the
        // other trigger is FeedRefreshWorker completing a scheduled background refresh.
        lifecycleScope.launch { UnreadWidget().updateAll(applicationContext) }

        // issue #150: sharing a URL from another app (ACTION_SEND) lands here to add it as a feed,
        // the same way tapping a widget feed lands on that feed's article list.
        val sharedUrl = intent.takeIf { it.action == Intent.ACTION_SEND && it.type == "text/plain" }
            ?.getStringExtra(Intent.EXTRA_TEXT)

        val startDestination = intent.getLongExtra(WIDGET_FEED_ID_EXTRA, -1L)
            .takeIf { it >= 0 }
            ?.let { feedId -> "articleList/$feedId" }
            ?: sharedUrl?.let { "addFeed?sharedUrl=${Uri.encode(it)}" }
            ?: "feedList"

        setContent {
            MyFeedsTheme {
                // Backs the mini-player <-> full-player shared-element morph (issue #112): the
                // artwork image and player container carry matching shared keys across
                // MiniPlayerBar, ExpandedPlayerBar (Next Up), and the reader's hero image, so
                // Compose animates bounds/position/size between whichever pair is transitioning
                // in/out at once instead of an instant cut.
                SharedTransitionLayout {
                    val sharedTransitionScope = this
                    val navController = rememberNavController()
                    val miniPlayerViewModel: MiniPlayerViewModel = hiltViewModel()
                    val playbackState by miniPlayerViewModel.playbackState.collectAsState()
                    LaunchedEffect(Unit) { miniPlayerViewModel.restoreLastPlayingItem() }
                    val currentBackStackEntry by navController.currentBackStackEntryAsState()
                    var currentReaderItemId by remember { mutableStateOf<String?>(null) }

                    // The reader screen has its own full player for the episode it's showing (issue #97),
                    // so the mini-player would be redundant there -- hide it only in that exact case, not
                    // just "some reader screen is open" (could be a different, non-playing episode). The
                    // nav route's itemId argument only reflects the episode the reader was *opened* on --
                    // HorizontalPager swipes don't renavigate -- so the on-screen item is tracked
                    // separately via ReaderScreen's onCurrentItemChange callback instead.
                    val isOnPlayingEpisodeReader = currentBackStackEntry?.destination?.route == "reader/{feedId}/{itemId}" &&
                        currentBackStackEntry?.arguments?.getLong("feedId") == playbackState.feedId &&
                        currentReaderItemId == playbackState.currentItemId

                    // Next Up (issue #106) shows the current episode as the top of its own list, with
                    // the full-size player, rather than needing the global bar rendered underneath it.
                    val isOnQueueScreen = currentBackStackEntry?.destination?.route == "queue"

                    Column(modifier = Modifier.fillMaxSize()) {
                        NavHost(
                            navController = navController,
                            startDestination = startDestination,
                            modifier = Modifier.weight(1f),
                        ) {
                            composable("feedList") {
                                FeedListScreen(
                                    onAddFeedClick = { navController.navigate("addFeed") },
                                    onFeedClick = { feedId -> navController.navigate("articleList/$feedId") },
                                    onSettingsClick = { navController.navigate("settings") },
                                    onQueueClick = { navController.navigate("queue") },
                                    onFeedLongClick = { feedId -> navController.navigate("feedProperties/$feedId") },
                                    onReadAllFeedsClick = { navController.navigate("feedRiver") },
                                    onDownloadsClick = { navController.navigate("downloads") },
                                )
                            }
                            composable("downloads") {
                                DownloadsScreen(onBack = { navController.popBackStack() })
                            }
                            composable("feedRiver") {
                                FeedRiverScreen(
                                    onBack = { navController.popBackStack() },
                                    onArticleClick = { feedId, itemId -> navController.navigate("reader/$feedId/$itemId") },
                                    onQueueClick = { navController.navigate("queue") },
                                )
                            }
                            composable(
                                "queue",
                                // None rather than a fade: the destination still gets a real,
                                // timed AnimatedContentScope transition (so the shared player
                                // elements have a window to interpolate through on both push and
                                // pop), but the screen content itself doesn't *also* fade/scale --
                                // doing both at once was fighting the shared-element motion and
                                // reading as a jarring jump instead of a smooth morph.
                                enterTransition = { EnterTransition.None },
                                exitTransition = { ExitTransition.None },
                                popEnterTransition = { EnterTransition.None },
                                popExitTransition = { ExitTransition.None },
                            ) {
                                QueueScreen(
                                    onBack = { navController.popBackStack() },
                                    onEpisodeClick = { feedId, itemId -> navController.navigate("reader/$feedId/$itemId") },
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedVisibilityScope = this,
                                )
                            }
                            composable(
                                "feedProperties/{feedId}",
                                arguments = listOf(navArgument("feedId") { type = NavType.LongType }),
                            ) {
                                FeedPropertiesScreen(onBack = { navController.popBackStack() })
                            }
                            composable("settings") {
                                SettingsScreen(onBack = { navController.popBackStack() })
                            }
                            composable(
                                "addFeed?sharedUrl={sharedUrl}",
                                arguments = listOf(
                                    navArgument("sharedUrl") {
                                        type = NavType.StringType
                                        nullable = true
                                        defaultValue = null
                                    },
                                ),
                            ) { backStackEntry ->
                                AddFeedScreen(
                                    initialUrl = backStackEntry.arguments?.getString("sharedUrl"),
                                    onDone = { navController.popBackStack() },
                                    onBack = { navController.popBackStack() },
                                )
                            }
                            composable(
                                "articleList/{feedId}",
                                arguments = listOf(navArgument("feedId") { type = NavType.LongType }),
                            ) { backStackEntry ->
                                val feedId = backStackEntry.arguments?.getLong("feedId") ?: 0L
                                ArticleListScreen(
                                    onBack = { navController.popBackStack() },
                                    onArticleClick = { itemId -> navController.navigate("reader/$feedId/$itemId") },
                                    onQueueClick = { navController.navigate("queue") },
                                    onFeedSettingsClick = { navController.navigate("feedProperties/$feedId") },
                                )
                            }
                            composable(
                                "reader/{feedId}/{itemId}",
                                arguments = listOf(
                                    navArgument("feedId") { type = NavType.LongType },
                                    navArgument("itemId") { type = NavType.StringType },
                                ),
                                // The mini/expanded player already handles its own exit (issue #112),
                                // so the reader page itself grows up from the bottom and fades in to
                                // meet it, then shrinks back down on the way out.
                                enterTransition = { expandVertically(tween(300), expandFrom = Alignment.Bottom) + fadeIn(tween(300)) },
                                exitTransition = { fadeOut(tween(150)) },
                                popEnterTransition = { fadeIn(tween(150)) },
                                popExitTransition = { shrinkVertically(tween(300), shrinkTowards = Alignment.Bottom) + fadeOut(tween(300)) },
                            ) {
                                ReaderScreen(
                                    onBack = { navController.popBackStack() },
                                    onCurrentItemChange = { currentReaderItemId = it },
                                    onQueueClick = { navController.navigate("queue") },
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedVisibilityScope = this,
                                )
                            }
                        }

                        AnimatedVisibility(
                            visible = playbackState.currentItemId != null && !isOnPlayingEpisodeReader && !isOnQueueScreen,
                        ) {
                            MiniPlayerBar(
                                playbackState = playbackState,
                                onClick = {
                                    val feedId = playbackState.feedId
                                    val itemId = playbackState.currentItemId
                                    if (feedId != null && itemId != null) {
                                        navController.navigate("reader/$feedId/$itemId")
                                    }
                                },
                                onTogglePlayPause = miniPlayerViewModel::togglePlayPause,
                                onSkipBackward = miniPlayerViewModel::skipBackward,
                                onSkipForward = miniPlayerViewModel::skipForward,
                                onNextChapter = miniPlayerViewModel::nextChapter,
                                onPreviousChapter = miniPlayerViewModel::previousChapter,
                                onSpeedChange = miniPlayerViewModel::setSpeed,
                                onStop = miniPlayerViewModel::stop,
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = this,
                            )
                        }
                    }
                }
            }
        }
    }

    companion object {
        /** Matches [io.pitman.myfeeds.widget.FeedIdParam]'s key name -- Glance's actionStartActivity
         * puts ActionParameters into the launch Intent's extras keyed by parameter name. */
        const val WIDGET_FEED_ID_EXTRA = "feedId"
    }
}
