package com.ico.nekofeed.ui.feed

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ico.nekofeed.data.local.TokenManager
import com.ico.nekofeed.data.local.AnalyticsEnvironment
import com.ico.nekofeed.data.local.FallbackFeedData
import com.ico.nekofeed.data.local.MockAnalyticsSeeder
import com.ico.nekofeed.data.local.db.AnalyticsEventEntity
import com.ico.nekofeed.data.local.db.FeedItemInteractionEntity
import com.ico.nekofeed.data.local.db.NekoFeedDatabase
import com.ico.nekofeed.data.model.FeedCategory
import com.ico.nekofeed.data.model.FeedItem
import com.ico.nekofeed.data.model.ItemInteraction
import com.ico.nekofeed.data.remote.RetrofitClient
import com.ico.nekofeed.data.repository.AiRepository
import com.ico.nekofeed.data.repository.FeedRepository
import com.ico.nekofeed.data.repository.InteractionSyncStore
import com.ico.nekofeed.data.repository.InteractionType
import com.ico.nekofeed.data.repository.UserProfileRepository
import com.ico.nekofeed.data.repository.UserRepository
import com.ico.nekofeed.util.FeedUiState
import com.ico.nekofeed.util.matchesCategory
import com.ico.nekofeed.ui.stats.AnalyticsEventType
import com.ico.nekofeed.ui.stats.StatsData
import com.ico.nekofeed.ui.stats.StatsRange
import com.ico.nekofeed.ui.stats.aggregateStats
import kotlinx.coroutines.Job
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.UUID

// ============================================================================
// 【第4站 · 核心业务逻辑（ViewModel）】
// ============================================================================
//
// 📌 这是整个项目最核心的文件——首页信息流的 ViewModel。
//    所有业务逻辑都在这里：加载数据、分页、筛选、点赞收藏、AI 分析、埋点统计……
//
// 📌 ViewModel 是什么？
//    - MVVM 架构的中间层，连接 UI（View）和数据（Model）
//    - 持有 UI 状态（StateFlow），UI 层只管"订阅"和"渲染"
//    - 生命周期比 Activity/Composable 长，屏幕旋转不会丢失数据
//
// 📌 本文件的核心模式：
//
//    ┌─ UI 层（Compose）──────────────────────────┐
//    │  val uiState by viewModel.uiState.collectAsState()  │
//    │  → 数据变了自动重组，不需要手动刷新         │
//    └────────────────────────────────────────────┘
//                    ↑ 订阅 StateFlow
//    ┌─ ViewModel（本文件）────────────────────────┐
//    │  _uiState = MutableStateFlow(FeedUiState()) │
//    │  → _uiState.update { it.copy(...) } 更新状态 │
//    │  → 调用 Repository 获取数据                  │
//    └────────────────────────────────────────────┘
//                    ↑ 调用
//    ┌─ Repository（数据仓库）─────────────────────┐
//    │  → Retrofit 网络请求 / Room 本地缓存          │
//    └────────────────────────────────────────────┘
//
// 📌 Kotlin 协程 & Flow 知识点：
//    - viewModelScope.launch { }  → 启动协程，ViewModel 销毁时自动取消
//    - MutableStateFlow<T>        → 可变的状态流，值变化时通知订阅者
//    - StateFlow<T>               → 不可变的状态流（对外暴露，防止外部修改）
//    - .collectAsState()          → 把 Flow 转成 Compose 的 State
//    - .update { it.copy(...) }   → 原子更新（线程安全），copy() 创建新副本
//    - combine(flow1, flow2) { } → 合并多个 Flow，任一变化都触发
//    - flatMapLatest { }         → 切换到新的内部 Flow（取消旧的）
// ====================================================================

/**
 * FeedViewModel —— 首页信息流的业务逻辑中心
 *
 * 继承 AndroidViewModel（而非普通 ViewModel），因为需要 Application Context
 * 来初始化 DataStore 和 Room 数据库。
 *
 * @param application Android 应用实例，用于获取 Context
 */
@OptIn(ExperimentalCoroutinesApi::class) // flatMapLatest 是实验性 API
class FeedViewModel(application: Application) : AndroidViewModel(application) {

    // ── 依赖初始化 ─────────────────────────────────────────────────
    // 这些是数据层的组件，ViewModel 通过它们获取和操作数据

    private val tokenManager = TokenManager(application) // 本地存储（Token、配置等）

