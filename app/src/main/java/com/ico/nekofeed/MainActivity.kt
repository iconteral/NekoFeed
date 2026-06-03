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
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val tokenManager = TokenManager(applicationContext)
        val cachedToken = AtomicReference<String?>(null)

        // tokenProvider 在 OkHttp 线程上调用，AtomicReference 保证可见性
        RetrofitClient.setTokenProvider {
            cachedToken.get()
        }

        val authRepository = AuthRepository(RetrofitClient.feedApi, tokenManager) { newToken ->
            cachedToken.set(newToken)
        }

        runBlocking { authRepository.restoreToken() }

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
