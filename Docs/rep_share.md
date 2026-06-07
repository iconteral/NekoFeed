# Project NekoFeed 

> **汇报时间**：2026-06-05

---

## 一、项目概述

### 1.1 项目简介

**NekoFeed** 是一个完整的 AI 信息流系统，包含：

| 组成 | 技术栈 | 说明 |
|------|--------|------|
| Android 客户端 | Kotlin + Jetpack Compose | 声明式 UI，Material 3 主题 |
| 本地 Feed Server | Python + FastAPI | RSS 聚合 + 数据清洗 |
| AI 增强层 | OpenAI 兼容 API | 双层 AI 架构 |

### 1.2 系统架构

```
┌─────────────────────────────────────────────────────────────┐
│                      上游数据源                               │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐    │
│  │ RSS 新闻  │  │ 科技资讯  │  │ 视频内容  │  │ 自定义广告 │    │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘    │
│       └──────────────┴──────────────┴──────────────┘         │
└─────────────────────────────────┬───────────────────────────┘
                                  ↓
┌─────────────────────────────────────────────────────────────┐
│                   NekoFeed Server                            │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐    │
│  │ RSS 抓取  │→│ 数据清洗  │→│ 媒体缓存  │→│ LLM 分类  │    │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘    │
│                         ↓                                    │
│                  ┌──────────┐                                │
│                  │ SQLite DB │                                │
│                  └────┬─────┘                                │
│                       ↓                                      │
│               ┌──────────────┐                               │
│               │ REST API     │                               │
│               │ /api/feed    │                               │
│               └──────┬───────┘                               │
└──────────────────────┼───────────────────────────────────────┘
                       ↓ HTTP JSON
┌─────────────────────────────────────────────────────────────┐
│                   Android App                                │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐    │
│  │ Retrofit  │→│ Repository│→│ ViewModel │→│ Compose   │    │
│  │ 网络层    │  │ 数据协调  │  │ 状态管理  │  │ UI 渲染   │    │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘    │
│       ↕               ↕               ↕                      │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐                   │
│  │ Room DB   │  │ DataStore │  │ ExoPlayer │                  │
│  │ AI 缓存   │  │ 配置存储  │  │ 视频播放  │                   │
│  └──────────┘  └──────────┘  └──────────┘                   │
└─────────────────────────────────────────────────────────────┘
```

---

## 二、核心数据模型

### 2.1 统一 FeedItem 模型

将广告、文章、视频、商品统一抽象为 `FeedItem`：

```kotlin
data class FeedItem(
    val id: String,
    
    // 基础内容
    val title: String,
    val summary: String?,
    val content: String?,
    
    // 来源信息
    val sourceName: String?,
    val sourceUrl: String?,
    
    // 分类与类型 ← 核心设计
    val itemType: String?,    // 内容类型：article/video/ad/product/local
    val cardType: String?,    // 卡片类型：large_image/small_image/video/product/text_only
    val category: String?,    // 频道分类：tech/ai/business/local/video/shopping
    
    // 媒体资源
    val imageUrl: String?,
    val mediaUrl: String?,
    
    // AI 分析结果
    val aiSummary: String?,
    val aiTags: List<String>?,
    val aiReason: String?,
    
    // 广告专属字段
    val brand: String?,
    val ctaText: String?,
    val priceText: String?,
    val isSponsored: Boolean,
    
    // 互动状态（服务端返回）
    val isLiked: Boolean,
    val isCollected: Boolean,
    val likeCount: Int,
    val collectCount: Int,
    
    // 统计数据
    val exposureCount: Int,
    val clickCount: Int,
    val playCount: Int
)
```

### 2.2 类型枚举设计

| 枚举 | 值 | 说明 |
|------|-----|------|
| **FeedItemType** | article, video, ad, product, local | 决定业务逻辑 |
| **FeedCardType** | large_image, small_image, video, product, text_only | 决定 UI 渲染 |
| **FeedCategory** | featured, tech, ai, business, local, video, shopping | 决定频道过滤 |

**设计亮点**：`itemType` × `cardType` 解耦，同一种内容可以有不同的展示方式

---

## 三、技术栈详解

### 3.1 Android 客户端

