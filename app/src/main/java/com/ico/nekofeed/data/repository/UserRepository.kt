package com.ico.nekofeed.data.repository

import com.ico.nekofeed.data.model.FeedItem
import com.ico.nekofeed.data.model.ItemInteraction
import com.ico.nekofeed.data.model.UserStats
import com.ico.nekofeed.data.remote.FeedApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UserRepository(private val feedApi: FeedApi) {

    suspend fun getUserStats(): Result<UserStats> {
        return withContext(Dispatchers.IO) {
            try {
                val stats = feedApi.getUserStats()
                Result.success(stats)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun toggleLike(itemId: String): Result<ItemInteraction> {
        return withContext(Dispatchers.IO) {
            try {
                val interaction = feedApi.toggleLike(itemId)
                Result.success(interaction)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun toggleCollect(itemId: String): Result<ItemInteraction> {
        return withContext(Dispatchers.IO) {
            try {
                val interaction = feedApi.toggleCollect(itemId)
                Result.success(interaction)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun recordHistory(itemId: String, duration: Int = 0): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val response = feedApi.recordHistory(itemId, duration)
                Result.success(response["message"] ?: "History recorded")
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getItemInteraction(itemId: String): Result<ItemInteraction> {
        return withContext(Dispatchers.IO) {
            try {
                val interaction = feedApi.getItemInteraction(itemId)
                Result.success(interaction)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getUserLikes(limit: Int = 20, offset: Int = 0): Result<List<FeedItem>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = feedApi.getUserLikes(limit, offset)
                Result.success(response.items)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getUserCollections(limit: Int = 20, offset: Int = 0): Result<List<FeedItem>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = feedApi.getUserCollections(limit, offset)
                Result.success(response.items)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getUserHistory(limit: Int = 20, offset: Int = 0): Result<List<FeedItem>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = feedApi.getUserHistory(limit, offset)
                Result.success(response.items)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun clearUserHistory(): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val response = feedApi.clearUserHistory()
                Result.success(response["message"] ?: "History cleared")
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
