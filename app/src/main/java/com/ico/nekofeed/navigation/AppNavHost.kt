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

// ============================================================================
// 【第2站 · 导航地图】
// ============================================================================
//
// 📌 这个文件定义了整个 App 的页面路由和跳转逻辑，相当于 App 的"地图"。
//
// 📌 核心概念：两级 NavHost（导航宿主）
//
//    ┌─ 外层 NavHost（navController）─────────────────────┐
//    │  "onboarding" → 引导页                              │
//    │  "login"      → 登录页                              │
//    │  "register"   → 注册页                              │
//    │  "main"       → 主界面（包含内层导航）                │
//    │  "profile"    → 个人中心（独立入口）                  │
//    │  "ai_settings"→ AI 设置（独立入口）                  │
//    │                                                      │
//    │  ┌─ 内层 NavHost（nestedNavController）──────────┐  │
//    │  │  "feed"        → 首页信息流                     │  │
//    │  │  "detail/{id}" → 详情页（带参数）               │  │
//    │  │  "search"      → 搜索页                        │  │
//    │  │  "chat"        → AI 对话                       │  │
//    │  │  "stats"       → 统计页                        │  │
//    │  │  "profile"     → 个人中心                      │  │
//    │  │  "likes"       → 我的点赞                      │  │
//    │  │  "collections" → 我的收藏                      │  │
//    │  │  "history"     → 浏览历史                      │  │
//    │  └────────────────────────────────────────────────┘  │
//    └──────────────────────────────────────────────────────┘
//
// 📌 为什么要分两层？
//    - 外层处理"认证流程"（未登录 → 登录/引导 → 进入主页）
//    - 内层处理"主功能"（带底部导航栏的各页面切换）
//    - 这样主页的底部导航栏可以一直显示，不会被登录页覆盖
//
// 📌 关键 API：
//    - composable("route") { Screen() } → 注册一个路由，绑定一个 Composable
//    - navController.navigate("route")  → 跳转到指定路由
//    - navController.popBackStack()      → 返回上一页
//    - viewModel()                       → 获取/创建 ViewModel（自动跟随生命周期）
// ============================================================================

/**
 * AppNavHost —— 整个 App 的导航入口
 *
 * 被 MainActivity.setContent {} 调用，是 Compose 世界的"根组件"。
 *
 * @param authRepository 认证仓库（登录/注册/Token 管理）
 * @param userRepository 用户仓库（用户信息/统计）
 * @param restartApp     重启 App 的 lambda（登出/切换模式时用）
 * @param startDestination 起始路由（已登录→"main"，未登录→"onboarding"）
 * @param navController  导航控制器（管理路由栈）
 */
@Composable
fun AppNavHost(
    authRepository: AuthRepository,
    userRepository: UserRepository,
    restartApp: () -> Unit,
    startDestination: String = "main",
    navController: NavHostController = rememberNavController()
) {
    // ── ViewModel 创建 ─────────────────────────────────────────────
    // viewModel() 是 Compose 提供的工厂函数，自动管理 ViewModel 的生命周期
    // FeedViewModel 在整个 App 生命周期内共享（所有页面用同一个实例）
    val feedViewModel: FeedViewModel = viewModel()
    // AuthViewModel 需要手动传入 authRepository，所以用 lambda 构造
    val authViewModel: AuthViewModel = viewModel { AuthViewModel(authRepository) }
    // collectAsState() 把 Flow 转成 Compose State，数据变化时自动触发重组
    val authState by authViewModel.uiState.collectAsState()

    // ── 外层 NavHost ───────────────────────────────────────────────
    // NavHost 是 Compose Navigation 的核心组件
    // startDestination: App 启动后显示的第一个页面
    // enterTransition/exitTransition: 页面进出动画
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { expressiveEnterTransition() },
        exitTransition = { expressiveExitTransition() },
        popEnterTransition = { expressivePopEnterTransition() },
        popExitTransition = { expressivePopExitTransition() }
    ) {
        // ── 引导页 ─────────────────────────────────────────────────
        // composable("route") 注册一个路由，当 navigate 到这个路由时显示对应的 Composable
        composable("onboarding") {
            OnboardingScreen(
                onComplete = {
                    // 引导完成后：刷新登录状态 → 跳转到主页
                    authViewModel.refreshLoginStatus()
                    // popBackStack 尝试返回；如果栈空了就 navigate 到 main
                    if (!navController.popBackStack()) {
                        navController.navigate("main") {
                            // popUpTo + inclusive: 把 onboarding 从栈里移除（不能返回引导页）
                            popUpTo("onboarding") { inclusive = true }
                        }
                    }
                }
            )
        }

        // ── 登录页 ─────────────────────────────────────────────────
        composable("login") {
            LoginScreen(
                viewModel = authViewModel,
                onLoginSuccess = {
                    // 登录成功：跳转主页，清除登录页
                    if (!navController.popBackStack()) {
                        navController.navigate("main") {
                            popUpTo("login") { inclusive = true }
                        }
                    }
                },
                onNavigateToRegister = {
                    navController.navigate("register") // 跳转注册页
                }
            )
        }

        // ── 注册页 ─────────────────────────────────────────────────
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
                    navController.popBackStack() // 返回上一页（登录页）
                }
            )
        }

        // ── 主界面 ─────────────────────────────────────────────────
        // MainScreen 内部包含第二层 NavHost（带底部导航栏的各功能页）
        composable("main") {
            MainScreen(
                feedViewModel = feedViewModel,
                authViewModel = authViewModel,
                userRepository = userRepository,
                navController = navController,
                restartApp = restartApp
            )
        }

        // ── 个人中心（外层独立入口，用于从非主页面直接跳入）────────
        composable("profile") {
            ProfileScreen(
                authViewModel = authViewModel,
                onLogout = { restartApp() },
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

        // ── AI 设置（外层独立入口）─────────────────────────────────
        composable("ai_settings") {
            AiSettingsScreen(
                onBack = { navController.popBackStack() },
                onSettingsSaved = { restartApp() } // 设置变更后重启 App 使配置生效
            )
        }
    }
}

