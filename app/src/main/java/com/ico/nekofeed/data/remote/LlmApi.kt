package com.ico.nekofeed.data.remote

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface LlmApi {
    @POST("v1/chat/completions")
    suspend fun chatCompletion(
        @Header("Authorization") auth: String,
        @Body request: ChatRequest
    ): ChatResponse
}

data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val max_tokens: Int = 512,
    val temperature: Float = 0.3f,
    val response_format: ResponseFormat? = null
)

data class ChatMessage(
    val role: String,
    val content: String
)

data class ChatResponse(
    val choices: List<Choice>
)

data class Choice(
    val message: ChatMessage
)

data class ResponseFormat(
    val type: String = "json_object"
)
