package com.ico.nekofeed.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.ico.nekofeed.data.local.TokenManager
import com.ico.nekofeed.data.local.db.ChatMessageEntity
import com.ico.nekofeed.data.local.db.NekoFeedDatabase
import com.ico.nekofeed.data.model.FeedItem
import com.ico.nekofeed.data.remote.ChatMessage
import com.ico.nekofeed.data.repository.AiRepository
import com.ico.nekofeed.data.repository.UserProfileRepository
import com.ico.nekofeed.util.ChatBubble
import com.ico.nekofeed.util.ChatUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val tokenManager = TokenManager(application)
    private val database = NekoFeedDatabase.getInstance(application)
    private val aiRepository = AiRepository(tokenManager, database.aiCacheDao())
    private val userProfileRepository = UserProfileRepository(database.userProfileDao())
    private val chatMessageDao = database.chatMessageDao()
    private val gson = Gson()

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val conversationHistory = mutableListOf<ChatMessage>()

    init {
        loadHistory()
    }

    fun sendMessage(text: String, allItems: List<FeedItem>) {
        if (text.isBlank()) return

        viewModelScope.launch {
            val userBubble = ChatBubble(
                role = "user",
                content = text,
                timestamp = System.currentTimeMillis()
            )
            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages + userBubble,
                isAiTyping = true,
                errorMessage = null
            )

            chatMessageDao.insertMessage(
                ChatMessageEntity(role = "user", content = text)
            )

            val userTags = userProfileRepository.getTopInterestTags(5)
            val systemPrompt = buildSystemPrompt(allItems, userTags)

            if (conversationHistory.isEmpty()) {
                conversationHistory.add(ChatMessage("system", systemPrompt))
            }
            conversationHistory.add(ChatMessage("user", text))

            val response = aiRepository.chatWithContext(conversationHistory)

            if (response != null) {
                conversationHistory.add(ChatMessage("assistant", response))

                val recommendedIds = parseRecommendedIds(response)
                val recommendedItems = if (recommendedIds.isNotEmpty()) {
                    allItems.filter { item -> item.id in recommendedIds }
                } else {
                    emptyList()
                }

                val cleanContent = removeRecommendedIdsJson(response)

                val assistantBubble = ChatBubble(
                    role = "assistant",
                    content = cleanContent,
                    recommendedItems = recommendedItems,
                    timestamp = System.currentTimeMillis()
                )

                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + assistantBubble,
                    isAiTyping = false
                )

                chatMessageDao.insertMessage(
                    ChatMessageEntity(
                        role = "assistant",
                        content = response,
                        recommendedIds = if (recommendedIds.isNotEmpty()) gson.toJson(recommendedIds) else null
                    )
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isAiTyping = false,
                    errorMessage = "AI 暂时无法回复，请稍后再试"
                )
                conversationHistory.removeAt(conversationHistory.size - 1)
            }
        }
    }

    fun clearChat() {
        viewModelScope.launch {
            chatMessageDao.clearAll()
            conversationHistory.clear()
            _uiState.value = ChatUiState()
        }
    }

    private fun loadHistory() {
        viewModelScope.launch {
            val entities = chatMessageDao.getAllMessages()
            if (entities.isEmpty()) return@launch

            val bubbles = mutableListOf<ChatBubble>()
            for (entity in entities) {
                val recommendedIds = if (entity.recommendedIds != null) {
                    try {
                        val listType = object : TypeToken<List<String>>() {}.type
                        gson.fromJson(entity.recommendedIds, listType) ?: emptyList()
                    } catch (e: Exception) {
                        emptyList<String>()
                    }
                } else {
                    emptyList()
                }

                bubbles.add(
                    ChatBubble(
                        id = entity.id,
                        role = entity.role,
                        content = entity.content,
                        timestamp = entity.timestamp
                    )
                )

                if (entity.role == "user") {
                    conversationHistory.add(ChatMessage("user", entity.content))
                } else {
                    conversationHistory.add(ChatMessage("assistant", entity.content))
                }
            }

            _uiState.value = _uiState.value.copy(messages = bubbles)
        }
    }

    private suspend fun buildSystemPrompt(
        allItems: List<FeedItem>,
        userTags: List<String>
    ): String {
        val tagsStr = if (userTags.isNotEmpty()) userTags.joinToString("、") else "暂无"
        val feedSummary = allItems.take(20).mapIndexed { i, item ->
            "${i + 1}. [ID:${item.id}] ${item.title} | 标签：${item.displayTags.joinToString(",")} | 类型：${item.itemType ?: "未知"}"
        }.joinToString("\n")

        return """你是 NekoFeed 的 AI 助手「Neko」。你可以：
1. 根据用户兴趣推荐信息流内容
2. 回答用户关于内容的问题
3. 进行友好的日常对话

---
【用户画像】
兴趣标签（按偏好排序）：$tagsStr

---
【当前可推荐内容】（共 ${allItems.take(20).size} 条）
$feedSummary

---
【回复规则】
- 使用中文回复，语气友好自然
- 当用户请求推荐内容时，从【当前可推荐内容】中选择最匹配的
- 推荐时在回复末尾追加一行 JSON：{"recommended_ids":["id1","id2",...]}
- 推荐 3~5 条最相关的内容，并简要说明推荐理由
- 如果没有匹配的内容，坦诚告知
- 非推荐请求则正常对话，不需要 JSON"""
    }

    private fun parseRecommendedIds(response: String): List<String> {
        return try {
            // Try to find the JSON block — handle multi-line with DOTALL flag
            val jsonPattern = """\{"recommended_ids"\s*:\s*\[.*?\]\s*\}""".toRegex(RegexOption.DOT_MATCHES_ALL)
            val match = jsonPattern.find(response)
            if (match != null) {
                val mapType = object : TypeToken<Map<String, List<String>>>() {}.type
                val map: Map<String, List<String>> = gson.fromJson(match.value, mapType)
                return map["recommended_ids"] ?: emptyList()
            }

            // Fallback: JSON might be truncated (common with long IDs).
            // Try to extract individual item IDs using a broader pattern.
            val truncatedPattern = """\{"recommended_ids"\s*:\s*\[(.*)""".toRegex(RegexOption.DOT_MATCHES_ALL)
            val truncatedMatch = truncatedPattern.find(response)
            if (truncatedMatch != null) {
                val idsContent = truncatedMatch.groupValues[1]
                val idPattern = """"(item_[a-f0-9]+)"""".toRegex()
                return idPattern.findAll(idsContent).map { it.groupValues[1] }.toList()
            }

            emptyList()
        } catch (e: Exception) {
            // Last resort: try to find any item IDs in the response tail
            try {
                val idPattern = """"(item_[a-f0-9]+)"""".toRegex()
                val lastChunk = response.takeLast(500)
                idPattern.findAll(lastChunk).map { it.groupValues[1] }.toList()
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    private fun removeRecommendedIdsJson(response: String): String {
        // Remove the JSON block (multi-line aware), including potential truncated JSON at the end
        var cleaned = response

        // Try complete JSON first
        val completePattern = """\n?\s*\{"recommended_ids"\s*:\s*\[.*?\]\s*\}""".toRegex(RegexOption.DOT_MATCHES_ALL)
        cleaned = cleaned.replace(completePattern, "")

        // If there's a truncated JSON at the end, remove it too
        val truncatedPattern = """\n?\s*\{"recommended_ids"\s*:\s*\[.*$""".toRegex(RegexOption.DOT_MATCHES_ALL)
        cleaned = cleaned.replace(truncatedPattern, "")

        return cleaned.trimEnd()
    }
}

