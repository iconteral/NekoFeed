package com.ico.nekofeed.data.repository

import com.ico.nekofeed.data.local.TokenManager
import com.ico.nekofeed.data.model.TokenResponse
import com.ico.nekofeed.data.model.User
import com.ico.nekofeed.data.remote.FeedApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

class AuthRepository(
    private val feedApiProvider: () -> FeedApi,
    private val tokenManager: TokenManager,
    private val onTokenChanged: ((String?) -> Unit)? = null
) {
    constructor(
        feedApi: FeedApi,
        tokenManager: TokenManager,
        onTokenChanged: ((String?) -> Unit)? = null
    ) : this({ feedApi }, tokenManager, onTokenChanged)

    val token = tokenManager.token
    val username = tokenManager.username

    /**
     * 从 DataStore 恢复已保存的 token 到内存缓存。
     * 必须在发起任何需要认证的 API 请求之前调用。
     */
    suspend fun restoreToken() {
        val savedToken = tokenManager.getToken()
        if (savedToken != null) {
            onTokenChanged?.invoke(savedToken)
        }
    }

    suspend fun register(username: String, password: String): Result<TokenResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val response = feedApiProvider().register(mapOf("username" to username, "password" to password))
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
                val response = feedApiProvider().login(mapOf("username" to username, "password" to password))
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
                val user = feedApiProvider().getMe()
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
                val user = feedApiProvider().updateMe(body)
                Result.success(user)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun changePassword(oldPassword: String, newPassword: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val response = feedApiProvider().changePassword(
                    mapOf("old_password" to oldPassword, "new_password" to newPassword)
                )
                Result.success(response["message"] ?: "Password changed")
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun logout() {
        tokenManager.clearAuth()
        onTokenChanged?.invoke(null)
    }

    suspend fun isLoggedIn(): Boolean {
        return token.firstOrNull() != null
    }
}
