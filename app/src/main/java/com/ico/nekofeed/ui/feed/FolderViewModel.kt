package com.ico.nekofeed.ui.feed

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ico.nekofeed.data.local.TokenManager
import com.ico.nekofeed.data.local.db.FeedItemInteractionDao
import com.ico.nekofeed.data.local.db.FeedItemInteractionEntity
import com.ico.nekofeed.data.local.db.NekoFeedDatabase
import com.ico.nekofeed.data.model.FeedCategory
import com.ico.nekofeed.data.model.FeedItem
import com.ico.nekofeed.data.remote.RetrofitClient
import com.ico.nekofeed.data.repository.AiRepository
import com.ico.nekofeed.data.repository.FeedRepository
import com.ico.nekofeed.data.repository.InteractionType
import com.ico.nekofeed.data.repository.UserProfileRepository
import com.ico.nekofeed.data.repository.UserRepository
import com.ico.nekofeed.util.FeedUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class FeedViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FeedRepository(RetrofitClient.feedApi)
    private val userRepository = UserRepository(RetrofitClient.feedApi)
    private val tokenManager = TokenManager(application)
    private val database = NekoFeedDatabase.getInstance(application)
    val aiRepository = AiRepository(tokenManager, database.aiCacheDao())
    private val userProfileRepository = UserProfileRepository(database.userProfileDao())
    private val interactionDao: FeedItemInteractionDao = database.feedItemInteractionDao()

    private val _uiState = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    private var allItems: List<FeedItem> = emptyList()
    private val pageSize = 20
    private var currentOffset = 0
    private var totalServerItems = Int.MAX_VALUE

    fun getAllItems(): List<FeedItem> = allItems

    private fun isLoggedIn(): Boolean {
        return RetrofitClient.hasToken()
    }

    init {
        loadFeed()
        observeLlmConfig()
    }

    private fun observeLlmConfig() {
        viewModelScope.launch {
            tokenManager.llmConfig.collect { config ->
                _uiState.update { it.copy(isAiEnabled = config.aiEnabled) }
            }
        }
    }

    fun loadFeed() {
        viewModelScope.launch {
            currentOffset = 0
            totalServerItems = Int.MAX_VALUE
            _uiState.update { it.copy(isLoading = true, errorMessage = null, usingFallback = false, hasMore = true) }

            repository.loadFeed(limit = pageSize, offset = 0).fold(
                onSuccess = { items ->
                    val merged = mergeLocalInteractions(items)
                    allItems = merged
                    currentOffset = items.size
                    // Try to get the total from the response to determine hasMore
                    val hasMore = items.size >= pageSize
                    val filteredItems = filterByCategory(items, _uiState.value.selectedCategory)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            items = filteredItems,
                            errorMessage = null,
                            usingFallback = false,
                            hasMore = hasMore
                        )
                    }
                    batchGenerateAi(items)
                },
                onFailure = { error ->
                    val fallbackItems = repository.getFallbackData()
                    allItems = fallbackItems
                    val filteredItems = filterByCategory(fallbackItems, _uiState.value.selectedCategory)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            items = filteredItems,
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
        // Guard: don't load more if already loading, no more items, or using fallback
        if (_uiState.value.isLoadingMore || !_uiState.value.hasMore || _uiState.value.usingFallback) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }

            repository.loadFeed(limit = pageSize, offset = currentOffset).fold(
                onSuccess = { newItems ->
                    if (newItems.isEmpty()) {
                        _uiState.update { it.copy(isLoadingMore = false, hasMore = false) }
                    } else {
                        allItems = allItems + newItems
                        currentOffset += newItems.size
                        val hasMore = newItems.size >= pageSize
                        updateFilteredItems()
                        _uiState.update { it.copy(isLoadingMore = false, hasMore = hasMore) }
                        batchGenerateAi(newItems)
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
        viewModelScope.launch {
            currentOffset = 0
            totalServerItems = Int.MAX_VALUE
            _uiState.update { it.copy(isRefreshing = true, hasMore = true) }

            repository.loadFeed(limit = pageSize, offset = 0).fold(
                onSuccess = { items ->
                    val merged = mergeLocalInteractions(items)
                    allItems = merged
                    currentOffset = items.size
                    val hasMore = items.size >= pageSize
                    val filteredItems = filterByCategory(merged, _uiState.value.selectedCategory)
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            items = filteredItems,
                            errorMessage = null,
                            usingFallback = false,
                            hasMore = hasMore
                        )
                    }
                    batchGenerateAi(items)
                },
                onFailure = { error ->
                    val fallbackItems = repository.getFallbackData()
                    allItems = fallbackItems
                    val filteredItems = filterByCategory(fallbackItems, _uiState.value.selectedCategory)
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            items = filteredItems,
                            errorMessage = "刷新失败: ${error.message}",
                            usingFallback = true,
                            hasMore = false
                        )
                    }
                }
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

    private val aiSemaphore = Semaphore(2)

    fun requestAiAnalysis(item: FeedItem) {
        if (!item.aiSummary.isNullOrBlank() || item.isAiLoading) return

        viewModelScope.launch {
            // Check cache first
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

            // Check config
            val config = tokenManager.getLlmConfig()
            if (!config.aiEnabled || config.baseUrl.isBlank()) return@launch

            // Set state to loading
            allItems = allItems.map { it ->
                if (it.id == item.id) {
                    it.copy(isAiLoading = true)
                } else it
            }
            updateFilteredItems()

            // Run generation with semaphore limit
            val result = aiSemaphore.withPermit {
                aiRepository.generateFeedAi(item)
            }

            // Update item with result
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
        _uiState.update { it.copy(selectedCategory = category) }
        val filteredItems = filterByCategory(allItems, category)
        _uiState.update { it.copy(items = filteredItems) }
    }

    fun toggleLike(itemId: String) {
        val item = allItems.find { it.id == itemId }
        if (item != null) {
            viewModelScope.launch {
                userProfileRepository.recordInteraction(item, InteractionType.LIKE)
            }
        }

        if (!isLoggedIn()) {
            val toggled = allItems.map { item ->
                if (item.id == itemId) {
                    item.copy(
                        isLiked = !item.isLiked,
                        likeCount = if (item.isLiked) item.likeCount - 1 else item.likeCount + 1,
                        aiTags = item.aiTags ?: emptyList()
                    )
                } else item
            }
            allItems = toggled
            updateFilteredItems()
            toggled.find { it.id == itemId }?.let { persistInteraction(it) }
            return
        }

        viewModelScope.launch {
            userRepository.toggleLike(itemId).fold(
                onSuccess = { interaction ->
                    allItems = allItems.map { item ->
                        if (item.id == itemId) {
                            item.copy(
                                isLiked = interaction.isLiked,
                                likeCount = interaction.likeCount,
                                isCollected = interaction.isCollected,
                                collectCount = interaction.collectCount,
                                aiTags = item.aiTags ?: emptyList()
                            )
                        } else item
                    }
                    updateFilteredItems()
                    allItems.find { it.id == itemId }?.let { persistInteraction(it) }
                },
                onFailure = {
                    allItems = allItems.map { item ->
                        if (item.id == itemId) {
                            item.copy(
                                isLiked = !item.isLiked,
                                likeCount = if (item.isLiked) item.likeCount - 1 else item.likeCount + 1,
                                aiTags = item.aiTags ?: emptyList()
                            )
                        } else item
                    }
                    updateFilteredItems()
                    allItems.find { it.id == itemId }?.let { persistInteraction(it) }
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

        if (!isLoggedIn()) {
            val toggled = allItems.map { item ->
                if (item.id == itemId) {
                    item.copy(
                        isCollected = !item.isCollected,
                        collectCount = if (item.isCollected) item.collectCount - 1 else item.collectCount + 1,
                        aiTags = item.aiTags ?: emptyList()
                    )
                } else item
            }
            allItems = toggled
            updateFilteredItems()
            toggled.find { it.id == itemId }?.let { persistInteraction(it) }
            return
        }

        viewModelScope.launch {
            userRepository.toggleCollect(itemId).fold(
                onSuccess = { interaction ->
                    allItems = allItems.map { item ->
                        if (item.id == itemId) {
                            item.copy(
                                isLiked = interaction.isLiked,
                                likeCount = interaction.likeCount,
                                isCollected = interaction.isCollected,
                                collectCount = interaction.collectCount,
                                aiTags = item.aiTags ?: emptyList()
                            )
                        } else item
                    }
                    updateFilteredItems()
                    allItems.find { it.id == itemId }?.let { persistInteraction(it) }
                },
                onFailure = {
                    allItems = allItems.map { item ->
                        if (item.id == itemId) {
                            item.copy(
                                isCollected = !item.isCollected,
                                collectCount = if (item.isCollected) item.collectCount - 1 else item.collectCount + 1,
                                aiTags = item.aiTags ?: emptyList()
                            )
                        } else item
                    }
                    updateFilteredItems()
                    allItems.find { it.id == itemId }?.let { persistInteraction(it) }
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
                item.copy(shareCount = item.shareCount + 1, aiTags = item.aiTags ?: emptyList())
            } else item
        }
        updateFilteredItems()
    }

    fun filterByTag(tag: String) {
        val currentTags = _uiState.value.selectedTags.toMutableList()
        if (currentTags.contains(tag)) {
            currentTags.remove(tag)
        } else {
            currentTags.add(tag)
        }
        _uiState.update { it.copy(selectedTags = currentTags) }
        updateFilteredItems()
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

    fun getStats(): com.ico.nekofeed.ui.stats.StatsData {
        val totalExposure = allItems.sumOf { it.exposureCount }
        val totalClick = allItems.sumOf { it.clickCount }
        val totalLike = allItems.sumOf { it.likeCount }
        val totalCollect = allItems.sumOf { it.collectCount }
        val totalShare = allItems.sumOf { it.shareCount }
        val totalPlay = allItems.sumOf { it.playCount }
        val ctr = if (totalExposure > 0) totalClick.toFloat() / totalExposure else 0f
        val topItems = allItems.sortedByDescending { it.exposureCount }.take(10)

        return com.ico.nekofeed.ui.stats.StatsData(
            totalExposure = totalExposure,
            totalClick = totalClick,
            totalLike = totalLike,
            totalCollect = totalCollect,
            totalShare = totalShare,
            totalPlay = totalPlay,
            ctr = ctr,
            topItems = topItems
        )
    }

    private fun filterByCategory(items: List<FeedItem>, category: FeedCategory): List<FeedItem> {
        return when (category) {
            FeedCategory.FEATURED -> items
            else -> items.filter { item ->
                item.category == category.value ||
                item.itemType == category.value ||
                (category == FeedCategory.VIDEO && item.isVideo) ||
                (category == FeedCategory.SHOPPING && (item.itemType == "product" || item.itemType == "ad"))
            }
        }
    }

    private fun updateFilteredItems() {
        val category = _uiState.value.selectedCategory
        val tags = _uiState.value.selectedTags
        var filtered = filterByCategory(allItems, category)
        if (tags.isNotEmpty()) {
            filtered = filtered.filter { item ->
                tags.any { tag -> item.displayTags.contains(tag) }
            }
        }
        _uiState.update { it.copy(items = filtered) }
    }

    private fun persistInteraction(item: FeedItem) {
        viewModelScope.launch {
            interactionDao.upsertInteraction(
                FeedItemInteractionEntity(
                    itemId = item.id,
                    isLiked = item.isLiked,
                    isCollected = item.isCollected,
                    likeCount = item.likeCount,
                    collectCount = item.collectCount
                )
            )
        }
    }

    private suspend fun mergeLocalInteractions(items: List<FeedItem>): List<FeedItem> {
        val localMap = interactionDao.getAllInteractions().associateBy { it.itemId }
        if (localMap.isEmpty()) return items
        return items.map { item ->
            val local = localMap[item.id]
            if (local != null && !item.isLiked && !item.isCollected && item.likeCount == 0 && item.collectCount == 0) {
                item.copy(
                    isLiked = local.isLiked,
                    isCollected = local.isCollected,
                    likeCount = local.likeCount,
                    collectCount = local.collectCount
                )
            } else if (local != null) {
                interactionDao.upsertInteraction(
                    FeedItemInteractionEntity(
                        itemId = item.id,
                        isLiked = item.isLiked,
                        isCollected = item.isCollected,
                        likeCount = item.likeCount,
                        collectCount = item.collectCount
                    )
                )
                item
            } else item
        }
    }
}
