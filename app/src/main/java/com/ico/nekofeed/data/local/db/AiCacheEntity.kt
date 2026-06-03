package com.ico.nekofeed.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ai_cache")
data class AiCacheEntity(
    @PrimaryKey val itemId: String,
    val aiSummary: String?,
    val aiTags: String,
    val aiReason: String?,
    val modelUsed: String,
    val createdAt: Long = System.currentTimeMillis()
)
