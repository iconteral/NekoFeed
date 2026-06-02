package com.ico.nekofeed.ui.feed

import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ico.nekofeed.data.local.FallbackFeedData
import com.ico.nekofeed.data.model.FeedCategory
import com.ico.nekofeed.data.model.FeedItem
import com.ico.nekofeed.ui.feed.components.FeedItemCard
import com.ico.nekofeed.util.FeedUiState

@Composable
fun FeedScreen(
    onItemClick: (String) -> Unit,
    onSearchClick: () -> Unit = {},
    onStatsClick: () -> Unit = {},
    viewModel: FeedViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    FeedScreenContent(
        uiState = uiState,
        onItemClick = onItemClick,
        onSearchClick = onSearchClick,
        onStatsClick = onStatsClick,
        onCategorySelected = viewModel::selectCategory,
        onRefresh = viewModel::refresh,
        onRetry = viewModel::retry,
        onLikeClick = viewModel::toggleLike,
        onCollectClick = viewModel::toggleCollect,
        onShareClick = viewModel::toggleShare,
        onTagClick = viewModel::filterByTag
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreenContent(
    uiState: FeedUiState,
    onItemClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onStatsClick: () -> Unit,
    onCategorySelected: (FeedCategory) -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onLikeClick: (String) -> Unit,
    onCollectClick: (String) -> Unit,
    onShareClick: (String) -> Unit,
    onTagClick: (String) -> Unit
) {
    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
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
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "NekoFeed AI",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "搜索"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Tab 频道栏
            FeedTabRow(
                selectedCategory = uiState.selectedCategory,
                onCategorySelected = onCategorySelected
            )

            // AI 搜索提示条
            AISearchBar(onClick = onSearchClick)

            // 信息流列表
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = onRefresh
            ) {
                when {
                    uiState.isLoading -> {
                        FeedLoadingContent()
                    }
                    uiState.errorMessage != null && uiState.items.isEmpty() -> {
                        ErrorContent(
                            message = uiState.errorMessage,
                            onRetry = onRetry
                        )
                    }
                    else -> {
                        FeedContent(
                            items = uiState.items,
                            usingFallback = uiState.usingFallback,
                            listState = listState,
                            onItemClick = onItemClick,
                            onLikeClick = onLikeClick,
                            onCollectClick = onCollectClick,
                            onShareClick = onShareClick,
                            onTagClick = onTagClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedTabRow(
    selectedCategory: FeedCategory,
    onCategorySelected: (FeedCategory) -> Unit
) {
    val categories = listOf(
        FeedCategory.FEATURED,
        FeedCategory.SHOPPING,
        FeedCategory.LOCAL,
        FeedCategory.VIDEO,
        FeedCategory.TECH,
        FeedCategory.AI
    )

    PrimaryScrollableTabRow(
        selectedTabIndex = categories.indexOf(selectedCategory),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
        edgePadding = 16.dp,
        divider = {}
    ) {
        categories.forEach { category ->
            Tab(
                selected = selectedCategory == category,
                onClick = { onCategorySelected(category) },
                text = {
                    Text(
                        text = category.displayName,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selectedCategory == category) FontWeight.Bold else FontWeight.Medium
                    )
                }
            )
        }
    }
}

@Composable
private fun AISearchBar(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "告诉 AI 你想看什么广告...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun FeedContent(
    items: List<FeedItem>,
    usingFallback: Boolean,
    listState: LazyListState,
    onItemClick: (String) -> Unit,
    onLikeClick: (String) -> Unit,
    onCollectClick: (String) -> Unit,
    onShareClick: (String) -> Unit,
    onTagClick: (String) -> Unit
) {
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 本地数据提示
        if (usingFallback) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Text(
                        text = "当前使用本地演示数据",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Feed 卡片列表
        items(
            items = items,
            key = { it.id }
        ) { item ->
            FeedItemCard(
                item = item,
                onClick = { onItemClick(item.id) },
                onLikeClick = onLikeClick,
                onCollectClick = onCollectClick,
                onShareClick = onShareClick,
                onTagClick = onTagClick
            )
        }

        // 底部间距
        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun FeedLoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "加载中...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "加载失败",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        androidx.compose.material3.Button(onClick = onRetry) {
            Text(text = "重试")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun FeedScreenPreview() {
    MaterialTheme {
        FeedScreenContent(
            uiState = FeedUiState(
                items = FallbackFeedData.items,
                isLoading = false
            ),
            onItemClick = {},
            onSearchClick = {},
            onStatsClick = {},
            onCategorySelected = {},
            onRefresh = {},
            onRetry = {},
            onLikeClick = {},
            onCollectClick = {},
            onShareClick = {},
            onTagClick = {}
        )
    }
}
