package com.ico.nekofeed.ui.onboarding

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ico.nekofeed.data.local.LlmConfig
import com.ico.nekofeed.data.local.ServerConfig
import com.ico.nekofeed.data.local.TokenManager
import com.ico.nekofeed.data.remote.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val currentPage: Int = 0,
    val useMockMode: Boolean = false,
    val serverUrl: String = TokenManager.DEFAULT_SERVER_BASE_URL,
    val llmEndpoint: String = "",
    val llmModel: String = "gpt-4o-mini",
    val llmApiKey: String = "",
    val isSaving: Boolean = false,
    val isCompleted: Boolean = false
)

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {
    private val tokenManager = TokenManager(application)

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun nextPage() {
        _uiState.update { it.copy(currentPage = (it.currentPage + 1).coerceAtMost(2)) }
    }

    fun previousPage() {
        _uiState.update { it.copy(currentPage = (it.currentPage - 1).coerceAtLeast(0)) }
    }

    fun setMockMode(enabled: Boolean) {
        _uiState.update { it.copy(useMockMode = enabled) }
    }

    fun updateServerUrl(url: String) {
        _uiState.update { it.copy(serverUrl = url) }
    }

    fun updateLlmEndpoint(endpoint: String) {
        _uiState.update { it.copy(llmEndpoint = endpoint) }
    }

    fun updateLlmModel(model: String) {
        _uiState.update { it.copy(llmModel = model) }
    }

    fun updateLlmApiKey(key: String) {
        _uiState.update { it.copy(llmApiKey = key) }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val state = _uiState.value

            tokenManager.setMockMode(state.useMockMode)

            if (!state.useMockMode) {
                val serverConfig = ServerConfig(
                    baseUrl = state.serverUrl.trim().ifEmpty { TokenManager.DEFAULT_SERVER_BASE_URL }
                )
                tokenManager.saveServerConfig(serverConfig)
                RetrofitClient.updateBaseUrl(serverConfig.baseUrl)
            }

            val llmConfig = LlmConfig(
                baseUrl = state.llmEndpoint.trim(),
                model = state.llmModel.trim(),
                apiKey = state.llmApiKey.trim(),
                aiEnabled = true,
                smartSearchEnabled = true
            )
            tokenManager.saveLlmConfig(llmConfig)

            tokenManager.setOnboardingCompleted(true)
            _uiState.update { it.copy(isSaving = false, isCompleted = true) }
        }
    }
}
