package com.ico.nekofeed.ui.onboarding

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ico.nekofeed.data.local.LlmConfig
import com.ico.nekofeed.data.local.ServerConfig
import com.ico.nekofeed.data.local.TokenManager
import com.ico.nekofeed.data.remote.RetrofitClient
import com.ico.nekofeed.data.repository.AuthRepository
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
    val isCompleted: Boolean = false,
    val isLoggedIn: Boolean = false,
    val loginUsername: String = "",
    val loginPassword: String = "",
    val isLoggingIn: Boolean = false,
    val loginError: String? = null
)

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {
    private val tokenManager = TokenManager(application)
    private val authRepository = AuthRepository(RetrofitClient.feedApi, tokenManager)

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        loadExistingConfig()
        checkLoginStatus()
    }

    private fun checkLoginStatus() {
        viewModelScope.launch {
            authRepository.restoreToken()
            val isLoggedIn = authRepository.isLoggedIn()
            _uiState.update { it.copy(isLoggedIn = isLoggedIn) }
        }
    }

    private fun loadExistingConfig() {
        viewModelScope.launch {
            val serverConfig = tokenManager.getServerConfig()
            val llmConfig = tokenManager.getLlmConfig()
            val useMockMode = tokenManager.isMockMode()

            _uiState.update {
                it.copy(
                    useMockMode = useMockMode,
                    serverUrl = serverConfig?.baseUrl ?: TokenManager.DEFAULT_SERVER_BASE_URL,
                    llmEndpoint = llmConfig?.baseUrl ?: "",
                    llmModel = llmConfig?.model ?: "gpt-4o-mini",
                    llmApiKey = llmConfig?.apiKey ?: ""
                )
            }
        }
    }

    fun nextPage() {
        val maxPage = if (_uiState.value.useMockMode) 2 else 3
        _uiState.update { it.copy(currentPage = (it.currentPage + 1).coerceAtMost(maxPage)) }
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

    fun updateLoginUsername(username: String) {
        _uiState.update { it.copy(loginUsername = username) }
    }

    fun updateLoginPassword(password: String) {
        _uiState.update { it.copy(loginPassword = password) }
    }

    fun login() {
        val state = _uiState.value
        if (state.loginUsername.isBlank() || state.loginPassword.isBlank()) {
            _uiState.update { it.copy(loginError = "用户名和密码不能为空") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoggingIn = true, loginError = null) }
            val result = authRepository.login(state.loginUsername, state.loginPassword)
            result.fold(
                onSuccess = {
                    _uiState.update { 
                        it.copy(
                            isLoggedIn = true,
                            isLoggingIn = false,
                            loginError = null
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update { 
                        it.copy(
                            isLoggingIn = false,
                            loginError = e.message ?: "登录失败"
                        )
                    }
                }
            )
        }
    }

    fun clearLoginError() {
        _uiState.update { it.copy(loginError = null) }
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
