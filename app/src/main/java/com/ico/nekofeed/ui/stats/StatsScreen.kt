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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    stats: StatsData,
    selectedRange: StatsRange,
    onRangeSelected: (StatsRange) -> Unit,
    onItemClick: (String) -> Unit
) {
    var rankingMetric by remember { mutableStateOf(RankingMetric.EXPOSURE) }
    val rankedItems = remember(stats.items, rankingMetric) {
        stats.items
            .sortedByDescending { it.valueFor(rankingMetric) }
            .filter { it.valueFor(rankingMetric) > 0 }
            .take(10)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        CenterAlignedTopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.BarChart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("行为分析", fontWeight = FontWeight.Bold)
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                RangeSelector(
                    selectedRange = selectedRange,
                    onRangeSelected = onRangeSelected
                )
            }
            item {
                Text(
                    text = "仅统计本机在所选时间内产生的有效行为",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item { OverviewGrid(stats) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.AutoMirrored.Filled.TrendingUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("内容排行榜", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
            item {
                RankingSelector(
                    selectedMetric = rankingMetric,
                    onMetricSelected = { rankingMetric = it }
                )
            }
            if (rankedItems.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "该时间范围内暂无${rankingMetric.label}数据",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                itemsIndexed(rankedItems, key = { _, item -> item.itemId }) { index, item ->
                    RankingItem(
                        rank = index + 1,
                        item = item,
                        metric = rankingMetric,
                        maxValue = rankedItems.first().valueFor(rankingMetric),
                        onClick = { onItemClick(item.itemId) },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }
}

@Composable
private fun RangeSelector(
    selectedRange: StatsRange,
    onRangeSelected: (StatsRange) -> Unit
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        StatsRange.entries.forEachIndexed { index, range ->
            SegmentedButton(
                selected = selectedRange == range,
                onClick = { onRangeSelected(range) },
                shape = SegmentedButtonDefaults.itemShape(index, StatsRange.entries.size),
                label = { Text(range.label) }
            )
        }
    }
}

@Composable
private fun RankingSelector(
    selectedMetric: RankingMetric,
    onMetricSelected: (RankingMetric) -> Unit
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        RankingMetric.entries.forEachIndexed { index, metric ->
            SegmentedButton(
                selected = selectedMetric == metric,
                onClick = { onMetricSelected(metric) },
                shape = SegmentedButtonDefaults.itemShape(index, RankingMetric.entries.size),
                label = { Text(metric.label) }
            )
        }
    }
}

@Composable
private fun OverviewGrid(stats: StatsData) {
    val cards = listOf(
        MetricCardData("有效曝光", stats.totalExposure.toString(), Icons.Filled.Visibility),
        MetricCardData("详情点击", stats.totalClick.toString(), Icons.Filled.Mouse),
        MetricCardData("真实播放", stats.totalPlay.toString(), Icons.Filled.PlayArrow),
        MetricCardData("CTR", formatPercent(stats.ctr), Icons.AutoMirrored.Filled.TrendingUp),
        MetricCardData("点赞操作", stats.totalLike.toString(), Icons.Filled.Favorite),
        MetricCardData("收藏操作", stats.totalCollect.toString(), Icons.Filled.Bookmark),
        MetricCardData("分享操作", stats.totalShare.toString(), Icons.Filled.Share),
        MetricCardData("触达内容", stats.uniqueContent.toString(), Icons.Filled.BarChart)
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        cards.chunked(2).forEach { rowCards ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowCards.forEach { card ->
                    MetricCard(card, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MetricCard(data: MetricCardData, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                data.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    data.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    data.value,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun RankingItem(
    rank: Int,
    item: StatsRankingItem,
    metric: RankingMetric,
    maxValue: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress by animateFloatAsState(
        targetValue = if (maxValue == 0) 0f else item.valueFor(metric).toFloat() / maxValue,
        animationSpec = tween(450),
        label = "ranking_progress"
    )
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(rank.toString(), fontWeight = FontWeight.Bold)
            }
            coil.compose.AsyncImage(
                model = item.imageUrl,
                contentDescription = item.title,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "${metric.label} ${item.valueFor(metric)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                )
            }
        }
    }
}

private fun formatPercent(value: Double): String =
    String.format(Locale.getDefault(), "%.1f%%", value * 100)

private data class MetricCardData(
    val label: String,
    val value: String,
    val icon: ImageVector
)
