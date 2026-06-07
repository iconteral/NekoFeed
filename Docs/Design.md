# AI 广告推荐信息流 App 设计大纲
## 1. 项目定位调整

本项目不再将 App 设计为一个单纯的广告 Mock 列表，而是设计为一个 **内容与广告混排的信息流客户端**。

App 的核心目标仍然是完成训练营题目中的“单列广告信息流”，但数据来源升级为：

1. 本地 Feed Server 聚合 RSS/Atom 上游内容。
2. 本地 Feed Server 提供自定义广告、视频广告、商品广告。
3. Android App 从本地 Feed Server 获取统一 JSON Feed。
4. App 端负责卡片渲染、详情页交互、视频播放、AI 摘要/标签展示、对话式搜索和埋点统计。
因此，项目结构从：

```
本地 Mock AdItem → Android 信息流
```

调整为：

```
RSS / 自定义广告 / 视频素材 / 商品广告
        ↓
本地 Feed Server 聚合、清洗、缓存
        ↓
统一 FeedItem JSON API
        ↓
Android App 单列信息流展示
```

这个设计更接近真实的信息流系统：上游内容复杂，后端负责清洗归一化，客户端负责稳定渲染和交互体验。

---

## 2. 数据来源定位

### 2.1 数据来源分层

整个系统分为两层数据来源。

第一层是 **上游内容源**：

- RSS 新闻源
- 科技资讯源
- 视频内容源
- 自定义广告数据
- 自定义商品广告数据
- 本地生活广告数据

第二层是 **本地 Feed Server**：

- 拉取上游 RSS
- 解析标题、摘要、图片、视频、发布时间
- 下载并缓存媒体资源
- 管理自定义广告 item
- 输出统一 JSON API
- 为 Android App 提供稳定数据源

Android App 不直接关心 RSS 的字段差异，也不直接处理上游媒体链接，而是只消费本地 Feed Server 清洗后的统一数据。

---

## 3. 为什么从 AdItem 扩展为 FeedItem

原来的 `AdItem` 只能表达广告：

```kotlin
data class AdItem(
    val id: String,
    val title: String,
    val description: String,
    val imageUrl: String?,
    val videoUrl: String?,
    val cardType: AdCardType
)
```

但现在数据源包括：

- 普通文章
- 新闻资讯
- 视频内容
- 原生广告
- 商品广告
- 本地生活广告

所以需要抽象成更通用的 `FeedItem`。

新的模型中，广告只是 `FeedItem` 的一种类型：

```
FeedItem
├── article 文章
├── video 视频
├── ad 广告
├── product 商品广告
└── local 本地生活内容
```

这样做的好处是：

1. 信息流可以混排不同内容类型。
2. UI 卡片可以根据 `itemType` 和 `cardType` 动态渲染。
3. AI 摘要、标签、搜索可以复用同一套数据结构。
4. 曝光、点击、点赞、收藏等统计逻辑可以统一处理。
5. 后续扩展更多内容类型时，不需要重写整体架构。

---

## 4. 核心数据模型设计

### 4.1 FeedItem

