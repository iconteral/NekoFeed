package com.ico.nekofeed.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ico.nekofeed.data.model.User
import com.ico.nekofeed.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoggedIn: Boolean = false,
    val isLoading: Boolean = false,
    val user: User? = null,
    val error: String? = null,
    val successMessage: String? = null
)

class AuthViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        checkLoginStatus()
    }

    private fun checkLoginStatus() {
        viewModelScope.launch {
            // 先从 DataStore 恢复 token 到内存缓存
            authRepository.restoreToken()
            val isLoggedIn = authRepository.isLoggedIn()
            _uiState.value = _uiState.value.copy(isLoggedIn = isLoggedIn)
            if (isLoggedIn) {
                loadUser()
            }
        }
    }

    fun register(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Username and password cannot be empty")
            return
        }
        if (password.length < 6) {
            _uiState.value = _uiState.value.copy(error = "Password must be at least 6 characters")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = authRepository.register(username, password)
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isLoggedIn = true,
                        isLoading = false,
                        successMessage = "Registration successful"
                    )
                    loadUser()
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Registration failed"
                    )
                }
            )
        }
    }

    fun login(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Username and password cannot be empty")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = authRepository.login(username, password)
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isLoggedIn = true,
                        isLoading = false,
                        successMessage = "Login successful"
                    )
                    loadUser()
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Login failed"
                    )
                }
            )
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _uiState.value = AuthUiState(isLoggedIn = false)
        }
    }

    fun loadUser() {
        viewModelScope.launch {
            val result = authRepository.getMe()
            result.fold(
                onSuccess = { user ->
                    _uiState.value = _uiState.value.copy(user = user)
                },
                onFailure = { }
            )
        }
    }

    fun updateProfile(avatar: String?, bio: String?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = authRepository.updateMe(avatar, bio)
            result.fold(
                onSuccess = { user ->
                    _uiState.value = _uiState.value.copy(
                        user = user,
                        isLoading = false,
                        successMessage = "Profile updated"
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Update failed"
                    )
                }
            )
        }
    }

    fun changePassword(oldPassword: String, newPassword: String) {
        if (oldPassword.isBlank() || newPassword.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Password cannot be empty")
            return
        }
        if (newPassword.length < 6) {
            _uiState.value = _uiState.value.copy(error = "New password must be at least 6 characters")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = authRepository.changePassword(oldPassword, newPassword)
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        successMessage = "Password changed"
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Change password failed"
                    )
                }
            )
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearSuccessMessage() {
        _uiState.value = _uiState.value.copy(successMessage = null)
    }
}
