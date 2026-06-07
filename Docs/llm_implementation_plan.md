# NekoFeed LLM 接入实现计划

## 背景与目标

当前 App 已实现：`FeedItem` 数据模型、`FeedRepository` 拉取服务端数据、`SearchScreen`（关键词匹配搜索）、DataStore 存 Token、Fallback 本地数据兜底。

**AI 字段（`aiSummary`、`aiTags`、`aiReason`）目前全部依赖服务端返回，搜索也是纯本地关键词匹配，没有真正的 LLM 调用。**

本计划目标：
1. 接入自定义 OpenAI-compatible endpoint（用户可配置 baseUrl + model + apiKey）
2. 用 **Room** 维护本地 AI 结果缓存库（避免重复调用）
3. 构建 **用户画像**（兴趣标签、交互历史偏好，存 DataStore/Room）
4. 实现真正的 **AI 摘要 / 标签生成**
5. 实现真正的 **AI 对话式搜索**（LLM 理解 query → 结构化过滤）
6. 提供 **AI 设置页**（endpoint / model / apiKey 配置 + 测试连接）

---

## User Review Required

> [!IMPORTANT]
> **用户画像数据范围**：计划存储用户的标签点击历史、点赞/收藏的 itemType 偏好、浏览时长分布。这些数据全部本地存储，不上传，请确认是否接受。

> [!IMPORTANT]
> **AI 调用时机**：AI 摘要生成有两种策略可选：
> - 方案 A：Feed 加载完成后**后台批量**为缺少 aiSummary 的 item 生成（低延迟，用户无感知）
> - 方案 B：用户**打开详情页时**按需生成（节省 API 调用次数）
>
> 推荐方案 A（批量后台生成），但每次最多处理 5 个 item，避免 API 压力。

> [!WARNING]
> **依赖变更**：需要新增 Room 依赖（数据库）和 kotlinx-coroutines-core（已有，确认版本）。`build.gradle.kts` 需要添加 Room。

---

## Open Questions

1. **AI endpoint 默认值**：是否有默认的 baseUrl、model 名称？（例如 `http://localhost:11434/v1` 用于 Ollama，或 `https://api.openai.com/v1`）
2. **AI 摘要语言**：系统 prompt 使用中文回复，还是跟随内容语言？
3. **Room 迁移策略**：首次引入 Room，是否允许 `fallbackToDestructiveMigration`？

---

## 现状分析

| 模块 | 现状 | 问题 |
|------|------|------|
| `FeedRepository` | 拉取数据、内存缓存 | 无持久化缓存，无 AI 结果缓存 |
| `SearchScreen` | 本地关键词匹配 | 非 LLM，用 `Thread.sleep` 伪造延迟 |
| `AiRepository` | **不存在** | 需要从零创建 |
| `用户画像` | **不存在** | 需要从零创建 |
| `AI 设置` | **不存在** | 需要设置页 |
| `FeedViewModel.searchItems` | 纯字符串匹配 | 需要接入 LLM query 解析 |

---

## Proposed Changes

### Layer 0 — 依赖配置

#### [MODIFY] [build.gradle.kts](file:///e:/NekoFeed/app/build.gradle.kts)

新增 Room、Room KSP 及 kotlin-serialization：

```kotlin
// Room
implementation("androidx.room:room-runtime:2.7.1")
implementation("androidx.room:room-ktx:2.7.1")
ksp("androidx.room:room-compiler:2.7.1")

// Kotlin JSON serialization（解析 LLM 返回）
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
```

同时在 plugins 块加 `alias(libs.plugins.ksp)` 和 `alias(libs.plugins.kotlin.serialization)`。

---

### Layer 1 — 本地数据库（Room）

新建 `data/local/db/` 目录，存放 Room 相关文件。

#### [NEW] `data/local/db/AiCacheEntity.kt`

```kotlin
@Entity(tableName = "ai_cache")
data class AiCacheEntity(
    @PrimaryKey val itemId: String,
    val aiSummary: String?,
    val aiTags: String,          // JSON array string
    val aiReason: String?,
    val modelUsed: String,
    val createdAt: Long = System.currentTimeMillis()
)
```

#### [NEW] `data/local/db/UserProfileEntity.kt`

```kotlin
@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val tag: String,
    val interestScore: Float,    // 加权点击/点赞/收藏得分
    val lastUpdated: Long = System.currentTimeMillis()
)
```

#### [NEW] `data/local/db/AiCacheDao.kt`

提供：
- `getCache(itemId): AiCacheEntity?`
- `insertCache(entity)`
- `deleteOldCache(beforeTime: Long)` （7天TTL清理）

#### [NEW] `data/local/db/UserProfileDao.kt`