| 技术 | 用途 | 版本 |
|------|------|------|
| **Kotlin** | 主开发语言 | JVM 11 |
| **Jetpack Compose** | 声明式 UI | BOM 管理 |
| **Material 3** | 设计系统 | 1.5.0-alpha20 (Expressive) |
| **Navigation Compose** | 页面路由 | 嵌套 NavHost |
| **Retrofit + OkHttp** | 网络请求 | 15s 超时 + Bearer Token |
| **Coil** | 图片加载 | 内存 25% + 磁盘 2% 缓存 |
| **Media3 ExoPlayer** | 视频播放 | 100MB LRU 磁盘缓存 |
| **Room** | 本地数据库 | AI 缓存 + 用户画像 |
| **DataStore** | 键值存储 | Token/配置持久化 |

### 3.2 后端服务

| 技术 | 用途 |
|------|------|
| **Python 3.10+** | 主开发语言 |
| **FastAPI** | Web 框架，自动生成 OpenAPI |
| **SQLAlchemy** | ORM |
| **SQLite** | 轻量数据库 |
| **feedparser** | RSS/Atom 解析 |
| **httpx** | 异步 HTTP 客户端 |
| **python-jose** | JWT 认证 |

---

## 四、核心功能实现

### 4.1 多样式卡片渲染

根据 `cardType` 动态选择 5 种卡片组件：

```
┌─────────────────────────────────────────────────────────────┐
│                     FeedItemCard 路由                        │
│                                                             │
│   when (cardType) {                                         │
│       LARGE_IMAGE → LargeImageFeedCard   // 大图卡片         │
│       SMALL_IMAGE → SmallImageFeedCard   // 小图卡片         │
│       VIDEO       → VideoFeedCard        // 视频卡片         │
│       PRODUCT     → ProductFeedCard      // 商品卡片         │
│       TEXT_ONLY   → SmallImageFeedCard   // 纯文本退化       │
│   }                                                         │
└─────────────────────────────────────────────────────────────┘
```

**卡片类型展示**：

| 卡片 | 布局 | 适用场景 |
|------|------|----------|
| 大图卡片 | 顶部大图 + 标题 + AI 摘要 + 标签 | 品牌广告、视觉素材 |
| 小图卡片 | 左文右图 | 资讯文章、新闻 |
| 视频卡片 | 视频播放器 + 播放/静音控制 | 视频内容、视频广告 |
| 商品卡片 | 商品图 + 价格 + CTA 按钮 | 电商广告、商品推荐 |

### 4.2 曝光埋点系统

**实现方案**：基于 Compose `snapshotFlow` + 可见性检测

```kotlin
// FolderScreen.kt
LaunchedEffect(listState) {
    snapshotFlow {
        listState.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? String }
    }
        .distinctUntilChanged()
        .debounce(300L)           // 300ms 防抖
        .collect { visibleIds ->
            visibleIds.forEach { id ->
                onExposure(id)    // 记录曝光
            }
        }
}
```

**技术要点**：
- `visibleItemsInfo` 获取当前可见 item 列表
- `distinctUntilChanged` 避免重复上报
- `debounce(300ms)` 防止快速滚动时频繁触发
- `exposedItems` Set 集合保证每个 item 只记录一次

### 4.3 视频播放管理

**PlayerManager 单例设计**：

```kotlin
class PlayerManager private constructor(context: Context) {
    companion object {
        @Volatile
        private var instance: PlayerManager? = null
        
        fun getInstance(context: Context): PlayerManager {
            return instance ?: synchronized(this) {
                instance ?: PlayerManager(context.applicationContext)
            }
        }
    }
    
    val exoPlayer: ExoPlayer
    private var simpleCache: SimpleCache  // 100MB LRU 缓存
    
    init {
        // 100MB 磁盘缓存
        val cacheSize: Long = 100 * 1024 * 1024
        val cacheEvictor = LeastRecentlyUsedCacheEvictor(cacheSize)
        simpleCache = SimpleCache(cacheDir, cacheEvictor, databaseProvider)
        
        // CacheDataSource 实现网络+缓存数据源
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(simpleCache)
            .setUpstreamDataSourceFactory(dataSourceFactory)
        
        exoPlayer = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
            .build()
    }
}
```

**资源管理策略**：
- 全局单例，避免重复创建播放器
- 100MB LRU 磁盘缓存，减少重复下载
- 列表中同一时间只播放一个视频
- 默认静音，用户可手动开启
- 循环播放模式（REPEAT_MODE_ONE）

