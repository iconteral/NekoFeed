package com.ico.nekofeed.data.remote

import android.util.Log
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object LlmClientFactory {
    fun create(baseUrl: String, timeoutSeconds: Long = 60): LlmApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .build()

        // 移除末尾的 /v1，因为LlmApi中已包含v1前缀
        var url = baseUrl.trimEnd('/')
        if (url.endsWith("/v1")) {
            url = url.dropLast(3)
        }
        url = "$url/"
        
        Log.d("LlmClientFactory", "创建API客户端: 原始URL=$baseUrl, 处理后URL=$url")

        return Retrofit.Builder()
            .baseUrl(url)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(LlmApi::class.java)
    }
}
