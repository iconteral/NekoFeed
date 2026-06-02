package com.ico.nekofeed.data.repository

import com.ico.nekofeed.data.local.TokenManager
import com.ico.nekofeed.data.model.TokenResponse
import com.ico.nekofeed.data.model.User
import com.ico.nekofeed.data.remote.FeedApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AuthRepository(
    private val feedApi: FeedApi,
    private val tokenManager: TokenManager,
    private val onTokenChanged: ((String?) -> Unit)? = null
) {
    val token = tokenManager.token
    val username = tokenManager.username

    init {
        CoroutineScope(Dispatchers.IO).launch {
            val savedToken = tokenManager.getToken()
            if (savedToken != null) {
                onTokenChanged?.invoke(savedToken)
            }
        }
    }

    suspend fun register(username: String, password: String): Result<TokenResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val response = feedApi.register(mapOf("username" to username, "password" to password))
                tokenManager.saveToken(response.accessToken)
                tokenManager.saveUsername(username)
                onTokenChanged?.invoke(response.accessToken)
                Result.success(response)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun login(username: String, password: String): Result<TokenResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val response = feedApi.login(mapOf("username" to username, "password" to password))
                tokenManager.saveToken(response.accessToken)
                tokenManager.saveUsername(username)
                onTokenChanged?.invoke(response.accessToken)
                Result.success(response)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getMe(): Result<User> {
        return withContext(Dispatchers.IO) {
            try {
                val user = feedApi.getMe()
                Result.success(user)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun updateMe(avatar: String?, bio: String?): Result<User> {
        return withContext(Dispatchers.IO) {
            try {
                val body = mutableMapOf<String, String>()
                avatar?.let { body["avatar"] = it }
                bio?.let { body["bio"] = it }
                val user = feedApi.updateMe(body)
                Result.success(user)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun changePassword(oldPassword: String, newPassword: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val response = feedApi.changePassword(
                    mapOf("old_password" to oldPassword, "new_password" to newPassword)
                )
                Result.success(response["message"] ?: "Password changed")
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun logout() {
        tokenManager.clearAll()
        onTokenChanged?.invoke(null)
    }

    suspend fun isLoggedIn(): Boolean {
        return token.firstOrNull() != null
    }
}
