package com.ico.nekofeed.data.model

import com.google.gson.annotations.SerializedName

data class User(
    val id: Int,
    val username: String,
    val avatar: String? = null,
    val bio: String? = null,
    val level: String = "Normal",
    @SerializedName("is_active")
    val isActive: Boolean = true,
    @SerializedName("created_at")
    val createdAt: String? = null
)

data class TokenResponse(
    @SerializedName("access_token")
    val accessToken: String,
    @SerializedName("token_type")
    val tokenType: String = "bearer"
)

data class UserStats(
    @SerializedName("likes_count")
    val likesCount: Int = 0,
    @SerializedName("collections_count")
    val collectionsCount: Int = 0,
    @SerializedName("history_count")
    val historyCount: Int = 0
)

data class ItemInteraction(
    @SerializedName("is_liked")
    val isLiked: Boolean = false,
    @SerializedName("is_collected")
    val isCollected: Boolean = false,
    @SerializedName("like_count")
    val likeCount: Int = 0,
    @SerializedName("collect_count")
    val collectCount: Int = 0
)

data class UserInteractionResponse(
    val items: List<FeedItem>,
    val total: Int,
    val limit: Int,
    val offset: Int
)
