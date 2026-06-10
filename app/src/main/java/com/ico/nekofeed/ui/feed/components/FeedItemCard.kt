package com.ico.nekofeed.ui.feed.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ico.nekofeed.data.model.FeedCardType
import com.ico.nekofeed.data.model.FeedItem
import com.ico.nekofeed.util.IntentUtils

// ============================================================================
// 【UI 层 · 卡片路由组件】
// ============================================================================
//
// 📌 这个文件展示了 Compose 的"策略模式"：
//    根据数据类型（cardType）分发到不同的 UI 组件。
//
// 📌 when 表达式 + 枚举：
//    - FeedCardType.fromString(item.cardType) 把字符串转为枚举
//    - when (cardType) 根据枚举值选择对应的卡片组件
//    - 编译器会检查是否覆盖了所有枚举值（不需要 else）
//
// 📌 Composable 函数的参数设计：
//    - item: FeedItem       → 数据（必须）
//    - onClick: () -> Unit   → 点击回调（lambda）
//    - onXxxClick: ((String) -> Unit)? = null → 可选回调（nullable lambda）
//    - modifier: Modifier = Modifier → Compose 标准的修饰符参数
//
// 📌 lambda 的两种写法：
//    - { itemId -> viewModel.toggleLike(itemId) }  → 显式参数
//    - viewModel::toggleLike                        → 函数引用（等价写法）
// ====================================================================

/**
 * FeedItemCard —— Feed 卡片路由
 *
 * 这是信息流列表中的"一条内容"的入口组件。
 * 它根据 cardType 分发到具体的卡片实现：
 *
 *    LARGE_IMAGE → LargeImageFeedCard（大图卡片）
 *    SMALL_IMAGE → SmallImageFeedCard（小图卡片）
 *    VIDEO       → VideoFeedCard（视频卡片）
 *    PRODUCT     → ProductFeedCard（商品卡片）
 *    TEXT_ONLY   → SmallImageFeedCard（纯文字复用小图卡片布局）
 *
 * @param item       FeedItem 数据
 * @param onClick    整个卡片的点击回调（跳转详情页）
 * @param onLikeClick    点赞回调（可选）
 * @param onCollectClick 收藏回调（可选）
 * @param onShareClick   分享回调（可选）
 * @param onTagClick     标签点击回调（可选，用于标签筛选）
 * @param isAiEnabled    AI 功能是否启用
 * @param isPlaying      视频是否在播放（只对 VIDEO 类型有效）
 * @param onPlaybackStarted 视频开始播放回调（埋点用）
 * @param onMuteToggle   静音切换回调
 */
@Composable
fun FeedItemCard(
    item: FeedItem,
    onClick: () -> Unit,
    onLikeClick: ((String) -> Unit)? = null,
    onCollectClick: ((String) -> Unit)? = null,
    onShareClick: ((String) -> Unit)? = null,
    onTagClick: ((String) -> Unit)? = null,
    isAiEnabled: Boolean = true,
    isPlaying: Boolean = false,
    onPlaybackStarted: (String) -> Unit = {},
    onMuteToggle: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // 把字符串转为枚举（安全转换，未知值返回 LARGE_IMAGE）
    val cardType = FeedCardType.fromString(item.cardType)

    // 分享 lambda：先通知 ViewModel 记录分享事件，再调用系统分享
    val shareItem: (String) -> Unit = { itemId ->
        onShareClick?.invoke(itemId)
        IntentUtils.shareContent(
            context = context,
            title = item.title,
            content = item.displaySummary,
            url = item.sourceUrl
        )
    }

    // Card 是 Material 3 的卡片容器组件
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 4.dp
        )
    ) {
        // ── 核心：when 分发到具体卡片 ──
        // when 是 Kotlin 的模式匹配表达式
        // 每个分支调用不同的 Composable 组件
        when (cardType) {
            FeedCardType.LARGE_IMAGE -> LargeImageFeedCard(
                item = item,
                onLikeClick = onLikeClick,
                onCollectClick = onCollectClick,
                onShareClick = shareItem,
                onTagClick = onTagClick,
                isAiEnabled = isAiEnabled
            )
            FeedCardType.SMALL_IMAGE -> SmallImageFeedCard(
                item = item,
                onLikeClick = onLikeClick,
                onCollectClick = onCollectClick,
                onShareClick = shareItem,
                onTagClick = onTagClick,
                isAiEnabled = isAiEnabled
            )
            FeedCardType.VIDEO -> VideoFeedCard(
                item = item,
                onLikeClick = onLikeClick,
                onCollectClick = onCollectClick,
                onShareClick = shareItem,
                onTagClick = onTagClick,
                isAiEnabled = isAiEnabled,
                isPlaying = isPlaying,
                onPlaybackStarted = onPlaybackStarted,
                onMuteToggle = onMuteToggle
            )
            FeedCardType.PRODUCT -> ProductFeedCard(
                item = item,
                onLikeClick = onLikeClick,
                onCollectClick = onCollectClick,
                onShareClick = shareItem,
                onTagClick = onTagClick,
                isAiEnabled = isAiEnabled
            )
            // TEXT_ONLY 复用小图卡片的布局（只是没有图片）
            FeedCardType.TEXT_ONLY -> SmallImageFeedCard(
                item = item,
                onLikeClick = onLikeClick,
                onCollectClick = onCollectClick,
                onShareClick = shareItem,
                onTagClick = onTagClick,
                isAiEnabled = isAiEnabled
            )
        }
    }
}
