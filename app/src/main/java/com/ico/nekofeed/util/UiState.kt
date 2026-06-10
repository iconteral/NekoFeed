package com.ico.nekofeed.util

import androidx.compose.runtime.Immutable
import com.ico.nekofeed.data.model.FeedCategory
import com.ico.nekofeed.data.model.FeedItem

// ============================================================================
// 【UI 层 · 状态数据类（UiState Pattern）】
// ============================================================================
//
// 📌 UiState 是 Compose + ViewModel 架构的核心模式：
//
//    ViewModel 持有一个不可变的 UiState → UI 层订阅它 → 数据变化时自动重组
//
//    ┌─ ViewModel ──────────────────────────────────────┐
//    │  _uiState = MutableStateFlow(FeedUiState())      │
//    │  _uiState.update { it.copy(isLoading = true) }    │
//    └──────────────────────────────────────────────────┘
//                        ↓ collectAsState()
//    ┌─ Composable ─────────────────────────────────────┐
//    │  val uiState by viewModel.uiState.collectAsState()│
//    │  if (uiState.isLoading) LoadingIndicator()        │
//    └──────────────────────────────────────────────────┘
//
// 📌 为什么用不可变 data class？
//    1. @Immutable 注解告诉 Compose "这个对象不会变"
//    2. 每次更新用 copy() 创建新对象，而不是修改旧对象
//    3. Compose 通过 == 比较新旧对象，决定是否重组
//    4. 如果直接修改属性，Compose 可能检测不到变化
//
// 📌 每个页面都有自己的 UiState：
//    - FeedUiState     → 首页（加载状态、数据列表、分类、标签等）
//    - FeedDetailUiState → 详情页
//    - SearchUiState   → 搜索页
//    - StatsUiState    → 统计页
//    - ChatUiState     → AI 对话页
// ====================================================================

/**
 * FeedUiState —— 首页信息流的 UI 状态
 *
 * 🔑 字段分组：
 *    - 加载状态：isLoading / isRefreshing / isLoadingMore
 *    - 数据：items / availableTags / selectedCategory
 *    - 分页：hasMore
 *    - 错误：errorMessage / usingFallback
 *    - AI：isAiLoading / isAiEnabled
 */
@Immutable
data class FeedUiState(
    val isLoading: Boolean = false,        // 首次加载中
    val isRefreshing: Boolean = false,     // 下拉刷新中
    val isLoadingMore: Boolean = false,    // 加载更多中（分页）
    val hasMore: Boolean = true,           // 是否还有更多数据
    val items: List<FeedItem> = emptyList(), // 当前显示的数据列表
    val selectedCategory: FeedCategory = FeedCategory.FEATURED, // 当前选中的分类
    val selectedTags: List<String> = emptyList(),  // 当前选中的标签筛选
    val availableTags: List<String> = emptyList(), // 可用的标签列表
    val errorMessage: String? = null,      // 错误信息（null = 无错误）
    val usingFallback: Boolean = false,    // 是否在使用降级数据
    val isAiLoading: Boolean = false,      // AI 批量分析中
    val isAiEnabled: Boolean = true        // AI 功能是否启用
)

/**
 * FeedDetailUiState —— 详情页的 UI 状态
 */
data class FeedDetailUiState(
    val item: FeedItem? = null,
    val isLoading: Boolean = false,
    val isVideoPlaying: Boolean = false,
    val errorMessage: String? = null
)

/**
 * SearchUiState —— 搜索页的 UI 状态
 */
data class SearchUiState(
    val query: String = "",                     // 用户输入的搜索词
    val parsedKeywords: List<String> = emptyList(), // AI 解析的关键词
    val matchedTags: List<String> = emptyList(),    // 匹配到的标签
    val results: List<FeedItem> = emptyList(),      // 搜索结果
    val isSearching: Boolean = false,               // 是否正在搜索
    val hasSearched: Boolean = false,               // 是否已经搜索过（区分"未搜索"和"无结果"）
    val errorMessage: String? = null
)

/**
 * StatsUiState —— 统计页的 UI 状态
 */
data class StatsUiState(
    val totalExposure: Int = 0,   // 总曝光数
    val totalClick: Int = 0,      // 总点击数
    val totalLike: Int = 0,       // 总点赞数
    val totalCollect: Int = 0,    // 总收藏数
    val totalShare: Int = 0,      // 总分享数
    val totalPlay: Int = 0,       // 总播放数
    val ctr: Float = 0f,          // 点击率 = click / exposure
    val topItems: List<FeedItem> = emptyList() // 热门条目
)

/**
 * ChatUiState —— AI 对话页的 UI 状态
 */
data class ChatUiState(
    val messages: List<ChatBubble> = emptyList(), // 聊天消息列表
    val isAiTyping: Boolean = false,              // AI 正在输入中
    val errorMessage: String? = null
)

/**
 * ChatBubble —— 单条聊天消息
 */
data class ChatBubble(
    val id: Long = 0,
    val role: String,                          // "user" 或 "assistant"
    val content: String,                       // 消息内容
    val recommendedItems: List<FeedItem> = emptyList(), // AI 推荐的 Feed 条目
    val timestamp: Long = System.currentTimeMillis()
)
