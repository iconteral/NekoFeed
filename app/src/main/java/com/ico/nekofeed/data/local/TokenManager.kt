package com.ico.nekofeed.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.ico.nekofeed.data.model.FeedItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

// ============================================================================
// 【本地存储 · DataStore 偏好管理】
// ============================================================================
//
// 📌 DataStore 是 Android Jetpack 推荐的轻量级持久化方案，替代 SharedPreferences。
//    它存储键值对数据（Token、配置、开关等），基于协程，线程安全。
//
// 📌 核心知识点：
//    1. preferencesDataStore(name) → 委托属性（by），创建 DataStore 实例
//    2. Preferences.Key<T>         → 类型安全的键（stringPreferencesKey / booleanPreferencesKey）
//    3. Flow<T>                    → 数据变化时自动推送新值（响应式）
//    4. dataStore.edit { }         → 修改数据（挂起函数，协程中调用）
//    5. dataStore.data.first()     → 读取一次当前值（挂起函数）
//    6. dataStore.data.map { }     → 转换为响应式 Flow（持续监听变化）
//
// 📌 本项目存储的数据：
//    - 认证：Token、用户名、设备 ID
//    - 服务器：服务器地址
//    - AI 配置：LLM 地址、模型、API Key
//    - 状态：引导完成、Mock 模式
//    - 缓存：Feed JSON 缓存（断网时使用）
// ====================================================================

// 扩展属性：为 Context 添加 dataStore 实例
// by preferencesDataStore 是 Kotlin 委托属性语法
// 整个 App 共享一个 DataStore 实例
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "neko_feed_prefs")

/**
 * LLM（大语言模型）配置
 * 用户在 AI 设置页面填写，用于调用 OpenAI 兼容接口
 */
data class LlmConfig(
    val baseUrl: String = "",                    // API 地址，如 "https://api.openai.com"
    val model: String = "gpt-4o-mini",           // 模型名称
    val apiKey: String = "",                     // API Key
    val aiEnabled: Boolean = true,               // 是否启用 AI 功能
    val smartSearchEnabled: Boolean = true       // 是否启用智能搜索
)

/**
 * 服务器配置
 */
data class ServerConfig(
    val baseUrl: String = "http://10.0.2.2:8000" // 默认模拟器访问本机
)

/**
 * 启动配置（App 启动时一次性读取）
 * 包含了 MainActivity 需要的所有初始化信息
 */
data class StartupConfig(
    val serverBaseUrl: String,      // 服务器地址
    val token: String?,             // 登录 Token（null = 未登录）
    val deviceId: String,           // 设备唯一 ID
    val onboardingCompleted: Boolean // 是否已完成引导
)

/**
 * TokenManager —— 本地存储管理器
 *
 * 封装了 DataStore 的所有读写操作，是数据层的"瑞士军刀"。
 * ViewModel 和 Repository 通过它来持久化配置和状态。
 *
 * 🔑 使用模式：
 *    - 读取（Flow）：tokenManager.token.collect { ... } → 持续监听
 *    - 读取（一次性）：tokenManager.getToken() → 只读一次
 *    - 写入：tokenManager.saveToken("xxx") → 挂起函数
 */
class TokenManager(private val context: Context) {
    private val gson = Gson()

    companion object {
        // ── 键定义 ──────────────────────────────────────────────────
        // 每个键对应 DataStore 中的一个存储项
        // 类型安全：stringPreferencesKey 只能存 String，booleanPreferencesKey 只能存 Boolean
        private val TOKEN_KEY = stringPreferencesKey("auth_token")
        private val USERNAME_KEY = stringPreferencesKey("username")
        private val DEVICE_ID_KEY = stringPreferencesKey("device_id")
        private val SERVER_BASE_URL_KEY = stringPreferencesKey("server_base_url")
        private val LLM_BASE_URL_KEY = stringPreferencesKey("llm_base_url")
        private val LLM_MODEL_KEY = stringPreferencesKey("llm_model")
        private val LLM_API_KEY_KEY = stringPreferencesKey("llm_api_key")
        private val AI_ENABLED_KEY = booleanPreferencesKey("ai_enabled")
        private val SMART_SEARCH_ENABLED_KEY = booleanPreferencesKey("smart_search_enabled")
        private val ONBOARDING_COMPLETED_KEY = booleanPreferencesKey("onboarding_completed")
        private val USE_MOCK_MODE_KEY = booleanPreferencesKey("use_mock_mode")

        /** 默认服务器地址（Android 模拟器访问宿主机的特殊 IP） */
        const val DEFAULT_SERVER_BASE_URL = "http://10.0.2.2:8000"
    }

    // ── 响应式 Flow 属性 ──────────────────────────────────────────
    // 这些 Flow 会持续推送最新值，当 DataStore 中的值变化时自动更新
    // UI 层可以用 collectAsState() 订阅它们

