package com.ico.nekofeed.ui.interaction

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ico.nekofeed.data.model.FeedItem
import com.ico.nekofeed.data.remote.RetrofitClient
import com.ico.nekofeed.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class InteractionType {
    LIKES, COLLECTIONS, HISTORY
}

data class UserInteractionUiState(
    val items: List<FeedItem> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = true,
    val currentPage: Int = 0
)

class UserInteractionViewModel(application: Application) : AndroidViewModel(application) {
    private val userRepository = UserRepository(RetrofitClient.feedApi)

    private val _uiState = MutableStateFlow(UserInteractionUiState())
    val uiState: StateFlow<UserInteractionUiState> = _uiState.asStateFlow()

    private var currentType: InteractionType = InteractionType.LIKES
    private val pageSize = 20

    fun setType(type: InteractionType) {
        if (currentType != type) {
            currentType = type
            loadItems(refresh = true)
        }
    }

    fun loadItems(refresh: Boolean = false) {
        if (refresh) {
            _uiState.update {
                it.copy(
                    items = emptyList(),
                    currentPage = 0,
                    hasMore = true,
                    error = null
                )
            }
        }

        if (_uiState.value.isLoading || _uiState.value.isLoadingMore) return
        if (!refresh && !_uiState.value.hasMore) return

        val isRefresh = refresh || _uiState.value.currentPage == 0

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = isRefresh,
                    isLoadingMore = !isRefresh,
                    error = null
                )
            }

            val offset = if (isRefresh) 0 else _uiState.value.currentPage * pageSize

            val result = when (currentType) {
                InteractionType.LIKES -> userRepository.getUserLikes(pageSize, offset)
                InteractionType.COLLECTIONS -> userRepository.getUserCollections(pageSize, offset)
                InteractionType.HISTORY -> userRepository.getUserHistory(pageSize, offset)
            }

            result.fold(
                onSuccess = { newItems ->
                    _uiState.update { state ->
                        val allItems = if (isRefresh) newItems else state.items + newItems
                        state.copy(
                            items = allItems,
                            isLoading = false,
                            isLoadingMore = false,
                            hasMore = newItems.size >= pageSize,
                            currentPage = state.currentPage + 1
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            error = e.message ?: "加载失败"
                        )
                    }
                }
            )
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            userRepository.clearUserHistory().fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(items = emptyList(), currentPage = 0, hasMore = false)
                    }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(error = e.message ?: "清空失败") }
                }
            )
        }
    }

    fun toggleLike(itemId: String) {
        viewModelScope.launch {
            userRepository.toggleLike(itemId).fold(
                onSuccess = { interaction ->
                    _uiState.update { state ->
                        state.copy(
                            items = state.items.map { item ->
                                if (item.id == itemId) {
                                    item.copy(
                                        isLiked = interaction.isLiked,
                                        likeCount = interaction.likeCount
                                    )
                                } else item
                            }
                        )
                    }
                },
                onFailure = { }
            )
        }
    }

    fun toggleCollect(itemId: String) {
        viewModelScope.launch {
            userRepository.toggleCollect(itemId).fold(
                onSuccess = { interaction ->
                    _uiState.update { state ->
                        state.copy(
                            items = state.items.map { item ->
                                if (item.id == itemId) {
                                    item.copy(
                                        isCollected = interaction.isCollected,
                                        collectCount = interaction.collectCount
                                    )
                                } else item
                            }
                        )
                    }
                },
                onFailure = { }
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
