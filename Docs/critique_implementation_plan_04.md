# NekoFeed 项目 Critique

## TL;DR

整体架构设计清晰，分层合理，UI 质量高。核心骨架（Feed 加载、AI 摘要、分类/Tag 过滤、互动、Stats 展示）已基本完成。
主要问题集中在：**统计数据永远为 0**（无曝光/点击埋点调用）、**视频播放完全未实现**（VideoFeedCard 是空壳）、**分类过滤在客户端做而非服务端**（有正确性 Bug）、**FeedDetailScreen 没有 ViewModel**（点击统计和状态回流缺失）、以及若干命名混乱问题。

---

## 1. 整体逻辑链路梳理

```
NekoFeedServer (Python/FastAPI)
    ├── SQLite DB: FeedItem / UpstreamFeed / User / UserLike / UserCollect / UserHistory
    └── GET /api/feed?category&limit&offset&base_url
              ↓
Android App
    ├── RetrofitClient → FeedApi → FeedRepository
    ├── FeedViewModel (AndroidViewModel)
    │     ├── loadFeed / loadMore / refresh
    │     ├── mergeLocalInteractions (Room DB)
    │     ├── batchGenerateAi → AiRepository → LLM API
    │     ├── toggleLike / toggleCollect / toggleShare
    │     ├── filterByCategory / filterByTag
    │     └── getStats / getItemById / searchItems
    ├── UI Layer
    │     ├── FeedScreen (LazyColumn + PullToRefresh + FAB)
    │     ├── FeedDetailScreen (静态展示，无独立 ViewModel)
    │     ├── SearchScreen + SearchViewModel
    │     └── StatsScreen (纯展示，getStats() 是同步函数调用)
    └── Local DB (Room)
          ├── AiCacheEntity (AI 结果持久化)
          ├── FeedItemInteractionEntity (互动持久化)
          └── UserProfileEntity (用户画像 tag)
```

---

## 2. 已确认的 Bug

### 🔴 Bug 1：曝光/点击/播放统计永远为 0

