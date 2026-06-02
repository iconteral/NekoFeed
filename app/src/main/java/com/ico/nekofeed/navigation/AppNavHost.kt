package com.ico.nekofeed.navigation

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ico.nekofeed.ui.components.NekoFeedBottomNavigationBar
import com.ico.nekofeed.ui.components.bottomNavItems
import com.ico.nekofeed.ui.detail.FeedDetailScreen
import com.ico.nekofeed.ui.feed.FeedScreen
import com.ico.nekofeed.ui.feed.FeedViewModel
import com.ico.nekofeed.ui.search.SearchScreen
import com.ico.nekofeed.ui.stats.StatsScreen
import com.ico.nekofeed.ui.stats.StatsData
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController()
) {
    val feedViewModel: FeedViewModel = viewModel()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "feed"

    // 判断是否显示底部导航栏
    val showBottomBar = currentRoute in bottomNavItems.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NekoFeedBottomNavigationBar(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo("feed") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = "feed",
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    bottom = paddingValues.calculateBottomPadding()
                )
        ) {
            // 首页
            composable("feed") {
                FeedScreen(
                    viewModel = feedViewModel,
                    onItemClick = { itemId ->
                        val encodedId = Uri.encode(itemId)
                        navController.navigate("detail/$encodedId")
                    },
                    onSearchClick = {
                        navController.navigate("search")
                    },
                    onStatsClick = {
                        navController.navigate("stats")
                    }
                )
            }

            // 详情页
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
                    onBack = { navController.popBackStack() },
                    onLikeClick = { id -> feedViewModel.toggleLike(id) },
                    onCollectClick = { id -> feedViewModel.toggleCollect(id) },
                    onShareClick = { id -> feedViewModel.toggleShare(id) }
                )
            }

            // 搜索页
            composable("search") {
                SearchScreen(
                    onBack = { navController.popBackStack() },
                    onItemClick = { itemId ->
                        val encodedId = Uri.encode(itemId)
                        navController.navigate("detail/$encodedId")
                    },
                    searchAds = { query ->
                        feedViewModel.searchItems(query)
                    }
                )
            }

            // 统计页
            composable("stats") {
                StatsScreen(
                    onBack = { navController.popBackStack() },
                    getStats = {
                        feedViewModel.getStats()
                    },
                    onItemClick = { itemId ->
                        val encodedId = Uri.encode(itemId)
                        navController.navigate("detail/$encodedId")
                    }
                )
            }

            // 个人页（占位）
            composable("profile") {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "个人中心 - 开发中",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewAppNavHost() {
    MaterialTheme {
        AppNavHost()
    }
}
