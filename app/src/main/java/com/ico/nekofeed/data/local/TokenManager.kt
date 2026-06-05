package com.ico.nekofeed.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "neko_feed_prefs")

data class LlmConfig(
    val baseUrl: String = "",
    val model: String = "gpt-4o-mini",
    val apiKey: String = "",
    val aiEnabled: Boolean = true,
    val smartSearchEnabled: Boolean = true
)

data class ServerConfig(
    val baseUrl: String = "http://10.0.2.2:8000"
)

class TokenManager(private val context: Context) {

    companion object {
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

    val token: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[TOKEN_KEY]
    }

    val username: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[USERNAME_KEY]
    }

    val serverConfig: Flow<ServerConfig> = context.dataStore.data.map { preferences ->
        ServerConfig(
            baseUrl = preferences[SERVER_BASE_URL_KEY] ?: DEFAULT_SERVER_BASE_URL
        )
    }

    val llmConfig: Flow<LlmConfig> = context.dataStore.data.map { preferences ->
        LlmConfig(
            baseUrl = preferences[LLM_BASE_URL_KEY] ?: "",
            model = preferences[LLM_MODEL_KEY] ?: "gpt-4o-mini",
            apiKey = preferences[LLM_API_KEY_KEY] ?: "",
            aiEnabled = preferences[AI_ENABLED_KEY] ?: true,
            smartSearchEnabled = preferences[SMART_SEARCH_ENABLED_KEY] ?: true
        )
    }

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
        return serverConfig.first()
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
     * 获取或生成设备唯一 ID，首次调用时自动生成并持久化。
     */
    suspend fun getDeviceId(): String {
        val existing = context.dataStore.data.first()[DEVICE_ID_KEY]
        if (existing != null) return existing

        val newId = UUID.randomUUID().toString()
        context.dataStore.edit { it[DEVICE_ID_KEY] = newId }
        return newId
    }

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
}
