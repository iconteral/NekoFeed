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
// 【UI 层 · 底部导航栏组件】
// ============================================================================
//
// 📌 这个文件展示了 Compose 的几个核心模式：
//
//    1. sealed class（密封类）:
//       - 比 enum class 更灵活：每个子类可以有不同的属性
//       - 编译器知道所有可能的子类，when 不需要 else
//       - data object 代替 data class（单例，不需要多个实例）
//
//    2. Material 3 组件：
//       - NavigationBar: Material 3 底部导航栏
//       - NavigationBarItem: 单个导航项
//       - AnimatedContent: 内容切换动画
//
//    3. 图标状态切换：
//       - 选中时用 Filled 图标，未选中时用 Outlined 图标
//       - AnimatedContent 实现平滑的图标切换动画
// ====================================================================

/**
 * BottomNavItem —— 底部导航项（密封类）
 *
 * 🔑 sealed class vs enum class：
 *    - enum: 每个实例完全相同，只有 name 和 ordinal
 *    - sealed: 每个子类可以有不同属性（route, label, selectedIcon, unselectedIcon）
 *
 * 🔑 data object vs data class：
 *    - data class: 可以有多个实例（如 FeedItem("1"), FeedItem("2")）
 *    - data object: 只有一个实例（单例），适合表示固定常量
 */
sealed class BottomNavItem(
    val route: String,                // 路由名（与 NavHost 中的 composable("xxx") 对应）
    val label: String,                // 显示文字
    val selectedIcon: ImageVector,    // 选中时的图标
    val unselectedIcon: ImageVector   // 未选中时的图标
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

/** 底部导航项列表（顺序决定显示顺序） */
val bottomNavItems = listOf(
    BottomNavItem.Home,
    BottomNavItem.Chat,
    BottomNavItem.Stats,
    BottomNavItem.Profile
)

/**
 * 底部导航栏 Composable
 *
 * @param currentRoute 当前路由（用于高亮选中项）
 * @param onNavigate   点击导航项的回调
 *
 * 🔑 Material 3 NavigationBar 的用法：
 *    NavigationBar {                    // 容器
 *        items.forEach { item ->        // 遍历导航项
 *            NavigationBarItem(         // 单个导航项
 *                selected = isSelected, // 是否选中
 *                onClick = { ... },     // 点击回调
 *                icon = { ... },        // 图标
 *                label = { ... }        // 文字
 *            )
 *        }
 *    }
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
            val isSelected = currentRoute == item.route ||
                (item.route == "feed" && currentRoute.startsWith("feed"))

            NavigationBarItem(
                selected = isSelected,
                onClick = { onNavigate(item.route) },
                icon = {
                    // ── AnimatedContent: 图标切换动画 ─────────────
                    // 当 isSelected 变化时，旧图标淡出 + 缩小，新图标淡入 + 放大
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
                // ── 导航项颜色配置 ─────────────────────────────────
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
