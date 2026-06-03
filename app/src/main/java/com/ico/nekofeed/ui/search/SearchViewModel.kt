package com.ico.nekofeed.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ico.nekofeed.data.local.TokenManager
import com.ico.nekofeed.data.local.db.NekoFeedDatabase
import com.ico.nekofeed.data.model.FeedItem
import com.ico.nekofeed.data.repository.AiRepository
import com.ico.nekofeed.data.repository.UserProfileRepository
import com.ico.nekofeed.util.SearchUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SearchViewModel(application: Application) : AndroidViewModel(application) {
    private val tokenManager = TokenManager(application)
    private val database = NekoFeedDatabase.getInstance(application)
    private val aiRepository = AiRepository(tokenManager, database.aiCacheDao())
    private val userProfileRepository = UserProfileRepository(database.userProfileDao())

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    fun search(query: String, allItems: List<FeedItem>) {
        if (query.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, hasSearched = false) }

            try {
                val topTags = userProfileRepository.getTopInterestTags(5)
                val intent = aiRepository.parseSearchQuery(query, topTags)

                if (intent != null && intent.explanation.isNotBlank()) {
                    val results = filterByIntent(allItems, intent, query)
                    _uiState.update {
                        it.copy(
                            isSearching = false,
                            hasSearched = true,
                            query = query,
                            parsedKeywords = intent.keywords,
                            matchedTags = intent.tags.take(5),
                            results = results,
                            errorMessage = null
                        )
                    }
                } else {
                    val results = fallbackSearch(query, allItems)
                    val keywords = query.split(" ", "，", ",", "、").filter { it.isNotBlank() }
                    _uiState.update {
                        it.copy(
                            isSearching = false,
                            hasSearched = true,
                            query = query,
                            parsedKeywords = keywords,
                            matchedTags = results.flatMap { it.displayTags }.distinct().take(5),
                            results = results,
                            errorMessage = null
                        )
                    }
                }
            } catch (e: Exception) {
                val results = fallbackSearch(query, allItems)
                val keywords = query.split(" ", "，", ",", "、").filter { it.isNotBlank() }
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        hasSearched = true,
                        query = query,
                        parsedKeywords = keywords,
                        matchedTags = results.flatMap { it.displayTags }.distinct().take(5),
                        results = results,
                        errorMessage = "AI 搜索不可用，使用本地搜索"
                    )
                }
            }
        }
    }

    private fun filterByIntent(
        items: List<FeedItem>,
        intent: com.ico.nekofeed.data.repository.SearchIntent,
        originalQuery: String
    ): List<FeedItem> {
        return items.map { item ->
            var score = 0f

            for (keyword in intent.keywords) {
                val k = keyword.lowercase()
                if (item.title.lowercase().contains(k)) score += 3f
                if (item.displaySummary.lowercase().contains(k)) score += 2f
                if (item.displayTags.any { it.lowercase().contains(k) }) score += 2f
                if ((item.content ?: "").lowercase().contains(k)) score += 1f
            }

            for (tag in intent.tags) {
                if (item.displayTags.any { it.equals(tag, ignoreCase = true) }) score += 2f
            }

            if (intent.itemTypes.isNotEmpty()) {
                val matchType = intent.itemTypes.any { type ->
                    item.itemType == type || item.cardType == type
                }
                if (matchType) score += 1.5f
            }

            val q = originalQuery.lowercase()
            if (item.title.lowercase().contains(q)) score += 2f
            if (item.displayTags.any { it.lowercase().contains(q) }) score += 1f

            item to score
        }.filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    private fun fallbackSearch(query: String, items: List<FeedItem>): List<FeedItem> {
        val q = query.lowercase().trim()
        if (q.isBlank()) return emptyList()

        val keywords = q.split(" ", "，", ",", "、").filter { it.isNotBlank() }

        return items.map { item ->
            var score = 0
            keywords.forEach { keyword ->
                if (item.title.lowercase().contains(keyword)) score += 3
                if (item.displaySummary.lowercase().contains(keyword)) score += 2
                if (item.displayTags.any { it.lowercase().contains(keyword) }) score += 2
                if ((item.content ?: "").lowercase().contains(keyword)) score += 1
                if ((item.brand ?: "").lowercase().contains(keyword)) score += 1
            }
            item to score
        }.filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    fun clearResults() {
        _uiState.update { SearchUiState() }
    }
}