提供：
- `getTopTags(limit: Int): List<UserProfileEntity>`
- `upsertTag(entity)`

#### [NEW] `data/local/db/NekoFeedDatabase.kt`

```kotlin
@Database(entities = [AiCacheEntity::class, UserProfileEntity::class], version = 1)
abstract class NekoFeedDatabase : RoomDatabase() {
    abstract fun aiCacheDao(): AiCacheDao
    abstract fun userProfileDao(): UserProfileDao

    companion object {
        fun getInstance(context: Context): NekoFeedDatabase { ... }
    }
}
```

---

### Layer 2 — AI 网络层（OpenAI-compatible Client）

#### [NEW] `data/remote/LlmApi.kt`

用 Retrofit 定义 OpenAI Chat Completions 接口：

```kotlin
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
    @SerializedName("max_tokens") val maxTokens: Int = 512,
    val temperature: Float = 0.3f,
    @SerializedName("response_format") val responseFormat: ResponseFormat? = null
)

data class ChatMessage(val role: String, val content: String)
data class ChatResponse(val choices: List<Choice>)
data class Choice(val message: ChatMessage)
data class ResponseFormat(val type: String = "json_object")
```

#### [NEW] `data/remote/LlmClientFactory.kt`

动态构建 Retrofit 实例（支持用户配置 baseUrl）：

```kotlin
object LlmClientFactory {
    fun create(baseUrl: String, timeoutSeconds: Long = 30): LlmApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl(baseUrl.trimEnd('/') + "/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(LlmApi::class.java)
    }
}
```

---

### Layer 3 — AI 设置存储

#### [MODIFY] `data/local/TokenManager.kt`

扩展 DataStore Key，新增：
- `LLM_BASE_URL_KEY`（默认 `""`）
- `LLM_MODEL_KEY`（默认 `"gpt-4o-mini"`）
- `LLM_API_KEY_KEY`

新增方法 `saveLlmConfig(baseUrl, model, apiKey)`、`getLlmConfig(): LlmConfig`。

---

### Layer 4 — AiRepository（核心 AI 逻辑）

#### [NEW] `data/repository/AiRepository.kt`

职责：

1. **`generateFeedAi(item: FeedItem): AiResult`**
   - 先查 Room 缓存（命中则直接返回）
   - 未命中则调 LLM，解析 JSON 返回的 `{summary, tags, reason}`
   - 结果写入 Room 缓存
   - 降级：LLM 失败时返回 `null`（UI 展示原始 summary）

2. **`parseSearchQuery(query: String, userProfile: List<String>): SearchIntent`**
   - 让 LLM 解析自然语言搜索词
   - 返回 `SearchIntent(keywords, itemTypes, tags, explanation)`
   - 系统 prompt 注入用户画像 top-3 标签（个性化搜索）

3. **`batchGenerateAi(items: List<FeedItem>)`**
   - 过滤掉已有缓存的 item
   - 最多取前 N 个（可配置，默认 5）
   - 串行（或并发 2）调用 `generateFeedAi`

4. **`testConnection(): Result<String>`**
   - 发送测试请求验证 endpoint 可用性

```kotlin
data class AiResult(
    val aiSummary: String?,
    val aiTags: List<String>,
    val aiReason: String?,
    val fromCache: Boolean
)

data class SearchIntent(
    val keywords: List<String>,
    val itemTypes: List<String>,   // ["article", "ad", "video"]
    val tags: List<String>,
    val explanation: String        // AI 解释给用户看
)
```

---

### Layer 5 — 用户画像（UserProfileRepository）

#### [NEW] `data/repository/UserProfileRepository.kt`

职责：
- `recordInteraction(item: FeedItem, action: InteractionType)` 更新标签兴趣分
  - LIKE: +3，COLLECT: +4，CLICK: +1，SHARE: +2
  - 标签来自 `item.displayTags`
- `getTopInterestTags(limit: Int = 5): List<String>` 返回高分标签
- 分数衰减：每次记录时对所有分数 × 0.99（防止早期标签永远占主导）

```kotlin
enum class InteractionType { CLICK, LIKE, COLLECT, SHARE }
```

---

### Layer 6 — ViewModel 层整合

#### [MODIFY] `ui/feed/FolderViewModel.kt`（即 FeedViewModel）

1. 注入 `AiRepository` 和 `UserProfileRepository`
2. `loadFeed()` 成功后，启动 `viewModelScope.launch { aiRepository.batchGenerateAi(items) }` 后台生成
3. AI 生成完成后，合并更新 `allItems`（`copy(aiSummary=..., aiTags=...)` ）
4. `toggleLike/Collect/Share` 时调用 `userProfileRepository.recordInteraction`
5. 新增 `uiState.isAiLoading: Boolean` 显示 AI 加载角标