### 4.4 图片加载策略

**Coil 配置**：

```kotlin
class NekoFeedApp : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)    // 占可用内存 25%
                    .build()
            }
            .diskCachePolicy(CachePolicy.ENABLED)
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02)    // 占磁盘 2%
                    .build()
            }
            .crossfade(false)
            .build()
    }
}
```

### 4.5 点赞交互流程

**乐观更新 + 服务端同步 + 失败回滚**：

```
用户点击 ❤️
    ↓
① 乐观更新 UI（立即响应）
    ↓
② 异步调用 POST /api/items/{id}/like
    ↓
③ 服务端返回权威数据
    ↓
④ 用权威值覆盖本地状态
    ↓
（失败时）回滚到快照
```

```kotlin
fun toggleLike(itemId: String) {
    // 1. 保存快照
    val snapshot = allItems
    
    // 2. 乐观更新
    allItems = allItems.map { item ->
        if (item.id == itemId) {
            item.copy(
                isLiked = !item.isLiked,
                likeCount = if (item.isLiked) item.likeCount - 1 else item.likeCount + 1
            )
        } else item
    }
    updateFilteredItems()  // 立即刷新 UI
    
    // 3. 服务端同步
    viewModelScope.launch {
        userRepository.toggleLike(itemId).fold(
            onSuccess = { interaction ->
                // 4. 权威值覆盖
                allItems = allItems.map { item ->
                    if (item.id == itemId) {
                        item.copy(
                            isLiked = interaction.isLiked,
                            likeCount = interaction.likeCount
                        )
                    } else item
                }
            },
            onFailure = {
                // 5. 失败回滚
                allItems = snapshot
            }
        )
        updateFilteredItems()
    }
}
```

### 4.6 用户系统设计

**三态用户模型**：

```
匿名用户 (null)
    │  首次发送 X-Device-Id
    ↓
设备用户 (device_xxx)
    │  注册/登录
    ↓
正式用户 (username)
    │  互动数据自动迁移
    ↓
    ✓
```

**数据迁移**：登录时自动将设备用户的 likes/collects/history 迁移到真实用户

---

## 五、AI 能力详解

### 5.1 三层 AI 架构

| 层 | 位置 | 功能 | 触发时机 |
|----|------|------|----------|
| **服务端 AI** | NekoFeedServer | 内容分类、品牌识别、CTA/价格推断 | Admin 手动批量 |
| **客户端 AI — 内容分析** | Android App | 一句话摘要、智能标签、推荐理由 | 自动 + 手动 |
| **客户端 AI — 智能搜索** | Android App | 自然语言意图解析 → 结构化搜索条件 | 用户发起搜索 |
| **客户端 AI — 对话助手** | Android App | 多轮对话 + 个性化推荐 | 用户打开 AI 助手 Tab |

### 5.2 客户端 AI — 内容分析

**AiRepository 核心逻辑**：

```kotlin
suspend fun generateFeedAi(item: FeedItem): AiResult {
    // 1. 检查本地缓存
    val cached = aiCacheDao.getCache(item.id)
    if (cached != null) return AiResult(fromCache = true, ...)

    // 2. 调用 LLM API
    val response = llmApi.chatCompletion(
        system = "你是一个内容分析助手...",
        user = "标题：${item.title}\n类型：${item.itemType}\n摘要：${item.summary}"
    )

    // 3. 解析 JSON 响应 + 存入缓存
    val result = parseAiResponse(response)
    aiCacheDao.insertCache(AiCacheEntity(...))
    return AiResult(fromCache = false, ...)
}
```

**AI Prompt 设计**：

```
系统：你是一个内容分析助手，请对以下信息流内容进行分析，用中文输出 JSON。
格式：{"summary":"一句话摘要(≤50字)","tags":["标签1","标签2","标签3"],"reason":"推荐理由(≤30字)"}

用户：标题：{title}
类型：{itemType}
原始摘要：{summary}
```

**触发策略**：
- **批量生成**：每次加载/刷新/加载更多后，自动处理最多 5 条未缓存 item，间隔 500ms
- **按需生成**：item 滚入视口中心 800ms 后触发，Semaphore(2) 限制并发
- **缓存策略**：Room 本地存储，7 天自动清理，模型字段记录使用了哪个模型

### 5.3 客户端 AI — 智能搜索

**搜索流程**：

