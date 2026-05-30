package com.ico.nekofeed.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ico.nekofeed.ui.detail.FeedDetailScreen
import com.ico.nekofeed.ui.feed.FeedScreen
import com.ico.nekofeed.ui.feed.FeedViewModel

@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController()
) {
    val feedViewModel: FeedViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = "feed"
    ) {
        composable("feed") {
            FeedScreen(
                viewModel = feedViewModel,
                onItemClick = { itemId ->
                    val encodedId = Uri.encode(itemId)
                    navController.navigate("detail/$encodedId")
                }
            )
        }
        composable(
            route = "detail/{itemId}",
            arguments = listOf(
                navArgument("itemId") {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            val itemId = backStackEntry.arguments?.getString("itemId") ?: ""
            val decodedId = Uri.decode(itemId)
            val item = remember(decodedId) {
                feedViewModel.getItemById(decodedId)
            }
            FeedDetailScreen(
                item = item,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
