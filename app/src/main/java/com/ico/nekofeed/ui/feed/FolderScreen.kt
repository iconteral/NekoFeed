package com.ico.nekofeed.ui.feed


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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ico.nekofeed.R
import com.ico.nekofeed.data.local.FallbackFeedData
import com.ico.nekofeed.data.model.FeedCategory
import com.ico.nekofeed.data.model.FeedItem
import com.ico.nekofeed.ui.feed.components.FeedItemCard
import com.ico.nekofeed.util.FeedUiState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.debounce

private val NotoEmojiFont = FontFamily(Font(resId = R.font.noto_emoji_regular))

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FeedScreen(
    onItemClick: (String) -> Unit,
    onSearchClick: () -> Unit = {},
    onStatsClick: () -> Unit = {},
    onAiSettingsClick: () -> Unit = {},
    isLoggedIn: Boolean = true,
    onLogin: () -> Unit = {},
    viewModel: FeedViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    FeedScreenContent(
        uiState = uiState,
        onItemClick = { itemId ->
            viewModel.recordClick(itemId)
            onItemClick(itemId)
        },
        onSearchClick = onSearchClick,
        onStatsClick = onStatsClick,
        onAiSettingsClick = onAiSettingsClick,
        onCategorySelected = viewModel::selectCategory,
        onRefresh = viewModel::refresh,
        onRetry = viewModel::retry,
        onLoadMore = viewModel::loadMore,
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
        onTagClick = viewModel::filterByTag,
        onAiRequest = viewModel::requestAiAnalysis,
        onExposure = viewModel::recordExposure,
        onPlayingItemChange = viewModel::setPlayingItemId
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FeedScreenContent(
    uiState: FeedUiState,
    onItemClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onStatsClick: () -> Unit,
    onAiSettingsClick: () -> Unit,
    onCategorySelected: (FeedCategory) -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onLikeClick: (String) -> Unit,
    onCollectClick: (String) -> Unit,
    onShareClick: (String) -> Unit,
    onTagClick: (String) -> Unit,
    onAiRequest: (FeedItem) -> Unit,
    onExposure: (String) -> Unit = {},
    onPlayingItemChange: (String?) -> Unit
) {
    val listState = rememberLazyListState()
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. 顶部标题栏
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

        // 2. Tab 频道栏
        FeedTabRow(
            selectedCategory = uiState.selectedCategory,
            onCategorySelected = onCategorySelected
        )

        // 3. AI 搜索提示条
        AISearchBar(onClick = onSearchClick)

        // 4. 信息流列表
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.weight(1f).fillMaxWidth()
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
                        isLoadingMore = uiState.isLoadingMore,
                        hasMore = uiState.hasMore,
                        listState = listState,
                        isAiEnabled = uiState.isAiEnabled,
                        onItemClick = onItemClick,
                        onLoadMore = onLoadMore,
                        onLikeClick = onLikeClick,
                        onCollectClick = onCollectClick,
                        onShareClick = onShareClick,
                        onTagClick = onTagClick,
                        onAiRequest = onAiRequest,
                        onExposure = onExposure,
                        onPlayingItemChange = onPlayingItemChange,
                        playingItemId = uiState.playingItemId
                    )
                }
            }
        }
        }

        // FAB
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            FloatingActionButton(
                onClick = { onSearchClick() },
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = "✨",
                    fontSize = MaterialTheme.typography.titleLarge.fontSize,
                    fontFamily = NotoEmojiFont,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,

                )
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
                text = "告诉 AI 你想看什么内容...",
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
    isLoadingMore: Boolean,
    hasMore: Boolean,
    listState: LazyListState,
    isAiEnabled: Boolean,
    onItemClick: (String) -> Unit,
    onLoadMore: () -> Unit,
    onLikeClick: (String) -> Unit,
    onCollectClick: (String) -> Unit,
    onShareClick: (String) -> Unit,
    onTagClick: (String) -> Unit,
    onAiRequest: (FeedItem) -> Unit,
    onExposure: (String) -> Unit,
    onPlayingItemChange: (String?) -> Unit,
    playingItemId: String?
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // Infinite scroll detection: trigger loadMore when scrolled near the bottom
    LaunchedEffect(listState) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val totalItemsCount = layoutInfo.totalItemsCount
            val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleItemIndex to totalItemsCount
        }
            .distinctUntilChanged()
            .filter { (lastVisible, totalCount) ->
                totalCount > 0 && lastVisible >= totalCount - 3
            }
            .collect {
                onLoadMore()
            }
    }

    // Auto-play detection: find the item closest to center, with debounce
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    LaunchedEffect(listState) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val center = (layoutInfo.viewportEndOffset + layoutInfo.viewportStartOffset) / 2
            
            var closestItem: androidx.compose.foundation.lazy.LazyListItemInfo? = null
            var minDistance = Int.MAX_VALUE
            
            for (itemInfo in layoutInfo.visibleItemsInfo) {
                val itemCenter = itemInfo.offset + itemInfo.size / 2
                val distance = kotlin.math.abs(itemCenter - center)
                if (distance < minDistance) {
                    minDistance = distance
                    closestItem = itemInfo
                }
            }
            closestItem?.key as? String
        }
        .distinctUntilChanged()
        .debounce(500L)
        .collect { itemId ->
            onPlayingItemChange(itemId)
        }
    }

    // Lifecycle observer to pause video when app goes to background
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                onPlayingItemChange(null)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        kotlinx.coroutines.suspendCancellableCoroutine<Unit> { continuation ->
            continuation.invokeOnCancellation {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }
    }

    // Exposure tracking: detect visible items for exposure count
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? String }
        }
            .distinctUntilChanged()
            .debounce(300L)
            .collect { visibleIds ->
                visibleIds.forEach { id ->
                    onExposure(id)
                }
            }
    }

    // AI request: only for the focused item (closest to center), with debounce
    val itemMap = remember(items) { items.associateBy { it.id } }
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    LaunchedEffect(listState, isAiEnabled) {
        if (!isAiEnabled) return@LaunchedEffect
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val center = (layoutInfo.viewportEndOffset + layoutInfo.viewportStartOffset) / 2
            var closestItem: androidx.compose.foundation.lazy.LazyListItemInfo? = null
            var minDistance = Int.MAX_VALUE
            for (itemInfo in layoutInfo.visibleItemsInfo) {
                val itemCenter = itemInfo.offset + itemInfo.size / 2
                val distance = kotlin.math.abs(itemCenter - center)
                if (distance < minDistance) {
                    minDistance = distance
                    closestItem = itemInfo
                }
            }
            closestItem?.key as? String
        }
            .distinctUntilChanged()
            .debounce(800L)
            .collect { focusedId ->
                focusedId?.let { id ->
                    itemMap[id]?.let { item ->
                        onAiRequest(item)
                    }
                }
            }
    }

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
            key = { it.id },
            contentType = { it.cardType ?: "large_image" }
        ) { item ->
            FeedItemCard(
                item = item,
                onClick = { onItemClick(item.id) },
                onLikeClick = onLikeClick,
                onCollectClick = onCollectClick,
                onShareClick = {
                    onShareClick(it)
                    com.ico.nekofeed.util.IntentUtils.shareContent(
                        context = context,
                        title = item.title,
                        content = item.summary ?: "",
                        url = item.sourceUrl
                    )
                },
                onTagClick = onTagClick,
                isAiEnabled = isAiEnabled,
                isPlaying = item.id == playingItemId,
                onMuteToggle = { /* handled internally or by global state if needed */ }
            )
        }

        // Bottom loading indicator
        if (isLoadingMore) {
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

        // End of feed indicator
        if (!hasMore && items.isNotEmpty() && !usingFallback) {
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
            LoadingIndicator(
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
            onAiSettingsClick = {},
            onCategorySelected = {},
            onRefresh = {},
            onRetry = {},
            onLoadMore = {},
            onLikeClick = {},
            onCollectClick = {},
            onShareClick = {},
            onTagClick = {},
            onAiRequest = {},
            onExposure = {},
            onPlayingItemChange = {}
        )
    }
}
