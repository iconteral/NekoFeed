package com.ico.nekofeed.data.model

data class FeedResponse(
    val items: List<FeedItem>,
    val limit: Int,
    val offset: Int,
    val total: Int
)
