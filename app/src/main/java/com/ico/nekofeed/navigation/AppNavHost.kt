package com.ico.nekofeed.navigation

import android.net.Uri
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.ico.nekofeed.ui.interaction.InteractionType
import com.ico.nekofeed.ui.interaction.UserInteractionScreen
import com.ico.nekofeed.ui.profile.ProfileScreen
import com.ico.nekofeed.ui.search.SearchScreen
import com.ico.nekofeed.ui.search.SearchViewModel
import com.ico.nekofeed.ui.settings.AiSettingsScreen
import com.ico.nekofeed.ui.stats.StatsScreen

@Composable
fun AppNavHost(
    authRepository: AuthRepository,
    userRepository: UserRepository,
    restartApp: () -> Unit,
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
                    if (!navController.popBackStack()) {
                        navController.navigate("main") {
                            popUpTo("login") { inclusive = true }
                        }
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
                    if (!navController.popBackStack()) {
                        navController.navigate("main") {
                            popUpTo("register") { inclusive = true }
                        }
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
                userRepository = userRepository,
                navController = navController,
                restartApp = restartApp
            )
        }

        // Profile (outside main for direct navigation)
        composable("profile") {
            if (authState.isLoggedIn) {
                ProfileScreen(
                    authViewModel = authViewModel,
                    onLogout = {
                        restartApp()
                    },
                    onLogin = { navController.navigate("login") },
                    onNavigateToLikes = {
                        navController.navigate("main") {
                            popUpTo("profile") { inclusive = true }
                        }
                    },
                    onNavigateToCollections = {
                        navController.navigate("main") {
                            popUpTo("profile") { inclusive = true }
                        }
                    },
                    onNavigateToHistory = {
                        navController.navigate("main") {
                            popUpTo("profile") { inclusive = true }
                        }
                    },
                    onBack = { navController.popBackStack() },
                    onNavigateToAiSettings = { navController.navigate("ai_settings") },
                    loadStats = {
                        userRepository.getUserStats().getOrNull()
                    }
                )
            } else {
                LoginScreen(
                    viewModel = authViewModel,
                    onLoginSuccess = {
                        if (!navController.popBackStack()) {
                            navController.navigate("main") {
                                popUpTo("login") { inclusive = true }
                            }
                        }
                    },
                    onNavigateToRegister = {
                        navController.navigate("register")
                    }
                )
            }
        }

        // AI Settings (outside main for direct navigation)
        composable("ai_settings") {
            AiSettingsScreen(
                onBack = { navController.popBackStack() },
                onSettingsSaved = { restartApp() }
            )
        }
    }
}

@Composable
private fun MainScreen(
    feedViewModel: FeedViewModel,
    authViewModel: AuthViewModel,
    userRepository: UserRepository,
    navController: NavHostController,
    restartApp: () -> Unit
) {
    val nestedNavController = rememberNavController()
    val navBackStackEntry by nestedNavController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "feed"
    val showBottomBar = currentRoute in bottomNavItems.map { it.route }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
                    },
                    onAiSettingsClick = {
                        nestedNavController.navigate("ai_settings")
                    }
                )
            }

            composable(
                route = "detail/{itemId}",
                arguments = listOf(navArgument("itemId") { type = NavType.StringType })
            ) { backStackEntry ->
                val itemId = backStackEntry.arguments?.getString("itemId") ?: ""
                val decodedId = Uri.decode(itemId)
                val uiState by feedViewModel.uiState.collectAsState()
                val item = remember(decodedId, uiState.items) { feedViewModel.getItemById(decodedId) }

                LaunchedEffect(decodedId) {
                    feedViewModel.recordClick(decodedId)
                }

                FeedDetailScreen(
                    item = item,
                    onBack = { nestedNavController.popBackStack() },
                    onLikeClick = { id -> feedViewModel.toggleLike(id) },
                    onCollectClick = { id -> feedViewModel.toggleCollect(id) },
                    onShareClick = { id -> feedViewModel.toggleShare(id) },
                    isAiEnabled = uiState.isAiEnabled,
                    onAiRequest = { feedViewModel.requestAiAnalysis(it) }
                )
            }

            composable("search") {
                val searchViewModel: SearchViewModel = viewModel()
                SearchScreen(
                    onBack = { nestedNavController.popBackStack() },
                    onItemClick = { itemId ->
                        val encodedId = Uri.encode(itemId)
                        nestedNavController.navigate("detail/$encodedId")
                    },
                    searchViewModel = searchViewModel,
                    allItems = feedViewModel.getAllItems()
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
                ProfileScreen(
                    authViewModel = authViewModel,
                    onLogout = {
                        restartApp()
                    },
                    onLogin = { navController.navigate("login") },
                    onNavigateToLikes = { nestedNavController.navigate("likes") },
                    onNavigateToCollections = { nestedNavController.navigate("collections") },
                    onNavigateToHistory = { nestedNavController.navigate("history") },
                    onBack = { nestedNavController.popBackStack() },
                    onNavigateToAiSettings = { nestedNavController.navigate("ai_settings") },
                    loadStats = {
                        userRepository.getUserStats().getOrNull()
                    }
                )
            }

            composable("likes") {
                UserInteractionScreen(
                    type = InteractionType.LIKES,
                    onBack = { nestedNavController.popBackStack() },
                    onItemClick = { itemId ->
                        val encodedId = Uri.encode(itemId)
                        nestedNavController.navigate("detail/$encodedId")
                    }
                )
            }

            composable("collections") {
                UserInteractionScreen(
                    type = InteractionType.COLLECTIONS,
                    onBack = { nestedNavController.popBackStack() },
                    onItemClick = { itemId ->
                        val encodedId = Uri.encode(itemId)
                        nestedNavController.navigate("detail/$encodedId")
                    }
                )
            }

            composable("history") {
                UserInteractionScreen(
                    type = InteractionType.HISTORY,
                    onBack = { nestedNavController.popBackStack() },
                    onItemClick = { itemId ->
                        val encodedId = Uri.encode(itemId)
                        nestedNavController.navigate("detail/$encodedId")
                    }
                )
            }

            composable("ai_settings") {
                AiSettingsScreen(
                    onBack = { nestedNavController.popBackStack() },
                    onSettingsSaved = { restartApp() }
                )
            }
        }
    }
}