    // FeedRepository: 信息流数据仓库（网络 + 本地缓存 + 降级数据）
    // feedApiProvider 是一个 lambda，延迟获取 Retrofit API 接口
    private val repository = FeedRepository(
        feedApiProvider = { RetrofitClient.feedApi },
        tokenManager = tokenManager
    )
    private val userRepository = UserRepository { RetrofitClient.feedApi } // 用户互动（点赞/收藏/历史）
    private val database = NekoFeedDatabase.getInstance(application)        // Room 数据库单例
    val aiRepository = AiRepository(tokenManager, database.aiCacheDao())   // AI 分析仓库
    private val userProfileRepository = UserProfileRepository(database.userProfileDao())
    private val interactionDao = database.feedItemInteractionDao()  // 互动数据 DAO
    private val analyticsDao = database.feedAnalyticsDao()          // 统计数据 DAO

    // ── UI 状态 ───────────────────────────────────────────────────
    // MutableStateFlow: 可变的状态流，ViewModel 内部用它来更新状态
    // FeedUiState: 不可变的数据类，包含 UI 需要的所有信息（加载状态、数据列表、错误信息等）
    // _uiState 是私有的（下划线前缀是 Kotlin 命名约定），外部不能直接修改
    private val _uiState = MutableStateFlow(FeedUiState())
    // uiState 是公开的只读版本（asStateFlow() 转为不可变），UI 层订阅它
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    // 统计时间范围（日/周/月）
    private val _statsRange = MutableStateFlow(StatsRange.WEEK)
    val statsRange: StateFlow<StatsRange> = _statsRange.asStateFlow()

