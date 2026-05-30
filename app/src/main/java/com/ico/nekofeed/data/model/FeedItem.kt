package com.ico.nekofeed.data.model

import com.google.gson.annotations.SerializedName

data class FeedItem(
    val id: String,
    val title: String,
    val summary: String?,
    val content: String?,
    @SerializedName("source_name")
    val sourceName: String?,
    @SerializedName("source_url")
    val sourceUrl: String?,
    val category: String?,
    @SerializedName("item_type")
    val itemType: String?,
    @SerializedName("card_type")
    val cardType: String?,
    @SerializedName("image_url")
    val imageUrl: String?,
    @SerializedName("media_url")
    val mediaUrl: String?,
    val tags: List<String> = emptyList(),
    @SerializedName("published_at")
    val publishedAt: String?
)
