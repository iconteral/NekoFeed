package com.ico.nekofeed.ui.feed

// ============================================================================
// 【UI 层 · 首页信息流主屏幕（Compose 核心）】
// ============================================================================
//
// 📌 这是整个项目最大的 Composable 文件，包含首页的所有 UI 逻辑。
//
// 📌 Compose 核心知识点（本文件涉及的）：
//
//    1. @Composable 函数 → 类似 XML 布局，但是用 Kotlin 代码写 UI
//       - 没有 findViewById，没有 XML，纯代码
//       - 数据变化时自动重新调用（重组 Recomposition）
//
//    2. 状态订阅 → val uiState by viewModel.uiState.collectAsState()
//       - by 是属性委托语法
//       - collectAsState() 把 Flow 转成 State，数据变化时触发重组
//
//    3. LaunchedEffect(key) → 在 Composable 生命周期内启动协程
//       - key 变化时重新执行
//       - 适合做一次性操作（加载数据、监听事件）
//
//    4. LazyColumn → 高性能列表（类似 RecyclerView）
//       - items(list, key, contentType) 声明数据源
//       - key: 稳定的 item key，帮助 Compose 正确复用
//       - contentType: 帮助 Compose 选择正确的 Composable
//
//    5. HorizontalPager → 横向翻页（类似 ViewPager）
//       - rememberPagerState 管理页面状态
//       - 配合 TabRow 实现"频道切换"
//
//    6. snapshotFlow → 把 Compose 的 State 转为 Flow
//       - 用于监听 LazyColumn 的滚动状态
//       - distinctUntilChanged() 去重，避免重复触发
//
//    7. DisposableEffect → 可清理的副作用
//       - onDispose {} 里清理资源（如移除监听器）
//       - 生命周期结束时自动调用
//
//    8. remember / rememberUpdatedState → 缓存和更新引用
//       - remember: 跨重组缓存值（不随重组重新创建）
//       - rememberUpdatedState: 在 LaunchedEffect 中引用最新的回调
// ====================================================================

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ico.nekofeed.R
import com.ico.nekofeed.data.local.FallbackFeedData
import com.ico.nekofeed.data.model.FeedCategory
import com.ico.nekofeed.data.model.FeedItem
import com.ico.nekofeed.ui.components.SparklesIcon
import com.ico.nekofeed.ui.feed.components.FeedItemCard
import com.ico.nekofeed.ui.feed.components.FeedTagChip
import com.ico.nekofeed.util.FeedUiState
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
private val FeedCategories = listOf(
    FeedCategory.FEATURED,
    FeedCategory.SHOPPING,
    FeedCategory.LOCAL,
    FeedCategory.VIDEO,
    FeedCategory.TECH
)

private enum class FeedBodyState {
    ERROR,
    CONTENT
}

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
        onPlaybackStarted = viewModel::recordPlaybackStarted,
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
    onPlaybackStarted: (String) -> Unit = {},
    onPlayingItemChange: (String?) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val selectedPage = FeedCategories.indexOf(uiState.selectedCategory).coerceAtLeast(0)
    val currentSelectedCategory by rememberUpdatedState(uiState.selectedCategory)
    val currentOnCategorySelected by rememberUpdatedState(onCategorySelected)
    val currentOnPlayingItemChange by rememberUpdatedState(onPlayingItemChange)
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
                if (category != currentSelectedCategory) {
                    currentOnPlayingItemChange(null)
                    currentOnCategorySelected(category)
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
                            modifier = Modifier
                                .size(35.dp)
                                .padding(bottom = 4.dp)
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
                    Box(
                        modifier = Modifier.size(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "搜索"
                        )
                        SparklesIcon(
                            size = 12.dp,
                            monochrome = true,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(bottom = 6.dp)
                        )
                    }
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
                val isPageLoading =
                    pageCategory != uiState.selectedCategory || uiState.isLoading
                if (isPageLoading) {
                    FeedLoadingContent()
                } else {
                    val bodyState =
                        if (uiState.errorMessage != null && uiState.items.isEmpty()) {
                            FeedBodyState.ERROR
                        } else {
                            FeedBodyState.CONTENT
                        }
                    AnimatedContent(
                        targetState = bodyState,
                        transitionSpec = {
                            (fadeIn() + scaleIn(
                                initialScale = 0.975f,
                                animationSpec = spring(
                                    dampingRatio = 0.84f,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            )) togetherWith
                                (fadeOut() + scaleOut(targetScale = 0.99f))
                        },
                        label = "feed_body_state"
                    ) { state ->
                        when (state) {
                            FeedBodyState.ERROR -> {
                                ErrorContent(
                                    message = uiState.errorMessage.orEmpty(),
                                    onRetry = onRetry
                                )
                            }
                            FeedBodyState.CONTENT -> {
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
                                    onPlaybackStarted = onPlaybackStarted,
                                    onPlayingItemChange = onPlayingItemChange
                                )
                            }
                        }
                    }
                }
            }
        }
        }

        // FAB
