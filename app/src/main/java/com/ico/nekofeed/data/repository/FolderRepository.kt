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
// 📌 Repository 是 MVVM 架构中"数据仓库"的角色。
//    ViewModel 不直接调用 Retrofit 或 Room，而是通过 Repository 获取数据。
//    这样做的好处：
//    1. ViewModel 不关心数据来自网络还是本地
//    2. 可以轻松切换 Mock 模式
//    3. 统一处理错误和降级逻辑
//
// 📌 数据获取策略（优先级从高到低）：
//    1. Mock 模式 → 直接返回 FallbackFeedData（硬编码数据）
//    2. 网络请求  → 成功则返回，失败则降级
//    3. 本地缓存  → DataStore 中缓存的上次成功的 Feed JSON
//    4. 降级数据  → FallbackFeedData（兜底）
//
// 📌 关键 API：
//    - withContext(Dispatchers.IO) → 切换到 IO 线程（网络/磁盘操作）
//    - Result<T>                  → Kotlin 标准库的结果包装（成功/失败）
//    - Result.success(data)       → 成功
//    - Result.failure(error)      → 失败
//    - .fold(onSuccess, onFailure) → 模式匹配处理成功和失败
// ====================================================================

/**
 * FeedRepository —— 信息流数据仓库
 *
 * @param feedApiProvider Lambda，延迟获取 Retrofit API 接口
 *                        （为什么用 lambda？因为服务器地址可能在运行时改变）
 * @param tokenManager    本地存储管理器（读取 Mock 模式、缓存等）
 */
class FeedRepository(
    private val feedApiProvider: () -> FeedApi,
    private val tokenManager: TokenManager? = null
) {
    // 辅助构造函数：直接传 FeedApi 实例
    constructor(
        feedApi: FeedApi,
        tokenManager: TokenManager? = null
    ) : this({ feedApi }, tokenManager)

    // 内存缓存：保存最近一次加载的 Feed 数据
    // ViewModel 通过 getCachedItemById() 查找
    private val cachedItems = mutableListOf<FeedItem>()

    /**
     * 加载信息流数据
     *
     * @param category 分类筛选（null = 全部）
     * @param itemType 类型筛选
     * @param limit    每页条数
     * @param offset   偏移量（分页）
     * @return Result<List<FeedItem>> 成功返回数据列表，失败返回异常
     */
    suspend fun loadFeed(
        category: String? = null,
        itemType: String? = null,
        limit: Int = 20,
        offset: Int = 0
    ): Result<List<FeedItem>> {
        // withContext(Dispatchers.IO): 切换到 IO 线程池
        // 网络请求和磁盘读写不能在主线程执行，会 ANR（Application Not Responding）
        return withContext(Dispatchers.IO) {
            val isMockMode = tokenManager?.isMockMode() ?: false

            // ── 策略 1：Mock 模式 → 直接返回硬编码数据 ──
            if (isMockMode) {
                val items = FallbackFeedData.items
                if (offset == 0) {
                    cachedItems.clear()
                }
                cachedItems.addAll(items)
                return@withContext Result.success(items)
            }

            // ── 策略 2：网络请求 ──
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
                    // 成功时缓存到 DataStore（断网时可用）
                    tokenManager?.saveCachedFeed(category, items)
                }
                cachedItems.addAll(items)
                Result.success(items)
            } catch (e: Exception) {
                // ── 策略 3：失败 → 尝试读取本地缓存 ──
                val cachedFeed = if (offset == 0) {
                    tokenManager?.getCachedFeed(category).orEmpty()
                } else {
                    emptyList()
                }
                if (cachedFeed.isNotEmpty()) {
                    // 有缓存 → 返回缓存数据
                    cachedItems.clear()
                    cachedItems.addAll(cachedFeed)
                    Result.success(cachedFeed)
                } else {
                    // ── 策略 4：无缓存 → 返回失败 ──
                    // ViewModel 会用 FallbackFeedData 兜底
                    Result.failure(e)
                }
            }
        }
    }

    /** 从内存缓存中查找条目（供详情页使用） */
    fun getCachedItemById(id: String): FeedItem? {
        return cachedItems.find { it.id == id }
    }

    /** 获取降级数据（供 ViewModel 兜底使用） */
    fun getFallbackData(): List<FeedItem> {
        return FallbackFeedData.items
    }
}
