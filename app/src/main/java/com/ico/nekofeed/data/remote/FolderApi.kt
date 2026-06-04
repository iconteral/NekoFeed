package com.ico.nekofeed.data.remote

import com.ico.nekofeed.data.model.FeedResponse
import com.ico.nekofeed.data.model.ItemInteraction
import com.ico.nekofeed.data.model.TokenResponse
import com.ico.nekofeed.data.model.User
import com.ico.nekofeed.data.model.UserInteractionResponse
import com.ico.nekofeed.data.model.UserStats
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface FeedApi {
    @GET("api/feed")
    suspend fun getFeed(
        @Query("category") category: String? = null,
        @Query("item_type") itemType: String? = null,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0,
        @Query("base_url") baseUrl: String? = null
    ): FeedResponse

    @POST("api/auth/register")
    suspend fun register(@Body body: Map<String, String>): TokenResponse

    @POST("api/auth/login")
    suspend fun login(@Body body: Map<String, String>): TokenResponse

    @GET("api/auth/me")
    suspend fun getMe(): User

    @PUT("api/auth/me")
    suspend fun updateMe(@Body body: Map<String, String>): User

    @POST("api/auth/change-password")
    suspend fun changePassword(@Body body: Map<String, String>): Map<String, String>

    @GET("api/auth/stats")
    suspend fun getUserStats(): UserStats

    @POST("api/items/{itemId}/like")
    suspend fun toggleLike(@Path("itemId") itemId: String): ItemInteraction

    @POST("api/items/{itemId}/collect")
    suspend fun toggleCollect(@Path("itemId") itemId: String): ItemInteraction

    @POST("api/items/{itemId}/history")
    suspend fun recordHistory(
        @Path("itemId") itemId: String,
        @Query("duration") duration: Int = 0
    ): Map<String, String>

    @GET("api/items/{itemId}/interaction")
    suspend fun getItemInteraction(@Path("itemId") itemId: String): ItemInteraction

    @GET("api/user/likes")
    suspend fun getUserLikes(
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): UserInteractionResponse

    @GET("api/user/collections")
    suspend fun getUserCollections(
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): UserInteractionResponse

    @GET("api/user/history")
    suspend fun getUserHistory(
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): UserInteractionResponse

    @DELETE("api/user/history")
    suspend fun clearUserHistory(): Map<String, String>
}