推荐使用一个统一的 `FeedItem` 作为 App 的核心数据模型。

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

    // 分类与类型
    val category: FeedCategory,
    val itemType: FeedItemType,
    val cardType: FeedCardType,

    // 媒体资源
    val imageUrl: String?,
    val mediaUrl: String?,

    // AI 结果
    val aiSummary: String?,
    val aiTags: List<String>,
    val aiReason: String?,

    // 广告相关字段，可为空
    val brand: String?,
    val ctaText: String?,
    val priceText: String?,
    val isSponsored: Boolean,

    // 互动状态
    val isLiked: Boolean,
    val isCollected: Boolean,
    val likeCount: Int,
    val collectCount: Int,
    val shareCount: Int,

    // 统计状态
    val exposureCount: Int,
    val clickCount: Int,
    val playCount: Int,

    // 时间
    val publishedAt: String?,
    val createdAt: String?
)
```

### 4.2 FeedItemType

```kotlin
enum class FeedItemType {
    ARTICLE,    // 普通资讯文章
    VIDEO,      // 视频内容
    AD,         // 广告
    PRODUCT,    // 商品广告
    LOCAL       // 本地生活内容
}
```

### 4.3 FeedCardType

```kotlin
enum class FeedCardType {
    LARGE_IMAGE,   // 大图卡片
    SMALL_IMAGE,   // 小图卡片
    VIDEO,         // 视频卡片
    TEXT_ONLY,     // 纯文本卡片
    PRODUCT        // 商品卡片
}
```

### 4.4 FeedCategory

```kotlin
enum class FeedCategory {
    FEATURED,
    TECH,
    AI,
    BUSINESS,
    LOCAL,
    VIDEO,
    SHOPPING
}
```

---

## 5. 服务端 API 与 App 数据映射

本地 Feed Server 提供统一接口：

```
GET /api/feed?category=tech&limit=20&offset=0&base_url=http://10.0.2.2:8000
```

返回示例：

```json
{
  "items": [
    {
      "id": "item_001",
      "title": "AI 手机新品发布",
      "summary": "这是一条科技资讯摘要",
      "content": "正文内容，可为空",
      "sourceName": "36Kr",
      "sourceUrl": "https://example.com/article",
      "category": "tech",
      "itemType": "article",
      "cardType": "large_image",
      "imageUrl": "http://10.0.2.2:8000/media/images/demo.jpg",
      "mediaUrl": null,
      "tags": ["科技", "AI", "手机"],
      "publishedAt": "2026-05-29T10:00:00Z"
    }
  ],
  "limit": 20,
  "offset": 0,
  "total": 100
}
```

Android App 接收到后转换为领域模型 `FeedItem`。

---

## 6. App 页面结构调整

### 6.1 原页面结构

原来页面结构是：

```
FeedScreen
DetailScreen
SearchScreen
StatsScreen
```

这个结构仍然保留，但页面语义调整为：

```
FeedScreen      统一信息流首页
DetailScreen    FeedItem 详情页
SearchScreen    对话式 Feed 搜索
StatsScreen     Feed 曝光/点击/互动统计
```

### 6.2 新页面结构

```
MainActivity
└── AppNavHost
    ├── FeedScreen
    ├── FeedDetailScreen
    ├── SearchScreen
    ├── StatsScreen
    └── DebugScreen 可选
