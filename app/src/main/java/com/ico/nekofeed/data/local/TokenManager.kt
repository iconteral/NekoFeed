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
// 📌 DataStore 是 Android Jetpack 推荐的键值对存储方案，替代 SharedPreferences。
//
// 📌 核心知识点：
//    1. preferencesDataStore(name) → 属性委托（by）创建 DataStore 实例
//       - 整个 App 共享同一个实例
//       - name 是文件名，数据存在 /data/data/包名/files/datastore/ 下
//
//    2. Flow<T> → 数据变化时自动通知订阅者
//       - val token: Flow<String?> = context.dataStore.data.map { it[TOKEN_KEY] }
//       - Token 变化时，所有 collect 这个 Flow 的地方都会收到通知
//
//    3. suspend fun + edit { } → 写入操作是挂起函数
//       - edit { preferences -> preferences[KEY] = value }
//       - 自动处理并发和原子性
//
//    4. first() → 从 Flow 中取一次值（挂起函数）
//       - 用于只需要读一次的场景（如启动时获取配置）
//
//    5. companion object + PreferencesKey → 定义类型安全的键
//       - stringPreferencesKey("auth_token") → 只能存 String
//       - booleanPreferencesKey("ai_enabled") → 只能存 Boolean
// ====================================================================

// 属性委托：Context.dataStore 扩展属性
// 整个 App 只有一个 DataStore 实例，通过 Context.dataStore 访问
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "neko_feed_prefs")

/**
 * LLM 配置数据类
 * 存储 AI 功能的配置信息（Base URL、模型名、API Key 等）
 */
data class LlmConfig(
    val baseUrl: String = "",
    val model: String = "gpt-4o-mini",
    val apiKey: String = "",
    val aiEnabled: Boolean = true,
    val smartSearchEnabled: Boolean = true
)

/**
 * 服务器配置数据类
 * 存储后端服务器地址
 */
data class ServerConfig(
    val baseUrl: String = "http://10.0.2.2:8000"  // 10.0.2.2 是模拟器访问宿主机的特殊地址
)

/**
 * 启动配置数据类
 * App 启动时一次性读取的所有配置
 */
data class StartupConfig(
    val serverBaseUrl: String,       // 服务器地址
    val token: String?,              // 登录 Token（null = 未登录）
    val deviceId: String,            // 设备唯一 ID
    val onboardingCompleted: Boolean // 是否已完成首次引导
)

/**
 * TokenManager —— 本地存储管理器
 *
 * 封装 DataStore 的所有读写操作，提供类型安全的 API。
 * 其他组件通过这个类访问本地数据，不需要直接操作 DataStore。
 *
 * 🔑 设计要点：
 *    - 所有读写方法都是 suspend fun（挂起函数），不能在主线程调用
 *    - Flow 类型的属性用于"持续监听"，suspend fun 用于"读一次"
 *    - companion object 里的 Key 是私有的，外部不能直接访问
 */
class TokenManager(private val context: Context) {
    private val gson = Gson()

    companion object {
        // ── 键定义（类型安全）────────────────────────────────────
        // stringPreferencesKey / booleanPreferencesKey 是 DataStore 提供的工厂方法
        // 编译时就能检查类型，不会出现 "读 String 当 Int 用" 的问题
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

        const val DEFAULT_SERVER_BASE_URL = "http://10.0.2.2:8000"
    }

    // ── Flow 类型的属性（持续监听）────────────────────────────────
    // map { } 把 DataStore 的 Preferences Flow 转换为具体类型的 Flow
    // 任何地方 collect 这个 Flow，数据变化时都会自动收到通知

    /** Token Flow：用于网络层自动添加 Authorization 头 */
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

    /** LLM 配置 Flow：AI 设置页监听配置变化 */
    val llmConfig: Flow<LlmConfig> = context.dataStore.data.map { preferences ->
        LlmConfig(
            baseUrl = preferences[LLM_BASE_URL_KEY] ?: "",
            model = preferences[LLM_MODEL_KEY] ?: "gpt-4o-mini",
            apiKey = preferences[LLM_API_KEY_KEY] ?: "",
            aiEnabled = preferences[AI_ENABLED_KEY] ?: true,
            smartSearchEnabled = preferences[SMART_SEARCH_ENABLED_KEY] ?: true
        )
    }

    // ── 写入方法（suspend fun）───────────────────────────────────
    // DataStore.edit { } 是原子操作，多个修改在一个 lambda 里要么全成功要么全失败

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

    /** 获取服务器配置（读一次） */
    suspend fun getServerConfig(): ServerConfig {
        return serverConfig.first()  // first() 从 Flow 取第一个值
    }

    /**
     * 获取启动配置（一次性读取所有启动需要的数据）
     *
     * 🔑 亮点：设备 ID 的懒生成
     *    - 如果没有 DEVICE_ID_KEY，自动生成 UUID 并保存
     *    - ?.also { } 语法：如果 ?. 不为 null，执行 also 里的操作
     *    - "首次安装时自动生成设备 ID" 的常见模式
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
     *
     * 首次调用时自动生成 UUID 并持久化，后续调用直接返回已有值。
     * 用于未登录用户的操作追踪（X-Device-Id 请求头）。
     */
    suspend fun getDeviceId(): String {
        val existing = context.dataStore.data.first()[DEVICE_ID_KEY]
        if (existing != null) return existing

        val newId = UUID.randomUUID().toString()
        context.dataStore.edit { it[DEVICE_ID_KEY] = newId }
        return newId
    }

    /** 引导完成状态 Flow */
    val onboardingCompleted: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[ONBOARDING_COMPLETED_KEY] ?: false
    }

    /** Mock 模式 Flow */
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

    // ── Feed 缓存（JSON 序列化存储）────────────────────────────
    // DataStore 不支持存复杂对象，所以用 Gson 转成 JSON 字符串再存

    suspend fun saveCachedFeed(category: String?, items: List<FeedItem>) {
        context.dataStore.edit { preferences ->
            preferences[cachedFeedKey(category)] = gson.toJson(items)
        }
    }

    suspend fun getCachedFeed(category: String?): List<FeedItem> {
        val json = context.dataStore.data.first()[cachedFeedKey(category)] ?: return emptyList()
        return runCatching {  // runCatching = try-catch 的函数式版本
            val type = object : TypeToken<List<FeedItem>>() {}.type
            gson.fromJson<List<FeedItem>>(json, type).orEmpty()
        }.getOrDefault(emptyList())  // 解析失败返回空列表
    }

    /** 动态生成缓存键：不同分类用不同的键 */
    private fun cachedFeedKey(category: String?) =
        stringPreferencesKey("cached_feed_${category ?: "featured"}")
}
