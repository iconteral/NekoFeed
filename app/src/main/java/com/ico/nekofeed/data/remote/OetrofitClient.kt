package com.ico.nekofeed.data.remote

import com.ico.nekofeed.data.local.TokenManager
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private var baseUrl: String = TokenManager.DEFAULT_SERVER_BASE_URL

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private var tokenProvider: (() -> String?)? = null
    private var deviceIdProvider: (() -> String?)? = null
    private var onUnauthorized: (() -> Unit)? = null

    fun setTokenProvider(provider: () -> String?) {
        tokenProvider = provider
    }

    fun setDeviceIdProvider(provider: () -> String?) {
        deviceIdProvider = provider
    }

    fun setUnauthorizedHandler(handler: () -> Unit) {
        onUnauthorized = handler
    }

    fun hasToken(): Boolean {
        return tokenProvider?.invoke() != null
    }

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor { chain ->
                val original = chain.request()
                val builder = original.newBuilder()

                // 添加 Bearer token（如果有）
                tokenProvider?.invoke()?.let {
                    builder.header("Authorization", "Bearer $it")
                }
                // 始终添加 device_id
                deviceIdProvider?.invoke()?.let {
                    builder.header("X-Device-Id", it)
                }

                val response = chain.proceed(builder.build())

                // 遇到 401 时清除登录状态
                if (response.code == 401) {
                    onUnauthorized?.invoke()
                }

                response
            }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    @Volatile
    private var retrofit: Retrofit = buildRetrofit(baseUrl)

    @Volatile
    var feedApi: FeedApi = retrofit.create(FeedApi::class.java)
        private set

    private fun buildRetrofit(url: String): Retrofit {
        val normalizedUrl = buildString {
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                append("http://")
            }
            append(url.trimEnd('/'))
            append("/")
        }
        return Retrofit.Builder()
            .baseUrl(normalizedUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    fun updateBaseUrl(url: String) {
        if (url.isNotBlank() && url != baseUrl) {
            baseUrl = url
            retrofit = buildRetrofit(url)
            feedApi = retrofit.create(FeedApi::class.java)
        }
    }

    fun getBaseUrl(): String = baseUrl
}