```

---

## 7. FeedScreen 首页设计调整

首页不再只展示广告，而是展示混合 Feed。

页面结构：

```
┌──────────────────────────────┐
│ TopAppBar: AdFlow AI          │
│ 搜索按钮 / 统计按钮            │
├──────────────────────────────┤
│ TabRow: 精选 | 科技 | AI | 本地 | 视频 │
├──────────────────────────────┤
│ AI 搜索提示条：描述你想看的内容/广告 │
├──────────────────────────────┤
│ LazyColumn 单列信息流          │
│                              │
│  资讯文章卡片                  │
│  大图广告卡片                  │
│  小图资讯卡片                  │
│  视频卡片                      │
│  商品广告卡片                  │
│                              │
└──────────────────────────────┘
```

### 7.1 信息流内容类型

FeedScreen 中会混排：

1. `ARTICLE`：普通资讯文章。
2. `AD`：品牌广告。
3. `VIDEO`：视频内容或视频广告。
4. `PRODUCT`：商品广告。
5. `LOCAL`：本地生活推荐。

### 7.2 卡片渲染逻辑

卡片不再根据 `AdCardType` 渲染，而是根据 `FeedCardType` 渲染：

```kotlin
@Composable
fun FeedItemCard(
    item: FeedItem,
    onClick: (FeedItem) -> Unit,
    onLikeClick: (String) -> Unit,
    onCollectClick: (String) -> Unit,
    onPlayClick: (String) -> Unit
) {
    when (item.cardType) {
        FeedCardType.LARGE_IMAGE -> LargeImageFeedCard(...)
        FeedCardType.SMALL_IMAGE -> SmallImageFeedCard(...)
        FeedCardType.VIDEO -> VideoFeedCard(...)
        FeedCardType.TEXT_ONLY -> TextOnlyFeedCard(...)
        FeedCardType.PRODUCT -> ProductFeedCard(...)
    }
}
```

这样既能支持广告卡片，也能支持资讯卡片和视频卡片。

---

## 8. 卡片设计调整

### 8.1 资讯文章卡片

用于 RSS 新闻和科技资讯。

```
┌──────────────────────────────┐
│ 文章标题                       │
│ 摘要内容                       │
│ 来源 · 发布时间                │
│ #科技 #AI #创业                │
│                     缩略图     │
└──────────────────────────────┘
```

特点：

- 更偏内容阅读。
- 可展示来源。
- 点击进入详情页或原文链接页。

### 8.2 大图广告卡片

用于品牌广告和视觉素材广告。

```
┌──────────────────────────────┐
│          大图广告素材           │
├──────────────────────────────┤
│ 品牌名 · Sponsored             │
│ 广告标题                       │
│ AI 摘要：一句话说明卖点         │
│ #学生党 #性价比 #通勤          │
│ 点赞 / 收藏 / 分享              │
└──────────────────────────────┘
```

特点：

- 仍然是项目重点。
- 体现广告信息流能力。
- 展示 AI 摘要和智能标签。

### 8.3 视频 Feed 卡片

用于视频广告或视频内容。

```
┌──────────────────────────────┐
│          视频封面 / 播放器       │
│        播放按钮 / 静音按钮       │
├──────────────────────────────┤
│ 视频标题                       │
│ 来源 / 品牌                    │
│ AI 摘要                        │
│ #视频 #种草 #本地生活          │
└──────────────────────────────┘
```

特点：

- 使用 Media3 播放。
- 列表中只允许一个视频播放。
- 滚出屏幕暂停。
- 进入详情页后可自动播放。

### 8.4 商品广告卡片

用于本地自定义商品广告。

```
┌──────────────────────────────┐
│ 商品图                         │
│ 商品标题                       │
│ 价格 / 优惠信息                 │
│ 品牌名 · Sponsored             │
│ CTA: 立即查看                  │
└──────────────────────────────┘
```

特点：

- 更像电商广告。
- 可以展示价格、CTA、标签。
- 很适合训练营演示“多样式广告卡片”。

---

## 9. DetailScreen 详情页调整

详情页从 `AdDetailScreen` 调整为 `FeedDetailScreen`。

### 9.1 页面结构

```
┌──────────────────────────────┐
│ 返回按钮        来源 / 品牌      │
├──────────────────────────────┤
│ 图片 / 视频播放器               │
├──────────────────────────────┤
│ 标题                           │
│ AI 摘要                         │
│ 智能标签 Chips                  │
│ 正文 / 原始摘要                 │
├──────────────────────────────┤
│ 点赞 / 收藏 / 分享 / 查看原文    │
└──────────────────────────────┘
```

### 9.2 不同类型详情行为

### ARTICLE

- 展示标题、摘要、来源、发布时间。
- 展示 AI 摘要和标签。
- 提供“查看原文”按钮。

### AD

- 展示品牌、广告图、广告文案。
- 展示 CTA。
- 支持点赞、收藏、分享。
- 点击进入详情时增加点击次数。

### VIDEO

- 顶部展示视频播放器。
- 支持播放、暂停、静音。
- 增加播放统计。

### PRODUCT

- 展示商品图、价格、优惠、品牌。
- 展示 CTA。
- 支持收藏和分享。

---

## 10. Repository 架构调整

### 10.1 原架构

原来：

```
AdRepository
AiRepository
AnalyticsRepository
```

### 10.2 新架构

调整为：

```
FeedRepository
AiRepository
InteractionRepository
AnalyticsRepository
PlayerRepository / PlayerManager
```

### 10.3 各 Repository 职责

### FeedRepository

负责：

- 请求本地 Feed Server。
- 分页加载 FeedItem。
- 根据频道加载数据。
- 本地缓存 FeedItem。
- 提供统一 `StateFlow<List<FeedItem>>`。

### AiRepository

负责：

- 为 FeedItem 生成摘要。
- 为 FeedItem 生成智能标签。
- 为搜索 query 生成结构化查询。
- 缓存 AI 结果。

### InteractionRepository

负责：

- 点赞。
- 收藏。
- 分享。
- 本地互动状态维护。
- 跨页面同步互动状态。

### AnalyticsRepository

负责：

- 曝光统计。
- 点击统计。
- 视频播放统计。
- CTR 计算。
- 统计页面数据聚合。

### PlayerManager

负责：

- Media3 ExoPlayer 实例复用。
- 当前播放 item 管理。
- 列表滚动时暂停不可见视频。
- 详情页和列表页播放状态协调。

---

## 11. 数据流设计调整

### 11.1 Feed 加载流程

```
FeedScreen
    ↓
FeedViewModel.loadFeed(category)
    ↓
FeedRepository.getFeed(category, limit, offset)
    ↓
