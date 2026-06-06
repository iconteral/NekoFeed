package com.ico.nekofeed.ui.feed

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.ico.nekofeed.data.local.TokenManager
import com.ico.nekofeed.data.local.db.NekoFeedDatabase
import com.ico.nekofeed.data.model.FeedCategory
import com.ico.nekofeed.data.model.FeedItem
import com.ico.nekofeed.data.paging.FeedPagingSource
import com.ico.nekofeed.data.remote.RetrofitClient
import com.ico.nekofeed.data.repository.AiRepository
import com.ico.nekofeed.data.repository.FeedRepository
import com.ico.nekofeed.data.repository.InteractionType
import com.ico.nekofeed.data.repository.UserProfileRepository
import com.ico.nekofeed.data.repository.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class PagingFeedViewModel(application: Application) : AndroidViewModel(application) {
    private val tokenManager = TokenManager(application)
    private val repository = FeedRepository(RetrofitClient.feedApi, tokenManager)
    private val userRepository = UserRepository(RetrofitClient.feedApi)
    private val database = NekoFeedDatabase.getInstance(application)
    val aiRepository = AiRepository(tokenManager, database.aiCacheDao())
    private val userProfileRepository = UserProfileRepository(database.userProfileDao())

    // 当前选中的分类
    private val _selectedCategory = MutableStateFlow(FeedCategory.FEATURED)
    val selectedCategory: StateFlow<FeedCategory> = _selectedCategory.asStateFlow()

    // AI 启用状态
    private val _isAiEnabled = MutableStateFlow(true)
    val isAiEnabled: StateFlow<Boolean> = _isAiEnabled.asStateFlow()

    // 正在播放的视频 ID
    private val _playingItemId = MutableStateFlow<String?>(null)
    val playingItemId: StateFlow<String?> = _playingItemId.asStateFlow()

    // 分页数据流
    val pagingDataFlow: Flow<PagingData<FeedItem>> = _selectedCategory
        .flatMapLatest { category ->
            Pager(
                config = PagingConfig(
                    pageSize = 20,
                    enablePlaceholders = false,
                    prefetchDistance = 5,
                    initialLoadSize = 20
                ),
                pagingSourceFactory = { FeedPagingSource(repository, category) }
            ).flow
        }
        .cachedIn(viewModelScope)

    // 本地交互状态缓存（乐观更新用）
    private val interactionCache = mutableMapOf<String, FeedItemInteraction>()

    data class FeedItemInteraction(
        val isLiked: Boolean? = null,
        val isCollected: Boolean? = null,
        val likeCount: Int? = null,
        val collectCount: Int? = null
    )

    init {
        observeLlmConfig()
    }

    private fun observeLlmConfig() {
        viewModelScope.launch {
            tokenManager.llmConfig.collect { config ->
                _isAiEnabled.value = config.aiEnabled
            }
        }
    }

    fun selectCategory(category: FeedCategory) {
        _selectedCategory.value = category
    }

    fun setPlayingItemId(id: String?) {
        _playingItemId.value = id
    }

    fun toggleLike(itemId: String) {
        viewModelScope.launch {
            // 乐观更新
            val current = interactionCache[itemId] ?: FeedItemInteraction()
            val isLiked = current.isLiked?.not() ?: true
            interactionCache[itemId] = current.copy(isLiked = isLiked)

            // 服务端同步
            userRepository.toggleLike(itemId).fold(
                onSuccess = { interaction ->
                    interactionCache[itemId] = FeedItemInteraction(
                        isLiked = interaction.isLiked,
                        isCollected = interaction.isCollected,
                        likeCount = interaction.likeCount,
                        collectCount = interaction.collectCount
                    )
                },
                onFailure = {
                    // 回滚
                    interactionCache[itemId] = current
                }
            )
        }
    }

    fun toggleCollect(itemId: String) {
        viewModelScope.launch {
            val current = interactionCache[itemId] ?: FeedItemInteraction()
            val isCollected = current.isCollected?.not() ?: true
            interactionCache[itemId] = current.copy(isCollected = isCollected)

            userRepository.toggleCollect(itemId).fold(
                onSuccess = { interaction ->
                    interactionCache[itemId] = FeedItemInteraction(
                        isLiked = interaction.isLiked,
                        isCollected = interaction.isCollected,
                        likeCount = interaction.likeCount,
                        collectCount = interaction.collectCount
                    )
                },
                onFailure = {
                    interactionCache[itemId] = current
                }
            )
        }
    }

    fun toggleShare(itemId: String) {
        // 分享不需要服务端同步
    }

    fun recordExposure(itemId: String) {
        // 曝光记录可以批量处理，不需要立即同步
    }

    fun recordClick(itemId: String) {
        viewModelScope.launch {
            userRepository.recordHistory(itemId)
        }
    }

    fun requestAiAnalysis(item: FeedItem) {
        if (!item.aiSummary.isNullOrBlank()) return

        viewModelScope.launch {
            val config = tokenManager.getLlmConfig()
            if (!config.aiEnabled || config.baseUrl.isBlank()) return@launch

            aiRepository.generateFeedAi(item)
        }
    }

    fun getInteractionState(itemId: String): FeedItemInteraction {
        return interactionCache[itemId] ?: FeedItemInteraction()
    }
}
