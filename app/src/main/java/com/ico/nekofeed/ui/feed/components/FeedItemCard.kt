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
// 【UI 层 · 卡片路由组件（策略模式）】
// ============================================================================
//
// 📌 这个文件展示了 Compose 中常用的"分发"模式：
//    根据数据类型，渲染不同的 UI 组件。
//
// 📌 核心逻辑：when (cardType) { ... }
//    - LARGE_IMAGE → LargeImageFeedCard（大图卡片）
//    - SMALL_IMAGE → SmallImageFeedCard（小图卡片）
//    - VIDEO       → VideoFeedCard（视频卡片）
//    - PRODUCT     → ProductFeedCard（商品卡片）
//    - TEXT_ONLY    → SmallImageFeedCard（纯文字，复用小图卡片）
//
// 📌 这就是"策略模式"的 Compose 版本：
//    - 数据决定渲染策略
//    - 每种策略是独立的 Composable
//    - 添加新卡片类型只需：1. 加枚举值 2. 加 when 分支 3. 写新 Composable
//
// 📌 lambda 参数设计：
//    - onLikeClick: ((String) -> Unit)? = null
//    - 双重括号 ((String) -> Unit) 是函数类型
//    - ? = null 表示可选参数
//    - 这样卡片组件不需要知道"点赞后做什么"，由外部决定
// ====================================================================

/**
 * FeedItemCard —— 信息流卡片路由
 *
 * 根据 FeedItem.cardType 分发到不同的卡片组件。
 * 外层包一个 Material 3 Card，提供统一的点击、形状和阴影效果。
 *
 * @param item        FeedItem 数据
 * @param onClick     卡片点击（跳转详情页）
 * @param onLikeClick 点赞回调（可选）
 * @param onCollectClick 收藏回调（可选）
 * @param onShareClick 分享回调（可选）
 * @param onTagClick  标签点击回调（可选）
 * @param isAiEnabled 是否启用 AI 功能
 * @param isPlaying   视频是否正在播放
 * @param onPlaybackStarted 视频开始播放回调
 * @param onMuteToggle 静音切换回调
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
    // 把字符串 "large_image" 转为枚举 FeedCardType.LARGE_IMAGE
    val cardType = FeedCardType.fromString(item.cardType)

    // 分享 lambda：先通知 ViewModel（埋点），再调用系统分享
    val shareItem: (String) -> Unit = { itemId ->
        onShareClick?.invoke(itemId)
        IntentUtils.shareContent(
            context = context,
            title = item.title,
            content = item.displaySummary,
            url = item.sourceUrl
        )
    }

    // ── 外层 Card：统一的点击和样式 ─────────────────────────────
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
        // ── when 分发：根据卡片类型渲染不同的 UI ────────────────
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