```
用户输入自然语言查询
    ↓
① 获取用户 Top 5 兴趣标签
    ↓
② LLM 解析搜索意图 → SearchIntent
    ↓
③ 基于意图的多维度评分匹配
    ↓
④ 返回排序结果
```

**Prompt 设计**（注入用户画像）：

```
系统：你是一个搜索理解助手。用户画像偏好标签：科技、AI、数码、运动、美食。
请解析用户搜索意图，用 JSON 输出。
格式：{"keywords":["词1"],"item_types":["article","video"],"tags":["标签"],"explanation":"AI理解：..."}

用户：推荐一些科技新闻
```

**评分匹配算法**：

```kotlin
fun filterByIntent(items, intent): List<ScoredItem> {
    items.map { item ->
        var score = 0
        // 标题匹配 +3
        // 摘要匹配 +2
        // 标签匹配 +2
        // 内容匹配 +1
        // 类型匹配 +1.5
        item to score
    }.filter { it.second > 0 }.sortedByDescending { it.second }
}
```

**降级方案**：LLM 不可用时，回退到本地关键词分词匹配。

### 5.4 客户端 AI — 对话助手（Neko）

**功能概述**：底部导航栏 AI Tab，多轮对话式 LLM 聊天助手。

**核心能力**：
1. **用户画像感知**：系统 prompt 注入用户兴趣标签
2. **Feed 上下文感知**：系统 prompt 注入最近 20 条 feed 摘要
3. **个性化推荐**：用户自然语言询问，AI 从 feed 中筛选并以卡片展示
4. **通用对话**：闲聊、咨询，不限于推荐
5. **对话持久化**：Room 存储聊天记录，支持历史加载和清空

**System Prompt 模板**：

```
你是 NekoFeed 的 AI 助手「Neko」。你可以：
1. 根据用户兴趣推荐信息流内容
2. 回答用户关于内容的问题
3. 进行友好的日常对话

---
【用户画像】
兴趣标签（按偏好排序）：科技、AI、数码、运动、美食

---
【当前可推荐内容】（共 20 条）
1. [ID:abc123] GPT-5 发布 | 标签：AI,技术,大语言模型 | 类型：article
2. [ID:def456] Apple WWDC | 标签：科技,Apple | 类型：article
...

---
【回复规则】
- 使用中文回复，语气友好自然
- 当用户请求推荐内容时，从【当前可推荐内容】中选择最匹配的
- 推荐时在回复末尾追加一行 JSON：{"recommended_ids":["id1","id2",...]}
- 推荐 3~5 条最相关的内容，并简要说明推荐理由
- 如果没有匹配的内容，坦诚告知
- 非推荐请求则正常对话，不需要 JSON
```

**推荐卡片渲染**：解析 AI 回复中的 `recommended_ids` JSON，匹配本地 FeedItem，以 `FeedItemCard` 形式嵌入聊天气泡。

**防抖机制**：
- 视频自动播放：滑动停止 500ms 后触发
- AI 请求：滑动停止 800ms 后，只为屏幕中心的焦点 item 发起请求（而非所有可见 item）

### 5.5 用户记忆系统（个性化引擎）

**数据模型**：`user_profile` 表，tag → interestScore 映射

```kotlin
@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val tag: String,        // 兴趣标签，如 "科技"、"美食"
    val interestScore: Float,            // 加权分数
    val lastUpdated: Long                // 时间戳
)
```

**交互权重**：

| 用户行为 | 权重分数 | 说明 |
|----------|----------|------|
| 点击 (CLICK) | 1.0 | 基础兴趣信号 |
| 分享 (SHARE) | 2.0 | 主动传播 |
| 点赞 (LIKE) | 3.0 | 明确喜欢 |
| 收藏 (COLLECT) | 4.0 | 最强正向信号 |

**兴趣衰减算法**：

```kotlin
suspend fun recordInteraction(item: FeedItem, action: InteractionType) {
    // 1. 全局衰减：所有现有分数 × 0.99
    userProfileDao.decayAllScores(DECAY_FACTOR = 0.99f)

    // 2. 分数累加：被互动 item 的每个标签 += 行为分数
    for (tag in item.displayTags) {
        val existing = userProfileDao.getTag(tag)
        userProfileDao.upsertTag(
            UserProfileEntity(
                tag = tag,
                interestScore = (existing?.interestScore ?: 0f) + action.score
            )
        )
    }
}
```

