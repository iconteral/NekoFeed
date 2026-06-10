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

// ============================================================================
// 【第3站 · App 入口】
// ============================================================================
//
// 📌 这是整个 Android App 的唯一入口点。
//    NekoFeed 采用"单 Activity 架构"——整个 App 只有这一个 Activity，
//    所有页面都是 Compose 函数，通过 Navigation 切换。
//
// 📌 启动流程（从上到下执行）：
//
//    1. enableEdgeToEdge()          → 启用全面屏（内容延伸到状态栏/导航栏后面）
//    2. TokenManager                → 初始化本地存储管理器（读写 Token、服务器地址等）
//    3. cachedToken / cachedDeviceId → 用 AtomicReference 缓存 Token 和设备 ID
//       （AtomicReference 保证多线程安全，Retrofit 的拦截器在 IO 线程读取这些值）
//    4. RetrofitClient 配置         → 注入 Token 提供者、设备 ID 提供者、401 处理器
//    5. lifecycleScope.launch       → 在主线程启动协程，异步读取启动配置
//    6. getStartupConfig()          → 从 DataStore 读取：服务器地址、Token、是否完成引导
//    7. setContent { }              → 🎯 这是 Compose 的入口！设置 Compose 内容
//    8. AppNavHost                  → 启动导航，显示第一个页面
//
// 📌 Kotlin 协程知识点：
//    - lifecycleScope.launch { }   → 启动一个协程，跟随 Activity 生命周期自动取消
//    - withContext(Dispatchers.IO)  → 切换到 IO 线程（读磁盘/网络不能在主线程）
//    - AtomicReference<T>           → 线程安全的引用容器
// ====================================================================

/**
 * MainActivity —— 唯一的 Activity
 *
 * 继承自 ComponentActivity（不是 AppCompatActivity），
 * 因为 Compose 不需要传统的 XML 布局和 Fragment 系统。
 */
class MainActivity : ComponentActivity() {
    // 防止 401 处理逻辑重复执行的标志位
    private var isHandling401 = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ── 1. 启用全面屏 ──────────────────────────────────────────
        // 让内容可以延伸到系统栏（状态栏、导航栏）后面
        enableEdgeToEdge()

        // ── 2. 初始化本地存储管理器 ────────────────────────────────
        // TokenManager 封装了 DataStore 的读写操作
        val tokenManager = TokenManager(applicationContext)

        // ── 3. 创建线程安全的缓存容器 ──────────────────────────────
        // AtomicReference 保证多线程读写安全
        // Retrofit 的 OkHttp 拦截器在 IO 线程会读取这些值来添加请求头
        val cachedToken = AtomicReference<String?>(null)
        val cachedDeviceId = AtomicReference<String?>(null)

        // ── 4. 配置 Retrofit 网络层 ────────────────────────────────
        // 注入 Token 提供者：每个 HTTP 请求的 Authorization 头都从这里取值
        RetrofitClient.setTokenProvider { cachedToken.get() }
        // 注入设备 ID 提供者：每个请求都会带上 X-Device-Id 头
        RetrofitClient.setDeviceIdProvider { cachedDeviceId.get() }

        // ── 重启 App 的 lambda ─────────────────────────────────────
        // 用于登出或切换数据模式后重新初始化整个 App
        // 原理：关闭当前 Activity → 重新启动它
        val restartApp: () -> Unit = {
            val restartIntent = intent
            finish()                    // 关闭当前 Activity
            startActivity(restartIntent) // 重新启动
        }

        // ── 5. 配置 401 未授权处理器 ──────────────────────────────
        // 当 Retrofit 收到 HTTP 401 响应时自动触发
        // 逻辑：清除 Token → 清除本地认证数据 → 重启 App（回到登录页）
        RetrofitClient.setUnauthorizedHandler {
            if (!isHandling401 && cachedToken.get() != null) {
                isHandling401 = true // 防止重复触发
                cachedToken.set(null)
                lifecycleScope.launch {
                    tokenManager.clearAuth()
                    restartApp()
                }
            }
        }

        // ── 6. 异步加载启动配置 ──────────────────────────────────
        // lifecycleScope.launch: 在主线程启动协程
        // withContext(Dispatchers.IO): 切换到 IO 线程读取本地数据
        lifecycleScope.launch {
            val startupConfig = withContext(Dispatchers.IO) {
                tokenManager.getStartupConfig()
            }

            // 用启动配置初始化网络层
            RetrofitClient.updateBaseUrl(startupConfig.serverBaseUrl) // 设置服务器地址
            cachedToken.set(startupConfig.token)                      // 缓存 Token
            cachedDeviceId.set(startupConfig.deviceId)                // 缓存设备 ID

            // ── 创建业务仓库 ──────────────────────────────────────
            // AuthRepository: 处理登录/注册/Token 管理
            val authRepository = AuthRepository(
                feedApiProvider = { RetrofitClient.feedApi },
                tokenManager = tokenManager,
                onTokenChanged = { newToken -> cachedToken.set(newToken) }
            )
            // UserRepository: 处理用户信息/互动/统计
            val userRepository = UserRepository { RetrofitClient.feedApi }

            // ── 决定起始页面 ──────────────────────────────────────
            // 已完成引导 → 直接进入主页
            // 未完成引导 → 显示引导页（首次安装时）
            val startDestination =
                if (startupConfig.onboardingCompleted) "main" else "onboarding"

            // ── 7. 🎯 Compose 入口！────────────────────────────────
            // setContent { } 替代了传统的 setContentView(R.layout.xxx)
            // 里面写的就是 Compose UI
            setContent {
                NekoFeedTheme {   // Material 3 主题（颜色、字体、形状）
                    AppNavHost(   // 启动导航系统，显示第一个页面
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
