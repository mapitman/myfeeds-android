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
                        )
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
                    ) {
                        ArticleListScreen(onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}