**设计特点**：
- **时间衰减**：每次交互都对所有标签做 0.99× 衰减，长期不互动的兴趣自然消退
- **正向累积**：频繁 + 近期交互的标签分数持续上升
- **Top-N 检索**：`getTopInterestTags(5)` 返回分数最高的 5 个标签
- **AI 标签回流**：`displayTags` 优先使用 AI 生成的标签构建画像，形成 LLM → 画像 → LLM 反馈环

### 5.6 个性化闭环

```
用户互动 (like/collect/share/click)
    ↓
UserProfileRepository.recordInteraction()
    ├─ 所有标签分数 × 0.99（全局衰减）
    └─ 互动 item 的标签 += 行为权重
    ↓
user_profile 表更新
    ↓
下次 LLM 调用读取 Top 5 标签
    ↓
注入到：
  ├─ 智能搜索 system prompt（用户偏好标签）
  └─ AI 助手 system prompt（用户画像 + feed 上下文）
    ↓
LLM 响应个性化
    ↓
用户与推荐内容互动
    ↓
（循环 …）
```

### 5.7 AI 配置管理

用户可在设置页自定义：

| 配置项 | 说明 |
|--------|------|
| 服务器地址 | Feed Server 的 IP/域名 |
| Endpoint URL | OpenAI 兼容 API 地址 |
| API Key | 可选认证密钥 |
| 模型名称 | 如 gpt-4o-mini |
| 自动生成摘要 | 开关：后台为 Feed 生成 AI 摘要 |
| 智能搜索 | 开关：使用 AI 理解搜索意图 |
| 连接测试 | 一键验证 LLM 配置是否正确 |
| 缓存管理 | 查看/清空 AI 缓存 |

---

## 六、数据库设计

### 6.1 服务端表结构

```
┌─────────────────────────────────────────────────────────────┐
│                      SQLite Database                         │
├─────────────────────────────────────────────────────────────┤
│  upstream_feeds     │ RSS 源配置表                           │
│  ├─ id PK           │                                        │
│  ├─ name            │                                        │
│  ├─ url UK          │                                        │
│  ├─ category        │                                        │
│  └─ enabled         │                                        │
├─────────────────────┼───────────────────────────────────────│
│  feed_items         │ Feed 内容表                            │
│  ├─ id PK           │ UUID 格式                              │
│  ├─ title           │                                        │
│  ├─ summary         │                                        │
│  ├─ item_type       │ article/video/ad/product/local         │
│  ├─ card_type       │ large_image/small_image/video/...      │
│  ├─ category        │                                        │
│  ├─ image_url       │                                        │
│  ├─ media_url       │                                        │
│  ├─ ai_summary      │ 服务端 AI 分类结果                     │
│  └─ ai_enriched     │                                        │
├─────────────────────┼───────────────────────────────────────│
│  users              │ 用户表                                 │
│  ├─ id PK           │                                        │
│  ├─ username UK     │                                        │
│  ├─ device_id UK    │ 设备用户标识                           │
│  ├─ is_device       │ 是否设备用户                           │
│  └─ linked_user_id  │ 绑定的真实用户                         │
├─────────────────────┼───────────────────────────────────────│
│  user_likes         │ 点赞表                                 │
│  user_collects      │ 收藏表                                 │
│  user_history       │ 浏览历史表                             │
└─────────────────────┴───────────────────────────────────────┘
```

### 6.2 客户端 Room 数据库

| 表 | 用途 | 特性 |
|----|------|------|
| `ai_cache` | AI 结果缓存 | 7 天自动清理，记录使用的模型 |
| `user_profile` | 用户偏好画像 | 标签权重统计，0.99 衰减 |
| `chat_messages` | 聊天对话记录 | 多轮对话持久化，支持清空 |
| `feed_item_interaction` | 本地互动记录 | like/collect 状态 |

---

## 七、项目完成度

### 7.1 功能清单

