package com.ico.nekofeed.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AiCacheDao {
    @Query("SELECT * FROM ai_cache WHERE itemId = :itemId")
    suspend fun getCache(itemId: String): AiCacheEntity?

    @Query("SELECT * FROM ai_cache WHERE itemId IN (:itemIds)")
    suspend fun getCaches(itemIds: List<String>): List<AiCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCache(entity: AiCacheEntity)

    @Query("DELETE FROM ai_cache WHERE createdAt < :beforeTime")
    suspend fun deleteOldCache(beforeTime: Long)

    @Query("SELECT COUNT(*) FROM ai_cache")
    suspend fun getCacheCount(): Int

    @Query("DELETE FROM ai_cache")
    suspend fun clearAll()

    @Query("DELETE FROM ai_cache WHERE itemId = :itemId")
    suspend fun deleteCache(itemId: String)
}