    // 分析环境（Mock 模式 vs 真实模式），用于隔离测试数据和真实数据
    private val analyticsEnvironment = tokenManager.useMockMode
        .map { if (it) AnalyticsEnvironment.MOCK else AnalyticsEnvironment.LIVE }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,      // 立即开始收集
            AnalyticsEnvironment.LIVE    // 初始值
        )

    // ── 统计数据流 ─────────────────────────────────────────────────
    // combine: 合并两个 Flow，任一变化都触发重新计算
    // flatMapLatest: 当时间范围或环境变化时，切换到新的数据库查询
    // map(::aggregateStats): 把原始事件列表聚合成统计数据
    // stateIn: 把 Flow 转成 StateFlow，WhileSubscribed 表示无人订阅时停止收集
    val stats: StateFlow<StatsData> = combine(_statsRange, analyticsEnvironment) { range, environment ->
        range to environment
    }
        .flatMapLatest { (range, environment) ->
            analyticsDao.observeEventsSince(
                since = System.currentTimeMillis() - range.durationMillis,
                environment = environment
            )
        }
        .map(::aggregateStats)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsData())

    // ── 视频播放状态 ───────────────────────────────────────────────
    // 分离 playingItemId，避免滑动时触发全局重组（性能优化）
    private val _playingItemId = MutableStateFlow<String?>(null)
    val playingItemId: StateFlow<String?> = _playingItemId.asStateFlow()

    // ── 内部数据 ───────────────────────────────────────────────────
    private var allItems: List<FeedItem> = emptyList()   // 全量数据（未筛选）
    private val pageSize = 20                             // 每页条数
    private var currentOffset = 0                         // 当前已加载到的偏移量（分页用）
    private var totalServerItems = Int.MAX_VALUE          // 服务端总条数
    private var feedLoadJob: Job? = null                   // 当前加载任务（用于取消）
    private var loadedSourceSignature: String? =          // 已加载数据源的签名（判断是否需要重新加载）
        null

    // ── 埋点去重集合 ───────────────────────────────────────────────
    // 同一会话内，同一内容只记录一次曝光/播放
    private val exposedItems = mutableSetOf<String>()
    private val playedItems = mutableSetOf<String>()
    private val analyticsSessionId = UUID.randomUUID().toString() // 会话 ID

    /** 获取所有数据（供搜索页使用） */
    fun getAllItems(): List<FeedItem> = allItems

    // ── 初始化块 ───────────────────────────────────────────────────
    // init 块在 ViewModel 创建时自动执行（构造函数之后）
    init {
        observeLlmConfig()           // 监听 AI 配置变化
        observeInteractionUpdates()  // 监听跨页面互动同步
        observeMockAnalyticsSeed()   // Mock 模式下填充演示数据
    }

    // ── 监听 AI 配置 ─────────────────────────────────────────────
    // DataStore 的 Flow 会持续推送配置变化
    private fun observeLlmConfig() {
        viewModelScope.launch {
            tokenManager.llmConfig.collect { config ->
                _uiState.update { it.copy(isAiEnabled = config.aiEnabled) }
            }
        }
    }

    // ── 加载信息流（公开方法，供 UI 调用）──────────────────────────
    fun loadFeed() {
        startFeedLoad(clearExisting = true, showInitialLoading = true)
    }

    // ── Mock 模式下填充演示统计数据 ───────────────────────────────
    private fun observeMockAnalyticsSeed() {
        viewModelScope.launch {
            tokenManager.useMockMode
                .distinctUntilChanged()  // 只在值真正变化时触发
                .collect { isMockMode ->
                    if (isMockMode) {
                        MockAnalyticsSeeder.seedIfNeeded(
                            analyticsDao = analyticsDao,
                            items = FallbackFeedData.items
                        )
                    }
                }
        }
    }

    // ── 核心方法：加载信息流 ───────────────────────────────────────
    //
    // 📌 加载流程：
    //    1. 取消之前的加载任务
    //    2. 重置分页状态
    //    3. 更新 UI 为"加载中"
    //    4. 调用 Repository 获取数据
    //    5. 成功 → 合并本地状态 → 更新 UI → 批量 AI 分析
    //    6. 失败 → 使用降级数据 → 显示错误信息
    //
    // 📌 fold(onSuccess, onFailure) 是 Kotlin 的 Result 处理模式
    //    类似于 try-catch，但更函数式
    private fun startFeedLoad(
        clearExisting: Boolean,
        showInitialLoading: Boolean = false
    ) {
        feedLoadJob?.cancel()  // 取消之前的加载任务（避免重复加载）
        feedLoadJob = viewModelScope.launch {
            // 重置分页状态
            currentOffset = 0
            totalServerItems = Int.MAX_VALUE
            if (clearExisting) {
                allItems = emptyList()
                _playingItemId.value = null
            }
            // 更新 UI 状态为"加载中"
            _uiState.update {
                it.copy(
                    isLoading = showInitialLoading,
                    isRefreshing = !showInitialLoading,  // 下拉刷新 vs 首次加载
                    items = if (clearExisting) emptyList() else it.items,
                    errorMessage = null,
                    usingFallback = false,
                    hasMore = true
                )
            }

            val category = _uiState.value.selectedCategory
            // "精选"频道不传分类参数（服务端返回全量）
            val categoryParam = if (category == FeedCategory.FEATURED) null else category.value
            val isMockMode = tokenManager.isMockMode()

            // 调用 Repository 加载数据
            // repository.loadFeed() 返回 Result<List<FeedItem>>
            // .fold(onSuccess, onFailure) 处理成功和失败两种情况
            repository.loadFeed(category = categoryParam, limit = pageSize, offset = 0).fold(
                onSuccess = { items ->
                    // ── 成功：合并本地状态 → 筛选 → 更新 UI ──
                    val mergedItems = mergeLocalState(items)          // 合并本地点赞/收藏状态
                    val visibleItems = filterByCategory(mergedItems, category) // 按分类筛选
                    allItems = mergedItems
                    currentOffset = mergedItems.size
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            items = visibleItems,
                            availableTags = collectAvailableTags(visibleItems), // 收集可用标签
                            errorMessage = null,
                            usingFallback = false,
                            hasMore = !isMockMode && mergedItems.size >= pageSize // Mock 模式不支持分页
                        )
                    }
                    batchGenerateAi(mergedItems) // 批量触发 AI 分析
                },
                onFailure = { error ->
                    // ── 失败：使用降级数据（本地 Mock 数据）────
                    val fallbackItems = mergeLocalState(repository.getFallbackData())
                    allItems = fallbackItems
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            items = filterByCategory(fallbackItems, category),
                            availableTags = collectAvailableTags(
                                filterByCategory(fallbackItems, category)
                            ),
                            errorMessage = "无法连接服务器: ${error.message}",
                            usingFallback = true,
                            hasMore = false
                        )
                    }
                    batchGenerateAi(fallbackItems)
                }
            )
        }
    }

    // ── 加载更多（分页）────────────────────────────────────────────
    //
    // 📌 防重复加载的守卫条件（Guard Clause）：
    //    正在加载中 / 正在刷新 / 正在加载更多 / 没有更多 / 使用降级数据 → 直接返回
    fun loadMore() {
        if (
            _uiState.value.isLoading ||
            _uiState.value.isRefreshing ||
            _uiState.value.isLoadingMore ||
            !_uiState.value.hasMore ||
            _uiState.value.usingFallback
        ) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }

            val category = _uiState.value.selectedCategory
            val categoryParam = if (category == FeedCategory.FEATURED) null else category.value

            // offset = currentOffset，从上次加载结束的位置继续
            repository.loadFeed(category = categoryParam, limit = pageSize, offset = currentOffset).fold(
                onSuccess = { newItems ->
                    if (newItems.isEmpty()) {
                        // 服务端没有更多数据了
                        _uiState.update { it.copy(isLoadingMore = false, hasMore = false) }
                    } else {
                        val mergedItems = mergeLocalState(newItems)
                        // distinctBy { it.id } 去重（防止重复数据）
                        allItems = (allItems + mergedItems).distinctBy { it.id }
                        currentOffset += mergedItems.size
                        _uiState.update {
                            it.copy(
                                isLoadingMore = false,
                                items = allItems,
                                availableTags = collectAvailableTags(allItems),
                                hasMore = mergedItems.size >= pageSize
                            )
                        }
                        batchGenerateAi(mergedItems)
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoadingMore = false,
                            errorMessage = "加载更多失败: ${error.message}"
                        )
                    }
                }
            )
        }
    }

    // ── 下拉刷新 ─────────────────────────────────────────────────
    fun refresh() {
        startFeedLoad(clearExisting = false)
    }

    // ── 进入页面时刷新（智能判断是否需要重新加载）────────────────
    //
    // 📌 优化逻辑：
    //    - 数据源变了（切换服务器/Mock 模式）→ 重新加载
    //    - 已有数据 → 不重新加载（保留滚动位置）
    //    - 没有数据 → 首次加载
    fun refreshOnEnter() {
        viewModelScope.launch {
            val sourceSignature =
                "${tokenManager.isMockMode()}|${tokenManager.getServerConfig().baseUrl}"
            val sourceChanged = sourceSignature != loadedSourceSignature
            loadedSourceSignature = sourceSignature

            if (!shouldReloadFeedOnEnter(sourceChanged, allItems.isNotEmpty())) {
                return@launch
            }

            startFeedLoad(
                clearExisting = sourceChanged,
                showInitialLoading = allItems.isEmpty()
            )
        }
    }

    // ── 批量 AI 分析 ─────────────────────────────────────────────
    //
    // 📌 对新加载的每条内容触发 AI 摘要/标签生成
    //    结果写入 Room 缓存，下次加载直接读取
    private fun batchGenerateAi(items: List<FeedItem>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAiLoading = true) }
            try {
                aiRepository.batchGenerateAi(items)
                // AI 分析完成后，把结果合并到 allItems
                val updatedItems = allItems.map { item ->
                    val cached = aiRepository.getCache(item.id)
                    if (cached != null) {
                        item.copy(
                            aiSummary = cached.aiSummary ?: item.aiSummary,
                            aiTags = parseTagsFromCache(cached.aiTags),
                            aiReason = cached.aiReason ?: item.aiReason
                        )
                    } else item
                }
                allItems = updatedItems
                updateFilteredItems()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _uiState.update { it.copy(isAiLoading = false) }
            }
        }
    }

    // ── AI 并发控制 ─────────────────────────────────────────────
    // Semaphore(8) 限制最多同时 8 个 AI 请求，避免打爆 API
    private val aiSemaphore = Semaphore(8)

    // ── 单条 AI 分析（详情页触发）────────────────────────────────
    //
    // 📌 流程：检查缓存 → 检查配置 → 显示 loading → 请求 AI → 更新数据
    fun requestAiAnalysis(item: FeedItem) {
        if (!item.aiSummary.isNullOrBlank() || item.isAiLoading) return // 已有结果或正在加载

        viewModelScope.launch {
            // 1. 先检查本地缓存
            val cached = aiRepository.getCache(item.id)
            if (cached != null) {
                val updatedTags = parseTagsFromCache(cached.aiTags)
                allItems = allItems.map { it ->
                    if (it.id == item.id) {
                        it.copy(
                            aiSummary = cached.aiSummary,
                            aiTags = updatedTags,
                            aiReason = cached.aiReason,
                            isAiLoading = false
                        )
                    } else it
                }
                updateFilteredItems()
                return@launch
            }

            // 2. 检查 AI 配置是否就绪
            val config = tokenManager.getLlmConfig()
            if (!config.aiEnabled || config.baseUrl.isBlank()) return@launch

            // 3. 显示 loading 状态
            allItems = allItems.map { it ->
                if (it.id == item.id) {
                    it.copy(isAiLoading = true)
                } else it
            }
            updateFilteredItems()

            // 4. 用 Semaphore 控制并发，请求 AI
            val result = aiSemaphore.withPermit {
                aiRepository.generateFeedAi(item)
            }

            // 5. 更新结果
            allItems = allItems.map { it ->
                if (it.id == item.id) {
                    if (result != null) {
                        it.copy(
                            aiSummary = result.aiSummary,
                            aiTags = result.aiTags,
                            aiReason = result.aiReason,
                            isAiLoading = false
                        )
                    } else {
                        it.copy(isAiLoading = false)
                    }
                } else it
            }
            updateFilteredItems()
        }
    }

    private fun parseTagsFromCache(json: String): List<String> {
        return try {
            val gson = com.google.gson.Gson()
            val listType = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
            gson.fromJson(json, listType) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 重试（错误页面的"重试"按钮调用） */
    fun retry() {
        loadFeed()
    }

    // ── 切换频道分类 ─────────────────────────────────────────────
    //
    // 📌 选择新分类时：清空当前数据 → 重新加载
    fun selectCategory(category: FeedCategory) {
        if (_uiState.value.selectedCategory == category) return
        _uiState.update {
            it.copy(
                selectedCategory = category,
                isLoading = true,
                isRefreshing = false,
                items = emptyList(),
                selectedTags = emptyList(),
                availableTags = emptyList(),
                errorMessage = null,
                usingFallback = false,
                hasMore = true
            )
        }
        startFeedLoad(clearExisting = true, showInitialLoading = true)
    }

    // ── 视频播放控制 ─────────────────────────────────────────────
    fun setPlayingItemId(id: String?) {
        _playingItemId.value = id
    }

    // ── 记录播放开始（去重，同一内容只记录一次）────────────────
    fun recordPlaybackStarted(itemId: String) {
        if (!playedItems.add(itemId)) return // 已记录过
        recordAnalyticsEvent(itemId, AnalyticsEventType.PLAY)
    }

    // ── 点赞（乐观更新模式）────────────────────────────────────
    //
    // 📌 乐观更新（Optimistic Update）是重要的 UI 设计模式：
    //    1. 先立即更新本地 UI（用户看到即时反馈）
    //    2. 同时发送网络请求
    //    3. 成功 → 用服务端返回的权威数据替换
    //    4. 失败 → 回滚到之前的状态（snapshot）
    //
    //    这样用户感觉"很快"，不需要等网络响应
    fun toggleLike(itemId: String) {
        // 记录互动到用户画像
        val item = allItems.find { it.id == itemId }
        if (item != null) {
            viewModelScope.launch {
                userProfileRepository.recordInteraction(item, InteractionType.LIKE)
            }
        }

        // ── 步骤 1：乐观更新（立即修改本地数据）────────────────
        val snapshot = allItems // 保存快照，失败时回滚
        allItems = allItems.map { item ->
            if (item.id == itemId) {
                item.copy(
                    isLiked = !item.isLiked,
                    likeCount = if (item.isLiked) item.likeCount - 1 else item.likeCount + 1
                )
            } else item
        }
        updateFilteredItems() // 立即更新 UI

        // ── 步骤 2：发送网络请求 ────────────────────────────────
        viewModelScope.launch {
            // Mock 模式：不走网络，直接用本地数据
            if (tokenManager.isMockMode()) {
                allItems.firstOrNull { it.id == itemId }?.let {
                    saveAndPublishInteraction(it)
                    if (it.isLiked) {
                        recordAnalyticsEvent(itemId, AnalyticsEventType.LIKE)
                    }
                }
                return@launch
            }

            // 真实模式：调用服务端 API
            userRepository.toggleLike(itemId).fold(
                onSuccess = { interaction ->
                    // ── 步骤 3a：成功 → 用服务端权威数据覆盖 ──
                    applyInteraction(itemId, interaction)
                    saveAndPublishInteraction(itemId, interaction)
                    if (interaction.isLiked) {
                        recordAnalyticsEvent(itemId, AnalyticsEventType.LIKE)
                    }
                },
                onFailure = {
                    // ── 步骤 3b：失败 → 回滚 ──────────────────
                    allItems = snapshot
                    updateFilteredItems()
                }
            )
        }
    }

    // ── 收藏（逻辑同点赞）──────────────────────────────────────
    fun toggleCollect(itemId: String) {
        val item = allItems.find { it.id == itemId }
        if (item != null) {
            viewModelScope.launch {
                userProfileRepository.recordInteraction(item, InteractionType.COLLECT)
            }
        }

        // 乐观更新
        val snapshot = allItems
        allItems = allItems.map { item ->
            if (item.id == itemId) {
                item.copy(
                    isCollected = !item.isCollected,
                    collectCount = if (item.isCollected) item.collectCount - 1 else item.collectCount + 1
                )
            } else item
        }
        updateFilteredItems()

        viewModelScope.launch {
            if (tokenManager.isMockMode()) {
                allItems.firstOrNull { it.id == itemId }?.let {
                    saveAndPublishInteraction(it)
                    if (it.isCollected) {
                        recordAnalyticsEvent(itemId, AnalyticsEventType.COLLECT)
                    }
                }
                return@launch
            }

            userRepository.toggleCollect(itemId).fold(
                onSuccess = { interaction ->
                    applyInteraction(itemId, interaction)
                    saveAndPublishInteraction(itemId, interaction)
                    if (interaction.isCollected) {
                        recordAnalyticsEvent(itemId, AnalyticsEventType.COLLECT)
                    }
                },
                onFailure = {
                    allItems = snapshot
                    updateFilteredItems()
                }
            )
        }
    }

    // ── 分享 ─────────────────────────────────────────────────────
    fun toggleShare(itemId: String) {
        val item = allItems.find { it.id == itemId }
        if (item != null) {
            viewModelScope.launch {
                userProfileRepository.recordInteraction(item, InteractionType.SHARE)
            }
        }

        allItems = allItems.map { item ->
            if (item.id == itemId) {
                item.copy(shareCount = item.shareCount + 1)
            } else item
        }
        updateFilteredItems()
        incrementAnalytics(itemId, AnalyticsEvent.SHARE)
        recordAnalyticsEvent(itemId, AnalyticsEventType.SHARE)
    }

    // ── 标签筛选 ─────────────────────────────────────────────────
    //
    // 📌 点击标签时：如果已选中就移除，否则添加（Toggle 逻辑）
    fun filterByTag(tag: String) {
        val currentTags = _uiState.value.selectedTags.toMutableList()
        if (currentTags.contains(tag)) {
            currentTags.remove(tag)
        } else {
            currentTags.add(0, tag) // 添加到列表头部
        }
        _uiState.update { it.copy(selectedTags = currentTags) }
        updateFilteredItems()
    }

    fun clearTagFilters() {
        if (_uiState.value.selectedTags.isEmpty()) return
        _uiState.update { it.copy(selectedTags = emptyList()) }
        updateFilteredItems()
    }

    // ── 曝光埋点 ─────────────────────────────────────────────────
    //
    // 📌 曝光判定：卡片可见像素 >= 50%，同一会话内同一内容只记录一次
    fun recordExposure(itemId: String) {
        if (exposedItems.contains(itemId)) return // 去重
        exposedItems.add(itemId)

        // 只更新内存数据，不触发 UI 重组（注释里说得很清楚）
        allItems = allItems.map { item ->
            if (item.id == itemId) {
                item.copy(exposureCount = item.exposureCount + 1)
            } else item
        }
        incrementAnalytics(itemId, AnalyticsEvent.EXPOSURE)
        recordAnalyticsEvent(itemId, AnalyticsEventType.EXPOSURE)
    }

    // ── 点击埋点 ─────────────────────────────────────────────────
    fun recordClick(itemId: String) {
        allItems = allItems.map { item ->
            if (item.id == itemId) {
                item.copy(clickCount = item.clickCount + 1)
            } else item
        }
        incrementAnalytics(itemId, AnalyticsEvent.CLICK)
        recordAnalyticsEvent(itemId, AnalyticsEventType.CLICK)

        viewModelScope.launch {
            allItems.firstOrNull { it.id == itemId }?.let {
                userProfileRepository.recordInteraction(it, InteractionType.CLICK)
            }
            recordLocalHistory(itemId)
            if (tokenManager.isMockMode()) return@launch
            userRepository.recordHistory(itemId)
        }
    }

    // ── 按 ID 查找数据 ─────────────────────────────────────────
    //
    // 📌 三级查找：内存数据 → 网络缓存 → 降级数据
    fun getItemById(id: String): FeedItem? {
        return allItems.find { it.id == id }
            ?: repository.getCachedItemById(id)
            ?: repository.getFallbackData().find { it.id == id }
    }

    // ── 本地搜索 ─────────────────────────────────────────────────
    //
    // 📌 纯本地关键词匹配 + 简单评分排序
    //    标题匹配权重最高(3分)，摘要/标签次之(2分)，正文/品牌最低(1分)
    fun searchItems(query: String): List<FeedItem> {
        val q = query.lowercase().trim()
        if (q.isBlank()) return emptyList()

        val keywords = q.split(" ", "，", ",", "、").filter { it.isNotBlank() }

        return allItems.map { item ->
            val searchable = "${item.title} ${item.displaySummary} ${item.content ?: ""} ${item.brand ?: ""} ${item.displayTags.joinToString(" ")}".lowercase()
            var score = 0
            keywords.forEach { keyword ->
                if (item.title.lowercase().contains(keyword)) score += 3
                if (item.displaySummary.lowercase().contains(keyword)) score += 2
                if (item.displayTags.any { it.lowercase().contains(keyword) }) score += 2
                if ((item.content ?: "").lowercase().contains(keyword)) score += 1
                if ((item.brand ?: "").lowercase().contains(keyword)) score += 1
            }
            item to score
        }.filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    fun selectStatsRange(range: StatsRange) {
        _statsRange.value = range
    }

    // ── 分类筛选 ─────────────────────────────────────────────────
    private fun filterByCategory(items: List<FeedItem>, category: FeedCategory): List<FeedItem> {
        return items.filter { it.matchesCategory(category) }
    }

    // ── 更新筛选后的显示数据 ─────────────────────────────────────
    //
    // 📌 每次数据变化后都要调用这个方法
    //    它根据当前选中的分类和标签，从 allItems 中筛选出要显示的数据
    private fun updateFilteredItems() {
        val category = _uiState.value.selectedCategory
        val tags = _uiState.value.selectedTags
        var filtered = filterByCategory(allItems, category)
        val availableTags = collectAvailableTags(filtered)
        if (tags.isNotEmpty()) {
            filtered = filtered.filter { item ->
                tags.any { tag -> item.displayTags.contains(tag) }
            }
        }
        _uiState.update {
            it.copy(
                items = filtered,
                availableTags = availableTags
            )
        }
    }

    // ── 收集可用标签 ─────────────────────────────────────────────
    // 从当前数据中提取所有标签，按出现频率排序，取前 16 个
    private fun collectAvailableTags(items: List<FeedItem>): List<String> {
        return items
            .flatMap { it.displayTags }
            .filter { it.isNotBlank() }
            .groupingBy { it }        // 按标签分组
            .eachCount()              // 计算每个标签出现次数
            .entries
            .sortedWith(
                compareByDescending<Map.Entry<String, Int>> { it.value } // 按频率降序
                    .thenBy { it.key }                                   // 频率相同按字母排序
            )
            .map { it.key }
            .take(16)                 // 最多取 16 个标签
    }

    // ── 合并本地状态 ─────────────────────────────────────────────
    //
    // 📌 服务端返回的数据不包含本地的点赞/收藏状态（Mock 模式）
    //    所以需要把 Room 数据库里的本地状态合并进去
    private suspend fun mergeLocalState(items: List<FeedItem>): List<FeedItem> {
        val analytics = analyticsDao.getAll().associateBy { it.itemId }
        val interactions = if (tokenManager.isMockMode()) {
            interactionDao.getAllInteractions().associateBy { it.itemId }
        } else {
            emptyMap()
        }

        return items.map { item ->
            val localAnalytics = analytics[item.id]
            val localInteraction = interactions[item.id]
            item.copy(
                isLiked = localInteraction?.isLiked ?: item.isLiked,
                isCollected = localInteraction?.isCollected ?: item.isCollected,
                likeCount = localInteraction?.likeCount ?: item.likeCount,
                collectCount = localInteraction?.collectCount ?: item.collectCount,
                exposureCount = item.exposureCount + (localAnalytics?.exposureCount ?: 0),
                clickCount = item.clickCount + (localAnalytics?.clickCount ?: 0),
                shareCount = item.shareCount + (localAnalytics?.shareCount ?: 0),
                playCount = item.playCount + (localAnalytics?.playCount ?: 0)
            )
        }
    }

    // ── 跨页面互动同步 ─────────────────────────────────────────
    //
    // 📌 InteractionSyncStore 是一个全局的事件总线（Event Bus）
    //    详情页点赞 → publish → 首页的 allItems 也同步更新
    //    这样用户从详情页返回首页时，点赞状态已经是最新的
    private fun observeInteractionUpdates() {
        viewModelScope.launch {
            InteractionSyncStore.updates.collect { update ->
                applyInteraction(update.itemId, update.interaction)
            }
        }
    }

    /** 把互动状态应用到 allItems */
    private fun applyInteraction(itemId: String, interaction: ItemInteraction) {
        allItems = allItems.map { item ->
            if (item.id == itemId) {
                item.copy(
                    isLiked = interaction.isLiked,
                    isCollected = interaction.isCollected,
                    likeCount = interaction.likeCount,
                    collectCount = interaction.collectCount
                )
            } else {
                item
            }
        }
        updateFilteredItems()
    }

    // ── 保存并广播互动状态 ─────────────────────────────────────
    // 保存到 Room 数据库 + 通过 InteractionSyncStore 广播给其他页面
    private suspend fun saveAndPublishInteraction(item: FeedItem) {
        saveAndPublishInteraction(
            item.id,
            ItemInteraction(
                isLiked = item.isLiked,
                isCollected = item.isCollected,
                likeCount = item.likeCount,
                collectCount = item.collectCount
            )
        )
    }

    private suspend fun saveAndPublishInteraction(
        itemId: String,
        interaction: ItemInteraction
    ) {
        val existing = interactionDao.getInteraction(itemId)
        interactionDao.upsertInteraction(
            FeedItemInteractionEntity(
                itemId = itemId,
                isLiked = interaction.isLiked,
                isCollected = interaction.isCollected,
                likeCount = interaction.likeCount,
                collectCount = interaction.collectCount,
                lastViewedAt = existing?.lastViewedAt
            )
        )
        InteractionSyncStore.publish(itemId, interaction) // 广播给其他页面
    }

    // ── 记录本地浏览历史 ─────────────────────────────────────
    private suspend fun recordLocalHistory(itemId: String) {
        val existing = interactionDao.getInteraction(itemId)
        interactionDao.upsertInteraction(
            (existing ?: FeedItemInteractionEntity(itemId = itemId)).copy(
                lastViewedAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    // ── 统计计数递增 ─────────────────────────────────────────
    // Room DAO 的增量更新（SQL 的 SET count = count + 1）
    private fun incrementAnalytics(itemId: String, event: AnalyticsEvent) {
        viewModelScope.launch {
            when (event) {
                AnalyticsEvent.EXPOSURE -> analyticsDao.incrementExposure(itemId)
                AnalyticsEvent.CLICK -> analyticsDao.incrementClick(itemId)
                AnalyticsEvent.SHARE -> analyticsDao.incrementShare(itemId)
                AnalyticsEvent.PLAY -> analyticsDao.incrementPlay(itemId)
            }
        }
    }

    // ── 记录分析事件（写入事件表）────────────────────────────
    // 每个事件都附带环境信息（Mock/Live），用于隔离测试数据
    private fun recordAnalyticsEvent(itemId: String, eventType: String) {
        val item = getItemById(itemId) ?: return
        viewModelScope.launch {
            analyticsDao.insertEvent(
                AnalyticsEventEntity(
                    itemId = item.id,
                    eventType = eventType,
                    timestamp = System.currentTimeMillis(),
                    sessionId = analyticsSessionId,
                    environment = analyticsEnvironment.value,
                    title = item.title,
                    imageUrl = item.imageUrl,
                    category = item.category,
                    itemType = item.itemType
                )
            )
        }
    }

    // ── 内部枚举：统计事件类型 ─────────────────────────────────
    private enum class AnalyticsEvent {
        EXPOSURE,
        CLICK,
        SHARE,
        PLAY
    }
}

// ── 顶层函数：判断是否需要重新加载 ─────────────────────────────
// 提取为独立函数方便单元测试（不需要创建 ViewModel 实例）
internal fun shouldReloadFeedOnEnter(
    sourceChanged: Boolean,
    hasLoadedItems: Boolean
): Boolean = sourceChanged || !hasLoadedItems