| 功能 | 状态 | 说明 |
|------|:----:|------|
| 统一 FeedItem 模型 | ✅ | 广告/文章/视频/商品统一抽象 |
| 5 种卡片渲染 | ✅ | 大图/小图/视频/商品/纯文本 |
| 曝光埋点 | ✅ | snapshotFlow 可见性检测 + 300ms 防抖 |
| 点击统计 | ✅ | 进入详情页时记录 |
| 视频播放 | ✅ | ExoPlayer + 100MB 缓存 + 500ms 播放防抖 |
| 点赞/收藏/分享 | ✅ | 乐观更新 + 服务端同步 + 访客拦截 |
| AI 内容分析 | ✅ | 客户端 LLM 摘要/标签/理由 + Room 缓存 |
| AI 智能搜索 | ✅ | 自然语言意图解析 + 用户画像注入 |
| AI 对话助手 | ✅ | 多轮对话 + Feed 推荐卡片 + 历史持久化 |
| 用户记忆系统 | ✅ | 标签权重 + 交互衰减 + 个性化 prompt 注入 |
| 搜索功能 | ✅ | AI 意图解析 + 关键词评分匹配 |
| 用户系统 | ✅ | 三态用户 + 数据迁移 + 访客个人中心 |
| 统计可视化 | ✅ | StatsScreen 图表展示 |
| Tab 频道切换 | ✅ | 7 个频道分类 |
| 下拉刷新/上拉加载 | ✅ | 分页加载（offset 分页） |
| 本地数据兜底 | ✅ | Server/图片/视频/AI 任一失败，App 仍稳定 |
| Material 3 主题 | ✅ | Expressive 风格 |
| GitHub Actions CI | ✅ | 自动编译 |

### 7.2 代码统计

| 模块 | 代码量 |
|------|--------|
| Android 客户端 | ~10000+ 行 |
| Feed Server | ~2000+ 行 |
| 设计文档 | ~5000+ 行 |

---

## 八、技术亮点总结

### 8.1 架构设计

1. **统一 FeedItem 模型** — 一套数据结构支持广告/文章/视频/商品
2. **itemType × cardType 解耦** — 内容类型与展示方式独立
3. **分层架构** — UI → ViewModel → Repository → Data Source
4. **三层 AI 架构** — 服务端批量分类 + 客户端实时摘要 + 对话式推荐

### 8.2 AI 个性化引擎

1. **用户记忆系统** — 标签权重追踪，交互衰减算法（0.99×），自动遗忘冷门兴趣
2. **Prompt 注入策略** — 用户画像 + Feed 上下文注入 LLM system prompt
3. **个性化闭环** — 用户互动 → 标签更新 → prompt 注入 → AI 个性化响应 → 互动
4. **AI 标签回流** — AI 生成的标签反哺用户画像，形成 LLM ↔ 画像反馈环

### 8.3 性能优化

1. **视频播放资源复用** — ExoPlayer 单例 + 100MB LRU 缓存
2. **图片加载缓存** — Coil 内存 25% + 磁盘 2%
3. **AI 结果缓存** — Room 本地存储，7 天自动清理
4. **滑动防抖** — 曝光 300ms / 视频播放 500ms / AI 请求 800ms
5. **AI 请求聚焦** — 只为屏幕中心 item 发起 AI 请求，非所有可见 item
6. **LazyColumn 优化** — contentType 按卡片类型回收，减少重组

### 8.4 用户体验

1. **乐观更新** — 点赞即时反馈，无感知延迟
2. **完整降级方案** — Server/图片/视频/AI 任一失败，App 仍稳定
3. **三态用户模型** — 匿名→设备→注册，数据无缝迁移
4. **访客模式** — 未登录可浏览，点赞/收藏拦截跳转登录
5. **AI 助手** — 自然语言对话 + Feed 推荐卡片，聊天记录持久化
6. **Material 3 Expressive** — 现代化 UI 设计

### 8.5 工程实践

1. **双层 AI 架构** — 服务端批量分类 + 客户端实时摘要/搜索/对话
2. **统一 API 设计** — RESTful 风格，OpenAPI 文档自动生成
3. **URL 容错** — 自动补全 http:// scheme，防止用户输入 IP 崩溃
4. **CI/CD** — GitHub Actions 自动编译

---

## 九、演示流程

### 9.1 演示脚本

