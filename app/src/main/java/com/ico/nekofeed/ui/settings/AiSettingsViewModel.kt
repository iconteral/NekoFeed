package com.ico.nekofeed.ui.settings

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ico.nekofeed.data.local.LlmConfig
import com.ico.nekofeed.data.local.ServerConfig
import com.ico.nekofeed.data.local.TokenManager
import com.ico.nekofeed.data.local.db.NekoFeedDatabase
import com.ico.nekofeed.data.remote.RetrofitClient
import com.ico.nekofeed.data.repository.AiRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AiSettingsUiState(
    val serverEndpoint: String = TokenManager.DEFAULT_SERVER_BASE_URL,
    val baseUrl: String = "",
    val model: String = "gpt-4o-mini",
    val apiKey: String = "",
    val aiEnabled: Boolean = true,
    val smartSearchEnabled: Boolean = true,
    val isTesting: Boolean = false,
    val testResult: String? = null,
    val testSuccess: Boolean = false,
    val cacheCount: Int = 0,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false
)

class AiSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val tokenManager = TokenManager(application)
    private val database = NekoFeedDatabase.getInstance(application)
    private val aiRepository = AiRepository(tokenManager, database.aiCacheDao())

    private val _uiState = MutableStateFlow(AiSettingsUiState())
    val uiState: StateFlow<AiSettingsUiState> = _uiState.asStateFlow()

    init {
        loadConfig()
    }

    private fun loadConfig() {
        viewModelScope.launch {
            val serverConfig = tokenManager.getServerConfig()
            val config = tokenManager.getLlmConfig()
            val count = aiRepository.getCacheCount()
            _uiState.update {
                it.copy(
                    serverEndpoint = serverConfig.baseUrl,
                    baseUrl = config.baseUrl,
                    model = config.model,
                    apiKey = config.apiKey,
                    aiEnabled = config.aiEnabled,
                    smartSearchEnabled = config.smartSearchEnabled,
                    cacheCount = count
                )
            }
        }
    }

    fun updateBaseUrl(value: String) {
        _uiState.update { it.copy(baseUrl = value, saveSuccess = false) }
    }

    fun updateServerEndpoint(value: String) {
        _uiState.update { it.copy(serverEndpoint = value, saveSuccess = false) }
    }

    fun updateModel(value: String) {
        _uiState.update { it.copy(model = value, saveSuccess = false) }
    }

    fun updateApiKey(value: String) {
        _uiState.update { it.copy(apiKey = value, saveSuccess = false) }
    }

    fun updateAiEnabled(value: Boolean) {
        _uiState.update { it.copy(aiEnabled = value, saveSuccess = false) }
    }

    fun updateSmartSearchEnabled(value: Boolean) {
        _uiState.update { it.copy(smartSearchEnabled = value, saveSuccess = false) }
    }

    fun saveConfig() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val state = _uiState.value

            val serverConfig = ServerConfig(
                baseUrl = state.serverEndpoint.trim().ifEmpty { TokenManager.DEFAULT_SERVER_BASE_URL }
            )
            tokenManager.saveServerConfig(serverConfig)
            RetrofitClient.updateBaseUrl(serverConfig.baseUrl)

            val config = LlmConfig(
                baseUrl = state.baseUrl.trim(),
                model = state.model.trim(),
                apiKey = state.apiKey.trim(),
                aiEnabled = state.aiEnabled,
                smartSearchEnabled = state.smartSearchEnabled
            )
            tokenManager.saveLlmConfig(config)
            _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            saveConfigSilently()
            _uiState.update { it.copy(isTesting = true, testResult = null) }
            Log.d("AiSettingsViewModel", "开始测试连接")

            val result = aiRepository.testConnection()
            result.fold(
                onSuccess = { message ->
                    Log.d("AiSettingsViewModel", "测试连接成功: $message")
                    _uiState.update {
                        it.copy(
                            isTesting = false,
                            testResult = message,
                            testSuccess = true
                        )
                    }
                },
                onFailure = { error ->
                    Log.e("AiSettingsViewModel", "测试连接失败: ${error.message}", error)
                    _uiState.update {
                        it.copy(
                            isTesting = false,
                            testResult = error.message ?: "未知错误",
                            testSuccess = false
                        )
                    }
                }
            )
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            aiRepository.clearCache()
            _uiState.update { it.copy(cacheCount = 0) }
        }
    }

    fun refreshCacheCount() {
        viewModelScope.launch {
            val count = aiRepository.getCacheCount()
            _uiState.update { it.copy(cacheCount = count) }
        }
    }

    fun resetSaveSuccess() {
        _uiState.update { it.copy(saveSuccess = false) }
    }

    private suspend fun saveConfigSilently() {
        val state = _uiState.value
        val serverConfig = ServerConfig(
            baseUrl = state.serverEndpoint.trim().ifEmpty { TokenManager.DEFAULT_SERVER_BASE_URL }
        )
        tokenManager.saveServerConfig(serverConfig)
        RetrofitClient.updateBaseUrl(serverConfig.baseUrl)

        val config = LlmConfig(
            baseUrl = state.baseUrl.trim(),
            model = state.model.trim(),
            apiKey = state.apiKey.trim(),
            aiEnabled = state.aiEnabled,
            smartSearchEnabled = state.smartSearchEnabled
        )
        tokenManager.saveLlmConfig(config)
    }
}
