package com.ico.nekofeed.ui.stats

import com.ico.nekofeed.data.local.db.AnalyticsEventEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class StatsModelsTest {
    @Test
    fun aggregateStats_countsLocalEventsAndCalculatesCtr() {
        val events = listOf(
            event("a", AnalyticsEventType.EXPOSURE, 1),
            event("a", AnalyticsEventType.EXPOSURE, 2),
            event("a", AnalyticsEventType.CLICK, 3),
            event("a", AnalyticsEventType.PLAY, 4),
            event("a", AnalyticsEventType.LIKE, 5),
            event("b", AnalyticsEventType.EXPOSURE, 6),
            event("b", AnalyticsEventType.COLLECT, 7),
            event("b", AnalyticsEventType.SHARE, 8)
        )

        val stats = aggregateStats(events)

        assertEquals(3, stats.totalExposure)
        assertEquals(1, stats.totalClick)
        assertEquals(1, stats.totalPlay)
        assertEquals(1, stats.totalLike)
        assertEquals(1, stats.totalCollect)
        assertEquals(1, stats.totalShare)
        assertEquals(2, stats.uniqueContent)
        assertEquals(1.0 / 3.0, stats.ctr, 0.0001)
        assertEquals(2, stats.items.first { it.itemId == "a" }.exposureCount)
    }

    @Test
    fun rankingValue_usesSelectedMetric() {
        val item = StatsRankingItem(
            itemId = "a",
            title = "A",
            imageUrl = null,
            exposureCount = 8,
            clickCount = 3,
            playCount = 2,
            likeCount = 1,
            collectCount = 0,
            shareCount = 0
        )

        assertEquals(8, item.valueFor(RankingMetric.EXPOSURE))
        assertEquals(3, item.valueFor(RankingMetric.CLICK))
        assertEquals(2, item.valueFor(RankingMetric.PLAY))
    }

    private fun event(itemId: String, type: String, timestamp: Long) =
        AnalyticsEventEntity(
            itemId = itemId,
            eventType = type,
            timestamp = timestamp,
            sessionId = "session",
            environment = "mock",
            title = "Item $itemId",
            imageUrl = null,
            category = "tech",
            itemType = "article"
        )
}