```
1. 启动 Feed Server
   → 展示 Admin 面板，刷新 RSS 数据

2. 打开 Android App
   → 展示信息流首页（混合卡片）
   → 滚动列表，观察曝光埋点（统计数字增加）
   → 展示滑动防抖：快速滑过不触发 AI，停下后才加载

3. AI 内容分析
   → 展示卡片上的 AI 摘要 + 标签
   → 展示未请求 AI 的卡片（无 loading UI）
   → 展示 AI 请求中的波浪进度条

4. 点击视频卡片
   → 展示视频播放 + 静音控制
   → 展示 ExoPlayer 缓存效果
   → 展示 500ms 播放防抖

5. 点赞/收藏操作
   → 已登录：展示乐观更新即时反馈
   → 访客：展示跳转登录页拦截

6. Tab 频道切换
   → 展示分类过滤功能（从服务器重新加载）

7. AI 智能搜索
   → 输入自然语言查询（如"推荐一些科技新闻"）
   → 展示 AI 意图解析 + 个性化结果

8. AI 对话助手
   → 打开底部 AI Tab
   → 展示多轮对话
   → 输入"推荐科技相关内容"
   → 展示 AI 回复 + FeedItemCard 推荐卡片
   → 点击推荐卡片跳转详情

9. 用户记忆系统
   → 展示点赞/收藏后用户画像变化
   → 展示 AI prompt 注入的个性化标签

10. 个人中心
    → 访客模式：设置 + 登录按钮
    → 已登录：点赞/收藏/历史/退出

11. 统计页面
    → 展示曝光/点击/点赞数据可视化
```

---

## 十、问题与规划

### 10.1 当前可优化项

| 优先级 | 问题 | 说明 |
|:------:|------|------|
| 🟡 | 详情页互动同步 | 返回列表时状态可能不一致 |
| 🟡 | CTA 按钮空实现 | 商品详情页 CTA 需确定跳转目标 |
| 🟢 | 负向信号 | 当前只有正向交互，缺少"不感兴趣"机制 |
| 🟢 | 推荐算法 | 当前按时间排序，可增加协同过滤 |
| 🟢 | 完播率统计 | 视频播放完成度统计 |
| 🟢 | 跨设备同步 | 用户画像纯本地，不同设备不共享 |

### 10.2 后续规划

1. **P0**：详情页互动同步修复
2. **P1**：CTA 按钮功能实现
3. **P1**：用户画像云端同步
4. **P2**：负向信号（不感兴趣）机制
5. **P2**：协同过滤推荐算法

---

## 附录：关键代码位置

| 功能 | 文件 | 说明 |
|------|------|------|
| FeedItem 模型 | `data/model/FeedItem.kt` | 统一数据结构 |
| 卡片路由 | `ui/feed/components/FeedItemCard.kt` | when(cardType) 分发 |
| 曝光埋点 | `ui/feed/FolderScreen.kt` | snapshotFlow + 300ms 防抖 |
| 视频自动播放 | `ui/feed/FolderScreen.kt` | 500ms 防抖，中心 item 检测 |
| AI 请求触发 | `ui/feed/FolderScreen.kt` | 800ms 防抖，仅焦点 item |
| 点赞逻辑 | `ui/feed/FolderViewModel.kt` | toggleLike() 乐观更新 |
| AI 内容分析 | `data/repository/AiRepository.kt` | generateFeedAi() |
| AI 智能搜索 | `data/repository/AiRepository.kt` | parseSearchQuery() |
| AI 对话助手 | `ui/chat/ChatViewModel.kt` | buildSystemPrompt() + 推荐解析 |
| AI 助手 UI | `ui/chat/ChatScreen.kt` | MD3 Expressive 对话界面 |
| 用户画像 | `data/repository/UserProfileRepository.kt` | 标签权重 + 0.99 衰减 |
| 画像 Entity | `data/local/db/UserProfileEntity.kt` | tag → interestScore |
| 聊天记录 | `data/local/db/ChatMessageEntity.kt` | Room 持久化 |
| 视频播放器 | `player/PlayerManager.kt` | ExoPlayer 单例 + 100MB 缓存 |
| 图片配置 | `NekoFeedApp.kt` | Coil 内存 25% + 磁盘 2% |
| URL 容错 | `data/remote/RetrofitClient.kt` | 自动补全 http:// scheme |
| AI 设置 | `ui/settings/AiSettingsScreen.kt` | LLM 配置 UI |
| 访客拦截 | `navigation/AppNavHost.kt` | like/collect 登录检查 |

---

> **汇报建议**：重点展示 ① 三层 AI 架构图 ② AI 对话助手推荐 demo ③ 用户记忆系统（个性化闭环）④ 性能防抖策略，这四个点最能体现项目的技术深度和 AI 能力。
