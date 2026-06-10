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

// ============================================================================
// 【网络层 · Retrofit API 接口定义】
// ============================================================================
//
// 📌 Retrofit 是 Android 最流行的 HTTP 客户端库。
//    你只需要定义一个 interface，Retrofit 会自动生成实现类。
//
// 📌 核心知识点：
//    1. interface + 注解 → Retrofit 用注解描述 HTTP 请求，不需要手写网络代码
//    2. suspend fun      → 每个方法都是挂起函数，可以在协程中直接调用
//    3. @GET / @POST     → HTTP 方法注解，括号里是相对路径
//    4. @Query           → URL 查询参数，如 ?category=tech&limit=20
//    5. @Path            → 路径参数，如 /api/items/{itemId} → /api/items/123
//    6. @Body            → 请求体（POST/PUT），自动序列化为 JSON
//
// 📌 使用方式（在 Repository 中）：
//    val response = feedApi.getFeed(category = "tech", limit = 20, offset = 0)
//    Retrofit 会自动发起 GET http://server/api/feed?category=tech&limit=20&offset=0
// ============================================================================

/**
 * FeedApi —— 项目的核心网络接口
 *
 * 定义了所有与后端交互的 HTTP 端点（Endpoint）。
 * Retrofit 在运行时会为这个 interface 生成代理实现。
 *
 * 🔑 方法命名约定：
 *    - getXxx   → GET 请求（获取数据）
 *    - toggleXxx → POST 请求（切换状态，如点赞/取消点赞）
 *    - recordXxx → POST 请求（记录行为，如浏览历史）
 *    - updateXxx → PUT 请求（更新数据）
 *    - clearXxx  → DELETE 请求（删除数据）
 */
interface FeedApi {

    // ── Feed 相关 ──────────────────────────────────────────────────

    /**
     * 获取信息流数据
     *
     * @param category 分类筛选（null = 全部，"tech" = 科技，等）
     * @param itemType 类型筛选（"article"、"video"、"ad" 等）
     * @param limit    每页条数（默认 20）
     * @param offset   偏移量（分页用，第一页 = 0，第二页 = 20）
     * @param baseUrl  服务器地址（用于多源聚合）
     * @return FeedResponse 包含 items 列表和分页信息
     */
    @GET("api/feed")
    suspend fun getFeed(
        @Query("category") category: String? = null,
        @Query("item_type") itemType: String? = null,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0,
        @Query("base_url") baseUrl: String? = null
    ): FeedResponse

    // ── 认证相关 ──────────────────────────────────────────────────

    /**
     * 注册新用户
     * @Body body: {"username": "xxx", "password": "xxx"}
     * @return TokenResponse 包含 access_token
     */
    @POST("api/auth/register")
    suspend fun register(@Body body: Map<String, String>): TokenResponse

    /**
     * 用户登录
     * @Body body: {"username": "xxx", "password": "xxx"}
     */
    @POST("api/auth/login")
    suspend fun login(@Body body: Map<String, String>): TokenResponse

    /** 获取当前用户信息（需要 Bearer Token） */
    @GET("api/auth/me")
    suspend fun getMe(): User

    /** 更新用户资料（头像、简介等） */
    @PUT("api/auth/me")
    suspend fun updateMe(@Body body: Map<String, String>): User

    /** 修改密码 */
    @POST("api/auth/change-password")
    suspend fun changePassword(@Body body: Map<String, String>): Map<String, String>

    /** 获取用户统计（点赞数、收藏数、浏览数） */
    @GET("api/auth/stats")
    suspend fun getUserStats(): UserStats

    // ── 互动相关 ──────────────────────────────────────────────────

    /**
     * 切换点赞状态（已赞→取消，未赞→点赞）
     * 这是"Toggle"模式——服务端自动切换，客户端不用关心当前状态
     * @return ItemInteraction 包含最新的 isLiked 和 likeCount
     */
    @POST("api/items/{itemId}/like")
    suspend fun toggleLike(@Path("itemId") itemId: String): ItemInteraction

    /** 切换收藏状态 */
    @POST("api/items/{itemId}/collect")
    suspend fun toggleCollect(@Path("itemId") itemId: String): ItemInteraction

    /** 记录浏览历史 */
    @POST("api/items/{itemId}/history")
    suspend fun recordHistory(
        @Path("itemId") itemId: String,
        @Query("duration") duration: Int = 0
    ): Map<String, String>

    /** 获取单条内容的互动状态 */
    @GET("api/items/{itemId}/interaction")
    suspend fun getItemInteraction(@Path("itemId") itemId: String): ItemInteraction

    // ── 用户列表相关 ──────────────────────────────────────────────

    /** 获取用户点赞列表 */
    @GET("api/user/likes")
    suspend fun getUserLikes(
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): UserInteractionResponse

    /** 获取用户收藏列表 */
    @GET("api/user/collections")
    suspend fun getUserCollections(
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): UserInteractionResponse

    /** 获取用户浏览历史 */
    @GET("api/user/history")
    suspend fun getUserHistory(
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): UserInteractionResponse

    /** 清空浏览历史 */
    @DELETE("api/user/history")
    suspend fun clearUserHistory(): Map<String, String>
}
