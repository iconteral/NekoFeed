package com.ico.nekofeed.data.remote

import com.ico.nekofeed.data.model.FeedResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface FeedApi {
    @GET("api/feed")
    suspend fun getFeed(
        @Query("category") category: String? = null,
        @Query("item_type") itemType: String? = null,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0,
        @Query("base_url") baseUrl: String = "http://10.0.2.2:8000"
    ): FeedResponse
}