#### [MODIFY] `ui/search/SearchScreen.kt`

1. 引入 `SearchViewModel`（新建）
2. 移除 `Thread.sleep` 伪延迟
3. 调用 `SearchViewModel.search(query)` → 走 `AiRepository.parseSearchQuery` → 结构化过滤

#### [NEW] `ui/search/SearchViewModel.kt`

```kotlin
class SearchViewModel(
    private val aiRepository: AiRepository,
    private val userProfileRepository: UserProfileRepository
) : ViewModel() {
    val uiState: StateFlow<SearchUiState>

    fun search(query: String, allItems: List<FeedItem>)
    // 1. 调 AiRepository.parseSearchQuery（LLM）
    // 2. 用 SearchIntent 在 allItems 中过滤（加权评分）
    // 3. LLM 失败时退化为原有关键词匹配
}
```

---

### Layer 7 — AI 设置 UI

#### [NEW] `ui/settings/AiSettingsScreen.kt`

页面结构：
```
┌──────────────────────────────┐
│ ← AI 设置                    │
├──────────────────────────────┤
│ Endpoint URL                 │
│ [http://localhost:11434/v1 ] │
│                              │
│ 模型名称                      │
│ [gpt-4o-mini              ]  │
│                              │
│ API Key (可留空)              │
│ [sk-...                   ]  │
├──────────────────────────────┤
│ [   测试连接   ]              │
│ ✓ 连接成功 / ✗ 错误信息       │
├──────────────────────────────┤
│ AI 功能开关                   │
│ ● 自动生成摘要               │
│ ● 智能搜索（关闭则退化关键词） │
├──────────────────────────────┤
│ 缓存管理                      │
│ 已缓存 X 条 AI 结果  [清空]   │
└──────────────────────────────┘
```

#### [NEW] `ui/settings/AiSettingsViewModel.kt`

#### [MODIFY] `navigation/AppNavHost.kt`

新增 `"ai_settings"` 路由，从 ProfileScreen 或 FeedScreen TopAppBar 进入。

---

### Layer 8 — Prompt 设计

#### 摘要 / 标签生成 Prompt（`AiRepository`）

```
系统：你是一个内容分析助手，请对以下信息流内容进行分析，用中文输出 JSON。
格式：{"summary":"一句话摘要(≤50字)","tags":["标签1","标签2","标签3"],"reason":"推荐理由(≤30字)"}

用户：
标题：{title}
类型：{itemType}
原始摘要：{summary}
```

#### 对话式搜索 Prompt

```
系统：你是一个搜索理解助手。用户画像偏好标签：{topTags}。
请解析用户搜索意图，用 JSON 输出。
格式：{"keywords":["词1"],"item_types":["article","ad"],"tags":["标签"],"explanation":"AI理解：..."}
item_types 可选值：article, video, ad, product, local
如无法判断 item_types，返回空数组。

用户：{query}
```

---

## 整体数据流（AI 路径）

```
FeedScreen 加载完成
    ↓
FeedViewModel.batchGenerateAi(items)
    ↓
AiRepository.generateFeedAi(item)
    ├── 命中 Room 缓存 → 直接返回
    └── 未命中 → LlmApi.chatCompletion()
                    ↓
               解析 JSON → AiResult
                    ↓
               写入 Room 缓存
                    ↓
               FeedViewModel 合并更新 allItems
                    ↓
               Compose 重组（aiSummary/aiTags 更新）

用户搜索
    ↓
SearchViewModel.search(query)
    ↓
AiRepository.parseSearchQuery(query, userProfile.top5Tags)
    ├── LLM 成功 → SearchIntent → 加权过滤 allItems
    └── LLM 失败 → 退化关键词匹配（原逻辑保留）
```

---

## Verification Plan

### 构建验证
- `./gradlew assembleDebug` 确认无编译错误

### 手动验证（开发机）
1. 进入 AI 设置页，填入本地 LLM endpoint（如 Ollama），点击"测试连接"
2. 返回 FeedScreen，观察 AI 摘要是否在 5~10 秒内出现在卡片上
3. 进入 SearchScreen，输入"学生党平价耳机"，观察 AI 理解结果卡片是否显示正确的意图解析
4. 点赞/收藏几个 item 后再次搜索，验证用户画像对结果排序有影响
5. 断网情况下：AI 摘要应展示原始 summary，搜索退化到关键词匹配

### 降级验证
- 关闭 AI 开关：`aiRepository` 所有方法直接返回 null，UI 展示原始数据
- endpoint 错误：Toast 提示"AI 服务不可用，使用本地搜索"
