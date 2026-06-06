package com.ico.nekofeed.ui.stats

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ico.nekofeed.data.model.FeedItem
import com.ico.nekofeed.ui.theme.AccentBlue
import com.ico.nekofeed.ui.theme.AccentGreen
import com.ico.nekofeed.ui.theme.AccentOrange
import com.ico.nekofeed.ui.theme.AccentPink
import com.ico.nekofeed.ui.theme.AccentRed
import com.ico.nekofeed.ui.theme.ExpressiveTokens
import com.ico.nekofeed.ui.theme.Primary
import com.ico.nekofeed.ui.theme.Secondary
import com.ico.nekofeed.ui.theme.StatExposureEnd
import com.ico.nekofeed.ui.theme.StatExposureStart
import com.ico.nekofeed.ui.theme.StatClickEnd
import com.ico.nekofeed.ui.theme.StatClickStart
import com.ico.nekofeed.ui.theme.StatLikeEnd
import com.ico.nekofeed.ui.theme.StatLikeStart
import com.ico.nekofeed.ui.theme.StatFavoriteEnd
import com.ico.nekofeed.ui.theme.StatFavoriteStart
import com.ico.nekofeed.ui.theme.StatShareEnd
import com.ico.nekofeed.ui.theme.StatShareStart
import com.ico.nekofeed.ui.theme.StatCtrEnd
import com.ico.nekofeed.ui.theme.StatCtrStart
import androidx.compose.ui.tooling.preview.Preview

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    stats: StatsData,
    onItemClick: (String) -> Unit
) {
    var selectedSortIndex by remember { mutableIntStateOf(0) }
    val sortOptions = listOf("按曝光", "按点赞", "按收藏")
    val sortedItems = remember(stats.topItems, selectedSortIndex) {
        when (selectedSortIndex) {
            1 -> stats.topItems.sortedByDescending { it.likeCount }
            2 -> stats.topItems.sortedByDescending { it.collectCount }
            else -> stats.topItems.sortedByDescending { it.exposureCount }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        CenterAlignedTopAppBar(
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.BarChart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "数据统计",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
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
                containerColor = Color.Transparent
            )
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 统计概览卡片
            item {
                StatOverviewCards(stats = stats)
            }

            // 广告排行榜标题
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "内容排行榜",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 排序选项
            item {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    sortOptions.forEachIndexed { index, label ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = sortOptions.size),
                            onClick = { selectedSortIndex = index },
                            selected = selectedSortIndex == index,
                            label = { Text(label) }
                        )
                    }
                }
            }

            // 排行榜列表
            itemsIndexed(
                items = sortedItems,
                key = { _, item -> item.id }
            ) { index, item ->
                RankingItem(
                    rank = index + 1,
                    item = item,
                    maxExposure = sortedItems.firstOrNull()?.exposureCount ?: 1,
                    onClick = { onItemClick(item.id) },
                    modifier = Modifier.animateItem()
                )
            }
        }
    }
}

@Composable
private fun StatOverviewCards(stats: StatsData) {
    val statCards = listOf(
        StatCardData("总曝光", stats.totalExposure, Icons.Filled.Visibility, StatExposureStart, StatExposureEnd),
        StatCardData("总点击", stats.totalClick, Icons.Filled.Visibility, StatClickStart, StatClickEnd),
        StatCardData("总点赞", stats.totalLike, Icons.Filled.Favorite, StatLikeStart, StatLikeEnd),
        StatCardData("总收藏", stats.totalCollect, Icons.Filled.Bookmark, StatFavoriteStart, StatFavoriteEnd),
        StatCardData("总分享", stats.totalShare, Icons.Filled.Share, StatShareStart, StatShareEnd),
        StatCardData("CTR", (stats.ctr * 100).toInt(), Icons.AutoMirrored.Filled.TrendingUp, StatCtrStart, StatCtrEnd),
        StatCardData("总播放", stats.totalPlay, Icons.Filled.PlayArrow, StatClickStart, StatClickEnd)
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 第一行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                data = statCards[0],
                modifier = Modifier.weight(1f)
            )
            StatCard(
                data = statCards[1],
                modifier = Modifier.weight(1f)
            )
        }
        StatCard(
            data = statCards[6],
            modifier = Modifier.fillMaxWidth()
        )
        // 第二行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                data = statCards[2],
                modifier = Modifier.weight(1f)
            )
            StatCard(
                data = statCards[3],
                modifier = Modifier.weight(1f)
            )
        }
        // 第三行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                data = statCards[4],
                modifier = Modifier.weight(1f)
            )
            StatCard(
                data = statCards[5],
                modifier = Modifier.weight(1f),
                suffix = "%"
            )
        }
    }
}

@Composable
private fun StatCard(
    data: StatCardData,
    modifier: Modifier = Modifier,
    suffix: String = ""
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(data.startColor, data.endColor)
                    )
                )
                .padding(20.dp)
        ) {
            // 装饰圆
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .align(Alignment.TopEnd)
                    .background(
                        Color.White.copy(alpha = 0.12f),
                        RoundedCornerShape(30.dp)
                    )
            )

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column {
                        Text(
                            text = data.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f),
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            text = "${formatCount(data.value)}$suffix",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Icon(
                        imageVector = data.icon,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RankingItem(
    rank: Int,
    item: FeedItem,
    maxExposure: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(item.exposureCount, maxExposure) {
        progress = if (maxExposure > 0) {
            (item.exposureCount.toFloat() / maxExposure).coerceIn(0f, 1f)
        } else {
            0f
        }
    }
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 600),
        label = "progress"
    )

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp, 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 排名
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (rank <= 3) Brush.linearGradient(
                            colors = listOf(Primary, StatExposureEnd)
                        ) else Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = rank.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (rank <= 3) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
            }

            // 缩略图
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (item.imageUrl != null) {
                    coil.compose.AsyncImage(
                        model = item.imageUrl,
                        contentDescription = item.title,
                        modifier = Modifier.size(44.dp),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Text(
                        text = "📷",
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }

            // 信息
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    StatMiniItem(icon = Icons.Filled.Visibility, value = item.exposureCount)
                    StatMiniItem(icon = Icons.Filled.Favorite, value = item.likeCount)
                    StatMiniItem(icon = Icons.Filled.Bookmark, value = item.collectCount)
                }

                // 进度条
                LinearWavyProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    wavelength = 20.dp,
                    amplitude = { 1f } // Try a lambda returning float
                )
            }
        }
    }
}

@Composable
private fun StatMiniItem(icon: ImageVector, value: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(11.dp)
        )
        Text(
            text = formatCount(value),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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

data class StatCardData(
    val label: String,
    val value: Int,
    val icon: ImageVector,
    val startColor: Color,
    val endColor: Color
)

data class StatsData(
    val totalExposure: Int,
    val totalClick: Int,
    val totalLike: Int,
    val totalCollect: Int,
    val totalShare: Int,
    val totalPlay: Int,
    val ctr: Float,
    val topItems: List<FeedItem>
)

@Preview(showBackground = true)
@Composable
fun StatsScreenPreview() {
    MaterialTheme {
        StatsScreen(
            onBack = {},
            stats = StatsData(
                totalExposure = 25600,
                totalClick = 3890,
                totalLike = 4520,
                totalCollect = 1870,
                totalShare = 780,
                totalPlay = 8200,
                ctr = 0.152f,
                topItems = com.ico.nekofeed.data.local.FallbackFeedData.items.take(5)
            ),
            onItemClick = {}
        )
    }
}
