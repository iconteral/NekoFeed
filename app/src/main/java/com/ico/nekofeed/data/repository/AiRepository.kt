package com.ico.nekofeed.data.repository

import com.ico.nekofeed.data.local.TokenManager
import com.ico.nekofeed.data.local.db.AiCacheDao
import com.ico.nekofeed.data.local.db.AiCacheEntity
import com.ico.nekofeed.data.model.FeedItem
import com.ico.nekofeed.data.remote.ChatMessage
import com.ico.nekofeed.data.remote.ChatRequest
import com.ico.nekofeed.data.remote.LlmApi
import com.ico.nekofeed.data.remote.LlmClientFactory
import com.ico.nekofeed.data.remote.ResponseFormat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.delay

data class AiResult(
    val aiSummary: String?,
    val aiTags: List<String>,
    val aiReason: String?,
    val fromCache: Boolean
)

data class SearchIntent(
    val keywords: List<String>,
    val itemTypes: List<String>,
    val tags: List<String>,
    val explanation: String
)

class AiRepository(
    private val tokenManager: TokenManager,
    private val aiCacheDao: AiCacheDao
) {
    private val gson = Gson()
    private var cachedApi: LlmApi? = null
    private var cachedBaseUrl: String? = null

    private suspend fun getApi(): LlmApi? {
        val config = tokenManager.getLlmConfig()
        if (config.baseUrl.isBlank()) return null

        if (cachedApi == null || cachedBaseUrl != config.baseUrl) {
            cachedApi = LlmClientFactory.create(config.baseUrl)
            cachedBaseUrl = config.baseUrl
        }
        return cachedApi
    }

    private suspend fun getConfig() = tokenManager.getLlmConfig()

    suspend fun generateFeedAi(item: FeedItem): AiResult? {
        val config = getConfig()
        if (!config.aiEnabled) return null

        val cached = aiCacheDao.getCache(item.id)
        if (cached != null) {
            return AiResult(
                aiSummary = cached.aiSummary,
                aiTags = parseTagsFromJson(cached.aiTags),
                aiReason = cached.aiReason,
                fromCache = true
            )
        }

        val api = getApi() ?: return null

        val systemPrompt = """你是一个内容分析助手，请对以下信息流内容进行分析，用中文输出 JSON。
格式：{"summary":"一句话摘要(≤50字)","tags":["标签1","标签2","标签3"],"reason":"推荐理由(≤30字)"}"""

        val userPrompt = """标题：${item.title}
类型：${item.itemType ?: "未知"}
原始摘要：${item.summary ?: "无"}"""

        return try {
            val request = ChatRequest(
                model = config.model,
                messages = listOf(
                    ChatMessage("system", systemPrompt),
                    ChatMessage("user", userPrompt)
                ),
                max_tokens = 256,
                temperature = 0.3f,
                response_format = ResponseFormat("json_object")
            )

            val auth = if (config.apiKey.isNotBlank()) "Bearer ${config.apiKey}" else ""
            val response = api.chatCompletion(auth, request)
            val content = response.choices.firstOrNull()?.message?.content ?: return null

            val parsed = parseAiResponse(content)

            aiCacheDao.insertCache(
                AiCacheEntity(
                    itemId = item.id,
                    aiSummary = parsed.aiSummary,
                    aiTags = gson.toJson(parsed.aiTags),
                    aiReason = parsed.aiReason,
                    modelUsed = config.model
                )
            )

            AiResult(
                aiSummary = parsed.aiSummary,
                aiTags = parsed.aiTags,
                aiReason = parsed.aiReason,
                fromCache = false
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun parseSearchQuery(query: String, userProfileTags: List<String>): SearchIntent? {
        val config = getConfig()
        if (!config.smartSearchEnabled) return null

        val api = getApi() ?: return null

        val tagsStr = if (userProfileTags.isNotEmpty()) userProfileTags.joinToString("、") else "无"

        val systemPrompt = """你是一个搜索理解助手。用户画像偏好标签：$tagsStr。
请解析用户搜索意图，用 JSON 输出。
格式：{"keywords":["词1"],"item_types":["article","ad"],"tags":["标签"],"explanation":"AI理解：..."}
item_types 可选值：article, video, ad, product, local
如无法判断 item_types，返回空数组。"""

        return try {
            val request = ChatRequest(
                model = config.model,
                messages = listOf(
                    ChatMessage("system", systemPrompt),
                    ChatMessage("user", query)
                ),
                max_tokens = 256,
                temperature = 0.3f,
                response_format = ResponseFormat("json_object")
            )

            val auth = if (config.apiKey.isNotBlank()) "Bearer ${config.apiKey}" else ""
            val response = api.chatCompletion(auth, request)
            val content = response.choices.firstOrNull()?.message?.content ?: return null

            parseSearchIntent(content)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun batchGenerateAi(items: List<FeedItem>, maxCount: Int = 5) {
        val config = getConfig()
        if (!config.aiEnabled) return

        val itemsToProcess = items
            .filter { item -> aiCacheDao.getCache(item.id) == null }
            .take(maxCount)

        for (item in itemsToProcess) {
            generateFeedAi(item)
            delay(500)
        }

        cleanOldCache()
    }

    suspend fun testConnection(): Result<String> {
        return try {
            val api = getApi()
                ?: return Result.failure(Exception("请先配置 AI Endpoint URL"))

            val config = getConfig()
            val request = ChatRequest(
                model = config.model,
                messages = listOf(
                    ChatMessage("user", "Hello, respond with 'OK'")
                ),
                max_tokens = 16,
                temperature = 0f
            )

            val auth = if (config.apiKey.isNotBlank()) "Bearer ${config.apiKey}" else ""
            val response = api.chatCompletion(auth, request)
            val content = response.choices.firstOrNull()?.message?.content ?: ""

            Result.success("连接成功！模型：${config.model}")
        } catch (e: Exception) {
            Result.failure(Exception("连接失败：${e.message}"))
        }
    }

    suspend fun getCacheCount(): Int {
        return aiCacheDao.getCacheCount()
    }

    suspend fun clearCache() {
        aiCacheDao.clearAll()
    }

    private suspend fun cleanOldCache() {
        val sevenDaysAgo = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
        aiCacheDao.deleteOldCache(sevenDaysAgo)
    }

    private fun parseAiResponse(json: String): AiResult {
        return try {
            val mapType = object : TypeToken<Map<String, Any>>() {}.type
            val map: Map<String, Any> = gson.fromJson(json, mapType)

            val summary = map["summary"] as? String
            val tags = (map["tags"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            val reason = map["reason"] as? String

            AiResult(aiSummary = summary, aiTags = tags, aiReason = reason, fromCache = false)
        } catch (e: Exception) {
            AiResult(aiSummary = null, aiTags = emptyList(), aiReason = null, fromCache = false)
        }
    }

    private fun parseSearchIntent(json: String): SearchIntent {
        return try {
            val mapType = object : TypeToken<Map<String, Any>>() {}.type
            val map: Map<String, Any> = gson.fromJson(json, mapType)

            val keywords = (map["keywords"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            val itemTypes = (map["item_types"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            val tags = (map["tags"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            val explanation = map["explanation"] as? String ?: ""

            SearchIntent(keywords = keywords, itemTypes = itemTypes, tags = tags, explanation = explanation)
        } catch (e: Exception) {
            SearchIntent(keywords = emptyList(), itemTypes = emptyList(), tags = emptyList(), explanation = "")
        }
    }

    private fun parseTagsFromJson(json: String): List<String> {
        return try {
            val listType = object : TypeToken<List<String>>() {}.type
            gson.fromJson(json, listType) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
