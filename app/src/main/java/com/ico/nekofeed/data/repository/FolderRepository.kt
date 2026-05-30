package com.ico.nekofeed.data.repository

import com.ico.nekofeed.data.local.FallbackFeedData
import com.ico.nekofeed.data.model.FeedItem
import com.ico.nekofeed.data.remote.FeedApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FeedRepository(private val feedApi: FeedApi) {
    private val cachedItems = mutableListOf<FeedItem>()

    suspend fun loadFeed(
        category: String? = null,
        itemType: String? = null,
        limit: Int = 20,
        offset: Int = 0
    ): Result<List<FeedItem>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = feedApi.getFeed(
                    category = category,
                    itemType = itemType,
                    limit = limit,
                    offset = offset
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
