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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import com.ico.nekofeed.ui.feed.components.FeedTagChip
import com.ico.nekofeed.util.FeedUiState
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.sp
private val NotoEmojiFont = FontFamily(Font(resId = R.font.noto_emoji_regular))
private val FeedCategories = listOf(
    FeedCategory.FEATURED,
    FeedCategory.SHOPPING,
    FeedCategory.LOCAL,
    FeedCategory.VIDEO,
    FeedCategory.TECH,
    FeedCategory.AI
)

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
    val playingItemId by viewModel.playingItemId.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.refreshOnEnter()
    }

    // 使用 remember 缓存 lambda，避免每次重组创建新实例
    val handleItemClick = remember<(String) -> Unit> {
        { itemId -> onItemClick(itemId) }
    }

    val handleLikeClick = remember<(String) -> Unit> {
        { itemId -> viewModel.toggleLike(itemId) }
    }

    val handleCollectClick = remember<(String) -> Unit> {
        { itemId -> viewModel.toggleCollect(itemId) }
    }

    FeedScreenContent(
        uiState = uiState,
        playingItemId = playingItemId,
        onItemClick = handleItemClick,
        onSearchClick = onSearchClick,
        onStatsClick = onStatsClick,
        onAiSettingsClick = onAiSettingsClick,
        onCategorySelected = viewModel::selectCategory,
        onRefresh = viewModel::refresh,
        onRetry = viewModel::retry,
        onLoadMore = viewModel::loadMore,
        onLikeClick = handleLikeClick,
        onCollectClick = handleCollectClick,
        onShareClick = viewModel::toggleShare,
        onTagClick = viewModel::filterByTag,
        onClearTags = viewModel::clearTagFilters,
        onAiRequest = viewModel::requestAiAnalysis,
        onExposure = viewModel::recordExposure,
        onPlayingItemChange = viewModel::setPlayingItemId
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FeedScreenContent(
    uiState: FeedUiState,
    playingItemId: String? = null,
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
    onClearTags: () -> Unit,
    onAiRequest: (FeedItem) -> Unit,
    onExposure: (String) -> Unit = {},
    onPlayingItemChange: (String?) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val selectedPage = FeedCategories.indexOf(uiState.selectedCategory).coerceAtLeast(0)
    val pagerState = rememberPagerState(
        initialPage = selectedPage,
        pageCount = { FeedCategories.size }
    )

    LaunchedEffect(uiState.selectedCategory) {
        if (pagerState.settledPage != selectedPage) {
            pagerState.animateScrollToPage(selectedPage)
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                val category = FeedCategories[page]
                if (category != uiState.selectedCategory) {
                    onPlayingItemChange(null)
                    onCategorySelected(category)
                }
            }
    }
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
                            .clip(RoundedCornerShape(8.dp)),
//                            .background(
//                                Brush.linearGradient(
//                                    colors = listOf(
//                                        MaterialTheme.colorScheme.primary,
//                                        MaterialTheme.colorScheme.secondary
//                                    )
//                                )
//                            ),
                        contentAlignment = Alignment.Center
                    ) {
//                        Icon(
//                            imageVector = Icons.Filled.Star,
//                            contentDescription = null,
//                            tint = Color.White,
//                            modifier = Modifier.size(14.dp)
//                        )
                        AsyncImage(
                            model = R.raw.neko_cat,
                            contentDescription = null,
                            modifier = Modifier.size(25.dp)
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
            selectedCategory = FeedCategories[pagerState.currentPage],
            onCategorySelected = { category ->
                coroutineScope.launch {
                    pagerState.animateScrollToPage(FeedCategories.indexOf(category))
                }
            }
        )

        // 3. AI 搜索提示条
//        AISearchBar(onClick = onSearchClick)

        // 4. 信息流列表
        FeedTagFilter(
            availableTags = uiState.availableTags,
            selectedTags = uiState.selectedTags,
            onTagClick = onTagClick,
            onClearTags = onClearTags
        )

        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { page ->
            val pageCategory = FeedCategories[page]
            val listState = rememberLazyListState()
            val pullToRefreshState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing && pageCategory == uiState.selectedCategory,
                onRefresh = onRefresh,
                state = pullToRefreshState,
                indicator = {
                    PullToRefreshDefaults.LoadingIndicator(
                        state = pullToRefreshState,
                        isRefreshing = uiState.isRefreshing,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                },
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    pageCategory != uiState.selectedCategory || uiState.isLoading -> {
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
                            playingItemId = playingItemId,
                            onItemClick = onItemClick,
                            onLoadMore = onLoadMore,
                            onLikeClick = onLikeClick,
                            onCollectClick = onCollectClick,
                            onShareClick = onShareClick,
                            onTagClick = onTagClick,
                            onAiRequest = onAiRequest,
                            onExposure = onExposure,
                            onPlayingItemChange = onPlayingItemChange
                        )
                    }
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
private fun FeedTagFilter(
    availableTags: List<String>,
    selectedTags: List<String>,
    onTagClick: (String) -> Unit,
    onClearTags: () -> Unit
) {
    if (availableTags.isEmpty()) return

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        item(key = "filter-label") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.FilterList,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "筛选",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        items(availableTags, key = { "tag-$it" }) { tag ->
            FeedTagChip(
                tag = tag,
                onClick = { onTagClick(tag) },
                selected = tag in selectedTags
            )
        }

        if (selectedTags.isNotEmpty()) {
            item(key = "clear-tags") {
                FilterChip(
                    selected = false,
                    onClick = onClearTags,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text("清除") }
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
    PrimaryScrollableTabRow(
        selectedTabIndex = FeedCategories.indexOf(selectedCategory),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
        edgePadding = 16.dp,
        divider = {}
    ) {
        FeedCategories.forEach { category ->
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
//
//@Composable
//private fun AISearchBar(onClick: () -> Unit) {
//    Card(
//        onClick = onClick,
//        modifier = Modifier
//            .fillMaxWidth()
//            .padding(horizontal = 16.dp, vertical = 8.dp),
//        shape = RoundedCornerShape(24.dp),
//        colors = CardDefaults.cardColors(
//            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)
//        ),
//        elevation = CardDefaults.cardElevation(
//            defaultElevation = 0.dp
//        )
//    ) {
//        Row(
//            modifier = Modifier
//                .fillMaxWidth()
//                .padding(horizontal = 16.dp, vertical = 12.dp),
//            verticalAlignment = Alignment.CenterVertically,
//            horizontalArrangement = Arrangement.spacedBy(8.dp)
//        ) {
//            Icon(
//                imageVector = Icons.Filled.Star,
//                contentDescription = null,
//                tint = MaterialTheme.colorScheme.primary,
//                modifier = Modifier.size(16.dp)
//            )
//            Text(
//                text = "告诉 AI 你想看什么内容...",
//                style = MaterialTheme.typography.bodyMedium,
//                color = MaterialTheme.colorScheme.primary
//            )
//        }
//    }
//}

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

    // Lifecycle observer to pause video when app goes to background
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val currentOnPlayingItemChange by rememberUpdatedState(onPlayingItemChange)
    var isLifecycleResumed by remember(lifecycleOwner) {
        mutableStateOf(
            lifecycleOwner.lifecycle.currentState.isAtLeast(
                androidx.lifecycle.Lifecycle.State.RESUMED
            )
        )
    }
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    isLifecycleResumed = true
                }
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                    isLifecycleResumed = false
                    currentOnPlayingItemChange(null)
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val itemMap = remember(items) { items.associateBy { it.id } }
    val currentItemMap by rememberUpdatedState(itemMap)
    val currentOnExposure by rememberUpdatedState(onExposure)
    val currentOnAiRequest by rememberUpdatedState(onAiRequest)

    // Expensive viewport work runs only after scrolling has fully stopped. This
    // avoids creating PlayerView, starting playback, and scheduling AI work while
    // LazyColumn is trying to meet frame deadlines.
    LaunchedEffect(listState, isAiEnabled, isLifecycleResumed) {
        if (!isLifecycleResumed) return@LaunchedEffect

        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collectLatest { isScrolling ->
                if (isScrolling) {
                    currentOnPlayingItemChange(null)
                    return@collectLatest
                }

                delay(350L)

                val layoutInfo = listState.layoutInfo
                val visibleItems = layoutInfo.visibleItemsInfo
                visibleItems
                    .mapNotNull { it.key as? String }
                    .forEach(currentOnExposure)

                val viewportCenter =
                    (layoutInfo.viewportEndOffset + layoutInfo.viewportStartOffset) / 2
                val focusedId = visibleItems
                    .minByOrNull { itemInfo ->
                        kotlin.math.abs(itemInfo.offset + itemInfo.size / 2 - viewportCenter)
                    }
                    ?.key as? String

                val playingVideoId = visibleItems
                    .filter { itemInfo ->
                        val itemId = itemInfo.key as? String
                        val item = itemId?.let(currentItemMap::get)
                        item?.isVideo == true && !item.mediaUrl.isNullOrBlank()
                    }
                    .minByOrNull { itemInfo ->
                        kotlin.math.abs(itemInfo.offset + itemInfo.size / 2 - viewportCenter)
                    }
                    ?.key as? String

                currentOnPlayingItemChange(playingVideoId)
                if (isAiEnabled && focusedId != null) {
                    currentItemMap[focusedId]?.let(currentOnAiRequest)
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Preview(showBackground = true)
@Composable
fun FeedScreenPreview() {
    MaterialTheme {
        FeedScreenContent(
            uiState = FeedUiState(
                items = FallbackFeedData.items,
                isLoading = false
            ),
            playingItemId = null,
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
            onClearTags = {},
            onAiRequest = {},
            onExposure = {},
            onPlayingItemChange = {}
        )
    }
}
