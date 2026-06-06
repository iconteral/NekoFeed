package com.ico.nekofeed.util

import androidx.compose.runtime.Immutable
import com.ico.nekofeed.data.model.FeedCategory
import com.ico.nekofeed.data.model.FeedItem

@Immutable
data class FeedUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val items: List<FeedItem> = emptyList(),
    val selectedCategory: FeedCategory = FeedCategory.FEATURED,
    val selectedTags: List<String> = emptyList(),
    val errorMessage: String? = null,
    val usingFallback: Boolean = false,
    val isAiLoading: Boolean = false,
    val isAiEnabled: Boolean = true
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

data class ChatUiState(
    val messages: List<ChatBubble> = emptyList(),
    val isAiTyping: Boolean = false,
    val errorMessage: String? = null
)

data class ChatBubble(
    val id: Long = 0,
    val role: String,
    val content: String,
    val recommendedItems: List<FeedItem> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)
