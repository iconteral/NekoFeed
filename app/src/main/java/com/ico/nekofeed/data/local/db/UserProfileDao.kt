package com.ico.nekofeed.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile ORDER BY interestScore DESC LIMIT :limit")
    suspend fun getTopTags(limit: Int): List<UserProfileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTag(entity: UserProfileEntity)

    @Query("UPDATE user_profile SET interestScore = interestScore * :factor")
    suspend fun decayAllScores(factor: Float)

    @Query("SELECT * FROM user_profile WHERE tag = :tag")
    suspend fun getTag(tag: String): UserProfileEntity?
}
