package com.ico.nekofeed

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ico.nekofeed.data.local.TokenManager
import com.ico.nekofeed.data.remote.RetrofitClient
import com.ico.nekofeed.data.repository.AuthRepository
import com.ico.nekofeed.data.repository.UserRepository
import com.ico.nekofeed.navigation.AppNavHost
import com.ico.nekofeed.ui.theme.NekoFeedTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val tokenManager = TokenManager(applicationContext)
        var cachedToken: String? = null

        // 每次从 DataStore 获取 token 会在 IO 线程
        RetrofitClient.setTokenProvider {
            cachedToken
        }

        val authRepository = AuthRepository(RetrofitClient.feedApi, tokenManager) { newToken ->
            cachedToken = newToken
        }
        val userRepository = UserRepository(RetrofitClient.feedApi)

        setContent {
            NekoFeedTheme {
                AppNavHost(
                    authRepository = authRepository,
                    userRepository = userRepository
                )
            }
        }
    }
}
