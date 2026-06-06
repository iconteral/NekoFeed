package com.ico.nekofeed.ui.interaction

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ico.nekofeed.data.local.FallbackFeedData
import com.ico.nekofeed.data.local.TokenManager
import com.ico.nekofeed.data.local.db.FeedItemInteractionEntity
import com.ico.nekofeed.data.local.db.NekoFeedDatabase
import com.ico.nekofeed.data.model.FeedItem
import com.ico.nekofeed.data.model.ItemInteraction
import com.ico.nekofeed.data.remote.RetrofitClient
import com.ico.nekofeed.data.repository.InteractionSyncStore
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
    private val userRepository = UserRepository { RetrofitClient.feedApi }
    private val tokenManager = TokenManager(application)
    private val interactionDao = NekoFeedDatabase.getInstance(application).feedItemInteractionDao()

    private val _uiState = MutableStateFlow(UserInteractionUiState())
    val uiState: StateFlow<UserInteractionUiState> = _uiState.asStateFlow()

    private var currentType: InteractionType? = null
    private val pageSize = 20

    init {
        viewModelScope.launch {
            InteractionSyncStore.updates.collect { update ->
                applyInteraction(update.itemId, update.interaction)
            }
        }
    }

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

            if (tokenManager.isMockMode()) {
                loadMockItems()
                return@launch
            }

            val result = when (currentType ?: InteractionType.LIKES) {
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
            if (tokenManager.isMockMode()) {
                interactionDao.clearHistory()
                _uiState.update {
                    it.copy(items = emptyList(), currentPage = 0, hasMore = false)
                }
                return@launch
            }

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
            if (tokenManager.isMockMode()) {
                val item = _uiState.value.items.firstOrNull { it.id == itemId } ?: return@launch
                val interaction = ItemInteraction(
                    isLiked = !item.isLiked,
                    isCollected = item.isCollected,
                    likeCount = if (item.isLiked) item.likeCount - 1 else item.likeCount + 1,
                    collectCount = item.collectCount
                )
                saveAndPublish(itemId, interaction)
                return@launch
            }

            userRepository.toggleLike(itemId).fold(
                onSuccess = { interaction ->
                    saveAndPublish(itemId, interaction)
                },
                onFailure = { }
            )
        }
    }

    fun toggleCollect(itemId: String) {
        viewModelScope.launch {
            if (tokenManager.isMockMode()) {
                val item = _uiState.value.items.firstOrNull { it.id == itemId } ?: return@launch
                val interaction = ItemInteraction(
                    isLiked = item.isLiked,
                    isCollected = !item.isCollected,
                    likeCount = item.likeCount,
                    collectCount = if (item.isCollected) {
                        item.collectCount - 1
                    } else {
                        item.collectCount + 1
                    }
                )
                saveAndPublish(itemId, interaction)
                return@launch
            }

            userRepository.toggleCollect(itemId).fold(
                onSuccess = { interaction ->
                    saveAndPublish(itemId, interaction)
                },
                onFailure = { }
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private suspend fun loadMockItems() {
        val interactions = interactionDao.getAllInteractions().associateBy { it.itemId }
        val type = currentType ?: InteractionType.LIKES
        val items = FallbackFeedData.items.mapNotNull { item ->
            val interaction = interactions[item.id] ?: return@mapNotNull null
            val merged = item.copy(
                isLiked = interaction.isLiked,
                isCollected = interaction.isCollected,
                likeCount = interaction.likeCount,
                collectCount = interaction.collectCount
            )
            when (type) {
                InteractionType.LIKES -> merged.takeIf { it.isLiked }
                InteractionType.COLLECTIONS -> merged.takeIf { it.isCollected }
                InteractionType.HISTORY -> merged.takeIf { interaction.lastViewedAt != null }
            }
        }.let { result ->
            if (type == InteractionType.HISTORY) {
                result.sortedByDescending { interactions[it.id]?.lastViewedAt ?: 0L }
            } else {
                result
            }
        }
        _uiState.update {
            it.copy(
                items = items,
                isLoading = false,
                isLoadingMore = false,
                error = null,
                hasMore = false,
                currentPage = 1
            )
        }
    }

    private fun applyInteraction(itemId: String, interaction: ItemInteraction) {
        _uiState.update { state ->
            state.copy(
                items = applyInteractionToList(
                    items = state.items,
                    itemId = itemId,
                    interaction = interaction,
                    type = currentType
                )
            )
        }
    }

    private suspend fun saveAndPublish(itemId: String, interaction: ItemInteraction) {
        val existing = interactionDao.getInteraction(itemId)
        interactionDao.upsertInteraction(
            FeedItemInteractionEntity(
                itemId = itemId,
                isLiked = interaction.isLiked,
                isCollected = interaction.isCollected,
                likeCount = interaction.likeCount,
                collectCount = interaction.collectCount,
                lastViewedAt = existing?.lastViewedAt
            )
        )
        InteractionSyncStore.publish(itemId, interaction)
    }
}
