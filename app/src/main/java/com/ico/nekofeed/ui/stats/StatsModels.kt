package com.ico.nekofeed.ui.stats

import com.ico.nekofeed.data.local.db.AnalyticsEventEntity

enum class StatsRange(val label: String, val durationMillis: Long) {
    DAY("24小时", 24L * 60 * 60 * 1000),
    WEEK("7天", 7L * 24 * 60 * 60 * 1000),
    MONTH("30天", 30L * 24 * 60 * 60 * 1000)
}

enum class RankingMetric(val label: String) {
    EXPOSURE("曝光"),
    CLICK("点击"),
    PLAY("播放")
}

data class StatsRankingItem(
    val itemId: String,
    val title: String,
    val imageUrl: String?,
    val exposureCount: Int,
    val clickCount: Int,
    val playCount: Int,
    val likeCount: Int,
    val collectCount: Int,
    val shareCount: Int
) {
    fun valueFor(metric: RankingMetric): Int = when (metric) {
        RankingMetric.EXPOSURE -> exposureCount
        RankingMetric.CLICK -> clickCount
        RankingMetric.PLAY -> playCount
    }
}

data class StatsData(
    val totalExposure: Int = 0,
    val totalClick: Int = 0,
    val totalLike: Int = 0,
    val totalCollect: Int = 0,
    val totalShare: Int = 0,
    val totalPlay: Int = 0,
    val uniqueContent: Int = 0,
    val ctr: Double = 0.0,
    val items: List<StatsRankingItem> = emptyList()
)

internal fun aggregateStats(events: List<AnalyticsEventEntity>): StatsData {
    val grouped = events.groupBy { it.itemId }
    val items = grouped.map { (itemId, itemEvents) ->
        val latest = itemEvents.maxBy { it.timestamp }
        StatsRankingItem(
            itemId = itemId,
            title = latest.title,
            imageUrl = latest.imageUrl,
            exposureCount = itemEvents.count { it.eventType == AnalyticsEventType.EXPOSURE },
            clickCount = itemEvents.count { it.eventType == AnalyticsEventType.CLICK },
            playCount = itemEvents.count { it.eventType == AnalyticsEventType.PLAY },
            likeCount = itemEvents.count { it.eventType == AnalyticsEventType.LIKE },
            collectCount = itemEvents.count { it.eventType == AnalyticsEventType.COLLECT },
            shareCount = itemEvents.count { it.eventType == AnalyticsEventType.SHARE }
        )
    }
    val exposure = items.sumOf { it.exposureCount }
    val click = items.sumOf { it.clickCount }
    return StatsData(
        totalExposure = exposure,
        totalClick = click,
        totalLike = items.sumOf { it.likeCount },
        totalCollect = items.sumOf { it.collectCount },
        totalShare = items.sumOf { it.shareCount },
        totalPlay = items.sumOf { it.playCount },
        uniqueContent = items.size,
        ctr = if (exposure == 0) 0.0 else click.toDouble() / exposure,
        items = items
    )
}

object AnalyticsEventType {
    const val EXPOSURE = "exposure"
    const val CLICK = "click"
    const val PLAY = "play"
    const val LIKE = "like"
    const val COLLECT = "collect"
    const val SHARE = "share"
}
