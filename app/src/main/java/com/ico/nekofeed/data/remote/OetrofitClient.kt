package com.ico.nekofeed.data.remote

import com.ico.nekofeed.data.local.TokenManager
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

// ============================================================================
// 【数据层 · 网络层核心 - Retrofit 客户端单例】
// ============================================================================
//
// 📌 Kotlin 初学者知识点速查：
//    1. object关键字  → 在 Kotlin 中，`object` 声明了一个单例类（Singleton）。
//       它会在第一次被使用时自动创建实例，并且全局只存在这一个实例。无需写双重校验锁。
//    2. by lazy      → 延迟初始化。只有当 `okHttpClient` 第一次被访问时，
//       花括号 `{}` 里的构建逻辑才会被执行，之后访问直接返回已创建好的对象，能节省内存和启动时间。
//    3. (() -> String?)? → 函数类型（Lambda 作为参数或变量）。
//       这里表示“一个不接收参数、返回可空 String 的函数，且该变量本身也可以是 null”。
//    4. let & apply  → Kotlin 的作用域函数：
//       - `let` 常用于安全调用可空对象：`obj?.let { ... }` 仅在 obj 不为 null 时执行。
//       - `apply` 用于对象初始化配置：在闭包内可以直接调用该对象的方法，最后返回该对象自己。
//    5. @Volatile    → 易变变量注解。确保多线程访问此变量时，所有线程都能看到最新的值（内存可见性）。
// ============================================================================

/**
 * RetrofitClient —— 管理所有 HTTP 网络请求的核心单例
 */
object RetrofitClient {
    // 存储当前请求的服务器基础地址（用户可在设置中修改，因此默认从 TokenManager 获取）
    private var baseUrl: String = TokenManager.DEFAULT_SERVER_BASE_URL

    // 日志拦截器：用于在控制台/Logcat 输出请求的 URL、方法和基本响应状态，方便调试
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    // ── 状态与回调提供者（通过 Lambda 函数动态获取最新值） ─────────────────────────
    // 使用函数类型的变量，可以保证每次网络请求时都调用该函数去拿最新的 token/deviceId，
    // 避免了静态变量无法及时更新的问题。
    private var tokenProvider: (() -> String?)? = null
    private var deviceIdProvider: (() -> String?)? = null
    private var onUnauthorized: (() -> Unit)? = null

    /**
     * 设置获取 Token 的回调方法
     */
    fun setTokenProvider(provider: () -> String?) {
        tokenProvider = provider
    }

    /**
     * 设置获取设备 ID 的回调方法
     */
    fun setDeviceIdProvider(provider: () -> String?) {
        deviceIdProvider = provider
    }

    /**
     * 设置 401（未授权/Token过期）发生时的回调处理器
     */
    fun setUnauthorizedHandler(handler: () -> Unit) {
        onUnauthorized = handler
    }

    /**
     * 判断当前是否已设置并存在 Token（用于判断是否处于登录状态）
     */
    fun hasToken(): Boolean {
        return tokenProvider?.invoke() != null
    }

    // ── OkHttpClient 配置（底层网络引擎） ─────────────────────────────────────────
    // 使用 `by lazy` 延迟加载，只有在第一次发起网络请求（即 Retrofit 初始化）时才会构建 OkHttpClient。
    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor) // 注入日志记录拦截器
            .addInterceptor { chain ->
                // chain.request() 拿到当前的请求包
                val original = chain.request()
                val builder = original.newBuilder()

                // 🔑 动态添加请求头 (Headers)
                // 1. 如果有 token，添加 Bearer 认证头部
                tokenProvider?.invoke()?.let {
                    builder.header("Authorization", "Bearer $it")
                }
                // 2. 始终在头部添加设备唯一标识 X-Device-Id
                deviceIdProvider?.invoke()?.let {
                    builder.header("X-Device-Id", it)
                }

                // 继续执行请求并拿到响应 (Response)
                val response = chain.proceed(builder.build())

                // 🔑 401 拦截处理
                // 如果发现服务器返回了 401 Unauthorized（说明 Token 过期或者无效了）
                if (response.code == 401) {
                    // 触发回调，让上层去清理登录态并跳转回登录页面
                    onUnauthorized?.invoke()
                }

                response
            }
            // 设置连接、读取和写入的超时时间为 15 秒
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    // ── Retrofit 实例与 API 接口声明 ─────────────────────────────────────────────
    // @Volatile 保证在切换 baseUrl 重新构建 retrofit 时，多线程之间能瞬间同步
    @Volatile
    private var retrofit: Retrofit = buildRetrofit(baseUrl)

    // 真正用于发起请求的 Api 服务接口。这里暴露给外部，但外部不可修改它（private set）
    @Volatile
    var feedApi: FeedApi = retrofit.create(FeedApi::class.java)
        private set

    /**
     * 构建 Retrofit 实例的辅助函数
     *
     * @param url 输入的 base url，如 "192.168.1.100:8000" 或 "https://api.nekofeed.com"
     */
    private fun buildRetrofit(url: String): Retrofit {
        // buildString 是 Kotlin 拼接字符串的糖，内部基于 StringBuilder
        val normalizedUrl = buildString {
            // 如果用户输入的地址没加 http 协议前缀，自动补齐，防止 Retrofit 报错
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                append("http://")
            }
            append(url.trimEnd('/')) // 去除末尾的斜杠，防止拼接出两个斜杠
            append("/") // Retrofit 的 BaseUrl 必须以 "/" 结尾
        }
        return Retrofit.Builder()
            .baseUrl(normalizedUrl)
            .client(okHttpClient) // 使用上面配置了拦截器和超时限制的 okHttpClient
            .addConverterFactory(GsonConverterFactory.create()) // 使用 Gson 自动将 JSON 字符串解析成数据模型（如 FeedItem）
            .build()
    }

    /**
     * 当用户在 APP 内部修改了服务器地址时，调用此方法动态刷新网络客户端
     */
    fun updateBaseUrl(url: String) {
        if (url.isNotBlank() && url != baseUrl) {
            baseUrl = url
            // 重新构建 Retrofit 实例并重新生成 API 实例
            retrofit = buildRetrofit(url)
            feedApi = retrofit.create(FeedApi::class.java)
        }
    }

    /**
     * 获取当前正在使用的服务器 Base URL
     */
    fun getBaseUrl(): String = baseUrl
}