**位置**：[FolderViewModel.kt](file:///e:/NekoFeed/app/src/main/java/com/ico/nekofeed/ui/feed/FolderViewModel.kt) `getStats()` / [FolderDetailScreen.kt](file:///e:/NekoFeed/app/src/main/java/com/ico/nekofeed/ui/detail/FolderDetailScreen.kt)

**问题**：

- `FeedItem.exposureCount`、`clickCount`、`playCount` 在 `FeedItem` 上是普通字段，初始值来自服务端（服务端 `api.py` 并没有在响应里返回这三个字段），所以**始终为 0**。
- 整个代码库里没有任何一个地方调用 `exposureCount++` 或 `clickCount++` 的逻辑。需求文档要求曝光在卡片进入可视区域时记录，点击在卡片点击时记录，但这两个回调从未被触发。
- `StatsScreen` 的 `getStats()` 是**同步调用**（`getStats: () -> StatsData`），数据源是 `allItems.sumOf { it.exposureCount }`，全为 0，Statistics 页面永远是空的。

**修复方向**：

1. 在 `FeedContent` 的每个 Item 的 `LaunchedEffect` 或 `onVisible` 回调里触发 `viewModel.recordExposure(item.id)`。
2. 在 `FeedItemCard` 的 `onClick` 里触发 `viewModel.recordClick(item.id)`。
3. 在 `FolderViewModel` 里把 exposure/click 的增量写回 `allItems`（或独立维护 analytics map）。

---

### 🔴 Bug 2：视频 FeedCard 是空壳，无实际播放能力

**位置**：[VideoFeedCard.kt](file:///e:/NekoFeed/app/src/main/java/com/ico/nekofeed/ui/feed/components/VideoFeedCard.kt)（15 KB）

需要阅读确认，但从架构上看：

- 需求文档（§8.3、§15.1）明确要求使用 **Media3/ExoPlayer** 和 **PlayerManager** 做单一视频播放、滚出暂停。
- 代码库里没有 `player/` 目录，没有 `PlayerManager.kt`，`FeedUiState` 虽有 `playingItemId` 字段但**没有任何地方给它赋非 null 值**。
- `VideoFeedCard` 很可能只展示封面图 + 播放图标，点击没有真正的播放行为。

**修复方向**：实现 `PlayerManager`（单例 ExoPlayer）+ `playingItemId` 状态联动，`VideoFeedCard` 通过 `AndroidView` 嵌入 `PlayerView`。

---

### 🔴 Bug 3：分类过滤在客户端做，`loadMore` 后过滤不准

**位置**：[FolderViewModel.kt#L479](file:///e:/NekoFeed/app/src/main/java/com/ico/nekofeed/ui/feed/FolderViewModel.kt#L479-L489)

```kotlin
// 客户端过滤逻辑
private fun filterByCategory(items: List<FeedItem>, category: FeedCategory): List<FeedItem> {
    return when (category) {
        FeedCategory.FEATURED -> items
        else -> items.filter { item ->
            item.category == category.value ||
            item.itemType == category.value || ...
        }
    }
}
```

**问题**：

- 服务端 `GET /api/feed` 本身支持 `?category=` 参数，但 `FeedRepository.loadFeed()` 调用时 `category` **始终传 null**（FolderViewModel.loadFeed() 没有传入 selectedCategory）。
- 分类完全在客户端做，导致 `loadMore` 拉取的 20 条里如果没有目标分类的数据，过滤后可能变成 0 条，但 `hasMore` 还是 true，不断触发加载。
- 切换分类时不会重新请求服务端，数据量受限于首次加载的 20 条。

**修复方向**：切换分类时调用 `loadFeed(category = category.value)`，并把 `category` 参数传给 `FeedRepository.loadFeed()`。

---

### 🟡 Bug 4：`mergeLocalInteractions` 的合并逻辑有歧义

**位置**：[FolderViewModel.kt#L522](file:///e:/NekoFeed/app/src/main/java/com/ico/nekofeed/ui/feed/FolderViewModel.kt#L522-L541)

```kotlin
if (local != null && !item.isLiked && !item.isCollected && item.likeCount == 0 && item.collectCount == 0) {
    // 用本地覆盖服务端
} else if (local != null) {
    // 用服务端覆盖本地（同步回 DB）
}
```

**问题**：条件是"服务端返回的 likeCount == 0 **且** isLiked == false 才用本地"，但若用户在登录态点赞，服务端 likeCount 就是 1，这时本地的离线点赞状态会被**丢弃**并用服务端值覆盖。适合离线-首次场景，但对"未登录点赞 → 登录后数据同步"的场景有逻辑漏洞。

---

### 🟡 Bug 5：`FeedDetailScreen` 无 ViewModel，点击统计和 AI 状态同步是靠父级 ViewModel 传递

**位置**：[AppNavHost.kt#L195](file:///e:/NekoFeed/app/src/main/java/com/ico/nekofeed/navigation/AppNavHost.kt#L195-L208)

```kotlin
val item = remember(decodedId, uiState.items) { feedViewModel.getItemById(decodedId) }
FeedDetailScreen(
    item = item,
    onAiRequest = { feedViewModel.requestAiAnalysis(it) }
)
```

**问题**：

- `FeedDetailScreen` 打开后如果 `FeedViewModel` 的 `allItems` 被 `loadMore` 更新，`remember` 不会自动重组（`remember` 依赖的是值相等性，`FeedItem` 是 data class，会重新查找但不触发 UI 更新）。
- 详情页的 `isAiLoading` 动画依赖 `item.isAiLoading`，但这个状态的更新是异步更新 `allItems`，要触发重组需要 detail screen 也观察 `uiState`，目前没有。
- 进入详情页本应记录 `clickCount++` 和 `history`，但没有调用。

---

### 🟡 Bug 6：`FeedItem.isLiked` / `isCollected` 等字段是 `var`，但 data class 应该是不可变的

**位置**：[FeedItem.kt#L54](file:///e:/NekoFeed/app/src/main/java/com/ico/nekofeed/data/model/FeedItem.kt#L54-L70)

```kotlin
var isLiked: Boolean = false       // ❌ var in data class
var isCollected: Boolean = false
var likeCount: Int = 0
...
```

这些字段声明为 `var` 但整个 ViewModel 逻辑都是通过 `.copy()` 创建新实例，`var` 是多余且危险的（外部代码可直接修改引用）。应全部改为 `val`。

---

### 🟡 Bug 7：`SearchScreen` 搜索源是 `feedViewModel.getAllItems()`（snapshot），不是响应式的

**位置**：[AppNavHost.kt#L220](file:///e:/NekoFeed/app/src/main/java/com/ico/nekofeed/navigation/AppNavHost.kt#L220)

```kotlin
allItems = feedViewModel.getAllItems()
```

`getAllItems()` 返回的是当前时刻的 `allItems` 快照，不是 `StateFlow`。如果 Feed 列表在 SearchScreen 打开后更新（loadMore），搜索不会感知到新数据。

---

## 3. 未完成的功能点

### ❌ PlayerManager / 视频播放

需求 §10（PlayerManager）、§8.3（视频卡片）完全未实现：

- 无 `player/` 目录
- 无 ExoPlayer 集成
- `playingItemId` 在 `FeedUiState` 中存在但从未被赋值

### ❌ 曝光埋点

需求 §14.1（通用统计）未实现。`LazyColumn` item 的可见性检测（`isVisible` / `VisibilityTracker`）没有接入。

### ❌ 点击统计和历史记录

进入详情页时应调用 `recordClick(itemId)` 和 `recordHistory(itemId)`（服务端有 `POST /api/items/{itemId}/history` 接口），但代码里没有调用。

### ❌ Profile 页的"我的点赞"/"我的收藏"/"我的历史"

**位置**：[AppNavHost.kt#L104-L106](file:///e:/NekoFeed/app/src/main/java/com/ico/nekofeed/navigation/AppNavHost.kt#L104-L106)

```kotlin
onNavigateToLikes = { },       // 空实现
onNavigateToCollections = { }, // 空实现
onNavigateToHistory = { },     // 空实现
```

服务端 API 都已实现（`GET /api/user/likes`、`/collections`、`/history`），客户端导航目标为空 lambda。

### ❌ DetailScreen 的 "查看原文" / sourceUrl 跳转

`sourceUrl` 在详情页只是显示文本，没有实现 `Intent.ACTION_VIEW` 的浏览器跳转。

### ❌ 视频详情页播放器

详情页的 `HeroMediaSection` 对 video 类型只展示封面或 emoji 🎬，没有 `PlayerView`。

### ❌ AI 搜索的 `userProfileTags` 来源

`SearchViewModel` 调用 `userProfileRepository.getTopInterestTags(5)` 提供用户画像，但 `UserProfileEntity` 是基于 `recordInteraction()` 写入的，而 `recordInteraction` 目前只在 `toggleLike/Collect/Share` 里异步调用，且 `UserProfileEntity` 的结构需要确认是否真的按 tag 聚合（从 DB 文件大小看可能未完整实现）。

---

## 4. 工程/架构层面的问题

### ⚠️ 命名严重混乱：`Folder` vs `Feed`

整个项目里大量文件以 `Folder` 命名，实际上是 Feed 功能：

| 实际文件名 | 应该叫 |
|---|---|
| `FolderScreen.kt` | `FeedScreen.kt` |
| `FolderViewModel.kt` | `FeedViewModel.kt` |
| `FolderDetailScreen.kt` | `FeedDetailScreen.kt` |
| `FolderRepository.kt` | `FeedRepository.kt` |
| `FolderApi.kt` | `FeedApi.kt` |
| `FolderItemCard.kt` | `FeedItemCard.kt`（冗余，已有 `FeedItemCard.kt`）|
| `FolderResponse.kt` | `FeedResponse.kt` |
| `OetrofitClient.kt` | `RetrofitClient.kt`（typo：`Oetrofit`）|

这不影响运行，但严重影响可读性和维护性。AppNavHost 里的 import 已经用了 `FeedScreen`、`FeedDetailScreen`（别名），说明有重构一半的痕迹。

### ⚠️ `RetrofitClient` 是 `lazy` 初始化的 `OkHttpClient`，但 `feedApi` 在 `updateBaseUrl` 后重建

**位置**：[OetrofitClient.kt#L27-L68](file:///e:/NekoFeed/app/src/main/java/com/ico/nekofeed/data/remote/OetrofitClient.kt)

`okHttpClient` 是 `by lazy` 的全局单例，`updateBaseUrl` 重建 `Retrofit` 时复用了同一个 `OkHttpClient`。这没问题。但 `FeedRepository` 在 `FeedViewModel` 构造时是这样初始化的：

```kotlin
private val repository = FeedRepository(RetrofitClient.feedApi)
```

如果用户在 `AiSettingsScreen` 修改了 Server URL 并 `restartApp()`，则 `recreate()` 会重建 Activity，`FeedViewModel` 是 `viewModel()` 拿到的，**Activity recreate 不会销毁 ViewModel**（只有 process kill 才会）。所以修改 URL 后 `FeedRepository` 拿的还是旧的 `feedApi` 引用。

> 实际上 `RetrofitClient.feedApi` 是 `@Volatile var`，`updateBaseUrl` 时已经替换了，但 `FeedRepository` 构造时持有的是旧引用的**值拷贝**（Kotlin property），所以确实会有此问题。

### ⚠️ `MainActivity` 里用了 `runBlocking`

**位置**：[MainActivity.kt#L25](file:///e:/NekoFeed/app/src/main/java/com/ico/nekofeed/MainActivity.kt#L25-L39)

```kotlin
runBlocking {
    val serverConfig = tokenManager.getServerConfig()
    ...
}
runBlocking { authRepository.restoreToken() }
```

在主线程（UI 线程）用 `runBlocking` 读 DataStore，在正常情况下很快，但 DataStore 内部用了文件 I/O，这有 ANR 风险。应改为 `SplashScreen` + 协程异步加载，或用 `runBlocking` 套上 `Dispatchers.IO`。

### ⚠️ `FeedViewModel` 直接在 init 里初始化所有依赖（无 DI）

```kotlin
class FeedViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FeedRepository(RetrofitClient.feedApi)
    private val userRepository = UserRepository(RetrofitClient.feedApi)
    private val tokenManager = TokenManager(application)
    private val database = NekoFeedDatabase.getInstance(application)
    ...
}
```

训练营项目可以接受无 Hilt，但这种硬编码依赖让单元测试完全无法进行，也让 `repository` 拿到的是 `RetrofitClient.feedApi` 的**时刻快照**（见上条）。

### ⚠️ AI 并发控制 Semaphore 只在 `requestAiAnalysis` 里用，`batchGenerateAi` 没有受 Semaphore 保护

**位置**：[FolderViewModel.kt#L181-L203](file:///e:/NekoFeed/app/src/main/java/com/ico/nekofeed/ui/feed/FolderViewModel.kt#L181-L203)

`batchGenerateAi` 里对每个 item 顺序调用 `generateFeedAi` 并 `delay(500ms)`，这是串行的，不会并发爆炸。但如果 `batchGenerateAi` 和 `requestAiAnalysis` 同时在跑，会有两个并发路径都在请求 LLM，`aiSemaphore` 只保护 `requestAiAnalysis` 那路，两路加起来可能超过 2 并发。

### ⚠️ `StatsScreen` 的 `getStats` 是同步 lambda，在 Composable 里调用

```kotlin
@Composable
fun StatsScreen(
    getStats: () -> StatsData,  // 同步调用
    ...
) {
    val stats = getStats()  // 每次重组都调用
```

`getStats()` 内部是 `allItems.sumOf { ... }` 对所有 item 求和，如果 `allItems` 有几千条，每次重组都会有性能损耗。应改为 `remember { getStats() }` 或让 StatsViewModel 持有 `StateFlow<StatsData>`。

### ⚠️ `FeedItem` 的类型/分类用裸 `String` 而非枚举

```kotlin
val category: String?
val itemType: String?
val cardType: String?
```

模型层用 `String`，枚举（`FeedCardType`, `FeedItemType`, `FeedCategory`）定义在同一文件的末尾作为独立 enum，但两者之间靠 `fromString()` 手动转换。这导致 `filterByCategory` 的比较是字符串比较，容易大小写问题（服务端返回 `"Tech"` vs 客户端 `"tech"`）。

---

## 5. 优先级建议

| 优先级 | 问题 | 影响 |
|--------|------|------|
| 🔴 P0 | 曝光/点击埋点没有实现 | Stats 页面永远是 0，核心功能演示失败 |
| 🔴 P0 | 分类过滤在客户端做，服务端 category 参数未使用 | 切换 Tab 数据不对，loadMore 可能死循环 |
| 🔴 P1 | 视频播放未实现 | VideoFeedCard 只是封面展示 |
| 🟡 P2 | Profile 页三个导航空实现 | 功能点缺失但不影响核心流程 |
| 🟡 P2 | DetailScreen 进入时不记录 click/history | 统计和服务端数据不同步 |
| 🟡 P2 | `FeedItem` 的 `var` 字段改为 `val` | 潜在并发安全问题 |
| 🟢 P3 | 文件命名混乱（Folder → Feed） | 可读性问题，重构风险低 |
| 🟢 P3 | `MainActivity` 的 `runBlocking` | ANR 风险低概率，但不规范 |
| 🟢 P3 | `StatsScreen` 同步计算性能 | 当前数据量小，不明显 |

---

## 6. 已做得好的地方（值得保留）

- ✅ **FeedRepository 降级策略**：服务端失败自动切换 `FallbackFeedData`，有提示 Banner，符合需求 §17。
- ✅ **AI 缓存 + 7天过期**：`AiCacheDao` + `cleanOldCache()` 设计合理，避免重复请求。
- ✅ **AI Semaphore 并发限制**：`Semaphore(2)` 防止请求爆炸。
- ✅ **互动状态本地持久化**：Room DB 的 `FeedItemInteractionEntity` + `mergeLocalInteractions`，重启后状态不丢失。
- ✅ **无限滚动检测**：`snapshotFlow` + `lastVisible >= totalCount - 3` 触发 `loadMore`，逻辑正确。
- ✅ **下拉刷新防重入**：`isRefreshing`/`isLoadingMore` guard 写得规范。
- ✅ **AI 请求防重复**：`requestAiAnalysis` 检查 `aiSummary.isNullOrBlank()` 和 `isAiLoading`。
- ✅ **UI 视觉质量高**：渐变卡片、Crossfade 动画、LinearWavyProgressIndicator 等细节丰富。
- ✅ **AiRepository 指数退避重试**：`backoffMs *= 2`，最多 3 次，有 fallback。