RemoteFeedDataSource 请求本地 Feed Server
    ↓
解析 JSON 为 FeedItem
    ↓
更新 FeedUiState
    ↓
Compose 渲染信息流
```

### 11.2 下拉刷新流程

```
用户下拉刷新
    ↓
FeedViewModel.refresh()
    ↓
FeedRepository.refreshFeed()
    ↓
请求 /api/feed?offset=0
    ↓
替换当前频道数据
    ↓
列表回到顶部或保持当前策略
```

### 11.3 上拉加载更多流程

```
LazyColumn 滚动到底部
    ↓
FeedViewModel.loadMore()
    ↓
请求 /api/feed?offset=currentSize
    ↓
追加 FeedItem
```

### 11.4 详情页状态同步

```
FeedDetailScreen 点赞/收藏
    ↓
InteractionRepository 更新本地状态
    ↓
FeedRepository 合并互动状态
    ↓
FeedScreen 同步更新对应 item
```

---

## 12. ViewModel 状态调整

### 12.1 FeedUiState

```kotlin
data class FeedUiState(
    val selectedCategory: FeedCategory = FeedCategory.FEATURED,
    val items: List<FeedItem> = emptyList(),
    val selectedTags: List<String> = emptyList(),

    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,

    val playingItemId: String? = null,
    val errorMessage: String? = null
)
```

### 12.2 FeedDetailUiState

```kotlin
data class FeedDetailUiState(
    val item: FeedItem? = null,
    val isLoading: Boolean = false,
    val isVideoPlaying: Boolean = false,
    val errorMessage: String? = null
)
```

### 12.3 SearchUiState

```kotlin
data class SearchUiState(
    val query: String = "",
    val parsedKeywords: List<String> = emptyList(),
    val matchedTags: List<String> = emptyList(),
    val results: List<FeedItem> = emptyList(),
    val isSearching: Boolean = false,
    val errorMessage: String? = null
)
```

### 12.4 StatsUiState

```kotlin
data class StatsUiState(
    val totalExposure: Int = 0,
    val totalClick: Int = 0,
    val totalLike: Int = 0,
    val totalCollect: Int = 0,
    val totalPlay: Int = 0,
    val ctr: Float = 0f,
    val topItems: List<FeedItemStats> = emptyList()
)
```

---

## 13. AI 能力调整

AI 不再只服务广告，而是服务所有 FeedItem。

### 13.1 对文章

生成：

- 一句话摘要。
- 内容标签。
- 主题分类。

示例：

```json
{
  "summary": "这篇文章讨论了 AI 手机的发展趋势。",
  "tags": ["AI", "手机", "科技"],
  "reason": "内容涉及 AI 功能和智能终端体验。"
}
```

### 13.2 对广告

生成：

- 广告卖点摘要。
- 用户场景标签。
- 受众标签。

示例：

```json
{
  "summary": "适合学生和通勤人群的高性价比降噪耳机。",
  "tags": ["学生党", "性价比", "通勤"],
  "reason": "价格友好，主打降噪和长续航。"
}
```

### 13.3 对视频

生成：

- 视频内容摘要。
- 视频主题标签。
- 推荐理由。

### 13.4 搜索能力

对话式搜索也从“广告搜索”扩展为“Feed 搜索”。

用户可以输入：

```
我想看适合学生党的数码产品广告
```

也可以输入：

```
找一些 AI 手机相关的科技内容
```

搜索流程：

```
用户自然语言 query
    ↓
AI 解析关键词、标签、类型
    ↓
在 FeedItem 的 title、summary、aiSummary、aiTags、itemType 中匹配
    ↓
返回结果
```

---

## 14. 埋点统计调整

统计对象从 `AdItem` 改为 `FeedItem`。

### 14.1 通用统计

所有 FeedItem 都支持：

- 曝光
- 点击
- 点赞
- 收藏
- 分享

### 14.2 视频额外统计

视频 FeedItem 额外支持：

- 播放次数
- 播放时长，可选
- 完播率，可选

### 14.3 广告额外统计

广告 FeedItem 重点展示：

- 曝光
- 点击
- CTR
- 收藏
- 分享

### 14.4 统计页设计

StatsScreen 可以分为：

```
总体数据
├── 总曝光
├── 总点击
├── 总点赞
├── 总收藏
└── 总 CTR

