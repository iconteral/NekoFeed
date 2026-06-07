package com.ico.nekofeed.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedAnalyticsDao {
    @Insert
    suspend fun insertEvent(event: AnalyticsEventEntity)

    @Insert
    suspend fun insertEvents(events: List<AnalyticsEventEntity>)

    @Query(
        """
        SELECT * FROM analytics_events
        WHERE environment = :environment AND timestamp >= :since
        ORDER BY timestamp DESC
        """
    )
    fun observeEventsSince(
        since: Long,
        environment: String
    ): Flow<List<AnalyticsEventEntity>>

    @Query("SELECT COUNT(*) FROM analytics_events WHERE sessionId = :sessionId")
    suspend fun countEventsForSession(sessionId: String): Int

    @Query("SELECT * FROM feed_analytics")
    fun observeAll(): Flow<List<FeedAnalyticsEntity>>

    @Query("SELECT * FROM feed_analytics")
    suspend fun getAll(): List<FeedAnalyticsEntity>

    @Query("SELECT * FROM feed_analytics WHERE itemId = :itemId")
    suspend fun get(itemId: String): FeedAnalyticsEntity?

    @Upsert
    suspend fun upsert(entity: FeedAnalyticsEntity)

    @Query(
        """
        INSERT INTO feed_analytics(itemId, exposureCount, clickCount, shareCount, playCount, updatedAt)
        VALUES(:itemId, 1, 0, 0, 0, :updatedAt)
        ON CONFLICT(itemId) DO UPDATE SET
            exposureCount = exposureCount + 1,
            updatedAt = :updatedAt
        """
    )
    suspend fun incrementExposure(itemId: String, updatedAt: Long = System.currentTimeMillis())

    @Query(
        """
        INSERT INTO feed_analytics(itemId, exposureCount, clickCount, shareCount, playCount, updatedAt)
        VALUES(:itemId, 0, 1, 0, 0, :updatedAt)
        ON CONFLICT(itemId) DO UPDATE SET
            clickCount = clickCount + 1,
            updatedAt = :updatedAt
        """
    )
    suspend fun incrementClick(itemId: String, updatedAt: Long = System.currentTimeMillis())

    @Query(
        """
        INSERT INTO feed_analytics(itemId, exposureCount, clickCount, shareCount, playCount, updatedAt)
        VALUES(:itemId, 0, 0, 1, 0, :updatedAt)
        ON CONFLICT(itemId) DO UPDATE SET
            shareCount = shareCount + 1,
            updatedAt = :updatedAt
        """
    )
    suspend fun incrementShare(itemId: String, updatedAt: Long = System.currentTimeMillis())

    @Query(
        """
        INSERT INTO feed_analytics(itemId, exposureCount, clickCount, shareCount, playCount, updatedAt)
        VALUES(:itemId, 0, 0, 0, 1, :updatedAt)
        ON CONFLICT(itemId) DO UPDATE SET
            playCount = playCount + 1,
            updatedAt = :updatedAt
        """
    )
    suspend fun incrementPlay(itemId: String, updatedAt: Long = System.currentTimeMillis())
}
