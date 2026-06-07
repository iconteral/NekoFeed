package com.ico.nekofeed.navigation

import android.net.Uri
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import com.ico.nekofeed.ui.chat.ChatScreen
import com.ico.nekofeed.ui.chat.ChatViewModel
import com.ico.nekofeed.ui.components.NekoFeedBottomNavigationBar
import com.ico.nekofeed.ui.components.bottomNavItems
import com.ico.nekofeed.ui.detail.FeedDetailScreen
import com.ico.nekofeed.ui.feed.FeedScreen
import com.ico.nekofeed.ui.feed.FeedViewModel
import com.ico.nekofeed.ui.interaction.InteractionType
import com.ico.nekofeed.ui.interaction.UserInteractionScreen
import com.ico.nekofeed.ui.onboarding.OnboardingScreen
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
    startDestination: String = "main",
    navController: NavHostController = rememberNavController()
) {
    val feedViewModel: FeedViewModel = viewModel()
    val authViewModel: AuthViewModel = viewModel { AuthViewModel(authRepository) }
    val authState by authViewModel.uiState.collectAsState()

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { expressiveEnterTransition() },
        exitTransition = { expressiveExitTransition() },
        popEnterTransition = { expressivePopEnterTransition() },
        popExitTransition = { expressivePopExitTransition() }
    ) {
        // Onboarding
        composable("onboarding") {
            OnboardingScreen(
                onComplete = {
                    // 刷新登录状态，确保从 Onboarding 登录后状态同步
                    authViewModel.refreshLoginStatus()
                    if (!navController.popBackStack()) {
                        navController.navigate("main") {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    }
                }
            )
        }

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
                onNavigateToOnboarding = { navController.navigate("onboarding") },
                loadStats = {
                    userRepository.getUserStats().getOrNull()
                }
            )
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
    val authState by authViewModel.uiState.collectAsState()

    val navigateToLogin: () -> Unit = {
        navController.navigate("login")
    }
    val openDetail: (String) -> Unit = { itemId ->
        feedViewModel.recordClick(itemId)
        val encodedId = Uri.encode(itemId)
        nestedNavController.navigate("detail/$encodedId")
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = fadeIn() + slideInVertically(
                    initialOffsetY = { it / 2 },
                    animationSpec = spring(
                        dampingRatio = 0.82f,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ),
                // Remove the app bottom bar from Scaffold layout immediately. Keeping it
                // during an exit animation temporarily pushes detail-screen bottom bars up.
                exit = ExitTransition.None
            ) {
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
            enterTransition = { expressiveEnterTransition() },
            exitTransition = { expressiveExitTransition() },
            popEnterTransition = { expressivePopEnterTransition() },
            popExitTransition = { expressivePopExitTransition() },
            modifier = Modifier.padding(
                bottom = paddingValues.calculateBottomPadding()
            )
        ) {
            composable("feed") {
                FeedScreen(
                    viewModel = feedViewModel,
                    onItemClick = openDetail,
                    onSearchClick = {
                        nestedNavController.navigate("search")
                    },
                    onStatsClick = {
                        nestedNavController.navigate("stats")
                    },
                    onAiSettingsClick = {
                        nestedNavController.navigate("ai_settings")
                    },
                    isLoggedIn = authState.isLoggedIn,
                    onLogin = navigateToLogin
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

                FeedDetailScreen(
                    item = item,
                    onBack = { nestedNavController.popBackStack() },
                    onLikeClick = feedViewModel::toggleLike,
                    onCollectClick = feedViewModel::toggleCollect,
                    onShareClick = { id -> feedViewModel.toggleShare(id) },
                    isAiEnabled = uiState.isAiEnabled,
                    onAiRequest = { feedViewModel.requestAiAnalysis(it) },
                    onTagClick = { tag ->
                        nestedNavController.popBackStack()
                        feedViewModel.filterByTag(tag)
                    }
                )
            }

            composable("search") {
                val searchViewModel: SearchViewModel = viewModel()
                SearchScreen(
                    onBack = { nestedNavController.popBackStack() },
                    onItemClick = openDetail,
                    searchViewModel = searchViewModel,
                    allItems = feedViewModel.getAllItems()
                )
            }

            composable("chat") {
                val chatViewModel: ChatViewModel = viewModel()
                ChatScreen(
                    chatViewModel = chatViewModel,
                    allItems = feedViewModel.getAllItems(),
                    onItemClick = openDetail,
                    onLikeClick = feedViewModel::toggleLike,
                    onCollectClick = feedViewModel::toggleCollect,
                    onShareClick = { itemId ->
                        feedViewModel.toggleShare(itemId)
                    }
                )
            }

            composable("stats") {
                val stats by feedViewModel.stats.collectAsState()
                val statsRange by feedViewModel.statsRange.collectAsState()
                StatsScreen(
                    onBack = { nestedNavController.popBackStack() },
                    stats = stats,
                    selectedRange = statsRange,
                    onRangeSelected = feedViewModel::selectStatsRange,
                    onItemClick = openDetail
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
                    onNavigateToOnboarding = { navController.navigate("onboarding") },
                    loadStats = {
                        userRepository.getUserStats().getOrNull()
                    }
                )
            }

            composable("likes") {
                UserInteractionScreen(
                    type = InteractionType.LIKES,
                    onBack = { nestedNavController.popBackStack() },
                    onItemClick = openDetail
                )
            }

            composable("collections") {
                UserInteractionScreen(
                    type = InteractionType.COLLECTIONS,
                    onBack = { nestedNavController.popBackStack() },
                    onItemClick = openDetail
                )
            }

            composable("history") {
                UserInteractionScreen(
                    type = InteractionType.HISTORY,
                    onBack = { nestedNavController.popBackStack() },
                    onItemClick = openDetail
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

private fun expressiveEnterTransition(): EnterTransition {
    return fadeIn(
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
    ) + scaleIn(
        initialScale = 0.96f,
        animationSpec = spring(
            dampingRatio = 0.82f,
            stiffness = Spring.StiffnessMediumLow
        )
    ) + slideInHorizontally(
        initialOffsetX = { it / 12 },
        animationSpec = spring(
            dampingRatio = 0.86f,
            stiffness = Spring.StiffnessMediumLow
        )
    )
}

private fun expressiveExitTransition(): ExitTransition {
    return fadeOut(
        animationSpec = spring(stiffness = Spring.StiffnessMedium)
    ) + scaleOut(
        targetScale = 0.985f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium)
    ) + slideOutHorizontally(
        targetOffsetX = { -it / 18 },
        animationSpec = spring(stiffness = Spring.StiffnessMedium)
    )
}

private fun expressivePopEnterTransition(): EnterTransition {
    return fadeIn(
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
    ) + scaleIn(
        initialScale = 0.985f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
    ) + slideInHorizontally(
        initialOffsetX = { -it / 18 },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
    )
}

private fun expressivePopExitTransition(): ExitTransition {
    return fadeOut(
        animationSpec = spring(stiffness = Spring.StiffnessMedium)
    ) + scaleOut(
        targetScale = 0.96f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium)
    ) + slideOutHorizontally(
        targetOffsetX = { it / 12 },
        animationSpec = spring(stiffness = Spring.StiffnessMedium)
    )
}
