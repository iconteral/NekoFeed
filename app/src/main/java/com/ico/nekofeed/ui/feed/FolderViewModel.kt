package com.ico.nekofeed.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ico.nekofeed.data.model.FeedItem
import com.ico.nekofeed.data.remote.RetrofitClient
import com.ico.nekofeed.data.repository.FeedRepository
import com.ico.nekofeed.util.FeedUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FeedViewModel : ViewModel() {
    private val repository = FeedRepository(RetrofitClient.feedApi)

    private val _uiState = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    init {
        loadFeed()
    }

    fun loadFeed() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null,
                usingFallback = false
            )

            repository.loadFeed().fold(
                onSuccess = { items ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        items = items,
                        errorMessage = null,
                        usingFallback = false
                    )
                },
                onFailure = { error ->
                    val fallbackItems = repository.getFallbackData()
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        items = fallbackItems,
                        errorMessage = "无法连接服务器: ${error.message}",
                        usingFallback = true
                    )
                }
            )
        }
    }

    fun retry() {
        loadFeed()
    }

    fun getItemById(id: String): FeedItem? {
        return repository.getCachedItemById(id) ?: repository.getFallbackData().find { it.id == id }
    }
}
