package com.ico.nekofeed.data.repository

import com.ico.nekofeed.data.local.FallbackFeedData
import com.ico.nekofeed.data.local.TokenManager
import com.ico.nekofeed.data.model.FeedItem
import com.ico.nekofeed.data.remote.FeedApi
import com.ico.nekofeed.data.remote.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

// ============================================================================
// 【数据层 · Repository 模式】
// ============================================================================
//
// 📌 Repository 是 MVVM 架构中连接"网络/数据库"和"ViewModel"的中间层。
//    ViewModel 不直接调用 Retrofit 或 Room，而是通过 Repository 获取数据。
//
// 📌 这样做的好处：
//    1. ViewModel 不关心数据来自网络还是本地缓存
//    2. 可以轻松切换 Mock 数据（开发/演示用）
//    3. 网络失败时自动降级到本地缓存
//    4. 方便单元测试（可以 Mock Repository）
//
// 📌 本项目的数据获取策略（优先级从高到低）：
//    1. Mock 模式 → 直接返回 FallbackFeedData（硬编码数据）
//    2. 网络请求 → 成功则返回并缓存
//    3. 网络失败 → 返回 DataStore 中的 JSON 缓存
//    4. 缓存也没有 → 返回 Result.failure（错误）
//
// 📌 构造函数知识点：
//    - feedApiProvider: () -> FeedApi → Lambda 类型参数（延迟获取）
//    - 为什么不直接传 FeedApi？因为 RetrofitClient 可能还没初始化
//    - 用 lambda 的话，调用时才去获取 feedApi，此时一定已经初始化好了
// ====================================================================

/**
 * FeedRepository —— 信息流数据仓库
 *
 * @param feedApiProvider 获取 FeedApi 的 lambda（延迟初始化）
 * @param tokenManager    本地存储管理器（用于 Mock 模式判断和缓存）
 */
class FeedRepository(
    private val feedApiProvider: () -> FeedApi,
    private val tokenManager: TokenManager? = null
) {
    // 次构造函数：直接传 FeedApi 实例时使用
    constructor(
        feedApi: FeedApi,
        tokenManager: TokenManager? = null
    ) : this({ feedApi }, tokenManager)

    // 内存缓存：当前已加载的所有 FeedItem
    private val cachedItems = mutableListOf<FeedItem>()

    /**
     * 加载信息流数据
     *
     * @param category 分类筛选
     * @param itemType 类型筛选
     * @param limit    每页条数
     * @param offset   偏移量（分页用）
     * @return Result<List<FeedItem>> 成功或失败的包装
     *
     * 🔑 Result 是 Kotlin 标准库的结果包装类：
     *    - Result.success(data) → 成功，包含数据
     *    - Result.failure(error) → 失败，包含异常
     *    - 调用方用 fold(onSuccess, onFailure) 或 getOrNull() 处理
     */
    suspend fun loadFeed(
        category: String? = null,
        itemType: String? = null,
        limit: Int = 20,
        offset: Int = 0
    ): Result<List<FeedItem>> {
        // withContext(Dispatchers.IO) 切换到 IO 线程池执行网络/磁盘操作
        // 不能在主线程做这些操作，会 ANR（Application Not Responding）
        return withContext(Dispatchers.IO) {
            val isMockMode = tokenManager?.isMockMode() ?: false

            // ── 策略 1：Mock 模式 ────────────────────────────────
            if (isMockMode) {
                val items = FallbackFeedData.items
                if (offset == 0) {
                    cachedItems.clear()
                }
                cachedItems.addAll(items)
                return@withContext Result.success(items)
            }

            // ── 策略 2：网络请求 ────────────────────────────────
            try {
                val response = feedApiProvider().getFeed(
                    category = category,
                    itemType = itemType,
                    limit = limit,
                    offset = offset,
                    baseUrl = RetrofitClient.getBaseUrl()
                )
                val items = response.items
                if (offset == 0) {
                    cachedItems.clear()
                    // 首页加载成功时，把数据缓存到 DataStore（JSON 格式）
                    tokenManager?.saveCachedFeed(category, items)
                }
                cachedItems.addAll(items)
                Result.success(items)
            } catch (e: Exception) {
                // ── 策略 3：网络失败 → 降级到本地缓存 ──────────
                val cachedFeed = if (offset == 0) {
                    tokenManager?.getCachedFeed(category).orEmpty()
                } else {
                    emptyList()
                }
                if (cachedFeed.isNotEmpty()) {
                    cachedItems.clear()
                    cachedItems.addAll(cachedFeed)
                    Result.success(cachedFeed)
                } else {
                    // ── 策略 4：缓存也没有 → 返回错误 ────────────
                    Result.failure(e)
                }
            }
        }
    }

    /** 从内存缓存中按 ID 查找（详情页用） */
    fun getCachedItemById(id: String): FeedItem? {
        return cachedItems.find { it.id == id }
    }

    /** 获取降级数据（兜底用） */
    fun getFallbackData(): List<FeedItem> {
        return FallbackFeedData.items
    }
}
