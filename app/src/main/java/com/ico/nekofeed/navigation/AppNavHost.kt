package com.ico.nekofeed.navigation

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ico.nekofeed.data.repository.AuthRepository
import com.ico.nekofeed.data.repository.UserRepository
import com.ico.nekofeed.ui.auth.AuthViewModel
import com.ico.nekofeed.ui.auth.LoginScreen
import com.ico.nekofeed.ui.auth.RegisterScreen
import com.ico.nekofeed.ui.components.NekoFeedBottomNavigationBar
import com.ico.nekofeed.ui.components.bottomNavItems
import com.ico.nekofeed.ui.detail.FeedDetailScreen
import com.ico.nekofeed.ui.feed.FeedScreen
import com.ico.nekofeed.ui.feed.FeedViewModel
import com.ico.nekofeed.ui.profile.ProfileScreen
import com.ico.nekofeed.ui.search.SearchScreen
import com.ico.nekofeed.ui.stats.StatsScreen

@Composable
fun AppNavHost(
    authRepository: AuthRepository,
    userRepository: UserRepository,
    navController: NavHostController = rememberNavController()
) {
    val feedViewModel: FeedViewModel = viewModel()
    val authViewModel: AuthViewModel = viewModel { AuthViewModel(authRepository) }
    val authState by authViewModel.uiState.collectAsState()

    NavHost(
        navController = navController,
        startDestination = "main"
    ) {
        // Login
        composable("login") {
            LoginScreen(
                viewModel = authViewModel,
                onLoginSuccess = {
                    navController.navigate("profile") {
                        popUpTo("login") { inclusive = true }
                    }
                },
                onNavigateToRegister = {
                    navController.navigate("register")
                }
            )
        }

        // Register
        composable("register") {
            RegisterScreen(
                viewModel = authViewModel,
                onRegisterSuccess = {
                    navController.navigate("profile") {
                        popUpTo("register") { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.popBackStack()
                }
            )
        }

        // Main app with bottom navigation
        composable("main") {
            MainScreen(
                feedViewModel = feedViewModel,
                authViewModel = authViewModel,
                navController = navController
            )
        }

        // Profile (outside main for direct navigation)
        composable("profile") {
            if (authState.isLoggedIn) {
                ProfileScreen(
                    authViewModel = authViewModel,
                    onLogout = {
                        navController.navigate("main") {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onNavigateToLikes = { },
                    onNavigateToCollections = { },
                    onNavigateToHistory = { },
                    onBack = { navController.popBackStack() }
                )
            } else {
                LoginScreen(
                    viewModel = authViewModel,
                    onLoginSuccess = {
                        navController.navigate("profile") {
                            popUpTo("login") { inclusive = true }
                        }
                    },
                    onNavigateToRegister = {
                        navController.navigate("register")
                    }
                )
            }
        }
    }
}

@Composable
private fun MainScreen(
    feedViewModel: FeedViewModel,
    authViewModel: AuthViewModel,
    navController: NavHostController
) {
    val nestedNavController = rememberNavController()
    val navBackStackEntry by nestedNavController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "feed"
    val showBottomBar = currentRoute in bottomNavItems.map { it.route }

    androidx.compose.material3.Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NekoFeedBottomNavigationBar(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        nestedNavController.navigate(route) {
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
            navController = nestedNavController,
            startDestination = "feed",
            modifier = Modifier.padding(
                bottom = paddingValues.calculateBottomPadding()
            )
        ) {
            composable("feed") {
                FeedScreen(
                    viewModel = feedViewModel,
                    onItemClick = { itemId ->
                        val encodedId = Uri.encode(itemId)
                        nestedNavController.navigate("detail/$encodedId")
                    },
                    onSearchClick = {
                        nestedNavController.navigate("search")
                    },
                    onStatsClick = {
                        nestedNavController.navigate("stats")
                    }
                )
            }

            composable(
                route = "detail/{itemId}",
                arguments = listOf(navArgument("itemId") { type = NavType.StringType })
            ) { backStackEntry ->
                val itemId = backStackEntry.arguments?.getString("itemId") ?: ""
                val decodedId = Uri.decode(itemId)
                val item = remember(decodedId) { feedViewModel.getItemById(decodedId) }
                FeedDetailScreen(
                    item = item,
                    onBack = { nestedNavController.popBackStack() },
                    onLikeClick = { id -> feedViewModel.toggleLike(id) },
                    onCollectClick = { id -> feedViewModel.toggleCollect(id) },
                    onShareClick = { id -> feedViewModel.toggleShare(id) }
                )
            }

            composable("search") {
                SearchScreen(
                    onBack = { nestedNavController.popBackStack() },
                    onItemClick = { itemId ->
                        val encodedId = Uri.encode(itemId)
                        nestedNavController.navigate("detail/$encodedId")
                    },
                    searchAds = { query -> feedViewModel.searchItems(query) }
                )
            }

            composable("stats") {
                StatsScreen(
                    onBack = { nestedNavController.popBackStack() },
                    getStats = { feedViewModel.getStats() },
                    onItemClick = { itemId ->
                        val encodedId = Uri.encode(itemId)
                        nestedNavController.navigate("detail/$encodedId")
                    }
                )
            }

            composable("profile") {
                val authState by authViewModel.uiState.collectAsState()
                if (authState.isLoggedIn) {
                    ProfileScreen(
                        authViewModel = authViewModel,
                        onLogout = {
                            navController.navigate("main") {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                        onNavigateToLikes = { },
                        onNavigateToCollections = { },
                        onNavigateToHistory = { },
                        onBack = { nestedNavController.popBackStack() }
                    )
                } else {
                    navController.navigate("login")
                }
            }
        }
    }
}
