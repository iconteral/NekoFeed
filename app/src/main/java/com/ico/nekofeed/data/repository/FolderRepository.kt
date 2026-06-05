package com.ico.nekofeed.data.repository

import com.ico.nekofeed.data.local.FallbackFeedData
import com.ico.nekofeed.data.local.TokenManager
import com.ico.nekofeed.data.model.FeedItem
import com.ico.nekofeed.data.remote.FeedApi
import com.ico.nekofeed.data.remote.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class FeedRepository(
    private val feedApi: FeedApi,
    private val tokenManager: TokenManager? = null
) {
    private val cachedItems = mutableListOf<FeedItem>()

    suspend fun loadFeed(
        category: String? = null,
        itemType: String? = null,
        limit: Int = 20,
        offset: Int = 0
    ): Result<List<FeedItem>> {
        return withContext(Dispatchers.IO) {
            val isMockMode = tokenManager?.isMockMode() ?: false

            if (isMockMode) {
                val items = FallbackFeedData.items
                if (offset == 0) {
                    cachedItems.clear()
                }
                cachedItems.addAll(items)
                return@withContext Result.success(items)
            }

            try {
                val response = feedApi.getFeed(
                    category = category,
                    itemType = itemType,
                    limit = limit,
                    offset = offset,
                    baseUrl = RetrofitClient.getBaseUrl()
                )
                val items = response.items
                if (offset == 0) {
                    cachedItems.clear()
                }
                cachedItems.addAll(items)
                Result.success(items)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    fun getCachedItemById(id: String): FeedItem? {
        return cachedItems.find { it.id == id }
    }

    fun getFallbackData(): List<FeedItem> {
        return FallbackFeedData.items
    }
}
