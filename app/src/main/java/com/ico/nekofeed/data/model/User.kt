package com.ico.nekofeed.data.model

import com.google.gson.annotations.SerializedName

// ============================================================================
// 【数据层 · 用户相关数据模型】
// ============================================================================
//
// 📌 这个文件定义了用户系统的所有数据类。
//    每个 data class 对应服务端返回的一种 JSON 结构。
//
// 📌 设计原则：
//    - 所有字段都有默认值（= false, = 0, = null），服务端不返回时不会崩溃
//    - @SerializedName 映射 JSON 的 snake_case 到 Kotlin 的 camelCase
//    - 这些类只负责"装数据"，不包含业务逻辑（纯 DTO = Data Transfer Object）
// ============================================================================

/**
 * 用户信息
 *
 * 对应服务端 GET /api/auth/me 的响应。
 * 注册/登录后，用 Token 请求这个接口获取用户详情。
 */
data class User(
    val id: Int,
    val username: String,
    val avatar: String? = null,     // 头像 URL
    val bio: String? = null,        // 个人简介
    val level: String = "Normal",   // 用户等级
    @SerializedName("is_active")
    val isActive: Boolean = true,   // 账号是否激活
    @SerializedName("created_at")
    val createdAt: String? = null   // 注册时间
)

/**
 * 登录/注册的 Token 响应
 *
 * 对应服务端 POST /api/auth/register 或 /api/auth/login 的响应。
 * access_token 是 JWT Token，后续所有需要认证的请求都要带上它。
 */
data class TokenResponse(
    @SerializedName("access_token")
    val accessToken: String,        // JWT Token 字符串
    @SerializedName("token_type")
    val tokenType: String = "bearer" // Token 类型（固定为 "bearer"）
)

/**
 * 用户统计信息
 *
 * 对应服务端 GET /api/auth/stats 的响应。
 * 展示在个人中心页面。
 */
data class UserStats(
    @SerializedName("likes_count")
    val likesCount: Int = 0,         // 累计点赞数
    @SerializedName("collections_count")
    val collectionsCount: Int = 0,   // 累计收藏数
    @SerializedName("history_count")
    val historyCount: Int = 0        // 累计浏览数
)

/**
 * 条目互动状态
 *
 * 这是最常用的模型之一！点赞/收藏操作后，服务端返回这个对象。
 * 它包含"当前用户是否已点赞/收藏"和"最新的计数"。
 *
 * 🔑 设计要点：
 *    - 服务端返回权威数据，客户端不做 +1/-1 的本地猜测
 *    - 这样可以避免多设备并发导致的计数不一致
 */
data class ItemInteraction(
    @SerializedName("is_liked")
    val isLiked: Boolean = false,    // 当前用户是否已点赞
    @SerializedName("is_collected")
    val isCollected: Boolean = false,// 当前用户是否已收藏
    @SerializedName("like_count")
    val likeCount: Int = 0,          // 最新点赞总数
    @SerializedName("collect_count")
    val collectCount: Int = 0        // 最新收藏总数
)

/**
 * 用户互动列表响应（点赞列表/收藏列表/浏览历史）
 *
 * 包含 FeedItem 列表 + 分页信息，用于个人中心的子页面。
 */
data class UserInteractionResponse(
    val items: List<FeedItem>,  // 内容列表
    val total: Int,             // 总条数
    val limit: Int,             // 每页条数
    val offset: Int             // 当前偏移量
)