    /** Token Flow（null = 未登录） */
    val token: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[TOKEN_KEY]
    }

    /** 用户名 Flow */
    val username: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[USERNAME_KEY]
    }

    /** 服务器配置 Flow */
    val serverConfig: Flow<ServerConfig> = context.dataStore.data.map { preferences ->
        ServerConfig(
            baseUrl = preferences[SERVER_BASE_URL_KEY] ?: DEFAULT_SERVER_BASE_URL
        )
    }

    /** LLM 配置 Flow */
    val llmConfig: Flow<LlmConfig> = context.dataStore.data.map { preferences ->
        LlmConfig(
            baseUrl = preferences[LLM_BASE_URL_KEY] ?: "",
            model = preferences[LLM_MODEL_KEY] ?: "gpt-4o-mini",
            apiKey = preferences[LLM_API_KEY_KEY] ?: "",
            aiEnabled = preferences[AI_ENABLED_KEY] ?: true,
            smartSearchEnabled = preferences[SMART_SEARCH_ENABLED_KEY] ?: true
        )
    }

    // ── 写入方法（suspend fun → 必须在协程中调用）─────────────────

    suspend fun saveToken(token: String) {
        context.dataStore.edit { preferences ->
            preferences[TOKEN_KEY] = token
        }
    }

    suspend fun saveUsername(username: String) {
        context.dataStore.edit { preferences ->
            preferences[USERNAME_KEY] = username
        }
    }

    suspend fun saveServerConfig(config: ServerConfig) {
        context.dataStore.edit { preferences ->
            preferences[SERVER_BASE_URL_KEY] = config.baseUrl
        }
    }

    suspend fun getServerConfig(): ServerConfig {
        return serverConfig.first() // .first() 只取一次值，不持续监听
    }

    /**
     * 获取启动配置（App 启动时调用一次）
     *
     * 🔑 deviceId 的懒初始化逻辑：
     *    如果没有存储过设备 ID，就生成一个 UUID 并保存
     *    ?.also { ... } 是 Elvis + also 的组合：左边为 null 时执行右边
     */
    suspend fun getStartupConfig(): StartupConfig {
        val preferences = context.dataStore.data.first()
        val deviceId = preferences[DEVICE_ID_KEY] ?: UUID.randomUUID().toString().also { newId ->
            context.dataStore.edit { it[DEVICE_ID_KEY] = newId }
        }
        return StartupConfig(
            serverBaseUrl = preferences[SERVER_BASE_URL_KEY] ?: DEFAULT_SERVER_BASE_URL,
            token = preferences[TOKEN_KEY],
            deviceId = deviceId,
            onboardingCompleted = preferences[ONBOARDING_COMPLETED_KEY] ?: false
        )
    }

    suspend fun saveLlmConfig(config: LlmConfig) {
        context.dataStore.edit { preferences ->
            preferences[LLM_BASE_URL_KEY] = config.baseUrl
            preferences[LLM_MODEL_KEY] = config.model
            preferences[LLM_API_KEY_KEY] = config.apiKey
            preferences[AI_ENABLED_KEY] = config.aiEnabled
            preferences[SMART_SEARCH_ENABLED_KEY] = config.smartSearchEnabled
        }
    }

    suspend fun getLlmConfig(): LlmConfig {
        return llmConfig.first()
    }

    /** 清除认证信息（登出时调用） */
    suspend fun clearAuth() {
        context.dataStore.edit { preferences ->
            preferences.remove(TOKEN_KEY)
            preferences.remove(USERNAME_KEY)
        }
    }

    suspend fun getToken(): String? {
        return context.dataStore.data.first()[TOKEN_KEY]
    }

    /**
     * 获取或生成设备唯一 ID
     * 首次调用时自动生成 UUID 并持久化，后续调用直接返回
     */
    suspend fun getDeviceId(): String {
        val existing = context.dataStore.data.first()[DEVICE_ID_KEY]
        if (existing != null) return existing

        val newId = UUID.randomUUID().toString()
        context.dataStore.edit { it[DEVICE_ID_KEY] = newId }
        return newId
    }

    // ── 引导状态 ─────────────────────────────────────────────────

    val onboardingCompleted: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[ONBOARDING_COMPLETED_KEY] ?: false
    }

    val useMockMode: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[USE_MOCK_MODE_KEY] ?: false
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ONBOARDING_COMPLETED_KEY] = completed
        }
    }

    suspend fun setMockMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[USE_MOCK_MODE_KEY] = enabled
        }
    }

    suspend fun isOnboardingCompleted(): Boolean {
        return context.dataStore.data.first()[ONBOARDING_COMPLETED_KEY] ?: false
    }

    suspend fun isMockMode(): Boolean {
        return context.dataStore.data.first()[USE_MOCK_MODE_KEY] ?: false
    }

    // ── Feed JSON 缓存 ───────────────────────────────────────────
    // 把服务端返回的 Feed JSON 序列化后存入 DataStore
    // 断网时用 getCachedFeed() 恢复上次的数据

    /** 缓存 Feed 数据（按分类分别缓存） */
    suspend fun saveCachedFeed(category: String?, items: List<FeedItem>) {
        context.dataStore.edit { preferences ->
            preferences[cachedFeedKey(category)] = gson.toJson(items)
        }
    }

    /** 读取缓存的 Feed 数据 */
    suspend fun getCachedFeed(category: String?): List<FeedItem> {
        val json = context.dataStore.data.first()[cachedFeedKey(category)] ?: return emptyList()
        // runCatching { } 是 Kotlin 的 Result 包装器，捕获异常而不崩溃
        return runCatching {
            val type = object : TypeToken<List<FeedItem>>() {}.type
            gson.fromJson<List<FeedItem>>(json, type).orEmpty()
        }.getOrDefault(emptyList())
    }

    /** 动态生成缓存键（不同分类用不同键） */
    private fun cachedFeedKey(category: String?) =
        stringPreferencesKey("cached_feed_${category ?: "featured"}")
}