内容类型分布
├── ARTICLE 数量
├── AD 数量
├── VIDEO 数量
└── PRODUCT 数量

广告表现排行
├── 广告标题
├── 曝光
├── 点击
└── CTR

视频播放排行
├── 视频标题
├── 播放次数
└── 点赞数
```

这样既满足题目中的曝光/点击统计，也能体现你做了内容/广告混排。

---

## 15. 技术架构更新

### 15.1 新整体架构

```
Upstream RSS / Custom Ads / Video Items
        ↓
Local Feed Server
        ↓
/api/feed 统一 JSON
        ↓
RemoteFeedDataSource
        ↓
FeedRepository
        ↓
FeedViewModel
        ↓
FeedScreen / DetailScreen / SearchScreen / StatsScreen
```

### 15.2 Android 分层架构

```
UI Layer
├── FeedScreen
├── FeedDetailScreen
├── SearchScreen
├── StatsScreen
└── components/

ViewModel Layer
├── FeedViewModel
├── FeedDetailViewModel
├── SearchViewModel
└── StatsViewModel

Data Layer
├── FeedRepository
├── AiRepository
├── InteractionRepository
├── AnalyticsRepository
├── RemoteFeedDataSource
└── LocalFallbackDataSource

Player Layer
└── PlayerManager
```

---

## 16. 推荐目录结构调整

```
app/src/main/java/com/example/adflow/
├── MainActivity.kt
├── navigation/
│   └── AppNavHost.kt
├── data/
│   ├── model/
│   │   ├── FeedItem.kt
│   │   ├── FeedItemType.kt
│   │   ├── FeedCardType.kt
│   │   ├── FeedCategory.kt
│   │   └── FeedResponse.kt
│   ├── remote/
│   │   ├── FeedApi.kt
│   │   └── RemoteFeedDataSource.kt
│   ├── local/
│   │   └── LocalFallbackDataSource.kt
│   └── repository/
│       ├── FeedRepository.kt
│       ├── AiRepository.kt
│       ├── InteractionRepository.kt
│       └── AnalyticsRepository.kt
├── ui/
│   ├── theme/
│   ├── feed/
│   │   ├── FeedScreen.kt
│   │   ├── FeedViewModel.kt
│   │   └── components/
│   │       ├── FeedItemCard.kt
│   │       ├── ArticleFeedCard.kt
│   │       ├── LargeImageFeedCard.kt
│   │       ├── SmallImageFeedCard.kt
│   │       ├── VideoFeedCard.kt
│   │       ├── ProductFeedCard.kt
│   │       └── FeedTagChips.kt
│   ├── detail/
│   │   ├── FeedDetailScreen.kt
│   │   └── FeedDetailViewModel.kt
│   ├── search/
│   └── stats/
├── player/
│   └── PlayerManager.kt
└── util/
    ├── UiState.kt
    └── DateFormatter.kt
```

---

## 17. 网络与降级策略

因为 App 依赖本地 Feed Server，所以必须设计降级方案。

### 17.1 正常流程

```
App 请求 http://10.0.2.2:8000/api/feed
    ↓
服务可用
    ↓
展示服务端 FeedItem
```

### 17.2 服务不可用

```
App 请求失败
    ↓
读取 LocalFallbackDataSource
    ↓
展示内置 mock FeedItem
    ↓
提示：当前使用本地演示数据
```

### 17.3 图片加载失败

```
Coil 加载 imageUrl 失败
    ↓
显示占位图
    ↓
不影响列表滚动
```

### 17.4 视频加载失败

```
Media3 加载 mediaUrl 失败
    ↓
显示封面图
    ↓
展示“视频加载失败”
    ↓
