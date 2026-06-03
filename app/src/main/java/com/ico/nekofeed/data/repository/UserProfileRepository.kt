package com.ico.nekofeed.data.repository

import com.ico.nekofeed.data.local.db.UserProfileDao
import com.ico.nekofeed.data.local.db.UserProfileEntity
import com.ico.nekofeed.data.model.FeedItem

enum class InteractionType(val score: Float) {
    CLICK(1f),
    LIKE(3f),
    COLLECT(4f),
    SHARE(2f)
}

class UserProfileRepository(
    private val userProfileDao: UserProfileDao
) {
    companion object {
        private const val DECAY_FACTOR = 0.99f
    }

    suspend fun recordInteraction(item: FeedItem, action: InteractionType) {
        userProfileDao.decayAllScores(DECAY_FACTOR)

        val tags = item.displayTags
        for (tag in tags) {
            val existing = userProfileDao.getTag(tag)
            val currentScore = existing?.interestScore ?: 0f
            userProfileDao.upsertTag(
                UserProfileEntity(
                    tag = tag,
                    interestScore = currentScore + action.score,
                    lastUpdated = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun getTopInterestTags(limit: Int = 5): List<String> {
        return userProfileDao.getTopTags(limit).map { it.tag }
    }
}
