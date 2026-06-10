package com.ico.nekofeed.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// ============================================================================
// 【UI 层 · 底部导航栏】
// ============================================================================
//
// 📌 这个文件展示了 Compose 的几个重要模式：
//
//    1. sealed class（密封类）：
//       - 限制类的子类只能在同一个文件中定义
//       - 编译器知道所有可能的子类，when 不需要 else
//       - 比 enum 更灵活：每个子类可以有不同的属性和方法
//
//    2. data object（数据对象）：
//       - Kotlin 1.9+ 语法，类似 data class 但用于 object
//       - 每个 object 是单例，适合定义固定的选项集
//
//    3. AnimatedContent：
//       - Compose 的内容切换动画组件
//       - 当 targetState 变化时，旧内容退出 + 新内容进入
//
//    4. Material 3 NavigationBar：
//       - Material Design 3 的底部导航栏组件
//       - NavigationBarItem 是每个 Tab
// ====================================================================

/**
 * BottomNavItem —— 底部导航栏的项目定义
 *
 * sealed class 限制只有这 4 个子类（Home / Chat / Stats / Profile）。
 * 每个子类定义了：路由名、标签、选中图标、未选中图标。
 *
 * 🔑 sealed class vs enum：
 *    - enum: 所有实例共享相同的属性结构
 *    - sealed class: 每个子类可以有不同的属性和行为
 *    - 本项目中每个 Tab 的图标不同，用 sealed class 更合适
 */
sealed class BottomNavItem(
    val route: String,                    // 路由名（与 NavHost 中的 composable("xxx") 对应）
    val label: String,                    // 显示文字
    val selectedIcon: ImageVector,        // 选中时的图标（实心）
    val unselectedIcon: ImageVector       // 未选中时的图标（空心）
) {
    data object Home : BottomNavItem(
        route = "feed",
        label = "首页",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home
    )

    data object Chat : BottomNavItem(
        route = "chat",
        label = "AI",
        selectedIcon = Icons.AutoMirrored.Filled.Chat,
        unselectedIcon = Icons.AutoMirrored.Outlined.Chat
    )

    data object Stats : BottomNavItem(
        route = "stats",
        label = "统计",
        selectedIcon = Icons.Filled.BarChart,
        unselectedIcon = Icons.Outlined.BarChart
    )

    data object Profile : BottomNavItem(
        route = "profile",
        label = "我的",
        selectedIcon = Icons.Filled.Person,
        unselectedIcon = Icons.Outlined.Person
    )
}

/** 底部导航栏项目列表（按显示顺序） */
val bottomNavItems = listOf(
    BottomNavItem.Home,
    BottomNavItem.Chat,
    BottomNavItem.Stats,
    BottomNavItem.Profile
)

/**
 * NekoFeedBottomNavigationBar —— 底部导航栏 Composable
 *
 * @param currentRoute 当前路由（用于高亮选中的 Tab）
 * @param onNavigate   Tab 点击回调（传入路由名）
 *
 * 🔑 Composable 函数命名约定：大写开头（PascalCase）
 *    普通函数用小写开头（camelCase）
 */
@Composable
fun NekoFeedBottomNavigationBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        bottomNavItems.forEach { item ->
            // 判断当前 Tab 是否选中
            val isSelected = currentRoute == item.route ||
                (item.route == "feed" && currentRoute.startsWith("feed"))

            NavigationBarItem(
                selected = isSelected,
                onClick = { onNavigate(item.route) },
                icon = {
                    // ── AnimatedContent：图标切换动画 ──
                    // 当 isSelected 变化时，图标会播放缩放 + 淡入淡出动画
                    AnimatedContent(
                        targetState = isSelected,
                        transitionSpec = {
                            (fadeIn() + scaleIn(
                                initialScale = 0.72f,
                                animationSpec = spring(
                                    dampingRatio = 0.68f,
                                    stiffness = Spring.StiffnessMedium
                                )
                            )) togetherWith
                                (fadeOut() + scaleOut(targetScale = 0.82f))
                        },
                        label = "bottom_nav_icon"
                    ) { selected ->
                        Icon(
                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                            contentDescription = item.label,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                },
                label = {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                    )
                },
                // ── Material 3 颜色配置 ──
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)
                )
            )
        }
    }
}
