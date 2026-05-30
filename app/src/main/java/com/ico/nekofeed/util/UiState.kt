package com.ico.nekofeed.util

import com.ico.nekofeed.data.model.FeedItem

data class FeedUiState(
    val isLoading: Boolean = false,
    val items: List<FeedItem> = emptyList(),
    val errorMessage: String? = null,
    val usingFallback: Boolean = false
)

data class FeedDetailUiState(
    val item: FeedItem? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
