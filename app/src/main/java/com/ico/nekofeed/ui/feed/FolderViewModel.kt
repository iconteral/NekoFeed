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

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModel(application: Application) : AndroidViewModel(application) {
    private val tokenManager = TokenManager(application)
    private val repository = FeedRepository(
        feedApiProvider = { RetrofitClient.feedApi },
        tokenManager = tokenManager
    )
    private val userRepository = UserRepository { RetrofitClient.feedApi }
    private val database = NekoFeedDatabase.getInstance(application)
    val aiRepository = AiRepository(tokenManager, database.aiCacheDao())
    private val userProfileRepository = UserProfileRepository(database.userProfileDao())
    private val interactionDao = database.feedItemInteractionDao()
    private val analyticsDao = database.feedAnalyticsDao()

    private val _uiState = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()
    private val _statsRange = MutableStateFlow(StatsRange.WEEK)
    val statsRange: StateFlow<StatsRange> = _statsRange.asStateFlow()
    private val analyticsEnvironment = tokenManager.useMockMode
        .map { if (it) AnalyticsEnvironment.MOCK else AnalyticsEnvironment.LIVE }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            AnalyticsEnvironment.LIVE
        )
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

    // 分离 playingItemId，避免滑动时触发全局重组
    private val _playingItemId = MutableStateFlow<String?>(null)
    val playingItemId: StateFlow<String?> = _playingItemId.asStateFlow()

    private var allItems: List<FeedItem> = emptyList()
    private val pageSize = 20
    private var currentOffset = 0
    private var totalServerItems = Int.MAX_VALUE
    private var feedLoadJob: Job? = null
    private var loadedSourceSignature: String? = null

    private val exposedItems = mutableSetOf<String>()
    private val playedItems = mutableSetOf<String>()
    private val analyticsSessionId = UUID.randomUUID().toString()

    fun getAllItems(): List<FeedItem> = allItems

    init {
        observeLlmConfig()
        observeInteractionUpdates()
        observeMockAnalyticsSeed()
    }

    private fun observeLlmConfig() {
        viewModelScope.launch {
            tokenManager.llmConfig.collect { config ->
                _uiState.update { it.copy(isAiEnabled = config.aiEnabled) }
            }
        }
    }

    fun loadFeed() {
        startFeedLoad(clearExisting = true, showInitialLoading = true)
    }

    private fun observeMockAnalyticsSeed() {
        viewModelScope.launch {
            tokenManager.useMockMode
                .distinctUntilChanged()
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

    private fun startFeedLoad(
        clearExisting: Boolean,
        showInitialLoading: Boolean = false
    ) {
        feedLoadJob?.cancel()
        feedLoadJob = viewModelScope.launch {
            currentOffset = 0
            totalServerItems = Int.MAX_VALUE
            if (clearExisting) {
                allItems = emptyList()
                _playingItemId.value = null
            }
            _uiState.update {
                it.copy(
                    isLoading = showInitialLoading,
                    isRefreshing = !showInitialLoading,
                    items = if (clearExisting) emptyList() else it.items,
                    errorMessage = null,
                    usingFallback = false,
                    hasMore = true
                )
            }

            val category = _uiState.value.selectedCategory
            val categoryParam = if (category == FeedCategory.FEATURED) null else category.value
            val isMockMode = tokenManager.isMockMode()

            repository.loadFeed(category = categoryParam, limit = pageSize, offset = 0).fold(
                onSuccess = { items ->
                    val mergedItems = mergeLocalState(items)
                    val visibleItems = filterByCategory(mergedItems, category)
                    allItems = mergedItems
                    currentOffset = mergedItems.size
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            items = visibleItems,
                            availableTags = collectAvailableTags(visibleItems),
                            errorMessage = null,
                            usingFallback = false,
                            hasMore = !isMockMode && mergedItems.size >= pageSize
                        )
                    }
                    batchGenerateAi(mergedItems)
                },
                onFailure = { error ->
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

            repository.loadFeed(category = categoryParam, limit = pageSize, offset = currentOffset).fold(
                onSuccess = { newItems ->
                    if (newItems.isEmpty()) {
                        _uiState.update { it.copy(isLoadingMore = false, hasMore = false) }
                    } else {
                        val mergedItems = mergeLocalState(newItems)
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

    fun refresh() {
        startFeedLoad(clearExisting = false)
    }

    fun refreshOnEnter() {
        viewModelScope.launch {
            val sourceSignature =
                "${tokenManager.isMockMode()}|${tokenManager.getServerConfig().baseUrl}"
            val sourceChanged = sourceSignature != loadedSourceSignature
            loadedSourceSignature = sourceSignature

            // Returning from detail recreates FeedScreen's composition. Keep the
            // already loaded pages so LazyListState can restore the exact item.
            if (!shouldReloadFeedOnEnter(sourceChanged, allItems.isNotEmpty())) {
                return@launch
            }

            startFeedLoad(
                clearExisting = sourceChanged,
                showInitialLoading = allItems.isEmpty()
            )
        }
    }

    private fun batchGenerateAi(items: List<FeedItem>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAiLoading = true) }
            try {
                aiRepository.batchGenerateAi(items)
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

    private val aiSemaphore = Semaphore(8)

    fun requestAiAnalysis(item: FeedItem) {
        if (!item.aiSummary.isNullOrBlank() || item.isAiLoading) return

        viewModelScope.launch {
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

            val config = tokenManager.getLlmConfig()
            if (!config.aiEnabled || config.baseUrl.isBlank()) return@launch

            allItems = allItems.map { it ->
                if (it.id == item.id) {
                    it.copy(isAiLoading = true)
                } else it
            }
            updateFilteredItems()

            val result = aiSemaphore.withPermit {
                aiRepository.generateFeedAi(item)
            }

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

    fun retry() {
        loadFeed()
    }

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

    fun setPlayingItemId(id: String?) {
        _playingItemId.value = id
    }

    fun recordPlaybackStarted(itemId: String) {
        if (!playedItems.add(itemId)) return
        recordAnalyticsEvent(itemId, AnalyticsEventType.PLAY)
    }

    fun toggleLike(itemId: String) {
        val item = allItems.find { it.id == itemId }
        if (item != null) {
            viewModelScope.launch {
                userProfileRepository.recordInteraction(item, InteractionType.LIKE)
            }
        }

        // 乐观更新
        val snapshot = allItems
        allItems = allItems.map { item ->
            if (item.id == itemId) {
                item.copy(
                    isLiked = !item.isLiked,
                    likeCount = if (item.isLiked) item.likeCount - 1 else item.likeCount + 1
                )
            } else item
        }
        updateFilteredItems()

        viewModelScope.launch {
            if (tokenManager.isMockMode()) {
                allItems.firstOrNull { it.id == itemId }?.let {
                    saveAndPublishInteraction(it)
                    if (it.isLiked) {
                        recordAnalyticsEvent(itemId, AnalyticsEventType.LIKE)
                    }
                }
                return@launch
            }

            userRepository.toggleLike(itemId).fold(
                onSuccess = { interaction ->
                    applyInteraction(itemId, interaction)
                    saveAndPublishInteraction(itemId, interaction)
                    if (interaction.isLiked) {
                        recordAnalyticsEvent(itemId, AnalyticsEventType.LIKE)
                    }
                },
                onFailure = {
                    allItems = snapshot
                    updateFilteredItems()
                }
            )
        }
    }

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

    fun filterByTag(tag: String) {
        val currentTags = _uiState.value.selectedTags.toMutableList()
        if (currentTags.contains(tag)) {
            currentTags.remove(tag)
        } else {
            currentTags.add(0, tag)
        }
        _uiState.update { it.copy(selectedTags = currentTags) }
        updateFilteredItems()
    }

    fun clearTagFilters() {
        if (_uiState.value.selectedTags.isEmpty()) return
        _uiState.update { it.copy(selectedTags = emptyList()) }
        updateFilteredItems()
    }

    fun recordExposure(itemId: String) {
        if (exposedItems.contains(itemId)) return
        exposedItems.add(itemId)

        // 只更新内存数据，不触发UI重组
        // 曝光计数会在下次加载时同步到服务端
        allItems = allItems.map { item ->
            if (item.id == itemId) {
                item.copy(exposureCount = item.exposureCount + 1)
            } else item
        }
        incrementAnalytics(itemId, AnalyticsEvent.EXPOSURE)
        recordAnalyticsEvent(itemId, AnalyticsEventType.EXPOSURE)
    }

    fun recordClick(itemId: String) {
        // 只更新内存数据，不触发UI重组
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

    fun getItemById(id: String): FeedItem? {
        return allItems.find { it.id == id }
            ?: repository.getCachedItemById(id)
            ?: repository.getFallbackData().find { it.id == id }
    }

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

    private fun filterByCategory(items: List<FeedItem>, category: FeedCategory): List<FeedItem> {
        return items.filter { it.matchesCategory(category) }
    }

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

    private fun collectAvailableTags(items: List<FeedItem>): List<String> {
        return items
            .flatMap { it.displayTags }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(
                compareByDescending<Map.Entry<String, Int>> { it.value }
                    .thenBy { it.key }
            )
            .map { it.key }
            .take(16)
    }

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

    private fun observeInteractionUpdates() {
        viewModelScope.launch {
            InteractionSyncStore.updates.collect { update ->
                applyInteraction(update.itemId, update.interaction)
            }
        }
    }

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
        InteractionSyncStore.publish(itemId, interaction)
    }

    private suspend fun recordLocalHistory(itemId: String) {
        val existing = interactionDao.getInteraction(itemId)
        interactionDao.upsertInteraction(
            (existing ?: FeedItemInteractionEntity(itemId = itemId)).copy(
                lastViewedAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

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

    private enum class AnalyticsEvent {
        EXPOSURE,
        CLICK,
        SHARE,
        PLAY
    }
}

internal fun shouldReloadFeedOnEnter(
    sourceChanged: Boolean,
    hasLoadedItems: Boolean
): Boolean = sourceChanged || !hasLoadedItems
