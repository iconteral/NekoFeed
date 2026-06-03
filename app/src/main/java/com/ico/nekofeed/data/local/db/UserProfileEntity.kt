package com.ico.nekofeed.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val tag: String,
    val interestScore: Float,
    val lastUpdated: Long = System.currentTimeMillis()
)
