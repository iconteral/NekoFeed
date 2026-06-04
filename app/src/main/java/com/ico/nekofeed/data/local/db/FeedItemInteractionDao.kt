package com.ico.nekofeed.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FeedItemInteractionDao {
    @Query("SELECT * FROM feed_item_interaction WHERE itemId = :itemId")
    suspend fun getInteraction(itemId: String): FeedItemInteractionEntity?

    @Query("SELECT * FROM feed_item_interaction")
    suspend fun getAllInteractions(): List<FeedItemInteractionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInteraction(entity: FeedItemInteractionEntity)

    @Query("DELETE FROM feed_item_interaction")
    suspend fun clearAll()
}
