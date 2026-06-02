package com.ico.nekofeed.util

import com.ico.nekofeed.data.model.FeedCategory
import com.ico.nekofeed.data.model.FeedItem

data class FeedUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val items: List<FeedItem> = emptyList(),
    val selectedCategory: FeedCategory = FeedCategory.FEATURED,
    val selectedTags: List<String> = emptyList(),
    val playingItemId: String? = null,
    val errorMessage: String? = null,
    val usingFallback: Boolean = false
)

data class FeedDetailUiState(
    val item: FeedItem? = null,
    val isLoading: Boolean = false,
    val isVideoPlaying: Boolean = false,
    val errorMessage: String? = null
)

data class SearchUiState(
    val query: String = "",
    val parsedKeywords: List<String> = emptyList(),
    val matchedTags: List<String> = emptyList(),
    val results: List<FeedItem> = emptyList(),
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
    val errorMessage: String? = null
)

data class StatsUiState(
    val totalExposure: Int = 0,
    val totalClick: Int = 0,
    val totalLike: Int = 0,
    val totalCollect: Int = 0,
    val totalShare: Int = 0,
    val totalPlay: Int = 0,
    val ctr: Float = 0f,
    val topItems: List<FeedItem> = emptyList()
)
