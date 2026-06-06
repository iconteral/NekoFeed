package com.ico.nekofeed

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.ico.nekofeed.data.local.TokenManager
import com.ico.nekofeed.data.remote.RetrofitClient
import com.ico.nekofeed.data.repository.AuthRepository
import com.ico.nekofeed.data.repository.UserRepository
import com.ico.nekofeed.navigation.AppNavHost
import com.ico.nekofeed.ui.theme.NekoFeedTheme
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var isHandling401 = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val tokenManager = TokenManager(applicationContext)
        val cachedToken = AtomicReference<String?>(null)
        val cachedDeviceId = AtomicReference<String?>(null)

        RetrofitClient.setTokenProvider { cachedToken.get() }
        RetrofitClient.setDeviceIdProvider { cachedDeviceId.get() }

        val restartApp: () -> Unit = {
            val restartIntent = intent
            finish()
            startActivity(restartIntent)
        }

        RetrofitClient.setUnauthorizedHandler {
            if (!isHandling401 && cachedToken.get() != null) {
                isHandling401 = true
                cachedToken.set(null)
                lifecycleScope.launch {
                    tokenManager.clearAuth()
                    restartApp()
                }
            }
        }

        lifecycleScope.launch {
            val startupConfig = withContext(Dispatchers.IO) {
                tokenManager.getStartupConfig()
            }
            RetrofitClient.updateBaseUrl(startupConfig.serverBaseUrl)
            cachedToken.set(startupConfig.token)
            cachedDeviceId.set(startupConfig.deviceId)

            val authRepository = AuthRepository(
                feedApiProvider = { RetrofitClient.feedApi },
                tokenManager = tokenManager,
                onTokenChanged = { newToken -> cachedToken.set(newToken) }
            )
            val userRepository = UserRepository { RetrofitClient.feedApi }
            val startDestination =
                if (startupConfig.onboardingCompleted) "main" else "onboarding"

            setContent {
                NekoFeedTheme {
                    AppNavHost(
                        authRepository = authRepository,
                        userRepository = userRepository,
                        restartApp = restartApp,
                        startDestination = startDestination
                    )
                }
            }
        }
    }
}