// ============================================================================
// MainScreen —— 主界面（带底部导航栏的内层导航）
// ============================================================================
//
// 📌 这是一个 private Composable，只在 AppNavHost 内部使用。
//    它包含：
//    1. Scaffold    → Material 3 的页面骨架（自动处理底部栏、内容区）
//    2. 底部导航栏   → NekoFeedBottomNavigationBar
//    3. 内层 NavHost → feed / search / chat / stats 等页面
//
// 📌 关键设计：nestedNavController（嵌套导航控制器）
//    - 外层 navController 管理"认证流程 → 主页"
//    - 内层 nestedNavController 管理"主页内部的各 Tab 页面"
//    - 两层互不干扰，底部导航栏可以一直显示
// ====================================================================

@Composable
private fun MainScreen(
    feedViewModel: FeedViewModel,
    authViewModel: AuthViewModel,
    userRepository: UserRepository,
    navController: NavHostController,    // 外层导航（用于跳登录页等）
    restartApp: () -> Unit
) {
    // 内层导航控制器，管理主页内部的页面切换
    val nestedNavController = rememberNavController()
    // 监听当前页面路由，用于控制底部导航栏高亮和显隐
    val navBackStackEntry by nestedNavController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "feed"
    // 只有在底部导航定义的页面才显示底部栏（详情页等不需要）
    val showBottomBar = currentRoute in bottomNavItems.map { it.route }
    val authState by authViewModel.uiState.collectAsState()

    // 跳转登录页的 lambda（传给需要登录态的页面）
    val navigateToLogin: () -> Unit = {
        navController.navigate("login")
    }
    // 打开详情页的 lambda：先记录点击事件，再导航
    val openDetail: (String) -> Unit = { itemId ->
        feedViewModel.recordClick(itemId)       // 埋点：记录点击
        val encodedId = Uri.encode(itemId)       // URL 编码（防止特殊字符破坏路由）
        nestedNavController.navigate("detail/$encodedId")
    }

    // ── Scaffold：Material 3 页面骨架 ─────────────────────────────
    // Scaffold 提供了标准的页面结构：顶部栏、底部栏、浮动按钮、内容区
    // contentWindowInsets = WindowInsets(0,0,0,0) 表示不自动处理系统栏边距
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            // ── 底部导航栏（带动画显隐）────────────────────────────
            // AnimatedVisibility 控制底部栏的显示/隐藏动画
            AnimatedVisibility(
                visible = showBottomBar,
                enter = fadeIn() + slideInVertically(
                    initialOffsetY = { it / 2 },
                    animationSpec = spring(
                        dampingRatio = 0.82f,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ),
                // ExitTransition.None: 隐藏时不播动画，立即移除
                // 这样详情页的底部栏不会被主页底部栏顶上去
                exit = ExitTransition.None
            ) {
                NekoFeedBottomNavigationBar(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        // 底部导航栏点击：切换 Tab，保留状态
                        nestedNavController.navigate(route) {
                            popUpTo("feed") { saveState = true }  // 回到 feed 为止，保存状态
                            launchSingleTop = true                // 避免重复创建同一页面
                            restoreState = true                   // 恢复之前保存的状态
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        // ── 内层 NavHost：主页内部的各功能页面 ─────────────────────
        // modifier.padding(bottom = ...) 给内容区留出底部导航栏的空间
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
            // ── 首页信息流 ─────────────────────────────────────────
            composable("feed") {
                FeedScreen(
                    viewModel = feedViewModel,
                    onItemClick = openDetail,
                    onSearchClick = { nestedNavController.navigate("search") },
                    onStatsClick = { nestedNavController.navigate("stats") },
                    onAiSettingsClick = { nestedNavController.navigate("ai_settings") },
                    isLoggedIn = authState.isLoggedIn,
                    onLogin = navigateToLogin
                )
            }

            // ── 详情页（带参数的路由）──────────────────────────────
            // route = "detail/{itemId}" 中的 {itemId} 是路径参数
            // navArgument 声明参数类型
            // 使用时：navigate("detail/abc123")，backStackEntry 就能取到 "abc123"
            composable(
                route = "detail/{itemId}",
                arguments = listOf(navArgument("itemId") { type = NavType.StringType })
            ) { backStackEntry ->
                val itemId = backStackEntry.arguments?.getString("itemId") ?: ""
                val decodedId = Uri.decode(itemId) // URL 解码
                // 从 ViewModel 的内存数据中查找对应条目
                val uiState by feedViewModel.uiState.collectAsState()
                val item = remember(decodedId, uiState.items) { feedViewModel.getItemById(decodedId) }

                FeedDetailScreen(
                    item = item,
                    onBack = { nestedNavController.popBackStack() },
                    onLikeClick = feedViewModel::toggleLike,       // :: 是函数引用语法
                    onCollectClick = feedViewModel::toggleCollect,
                    onShareClick = { id -> feedViewModel.toggleShare(id) },
                    isAiEnabled = uiState.isAiEnabled,
                    onAiRequest = { feedViewModel.requestAiAnalysis(it) },
                    onTagClick = { tag ->
                        nestedNavController.popBackStack()         // 返回列表
                        feedViewModel.filterByTag(tag)             // 用标签筛选
                    }
                )
            }

            // ── 搜索页 ─────────────────────────────────────────────
            // SearchViewModel 是搜索页专用的 ViewModel，只在搜索页生命周期内存在
            composable("search") {
                val searchViewModel: SearchViewModel = viewModel()
                SearchScreen(
                    onBack = { nestedNavController.popBackStack() },
                    onItemClick = openDetail,
                    searchViewModel = searchViewModel,
                    allItems = feedViewModel.getAllItems() // 把所有数据传给搜索页做本地匹配
                )
            }

            // ── AI 对话页 ───────────────────────────────────────────
            composable("chat") {
                val chatViewModel: ChatViewModel = viewModel()
                ChatScreen(
                    chatViewModel = chatViewModel,
                    allItems = feedViewModel.getAllItems(),
                    onItemClick = openDetail,
                    onLikeClick = feedViewModel::toggleLike,
                    onCollectClick = feedViewModel::toggleCollect,
                    onShareClick = { itemId -> feedViewModel.toggleShare(itemId) }
                )
            }

            // ── 统计页 ─────────────────────────────────────────────
            // stats 和 statsRange 都是 StateFlow，在这里 collectAsState 订阅
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

            // ── 个人中心（内层，从底部导航进入）────────────────────
            composable("profile") {
                ProfileScreen(
                    authViewModel = authViewModel,
                    onLogout = { restartApp() },
                    onLogin = { navController.navigate("login") },
                    onNavigateToLikes = { nestedNavController.navigate("likes") },
                    onNavigateToCollections = { nestedNavController.navigate("collections") },
                    onNavigateToHistory = { nestedNavController.navigate("history") },
                    onBack = { nestedNavController.popBackStack() },
                    onNavigateToAiSettings = { nestedNavController.navigate("ai_settings") },
                    onNavigateToOnboarding = { navController.navigate("onboarding") },
                    loadStats = { userRepository.getUserStats().getOrNull() }
                )
            }

            // ── 我的点赞 ───────────────────────────────────────────
            composable("likes") {
                UserInteractionScreen(
                    type = InteractionType.LIKES,
                    onBack = { nestedNavController.popBackStack() },
                    onItemClick = openDetail
                )
            }

            // ── 我的收藏 ───────────────────────────────────────────
            composable("collections") {
                UserInteractionScreen(
                    type = InteractionType.COLLECTIONS,
                    onBack = { nestedNavController.popBackStack() },
                    onItemClick = openDetail
                )
            }

            // ── 浏览历史 ───────────────────────────────────────────
            composable("history") {
                UserInteractionScreen(
                    type = InteractionType.HISTORY,
                    onBack = { nestedNavController.popBackStack() },
                    onItemClick = openDetail
                )
            }

            // ── AI 设置（内层入口）─────────────────────────────────
            composable("ai_settings") {
                AiSettingsScreen(
                    onBack = { nestedNavController.popBackStack() },
                    onSettingsSaved = { restartApp() }
                )
            }
        }
    }
}

// ============================================================================
// 页面切换动画
// ============================================================================
// 📌 这四个函数定义了页面进入/退出的动画效果。
//    使用 Compose 的 spring 弹簧动画，让切换更自然。
//
//    expressiveEnterTransition  → 正常进入（从右往左滑入 + 淡入 + 微缩放）
//    expressiveExitTransition   → 正常退出（往左滑出 + 淡出 + 微缩放）
//    expressivePopEnterTransition  → 返回进入（从左往右滑入）
//    expressivePopExitTransition   → 返回退出（往右滑出）
//
//    spring() 参数：
//    - dampingRatio: 阻尼比（越小弹跳越明显，0.82 是轻微弹跳）
//    - stiffness: 刚度（越大动画越快）
// ====================================================================

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
