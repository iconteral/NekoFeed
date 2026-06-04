# NekoFeed 功能完善实现计划

为了消除当前项目与 [原需求.md](file:///l:/NekoFeed/Docs/原需求.md) 和 [Design.md](file:///l:/NekoFeed/Docs/Design.md) 之间的差距，我们需要在服务端和客户端做出一系列改进。本项目包含的主要缺陷有：**视频无法播放**、**缺少真实曝光/点击埋点上报**、**详情页互动状态不同步**。

以下是详细的设计与实施方案。

---

## 1. 服务端改造方案

为了支持真实的曝光、点击与视频播放上报，服务端需要在数据库中持久化这些数据，并提供自增统计接口。

### 1.1 数据库与模型调整
* 在 `NekoFeedServer/app/models.py` 的 [FeedItem](file:///l:/NekoFeed/NekoFeedServer/app/models.py#L19) 类中增加以下字段：
  - `exposure_count = Column(Integer, default=0)` (曝光计数)
  - `click_count = Column(Integer, default=0)` (点击计数)
  - `play_count = Column(Integer, default=0)` (视频播放计数)
* 在 `NekoFeedServer/app/schemas.py` 的 [FeedItemResponse](file:///l:/NekoFeed/NekoFeedServer/app/schemas.py#L45) 结构体中添加字段，以别名映射的方式支持客户端的反序列化：
  ```python
  exposureCount: int = Field(0, alias="exposure_count")
  clickCount: int = Field(0, alias="click_count")
  playCount: int = Field(0, alias="play_count")
  ```

### 1.2 路由与 API 逻辑
* 在 `NekoFeedServer/app/routers/user_interaction.py`（或 `api.py`）中，新增以下三个上报统计的 POST API，每次请求将数据库中对应的字段自增 1，并返回最新数值：
  - `POST /api/items/{item_id}/exposure`
  - `POST /api/items/{item_id}/click`
  - `POST /api/items/{item_id}/play`

### 1.3 初始化数据
* 修改 `NekoFeedServer/seed.py` 中自定义广告和视频的预置字段，确保初始计数有默认值。
* 执行重建数据库迁移（删除本地 SQLite 数据库文件 `neko_feed.db`），并重新运行 `python seed.py` 以适应新的表结构。

---

## 2. 客户端依赖引入

需要引入 Media3 ExoPlayer 用于播放视频。

* 在 [libs.versions.toml](file:///l:/NekoFeed/gradle/libs.versions.toml) 中增加：
  ```toml
  [versions]
  media3 = "1.4.1" # 保证 compileSdk 37 的良好兼容性

  [libraries]
  media3-exoplayer = { group = "androidx.media3", name = "media3-exoplayer", version.ref = "media3" }
  media3-ui = { group = "androidx.media3", name = "media3-ui", version.ref = "media3" }
  ```
* 在 [app/build.gradle.kts](file:///l:/NekoFeed/app/build.gradle.kts) 的 `dependencies` 块中加入：
  ```kotlin
  implementation(libs.media3.exoplayer)
  implementation(libs.media3.ui)
  ```

---

## 3. 跨页面互动状态同步 (P0)

当前详情页点赞/收藏后返回列表，列表显示不正确，且详情页本身的图标状态不会在点击后实时刷新。

### 3.1 引入 Room Live Query 支持
* 修改 [FeedItemInteractionDao.kt](file:///l:/NekoFeed/app/src/main/java/com/ico/nekofeed/data/local/db/FeedItemInteractionDao.kt)，使其查询支持 Flow 流式订阅：
  ```kotlin
  @Query("SELECT * FROM feed_item_interaction WHERE itemId = :itemId")
  fun getInteractionFlow(itemId: String): Flow<FeedItemInteractionEntity?>

  @Query("SELECT * FROM feed_item_interaction")
  fun getAllInteractionsFlow(): Flow<List<FeedItemInteractionEntity>>
  ```

### 3.2 抽取 InteractionRepository
* **[NEW]** 新建 `data/repository/InteractionRepository.kt`：
  - 封装原本分散在 `FeedViewModel` 中的 `toggleLike`、`toggleCollect` 和 `toggleShare` 逻辑。
  - 在底层同时修改 Room 数据库表，并根据用户登录状态调用网络 API 同步。
  - 通过 `getAllInteractionsFlow()` 提供流式的全量交互数据流。

### 3.3 ViewModel 与 Navigation 重构
* 在 `FeedViewModel` 中：
  - 引入 `InteractionRepository`。
  - 在 `init` 阶段，通过协程 `collect` 并监听 `InteractionRepository.getAllInteractionsFlow()`，每当本地 Room 缓存的交互状态变更时，将这些交互映射并合并到 `uiState.items` 中，从而自动触发列表刷新。
  - 暴露一个 `observeItem(itemId: String): Flow<FeedItem?>` 方法，方便详情页直接流式监听特定 Item 的变化。
* 在 [AppNavHost.kt](file:///l:/NekoFeed/app/src/main/java/com/ico/nekofeed/navigation/AppNavHost.kt#L181) 详情页 Composable 声明中：
  - 弃用静态的 `remember(decodedId) { feedViewModel.getItemById(decodedId) }`。
  - 改为使用：
    ```kotlin
    val itemState by feedViewModel.observeItem(decodedId).collectAsState(initial = null)
    ```
  - 将 `itemState` 传给 `FeedDetailScreen`，使详情页的图标状态与数据层时刻同步。

---

## 4. 真实曝光与点击埋点上报 (P0)

通过监听 `LazyColumn` 滚动和进入详情页的生命周期，实现精确上报。

### 4.1 新建 AnalyticsRepository
* **[NEW]** 新建 `data/repository/AnalyticsRepository.kt`：
  - 负责调用网络 API 上报埋点：`reportExposure(itemId)`、`reportClick(itemId)`、`reportPlay(itemId)`。
  - 如果 API 失败，进行本地日志输出或进行静默重试。

### 4.2 列表曝光检测逻辑
* 在 [FolderScreen.kt](file:///l:/NekoFeed/app/src/main/java/com/ico/nekofeed/ui/feed/FolderScreen.kt) 对应的 Composable `FeedContent` 中，通过 `snapshotFlow` 监控可见 items 的变动。
* 在 `FeedViewModel` 中维护已曝光 Item 的去重 Set：
  ```kotlin
  private val exposedItemIds = mutableSetOf<String>()
  
  fun reportItemExposure(itemId: String) {
      if (exposedItemIds.add(itemId)) {
          viewModelScope.launch {
              analyticsRepository.reportExposure(itemId)
              // 更新本地 uiState 里的 exposureCount 使其有即时反馈
              updateLocalItemStats(itemId, isExposure = true)
          }
      }
  }
  ```
* 当滚动检测到 item 索引进入可见范围时，调用 ViewModel 的 `reportItemExposure` 方法。

### 4.3 点击进入详情页上报
* 在 [AppNavHost.kt](file:///l:/NekoFeed/app/src/main/java/com/ico/nekofeed/navigation/AppNavHost.kt) 中进入详情页的路由块内，使用 `LaunchedEffect(decodedId)` 触发 `feedViewModel.reportItemClick(decodedId)` 上报，并通过 `userProfileRepository.recordInteraction(item, CLICK)` 记录用户标签画像。

---

## 5. 视频播放器实现与复用 (P0)

列表中只允许播放一个视频（最好支持静音和滚动滑出暂停），详情页可以无缝继续播放。

### 5.1 实现 PlayerManager
* **[NEW]** 新建 `util/PlayerManager.kt`（全局单例）：
  - 内部持有唯一的 `ExoPlayer` 实例。
  - 提供 `playVideo(context, mediaUrl, itemId)` 和 `pauseVideo()` 方法。
  - 追踪 `currentPlayingItemId`（作为 StateFlow 暴露）。
  - 提供静音与取消静音切换。

### 5.2 列表卡片 VideoFeedCard 改造
* 在 [VideoFeedCard.kt](file:///l:/NekoFeed/app/src/main/java/com/ico/nekofeed/ui/feed/components/VideoFeedCard.kt) 中：
  - 监听 `PlayerManager.currentPlayingItemId`。如果当前 Item 正在播放，则隐藏静态 cover 图像，在原位展示 `AndroidView(factory = { PlayerView(...) })`，将共享的 ExoPlayer attach 上去。
  - 提供视频封面中心的“播放”叠加按钮。点击时开始播放并在卡片显示静音/取消静音控制。
* **滑动暂停支持**：在列表曝光检测的基础上，如果当前播放的 `playingItemId` 不在可见 Items 的列表中，立即调用 `PlayerManager.pauseVideo()`。

### 5.3 详情页 FeedDetailScreen 播放支持
* 在 [FolderDetailScreen.kt](file:///l:/NekoFeed/app/src/main/java/com/ico/nekofeed/ui/detail/FolderDetailScreen.kt#L132) 的 `HeroMediaSection` 中：
  - 若 `item.isVideo` 为 true，用 `AndroidView` 渲染 Media3 `PlayerView`。
  - 进入详情页时利用 `LaunchedEffect` 调用 `PlayerManager.playVideo` 自动播放视频，静音状态继承自全局状态。
  - 在详情页提供原生的控制栏，或者通过 Overlay 浮层实现播放/暂停和静音按钮。
  - 当视频播放开始时，触发 `feedViewModel.reportVideoPlay(itemId)`。

---

## 6. P1 体验优化 (CTA 及外链)

* **“查看原文” 与 广告/商品 CTA 跳转**：
  - 在 [FolderDetailScreen.kt](file:///l:/NekoFeed/app/src/main/java/com/ico/nekofeed/ui/detail/FolderDetailScreen.kt) 中引入 `LocalUriHandler.current`。
  - ARTICLE 的“来源 URL”和“查看原文”按钮的 `onClick`，以及广告/商品的 CTA 按钮，执行 `uriHandler.openUri(item.sourceUrl)`，在浏览器中打开目标外链。

---

## 验证计划

### 1. 服务端验证
* 使用 Postman 或 Curl 验证上报接口能否正确让对应的 `exposure_count`、`click_count` 和 `play_count` + 1：
  `curl -X POST http://localhost:8000/api/items/custom_xxx/exposure`

### 2. 客户端跨页面同步验证
* 进入商品详情页，点击点赞按钮。
* 观察详情页的点赞图标颜色发生切换（红心）。
* 返回首页列表，观察该商品的点赞数量递增且红心亮起。

### 3. 曝光与点击埋点验证
* 打开 Logcat，滑动首页列表，检查是否每滑入一个新的卡片都触发一次曝光日志打印。
* 点击进入详情页，检查 Logcat 是否伴随有 `POST /api/items/{id}/click` 发送。
* 进入 `Stats`（数据统计）页面，验证曝光率、点击率和各项统计折线/柱状图数据有真实累计（不再全为 0）。

### 4. 视频播放器复用验证
* 找到一个视频卡片，点击播放，声音和视频能够正常加载并输出。
* 点击卡片进入详情页，视频能够无缝衔接直接在详情页顶部自动继续播放。
* 在详情页按返回键，播放器的状态能正常回收或停止，不会产生后台音频泄露或闪退。
