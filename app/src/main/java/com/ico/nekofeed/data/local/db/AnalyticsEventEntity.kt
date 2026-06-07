package com.ico.nekofeed.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "analytics_events",
    indices = [
        Index(value = ["environment", "timestamp"]),
        Index(value = ["itemId", "eventType", "sessionId"])
    ]
)
data class AnalyticsEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: String,
    val eventType: String,
    val timestamp: Long,
    val sessionId: String,
    @ColumnInfo(defaultValue = "'unknown'")
    val environment: String = "unknown",
    val title: String,
    val imageUrl: String?,
    val category: String?,
    val itemType: String?
)
