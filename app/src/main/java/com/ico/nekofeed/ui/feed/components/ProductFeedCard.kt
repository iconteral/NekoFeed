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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ico.nekofeed.data.model.FeedItem
import androidx.compose.animation.Crossfade
import androidx.compose.material3.LinearWavyProgressIndicator

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProductFeedCard(
    item: FeedItem,
    onLikeClick: ((String) -> Unit)? = null,
    onCollectClick: ((String) -> Unit)? = null,
    onShareClick: ((String) -> Unit)? = null,
    onTagClick: ((String) -> Unit)? = null,
    isAiEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // 商品图片区域
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            if (item.imageUrl != null) {
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = item.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentScale = ContentScale.Crop
                )
                // 渐变遮罩
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.3f)
                                )
                            )
                        )
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🛍️",
                        style = MaterialTheme.typography.displayMedium
                    )
                }
            }

            // 品牌标签
            if (item.brand != null) {
                Box(
                    modifier = Modifier
                        .padding(12.dp)
                        .align(Alignment.TopStart)
                        .background(
                            Color.Black.copy(alpha = 0.45f),
                            RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${item.brand} · 赞助",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // 内容区域
        Column(
            modifier = Modifier.padding(14.dp, 16.dp)
        ) {
            // 标题
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // 价格信息
            if (item.priceText != null) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.padding(bottom = 10.dp)
                ) {
                    Text(
                        text = item.priceText,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Crossfade(
                targetState = item.aiSummary to item.isAiLoading,
                label = "ai_analysis_transition"
            ) { (summary, isLoading) ->
                if (!summary.isNullOrBlank()) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f),
                                    RoundedCornerShape(10.dp)
                                )
                                .padding(10.dp, 12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "✨",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = MaterialTheme.typography.bodySmall.lineHeight
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))

                        if (item.displayTags.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(bottom = 14.dp)
                            ) {
                                item.displayTags.forEach { tag ->
                                    FeedTagChip(
                                        tag = tag,
                                        onClick = { onTagClick?.invoke(tag) }
                                    )
                                }
                            }
                        }
                    }
                } else if (isAiEnabled && (isLoading || item.aiSummary == null)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.05f),
                                RoundedCornerShape(10.dp)
                            )
                            .padding(10.dp, 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Text(
                                text = "✨",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "AI 正在分析商品卖点与特征...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Box(
                            modifier = Modifier.fillMaxWidth().height(16.dp),
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
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            // CTA 按钮和互动按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    // 点赞
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        IconButton(
                            onClick = { onLikeClick?.invoke(item.id) },
                            modifier = Modifier.size(18.dp)
                        ) {
                            Icon(
                                imageVector = if (item.isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = "点赞",
                                tint = if (item.isLiked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = formatCount(item.likeCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (item.isLiked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // 收藏
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        IconButton(
                            onClick = { onCollectClick?.invoke(item.id) },
                            modifier = Modifier.size(18.dp)
                        ) {
                            Icon(
                                imageVector = if (item.isCollected) Icons.Filled.Bookmark else Icons.Outlined.Bookmark,
                                contentDescription = "收藏",
                                tint = if (item.isCollected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = formatCount(item.collectCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (item.isCollected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 分享
                IconButton(
                    onClick = { onShareClick?.invoke(item.id) },
                    modifier = Modifier.size(18.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = "分享",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // CTA 按钮
            if (item.ctaText != null) {
                var menuExpanded by remember { mutableStateOf(false) }

                Box(modifier = Modifier.padding(top = 12.dp)) {
                    SplitButtonLayout(
                        leadingButton = {
                            SplitButtonDefaults.LeadingButton(
                                onClick = { /* 处理CTA点击 */ }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ShoppingCart,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(
                                    text = item.ctaText,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        },
                        trailingButton = {
                            SplitButtonDefaults.TrailingButton(
                                onClick = { menuExpanded = true }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "更多选项"
                                )
                            }
                        }
                    )

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("加入购物车") },
                            onClick = { menuExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("联系商家") },
                            onClick = { menuExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("查看相似推荐") },
                            onClick = { menuExpanded = false }
                        )
                    }
                }
            }
        }
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
