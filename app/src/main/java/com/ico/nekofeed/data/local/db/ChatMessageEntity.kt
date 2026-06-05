package com.ico.nekofeed.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String,
    val content: String,
    val recommendedIds: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
