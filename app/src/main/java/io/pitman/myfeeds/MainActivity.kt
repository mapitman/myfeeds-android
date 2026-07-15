package io.pitman.myfeeds

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dagger.hilt.android.AndroidEntryPoint
import io.pitman.myfeeds.addfeed.AddFeedScreen
import io.pitman.myfeeds.articlelist.ArticleListScreen
import io.pitman.myfeeds.data.settings.SettingsDataStore
import io.pitman.myfeeds.feedlist.FeedListScreen
import io.pitman.myfeeds.feedproperties.FeedPropertiesScreen
import io.pitman.myfeeds.reader.ReaderScreen
import io.pitman.myfeeds.refresh.FeedRefreshScheduler
import io.pitman.myfeeds.settings.SettingsScreen
import io.pitman.myfeeds.ui.theme.MyFeedsTheme
import io.pitman.myfeeds.widget.UnreadWidget
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

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

        val startDestination = intent.getLongExtra(WIDGET_FEED_ID_EXTRA, -1L)
            .takeIf { it >= 0 }
            ?.let { feedId -> "articleList/$feedId" }
            ?: "feedList"

        setContent {
            MyFeedsTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = startDestination) {
                    composable("feedList") {
                        FeedListScreen(
                            onAddFeedClick = { navController.navigate("addFeed") },
                            onFeedClick = { feedId -> navController.navigate("articleList/$feedId") },
                            onSettingsClick = { navController.navigate("settings") },
                            onFeedLongClick = { feedId -> navController.navigate("feedProperties/$feedId") },
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
                    composable("addFeed") {
                        AddFeedScreen(
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
                        )
                    }
                    composable(
                        "reader/{feedId}/{itemId}",
                        arguments = listOf(
                            navArgument("feedId") { type = NavType.LongType },
                            navArgument("itemId") { type = NavType.StringType },
                        ),
                    ) {
                        ReaderScreen(onBack = { navController.popBackStack() })
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
