package com.ico.nekofeed.data.local

import com.ico.nekofeed.data.local.db.AnalyticsEventEntity
import com.ico.nekofeed.data.local.db.FeedAnalyticsDao
import com.ico.nekofeed.data.model.FeedItem
import com.ico.nekofeed.ui.stats.AnalyticsEventType
import kotlin.random.Random

object MockAnalyticsSeeder {
    const val SESSION_ID = "mock_seed_v1"
    private const val THIRTY_DAYS_MILLIS = 30L * 24 * 60 * 60 * 1000

    suspend fun seedIfNeeded(
        analyticsDao: FeedAnalyticsDao,
        items: List<FeedItem>,
        now: Long = System.currentTimeMillis()
    ) {
        if (items.isEmpty() || analyticsDao.countEventsForSession(SESSION_ID) > 0) return

        val random = Random(20260607)
        val events = buildList {
            items.forEachIndexed { index, item ->
                val exposureCount = 70 + index * 11 + random.nextInt(35)
                val clickCount = (exposureCount * (0.08 + random.nextDouble() * 0.08)).toInt()
                val playCount = if (item.isVideo) 18 + random.nextInt(28) else 0
                val likeCount = 3 + random.nextInt(10)
                val collectCount = 2 + random.nextInt(7)
                val shareCount = 1 + random.nextInt(4)

                addEvents(item, AnalyticsEventType.EXPOSURE, exposureCount, now, random)
                addEvents(item, AnalyticsEventType.CLICK, clickCount, now, random)
                addEvents(item, AnalyticsEventType.PLAY, playCount, now, random)
                addEvents(item, AnalyticsEventType.LIKE, likeCount, now, random)
                addEvents(item, AnalyticsEventType.COLLECT, collectCount, now, random)
                addEvents(item, AnalyticsEventType.SHARE, shareCount, now, random)
            }
        }
        analyticsDao.insertEvents(events)
    }

    private fun MutableList<AnalyticsEventEntity>.addEvents(
        item: FeedItem,
        eventType: String,
        count: Int,
        now: Long,
        random: Random
    ) {
        repeat(count) {
            add(
                AnalyticsEventEntity(
                    itemId = item.id,
                    eventType = eventType,
                    timestamp = now - random.nextLong(THIRTY_DAYS_MILLIS),
                    sessionId = SESSION_ID,
                    environment = AnalyticsEnvironment.MOCK,
                    title = item.title,
                    imageUrl = item.imageUrl,
                    category = item.category,
                    itemType = item.itemType
                )
            )
        }
    }
}

object AnalyticsEnvironment {
    const val MOCK = "mock"
    const val LIVE = "live"
}
