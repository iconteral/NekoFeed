package com.ico.nekofeed.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ico.nekofeed.data.model.FeedCategory
import com.ico.nekofeed.data.model.FeedItem
import com.ico.nekofeed.data.remote.RetrofitClient
import com.ico.nekofeed.data.repository.FeedRepository
import com.ico.nekofeed.util.FeedUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FeedViewModel : ViewModel() {
    private val repository = FeedRepository(RetrofitClient.feedApi)

    private val _uiState = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    // 缓存所有数据用于频道过滤
    private var allItems: List<FeedItem> = emptyList()

    init {
        loadFeed()
    }

    fun loadFeed() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, usingFallback = false) }

            repository.loadFeed().fold(
                onSuccess = { items ->
                    allItems = items
                    val filteredItems = filterByCategory(items, _uiState.value.selectedCategory)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            items = filteredItems,
                            errorMessage = null,
                            usingFallback = false
                        )
                    }
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
                            usingFallback = true
                        )
                    }
                }
            )
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }

            repository.loadFeed().fold(
                onSuccess = { items ->
                    allItems = items
                    val filteredItems = filterByCategory(items, _uiState.value.selectedCategory)
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            items = filteredItems,
                            errorMessage = null,
                            usingFallback = false
                        )
                    }
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
                            usingFallback = true
                        )
                    }
                }
            )
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
        allItems = allItems.map { item ->
            if (item.id == itemId) {
                item.copy(
                    isLiked = !item.isLiked,
                    likeCount = if (item.isLiked) item.likeCount - 1 else item.likeCount + 1
                )
            } else item
        }
        updateFilteredItems()
    }

    fun toggleCollect(itemId: String) {
        allItems = allItems.map { item ->
            if (item.id == itemId) {
                item.copy(
                    isCollected = !item.isCollected,
                    collectCount = if (item.isCollected) item.collectCount - 1 else item.collectCount + 1
                )
            } else item
        }
        updateFilteredItems()
    }

    fun toggleShare(itemId: String) {
        allItems = allItems.map { item ->
            if (item.id == itemId) {
                item.copy(shareCount = item.shareCount + 1)
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
}