//        Box(
//            modifier = Modifier
//                .align(Alignment.BottomEnd)
//                .padding(16.dp)
//        ) {
//            FloatingActionButton(
//                onClick = { onSearchClick() },
//                containerColor = MaterialTheme.colorScheme.primaryContainer
//            ) {
//                SparklesIcon(size = 24.dp)
//            }
//        }
    }
}

@Composable
private fun FeedTagFilter(
    availableTags: List<String>,
    selectedTags: List<String>,
    onTagClick: (String) -> Unit,
    onClearTags: () -> Unit
) {
    val orderedTags =
        selectedTags.filter { it in availableTags } +
            availableTags.filterNot { it in selectedTags }

    AnimatedVisibility(
        visible = availableTags.isNotEmpty(),
        enter = fadeIn() + scaleIn(
            initialScale = 0.94f,
            animationSpec = spring(
                dampingRatio = 0.76f,
                stiffness = Spring.StiffnessMediumLow
            )
        ),
        exit = fadeOut() + scaleOut(targetScale = 0.96f)
    ) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = 0.82f,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
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

            items(orderedTags, key = { "tag-$it" }) { tag ->
                FeedTagChip(
                    tag = tag,
                    onClick = { onTagClick(tag) },
                    selected = tag in selectedTags,
                    modifier = Modifier.animateItem()
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
                        label = { Text("清除") },
                        modifier = Modifier.animateItem()
                    )
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
    onPlaybackStarted: (String) -> Unit,
    onPlayingItemChange: (String?) -> Unit,
    playingItemId: String?
) {
    // ── 无限滚动检测 ─────────────────────────────────────────────
    // snapshotFlow: 把 Compose 的 State 转为 Kotlin Flow
    // 当最后可见 item 接近总数时，触发 loadMore()
    // 这是"无限滚动"（Infinite Scroll）的标准实现方式
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

    // ── 视口计算：曝光埋点 + 视频自动播放 + AI 请求 ──────────────
    // 等滚动完全停止后（delay 1 秒），才执行以下昂贵操作：
    // 1. 曝光：计算可见像素 >= 50% 的 item，记录曝光事件
    // 2. 视频：找到离视口中心最近的视频 item，自动播放
    // 3. AI：找到离视口中心最近的 item，触发 AI 分析
    // 为什么要等滚动停止？因为滚动中做这些操作会影响帧率
    LaunchedEffect(listState, isAiEnabled, isLifecycleResumed) {
        if (!isLifecycleResumed) return@LaunchedEffect

        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collectLatest { isScrolling ->
                if (isScrolling) {
                    currentOnPlayingItemChange(null)
                    return@collectLatest
                }

                delay(1_000L)

                val layoutInfo = listState.layoutInfo
                val visibleItems = layoutInfo.visibleItemsInfo
                visibleItems
                    .filter { itemInfo ->
                        val visibleStart = maxOf(
                            itemInfo.offset,
                            layoutInfo.viewportStartOffset
                        )
                        val visibleEnd = minOf(
                            itemInfo.offset + itemInfo.size,
                            layoutInfo.viewportEndOffset
                        )
                        val visiblePixels = (visibleEnd - visibleStart).coerceAtLeast(0)
                        itemInfo.size > 0 &&
                            visiblePixels.toFloat() / itemInfo.size >= 0.5f
                    }
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
                onShareClick = onShareClick,
                onTagClick = onTagClick,
                isAiEnabled = isAiEnabled,
                isPlaying = item.id == playingItemId,
                onPlaybackStarted = onPlaybackStarted,
                onMuteToggle = { /* handled internally or by global state if needed */ },
                modifier = Modifier.animateItem()
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
