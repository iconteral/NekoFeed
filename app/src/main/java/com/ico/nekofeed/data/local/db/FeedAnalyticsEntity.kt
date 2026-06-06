package com.ico.nekofeed.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "feed_analytics")
data class FeedAnalyticsEntity(
    @PrimaryKey val itemId: String,
    val exposureCount: Int = 0,
    val clickCount: Int = 0,
    val shareCount: Int = 0,
    val playCount: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
)
