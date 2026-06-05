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
    private var isHandling401 = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        isHandling401 = false

        val tokenManager = TokenManager(applicationContext)
        val cachedToken = AtomicReference<String?>(null)
        val cachedDeviceId = AtomicReference<String?>(null)

        // 恢复自定义 server endpoint 和 deviceId
        runBlocking {
            val serverConfig = tokenManager.getServerConfig()
            RetrofitClient.updateBaseUrl(serverConfig.baseUrl)
            cachedDeviceId.set(tokenManager.getDeviceId())
        }

        // tokenProvider 在 OkHttp 线程上调用，AtomicReference 保证可见性
        RetrofitClient.setTokenProvider {
            cachedToken.get()
        }
        RetrofitClient.setDeviceIdProvider {
            cachedDeviceId.get()
        }

        val authRepository = AuthRepository(RetrofitClient.feedApi, tokenManager) { newToken ->
            cachedToken.set(newToken)
        }

        val restartApp: () -> Unit = {
            val intent = intent
            finish()
            startActivity(intent)
        }

        // 遇到 401 时自动清除登录状态并重启
        RetrofitClient.setUnauthorizedHandler {
            if (!isHandling401 && cachedToken.get() != null) {
                isHandling401 = true
                cachedToken.set(null)
                runBlocking {
                    tokenManager.clearAuth()
                }
                runOnUiThread { restartApp() }
            }
        }

        runBlocking { authRepository.restoreToken() }

        val userRepository = UserRepository(RetrofitClient.feedApi)

        setContent {
            NekoFeedTheme {
                AppNavHost(
                    authRepository = authRepository,
                    userRepository = userRepository,
                    restartApp = restartApp
                )
            }
        }
    }
}
