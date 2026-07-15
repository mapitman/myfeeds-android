package io.pitman.myfeeds

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dagger.hilt.android.AndroidEntryPoint
import io.pitman.myfeeds.addfeed.AddFeedScreen
import io.pitman.myfeeds.articlelist.ArticleListScreen
import io.pitman.myfeeds.feedlist.FeedListScreen
import io.pitman.myfeeds.reader.ReaderScreen
import io.pitman.myfeeds.settings.SettingsScreen
import io.pitman.myfeeds.ui.theme.MyFeedsTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyFeedsTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "feedList") {
                    composable("feedList") {
                        FeedListScreen(
                            onAddFeedClick = { navController.navigate("addFeed") },
                            onFeedClick = { feedId -> navController.navigate("articleList/$feedId") },
                            onSettingsClick = { navController.navigate("settings") },
                        )
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
}
