package com.ico.nekofeed.ui.feed.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ico.nekofeed.data.model.FeedItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.ui.tooling.preview.Preview

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SmallImageFeedCard(
    item: FeedItem,
    onLikeClick: ((String) -> Unit)? = null,
    onCollectClick: ((String) -> Unit)? = null,
    onShareClick: ((String) -> Unit)? = null,
    onTagClick: ((String) -> Unit)? = null,
    isAiEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(14.dp, 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 左侧内容
        Column(
            modifier = Modifier.weight(1f)
        ) {
            // 标题
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            val summary = item.aiSummary
            val isLoading = item.isAiLoading
            
            AnimatedContent(
                targetState = Triple(summary, isLoading, summary.isNullOrBlank() && isAiEnabled && isLoading),
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "ai_content_transition"
            ) { (summaryText, loading, showLoading) ->
                if (!summaryText.isNullOrBlank()) {
                    Column {
                        // AI 摘要
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.padding(bottom = 4.dp)
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f),
                                RoundedCornerShape(10.dp)
                            )
                            .padding(4.dp, 6.dp),
                        ) {
                            Text(
                                text = "✨",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            Text(
                                text = summaryText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = MaterialTheme.typography.bodySmall.lineHeight
                            )
                        }

                        // 标签
                        if (item.displayTags.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(bottom = 8.dp)
                            ) {
                                item.displayTags.take(2).forEach { tag ->
                                    FeedTagChip(
                                        tag = tag,
                                        onClick = { onTagClick?.invoke(tag) },
                                        small = true
                                    )
                                }
                            }
                        }
                    }
                } else if (showLoading) {
                    // Placeholder frame (缺省框体)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.05f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            Text(
                                text = "✨",
                                style = MaterialTheme.typography.labelSmall
                            )
                            Text(
                                text = "AI 正在分析文章...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(16.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            LinearWavyProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            }

            // 互动按钮
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 点赞
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = { onLikeClick?.invoke(item.id) },
                        modifier = Modifier.size(14.dp)
                    ) {
                        Icon(
                            imageVector = if (item.isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "点赞",
                            tint = if (item.isLiked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = formatCount(item.likeCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (item.isLiked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 收藏
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = { onCollectClick?.invoke(item.id) },
                        modifier = Modifier.size(14.dp)
                    ) {
                        Icon(
                            imageVector = if (item.isCollected) Icons.Filled.Bookmark else Icons.Outlined.Bookmark,
                            contentDescription = "收藏",
                            tint = if (item.isCollected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = formatCount(item.collectCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (item.isCollected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = { onShareClick?.invoke(item.id) },
                    modifier = Modifier.size(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = "分享",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        // 右侧缩略图 - 使用骨架屏
        SmallSkeletonImage(
            imageUrl = item.imageUrl,
            contentDescription = item.title,
            modifier = Modifier.size(91.dp)
        )
    }
}

private fun formatCount(count: Int): String {
    return when {
        count >= 10000 -> "${(count / 10000f).format(1)}万"
        count >= 1000 -> "${(count / 1000f).format(1)}k"
        else -> count.toString()
    }
}

private fun Float.format(digits: Int) = "%.${digits}f".format(this)

@Preview(showBackground = true, widthDp = 380)
@Composable
private fun SmallImageFeedCardPreview() {
    val sampleItem = FeedItem(
        id = "1",
        title = "2024年度最值得关注的AI技术趋势与前沿应用探索",
        summary = "深度学习领域正在经历快速变革，大语言模型和多模态技术带来了全新的可能性。",
        content = null,
        sourceName = "36氪",
        sourceUrl = null,
        category = null,
        itemType = "article",
        cardType = "small_image",
        imageUrl = null,
        mediaUrl = null,
        tags = listOf("AI", "深度学习"),
        aiSummary = "AI技术在2024年迎来重大突破，多模态模型成为主流趋势。",
        aiTags = listOf("AI", "技术", "深度学习"),
        isLiked = true,
        likeCount = 1280,
        isCollected = false,
        collectCount = 56,
        publishedAt = "2024-01-15"
    )

    MaterialTheme {
        Column {
            SmallImageFeedCard(item = sampleItem)
            SmallImageFeedCard(
                item = sampleItem.copy(
                    id = "2",
                    title = "Kotlin 2.0 发布：全新 K2 编译器性能提升显著",
                    aiSummary = null,
                    aiTags = emptyList(),
                    isLiked = false,
                    likeCount = 42,
                    isCollected = true,
                    collectCount = 99,
                    imageUrl = "https://example.com/image.jpg"
                ),
                isAiEnabled = false
            )
        }
    }
}

