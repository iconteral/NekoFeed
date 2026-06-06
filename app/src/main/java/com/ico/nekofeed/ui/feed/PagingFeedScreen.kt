package com.ico.nekofeed.ui.feed

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.ico.nekofeed.R
import com.ico.nekofeed.data.local.FallbackFeedData
import com.ico.nekofeed.data.model.FeedCategory
import com.ico.nekofeed.data.model.FeedItem
import com.ico.nekofeed.ui.feed.components.FeedItemCard

private val NotoEmojiFont = FontFamily(Font(resId = R.font.noto_emoji_regular))

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PagingFeedScreen(
    onItemClick: (String) -> Unit,
    onSearchClick: () -> Unit = {},
    onStatsClick: () -> Unit = {},
    onAiSettingsClick: () -> Unit = {},
    isLoggedIn: Boolean = true,
    onLogin: () -> Unit = {},
    viewModel: PagingFeedViewModel = viewModel()
) {
    val pagingItems = viewModel.pagingDataFlow.collectAsLazyPagingItems()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val playingItemId by viewModel.playingItemId.collectAsState()
    val isAiEnabled by viewModel.isAiEnabled.collectAsState()

    PagingFeedScreenContent(
        pagingItems = pagingItems,
        selectedCategory = selectedCategory,
        playingItemId = playingItemId,
        isAiEnabled = isAiEnabled,
        onItemClick = { itemId ->
            viewModel.recordClick(itemId)
            onItemClick(itemId)
        },
        onSearchClick = onSearchClick,
        onStatsClick = onStatsClick,
        onAiSettingsClick = onAiSettingsClick,
        onCategorySelected = viewModel::selectCategory,
        onLikeClick = { itemId ->
            if (isLoggedIn) {
                viewModel.toggleLike(itemId)
            } else {
                onLogin()
            }
        },
        onCollectClick = { itemId ->
            if (isLoggedIn) {
                viewModel.toggleCollect(itemId)
            } else {
                onLogin()
            }
        },
        onShareClick = viewModel::toggleShare,
        onTagClick = { /* TODO: 标签筛选 */ },
        onAiRequest = viewModel::requestAiAnalysis,
        onExposure = viewModel::recordExposure,
        onPlayingItemChange = viewModel::setPlayingItemId
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PagingFeedScreenContent(
    pagingItems: LazyPagingItems<FeedItem>,
    selectedCategory: FeedCategory,
    playingItemId: String?,
    isAiEnabled: Boolean,
    onItemClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onStatsClick: () -> Unit,
    onAiSettingsClick: () -> Unit,
    onCategorySelected: (FeedCategory) -> Unit,
    onLikeClick: (String) -> Unit,
    onCollectClick: (String) -> Unit,
    onShareClick: (String) -> Unit,
    onTagClick: (String) -> Unit,
    onAiRequest: (FeedItem) -> Unit,
    onExposure: (String) -> Unit,
    onPlayingItemChange: (String?) -> Unit
) {
    val listState = rememberLazyListState()

    Column(modifier = Modifier.fillMaxSize()) {
        // TopAppBar
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "\uD83D\uDC31",
                        fontSize = 25.sp
                    )
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
                    Icon(Icons.Filled.Search, contentDescription = "搜索")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        // Tab 栏
        PagingFeedTabRow(
            selectedCategory = selectedCategory,
            onCategorySelected = onCategorySelected
        )

        // AI 搜索提示条
        PagingAISearchBar(onClick = onSearchClick)

        // 列表内容
        when {
            pagingItems.loadState.refresh is LoadState.Loading && pagingItems.itemCount == 0 -> {
                // 首次加载
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        LoadingIndicator(color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "加载中...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            pagingItems.loadState.refresh is LoadState.Error && pagingItems.itemCount == 0 -> {
                // 加载错误
                val error = (pagingItems.loadState.refresh as LoadState.Error).error
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "加载失败",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = error.message ?: "未知错误",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        androidx.compose.material3.Button(onClick = { pagingItems.retry() }) {
                            Text("重试")
                        }
                    }
                }
            }
            else -> {
                // 正常列表
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(
                        count = pagingItems.itemCount,
                        key = pagingItems.itemKey { it.id },
                        contentType = pagingItems.itemContentType { it.cardType ?: "large_image" }
                    ) { index ->
                        val item = pagingItems[index]
                        if (item != null) {
                            FeedItemCard(
                                item = item,
                                onClick = { onItemClick(item.id) },
                                onLikeClick = onLikeClick,
                                onCollectClick = onCollectClick,
                                onShareClick = { id ->
                                    onShareClick(id)
                                },
                                onTagClick = onTagClick,
                                isAiEnabled = isAiEnabled,
                                isPlaying = item.id == playingItemId,
                                onMuteToggle = { }
                            )
                        }
                    }

                    // 加载更多指示器
                    when (val appendState = pagingItems.loadState.append) {
                        is LoadState.Loading -> {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        is LoadState.Error -> {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "加载失败",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                        androidx.compose.material3.TextButton(
                                            onClick = { pagingItems.retry() }
                                        ) {
                                            Text("重试")
                                        }
                                    }
                                }
                            }
                        }
                        is LoadState.NotLoading -> {
                            if (appendState.endOfPaginationReached) {
                                item {
                                    Text(
                                        text = "— 已经到底啦 —",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PagingFeedTabRow(
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
private fun PagingAISearchBar(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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
                text = "告诉 AI 你想看什么内容...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