允许点击重试
```

---

## 18. 训练营功能对应关系

### 18.1 核心功能对应

| 题目要求 | 新设计实现方式 |
| --- | --- |
| 单列广告信息流 | FeedScreen 使用 LazyColumn 展示混合 Feed |
| 多样式广告卡片 | 根据 FeedCardType 渲染大图、小图、视频、商品卡片 |
| Tab 频道切换 | 根据 FeedCategory 请求不同 category |
| 点击进入详情页 | FeedDetailScreen 展示不同类型详情 |
| 返回保持原位置 | rememberLazyListState + ViewModel 保持列表状态 |
| 下拉刷新 | 重新请求 `/api/feed?offset=0` |
| 上拉加载更多 | 请求 `/api/feed?offset=currentSize` |
| 播放器资源复用 | PlayerManager 管理 Media3 ExoPlayer |
| 数据状态同步 | FeedRepository + InteractionRepository |
| 点赞收藏分享 | 所有 FeedItem 通用互动 |
| 曝光点击统计 | AnalyticsRepository 统一统计 FeedItem |

### 18.2 可选功能对应

| 可选功能 | 新设计实现方式 |
| --- | --- |
| AI 摘要 | 对 FeedItem 的 title/summary/content 生成 aiSummary |
| 智能标签 | 对 FeedItem 生成 aiTags |
| 标签点击过滤 | 根据 aiTags 和 tags 过滤 items |
| 对话式搜索 | 对 FeedItem 做自然语言检索 |
| 图片/视频缓存 | 服务端缓存媒体，App 端用 Coil 和 Media3 加载 |

---

## 19. 技术文档重点更新

技术设计文档中，需要重点强调以下变化。

### 19.1 数据来源不是单纯 Mock

原项目如果只用本地 Mock 广告，数据来源比较弱。现在增加本地 Feed Server 后，可以说明：

> 本项目将数据来源拆分为“上游内容源”和“客户端消费源”。上游内容源包括 RSS 新闻、科技资讯和自定义广告；本地 Feed Server 负责清洗、缓存和统一输出；Android App 只消费统一 FeedItem JSON，从而降低客户端对不同数据格式的耦合。
>

### 19.2 FeedItem 是统一抽象

可以写：

> 为了支持广告、文章、视频、商品等多类型内容混排，客户端将原本的 AdItem 扩展为 FeedItem。FeedItem 通过 itemType 表示内容类型，通过 cardType 表示渲染样式，从而实现数据驱动 UI。
>

### 19.3 广告仍然是核心

需要避免导师觉得你偏题成新闻 App。

可以写：

> 虽然数据模型从 AdItem 扩展为 FeedItem，但广告仍然是核心展示对象。FeedItem 中的 AD、PRODUCT、VIDEO_AD 类型用于承载广告内容，ARTICLE 类型主要用于模拟真实信息流中的内容广告混排场景，增强产品真实感。
>

### 19.4 本地 Feed Server 是辅助系统

可以写：

> 本地 Feed Server 不是生产级后端，而是训练营项目中的辅助数据服务。它的目标是为 App 提供稳定、可控、可复现的多类型 Feed 数据，避免直接依赖不稳定的外部 RSS 和远程媒体链接。
>

---

## 20. 最终 MVP 范围更新

### 必须完成

1. Android App 从本地 Feed Server 请求 `/api/feed`。
2. 使用 `FeedItem` 作为统一数据模型。
3. FeedScreen 展示单列混合信息流。
4. 支持大图、小图、视频、商品等卡片。
5. 支持详情页。
6. 支持点赞、收藏、分享。
7. 支持曝光、点击统计。
8. 支持下拉刷新、上拉加载。
9. 本地服务不可用时使用内置 mock 数据兜底。

### 建议完成

1. AI 摘要和标签。
2. 标签过滤。
3. 对话式搜索。
4. 视频播放复用。
5. StatsScreen 统计可视化。
6. Feed Server 管理页面。

### 可以简化

1. 不做复杂推荐算法。
2. 不做真实广告 SDK。
3. 不做用户登录。
4. 不做复杂后端权限。
5. 不强制缓存所有视频。
6. 不做生产级 RSS 抓取系统。

---

## 21. 新项目亮点总结

修改后的项目亮点可以总结为：

1. **统一 FeedItem 模型**：将广告、文章、视频、商品统一抽象，支持内容/广告混排。
2. **本地 Feed Server**：聚合 RSS 和自定义广告，为 App 提供稳定数据源。
3. **数据驱动卡片渲染**：通过 `itemType` 和 `cardType` 动态选择不同卡片组件。
4. **视频播放资源复用**：通过 Media3 和 PlayerManager 控制列表视频播放。
5. **AI 内容理解**：对 FeedItem 生成摘要、标签和推荐理由。
6. **对话式搜索**：用户可以用自然语言搜索内容或广告。
7. **埋点统计可视化**：对曝光、点击、点赞、收藏、播放进行本地统计。
8. **完整降级方案**：Feed Server、图片、视频、AI 任一环节失败时，App 仍能稳定运行。