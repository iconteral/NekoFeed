package com.ico.nekofeed.util

import androidx.compose.runtime.Immutable
import com.ico.nekofeed.data.model.FeedCategory
import com.ico.nekofeed.data.model.FeedItem

// ============================================================================
// 【UI 层 · 状态数据类（UiState）】
// ============================================================================
//
// 📌 UiState 是 Compose + ViewModel 架构的核心概念。
//    每个页面都有一个对应的 UiState data class，描述该页面"当前长什么样"。
//
// 📌 设计原则：
//    1. 不可变（@Immutable / data class）→ Compose 能安全跳过未变化的重组
//    2. 所有字段都有默认值 → 创建时不需要填满所有字段
//    3. ViewModel 持有它（通过 MutableStateFlow），UI 层只读
//    4. 更新时用 .copy() 创建新副本，不修改原对象
//
// 📌 典型的 UI 状态包含：
//    - 数据：items / results / messages
//    - 状态：isLoading / isRefreshing / isAiTyping
//    - 错误：errorMessage（null = 无错误）
//    - 选择：selectedCategory / selectedTags
//
// 📌 数据流：
//    ViewModel:  _uiState.update { it.copy(isLoading = true) }
//                        ↓ StateFlow
//    UI:         val uiState by viewModel.uiState.collectAsState()
//                        ↓ Compose 重组
//    Screen:     if (uiState.isLoading) LoadingIndicator()
// ====================================================================

/**
 * FeedUiState —— 首页信息流的 UI 状态
 *
 * 🔑 字段分组：
 *    - 加载控制：isLoading / isRefreshing / isLoadingMore / hasMore
 *    - 数据：items（当前显示的列表）
 *    - 筛选：selectedCategory / selectedTags / availableTags
 *    - 错误：errorMessage / usingFallback
 *    - AI：isAiEnabled / isAiLoading
 */
@Immutable
data class FeedUiState(
    val isLoading: Boolean = false,           // 首次加载中（显示骨架屏）
    val isRefreshing: Boolean = false,        // 下拉刷新中（显示刷新指示器）
    val isLoadingMore: Boolean = false,       // 加载更多中（底部显示 loading）
    val hasMore: Boolean = true,              // 是否还有更多数据（没有则不触发 loadMore）
    val items: List<FeedItem> = emptyList(),  // 当前显示的 Feed 列表
    val selectedCategory: FeedCategory = FeedCategory.FEATURED, // 当前选中的频道
    val selectedTags: List<String> = emptyList(),   // 已选中的标签
    val availableTags: List<String> = emptyList(),  // 可用标签列表
    val errorMessage: String? = null,         // 错误信息（null = 无错误）
    val usingFallback: Boolean = false,       // 是否在使用降级数据
    val isAiLoading: Boolean = false,         // AI 是否正在批量分析
    val isAiEnabled: Boolean = true           // AI 功能是否启用
)

/**
 * FeedDetailUiState —— 详情页的 UI 状态
 */
data class FeedDetailUiState(
    val item: FeedItem? = null,         // 当前显示的内容
    val isLoading: Boolean = false,     // 加载中
    val isVideoPlaying: Boolean = false,// 视频是否在播放
    val errorMessage: String? = null    // 错误信息
)

/**
 * SearchUiState —— 搜索页的 UI 状态
 *
 * 🔑 搜索结果分两部分：
 *    - parsedKeywords: AI 解析后的关键词
 *    - matchedTags:    匹配到的标签
 *    - results:        搜索结果列表
 */
data class SearchUiState(
    val query: String = "",                        // 用户输入的搜索词
    val parsedKeywords: List<String> = emptyList(),// AI 解析后的关键词
    val matchedTags: List<String> = emptyList(),   // 匹配到的标签
    val results: List<FeedItem> = emptyList(),     // 搜索结果
    val isSearching: Boolean = false,              // 搜索中
    val hasSearched: Boolean = false,              // 是否已执行过搜索
    val errorMessage: String? = null               // 错误信息
)

/**
 * StatsUiState —— 统计页的 UI 状态
 *
 * 包含曝光、点击、播放、点赞、收藏、分享的总数和 CTR。
 * CTR（Click-Through Rate）= 点击数 / 曝光数
 */
data class StatsUiState(
    val totalExposure: Int = 0,   // 总曝光数
    val totalClick: Int = 0,      // 总点击数
    val totalLike: Int = 0,       // 总点赞数
    val totalCollect: Int = 0,    // 总收藏数
    val totalShare: Int = 0,      // 总分享数
    val totalPlay: Int = 0,       // 总播放数
    val ctr: Float = 0f,          // 点击率 = click / exposure
    val topItems: List<FeedItem> = emptyList() // 热门内容排行
)

/**
 * ChatUiState —— AI 对话页的 UI 状态
 */
data class ChatUiState(
    val messages: List<ChatBubble> = emptyList(), // 聊天消息列表
    val isAiTyping: Boolean = false,              // AI 是否正在输入
    val errorMessage: String? = null               // 错误信息
)

/**
 * ChatBubble —— 单条聊天消息
 *
 * @param role       角色："user"（用户）或 "assistant"（AI）
 * @param content    消息内容
 * @param recommendedItems AI 推荐的相关 Feed 条目
 */
data class ChatBubble(
    val id: Long = 0,
    val role: String,
    val content: String,
    val recommendedItems: List<FeedItem> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)
