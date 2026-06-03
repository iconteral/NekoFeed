package com.ico.nekofeed.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.Crossfade
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import coil.compose.AsyncImage
import com.ico.nekofeed.data.model.FeedItem
import com.ico.nekofeed.ui.feed.components.FeedTagChip

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FeedDetailScreen(
    item: FeedItem?,
    onBack: () -> Unit,
    onLikeClick: ((String) -> Unit)? = null,
    onCollectClick: ((String) -> Unit)? = null,
    onShareClick: ((String) -> Unit)? = null,
    isAiEnabled: Boolean = true,
    onAiRequest: ((FeedItem) -> Unit)? = null
) {
    LaunchedEffect(item?.id, isAiEnabled) {
        if (item != null && isAiEnabled) {
            onAiRequest?.invoke(item)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = {
                Text(
                    text = item?.sourceName ?: "详情",
                    style = MaterialTheme.typography.titleMedium
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回"
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (item == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "未找到该 FeedItem，请返回首页重新加载",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    HeroMediaSection(item)
                    ContentSection(item, isAiEnabled)
                    // 不需要在这里加巨大的 Spacer 了，因为 BottomBar 不再是 Scaffold 浮动的
                }
            }
        }

        if (item != null) {
            DetailBottomBar(
                item = item,
                onLikeClick = onLikeClick,
                onCollectClick = onCollectClick,
                onShareClick = onShareClick
            )
        }
    }
}

@Composable
private fun HeroMediaSection(item: FeedItem) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (item.imageUrl != null) {
            AsyncImage(
                model = item.imageUrl,
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            // 底部渐变遮罩
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    )
            )
        } else {
            Text(
                text = if (item.isVideo) "🎬" else "📷",
                style = MaterialTheme.typography.displayLarge
            )
        }

        // 品牌标签
        if (item.brand != null) {
            Box(
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.TopStart)
                    .background(
                        Color.Black.copy(alpha = 0.45f),
                        RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = item.brandDisplay,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ContentSection(item: FeedItem, isAiEnabled: Boolean) {
    Column(
        modifier = Modifier.padding(20.dp)
    ) {
        // 品牌和赞助信息
        if (item.brand != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = item.brandDisplay,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // 标题
        Text(
            text = item.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // 统计信息条
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatItem(label = "曝光", value = item.exposureCount)
            StatItem(label = "点击", value = item.clickCount)
            StatItem(label = "点赞", value = item.likeCount)
            StatItem(label = "收藏", value = item.collectCount)
        }

        // AI 智能总结模块
        Crossfade(
            targetState = item.aiSummary to item.isAiLoading,
            label = "ai_detail_summary_transition"
        ) { (summary, isLoading) ->
            if (!summary.isNullOrBlank()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)
                    ),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 0.dp
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.primary,
                                                MaterialTheme.colorScheme.secondary
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "AI 智能总结",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                        )

                        if (item.displayTags.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                item.displayTags.forEach { tag ->
                                    FeedTagChip(
                                        tag = tag,
                                        onClick = { }
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (isAiEnabled && (isLoading || item.aiSummary == null)) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.05f)
                    ),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 0.dp
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.primary,
                                                MaterialTheme.colorScheme.secondary
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "AI 智能总结生成中...",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        Box(
                            modifier = Modifier.fillMaxWidth().height(24.dp),
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
        }

        // 产品介绍
        if (item.content != null || item.summary != null) {
            Text(
                text = "产品介绍",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Text(
                text = item.content ?: item.summary ?: "",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
            )
        }

        // 来源信息
        if (item.sourceName != null || item.sourceUrl != null) {
            Spacer(modifier = Modifier.height(20.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    if (item.sourceName != null) {
                        Text(
                            text = "来源: ${item.sourceName}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (item.sourceUrl != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = item.sourceUrl,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // 发布时间
        if (item.publishedAt != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "发布时间: ${item.publishedAt}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatItem(label: String, value: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = formatCount(value),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun DetailBottomBar(
    item: FeedItem,
    onLikeClick: ((String) -> Unit)? = null,
    onCollectClick: ((String) -> Unit)? = null,
    onShareClick: ((String) -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 8.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 互动按钮
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // 点赞
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IconButton(
                        onClick = { onLikeClick?.invoke(item.id) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (item.isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "点赞",
                            tint = if (item.isLiked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Text(
                        text = if (item.isLiked) "已赞" else "点赞",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (item.isLiked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 收藏
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IconButton(
                        onClick = { onCollectClick?.invoke(item.id) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (item.isCollected) Icons.Filled.Bookmark else Icons.Outlined.Bookmark,
                            contentDescription = "收藏",
                            tint = if (item.isCollected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Text(
                        text = if (item.isCollected) "已收藏" else "收藏",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (item.isCollected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 分享
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IconButton(
                        onClick = { onShareClick?.invoke(item.id) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = "分享",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Text(
                        text = "分享",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // CTA 按钮
            Button(
                onClick = { /* 处理CTA点击 */ },
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = item.ctaText ?: "查看详情",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
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

@Preview(showBackground = true)
@Composable
fun FeedDetailScreenPreview() {
    MaterialTheme {
        FeedDetailScreen(
            item = com.ico.nekofeed.data.local.FallbackFeedData.items.first(),
            onBack = {},
            onLikeClick = {},
            onCollectClick = {},
            onShareClick = {}
        )
    }
}
