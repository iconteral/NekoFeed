package com.ico.nekofeed.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "feed_item_interaction")
data class FeedItemInteractionEntity(
    @PrimaryKey val itemId: String,
    val isLiked: Boolean = false,
    val isCollected: Boolean = false,
    val likeCount: Int = 0,
    val collectCount: Int = 0,
    val lastViewedAt: Long? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
