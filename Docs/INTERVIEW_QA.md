# NekoFeed 面试/答辩/汇报 Q&A 手册

> 基于项目实际实现，覆盖技术选型、架构设计、Kotlin 语法、Android/Compose、
> 数据层、网络层、状态管理、视频播放、AI 集成、埋点统计、性能优化、安全、
> 测试、踩坑复盘、未来演进等所有可能被问到的方向。

---

## 目录

- [一、项目概述](#一项目概述)
- [二、架构设计](#二架构设计)
- [三、Kotlin 语言](#三kotlin-语言)
- [四、Jetpack Compose UI](#四jetpack-compose-ui)
- [五、数据层：Room & DataStore](#五数据层room--datastore)
- [六、网络层：Retrofit & OkHttp](#六网络层retrofit--okhttp)
- [七、状态管理：StateFlow & ViewModel](#七状态管理stateflow--viewmodel)
- [八、视频播放：ExoPlayer](#八视频播放exoplayer)
- [九、AI 集成](#九ai-集成)
- [十、埋点与统计](#十埋点与统计)
- [十一、性能优化](#十一性能优化)
- [十二、安全与隐私](#十二安全与隐私)
- [十三、测试策略](#十三测试策略)
- [十四、踩坑与解决方案](#十四踩坑与解决方案)
- [十五、未来演进](#十五未来演进)
- [十六、开放性问题](#十六开放性问题)

---

## 一、项目概述

### Q1：项目是做什么的？

**A：** NekoFeed 是一个"内容与广告混排"的 Android 单列信息流 App。后端用 FastAPI 聚合 RSS/Atom 和自定义广告，归一化为统一的 `FeedItem` JSON；客户端用 Jetpack Compose 渲染多种卡片（大图、小图、视频、商品），支持分类浏览、点赞收藏、视频播放、AI 摘要/标签/对话搜索，以及本地曝光/点击/播放统计。

### Q2：为什么从 AdItem 扩展为 FeedItem？

**A：** 原始题目是"单列广告信息流"，但真实信息流不会只有广告。把 `AdItem` 扩展为 `FeedItem` 后：
1. 可以用同一个数据模型承载文章、视频、广告、商品、本地生活
2. UI 卡片根据 `itemType` 和 `cardType` 动态渲染，不需要每种类型写一套
3. AI 摘要、搜索、统计逻辑可以统一复用
4. 后续扩展新类型不需要重写整体架构

### Q3：项目的技术栈是什么？

**A：**

| 层 | 技术 |
|---|---|
| UI | Kotlin + Jetpack Compose + Material 3 |
| 状态管理 | ViewModel + StateFlow + Coroutines |
| 网络 | Retrofit + OkHttp |
| 持久化 | Room (SQLite) + DataStore (键值) |
| 图片 | Coil（内存+磁盘缓存） |
| 视频 | Media3 / ExoPlayer |
| 导航 | Navigation Compose |
| 后端 | Python FastAPI + SQLAlchemy + SQLite |
| AI | OpenAI-compatible Chat Completions |

### Q4：项目有哪些模块？

**A：**

```
app/                        → Android 客户端
├── data/model/             → 数据模型（FeedItem, User 等）
├── data/remote/            → 网络层（Retrofit API, 拦截器）
├── data/local/             → 本地存储（DataStore, Room DB）
├── data/repository/        → 数据仓库（Feed, Auth, AI, 互动）
├── navigation/             → 导航路由
├── player/                 → 视频播放器管理
├── ui/                     → 各页面 Compose UI
└── util/                   → 工具类

NekoFeedServer/             → FastAPI 后端
```

---

## 二、架构设计

### Q5：整体架构是怎样的？

**A：** 采用 MVVM（Model-View-ViewModel）分层架构：

```
Compose UI (View)
    ↓ collectAsState() / 调用方法
ViewModel (StateFlow)
    ↓ suspend fun
Repository
    ↓
Retrofit (Remote) / Room + DataStore (Local)
```

设计原则：
- 服务端消化上游字段差异，客户端只消费统一 `FeedItem` 契约
- 远端不可用时允许缓存或 Mock 降级
- 互动与统计采用事件驱动，避免 UI 组件自行维护多份状态

### Q6：为什么选 MVVM 而不是 MVP 或 MVI？

**A：**
- **MVVM**：ViewModel 持有 StateFlow，Compose 通过 `collectAsState()` 自动订阅，天然适合声明式 UI
- **MVP**：需要手动绑定 View 接口，Compose 时代不够声明式
- **MVI**：过度工程化（单向数据流 + Reducer），对本项目规模来说太重
- MVVM 在 Compose 生态中是 Google 推荐的官方模式，`viewModel()` + `StateFlow` + `collectAsState()` 形成完整闭环

### Q7：Repository 模式的作用是什么？

**A：** Repository 是数据仓库，ViewModel 不直接调用 Retrofit 或 Room，而是通过 Repository 获取数据。好处：
1. ViewModel 不关心数据来自网络还是本地
2. 可以轻松切换 Mock 模式
3. 统一处理错误和降级逻辑（网络失败 → 本地缓存 → 硬编码数据）

### Q8：为什么用单 Activity 架构？

**A：** 整个 App 只有 `MainActivity` 一个 Activity，所有页面都是 Composable 函数，通过 Navigation Compose 切换。好处：
1. 页面切换没有 Activity 生命周期开销
2. ViewModel 可以在 NavHost 级别共享（如 FeedViewModel 跨页面共享）
3. 页面过渡动画更容易控制
4. 代码更简洁，不需要 Fragment

---

## 三、Kotlin 语言

### Q9：data class 是什么？为什么用它？

**A：** `data class` 自动为类生成 `equals()`、`hashCode()`、`toString()`、`copy()` 和解构函数。在本项目中：
- `FeedItem`、`User`、`FeedUiState` 等都是 data class
- `copy()` 用于创建不可变副本（如 `item.copy(isLiked = true)`），配合 StateFlow 实现状态更新
- `equals()` 让 Compose 能正确判断数据是否变化，决定是否重组

### Q10：sealed class 和 enum class 有什么区别？项目中怎么用的？

**A：**

| 特性 | enum class | sealed class |
|------|-----------|-------------|
| 实例 | 固定数量的单例 | 可以有多个实例 |
| 属性 | 所有实例共享相同结构 | 每个子类可以有不同属性 |
| 用途 | 简单枚举（如 FeedCategory） | 复杂类型层次（如 BottomNavItem） |

项目中：
- `FeedCategory`、`FeedCardType`、`FeedItemType` → enum class（固定选项）
- `BottomNavItem` → sealed class（每个 Tab 有不同图标和路由）
- `VideoPlaybackStatus` → enum class（播放状态机）

### Q11：扩展函数是什么？项目中怎么用的？

**A：** 扩展函数允许你"给已有的类添加新方法"，而不需要继承或修改源码。项目中：
```kotlin
// 给 FeedItem 添加 matchesCategory() 方法
fun FeedItem.matchesCategory(category: FeedCategory): Boolean {
    return when (category) {
        FeedCategory.FEATURED -> true
        FeedCategory.VIDEO -> isVideo || this.category == category.value
        // ...
    }
}
```
好处：逻辑内聚在数据类附近，方便单元测试（`FeedRulesTest.kt`）

### Q12：Kotlin 的空安全机制是怎么工作的？

**A：** Kotlin 通过类型系统在编译时防止 NPE：
- `String?` → 可空类型，`String` → 非空类型
- `?.` → 安全调用（左边为 null 则整个表达式为 null）
- `?:` → Elvis 操作符（左边为 null 用右边的值）
- `!!` → 非空断言（确定不是 null，否则抛异常）
- `let { }` → 非空时执行块

项目中大量使用：`item.aiSummary ?: summary ?: ""` 实现优先级回退。

### Q13：协程（Coroutine）和 Flow 在项目中怎么用的？

**A：**
- **协程**：`viewModelScope.launch { }` 启动异步任务，`withContext(Dispatchers.IO)` 切换到 IO 线程
- **StateFlow**：`MutableStateFlow<FeedUiState>` 持有 UI 状态，值变化时通知订阅者
- **SharedFlow**：`InteractionSyncStore` 用 SharedFlow 广播互动事件（无初始值的事件流）
- **combine**：合并多个 Flow（如统计时间范围 + 分析环境）
- **flatMapLatest**：当外层 Flow 变化时，取消旧的内层 Flow，切换到新的

---

## 四、Jetpack Compose UI

### Q14：Compose 和传统 XML 布局有什么区别？

**A：**

| 特性 | XML 布局 | Compose |
|------|---------|---------|
| UI 定义 | XML 文件 | Kotlin 函数 |
| 状态绑定 | findViewById + 手动更新 | StateFlow + collectAsState 自动重组 |
| 复用 | include / Fragment | Composable 函数 |
| 生命周期 | Activity/Fragment 生命周期 | 组合生命周期 |
| 预览 | Layout Inspector | @Preview 注解 |

### Q15：Compose 中的状态管理是怎么做的？

**A：** 采用"状态提升"模式：
- ViewModel 持有 `MutableStateFlow<FeedUiState>`（可变，私有）
- 对外暴露 `StateFlow<FeedUiState>`（不可变，只读）
- UI 层 `val uiState by viewModel.uiState.collectAsState()` 订阅
- 数据变化 → StateFlow 更新 → collectAsState 触发 → Compose 重组

### Q16：LazyColumn 是什么？和 RecyclerView 有什么区别？

**A：** `LazyColumn` 是 Compose 的虚拟滚动列表，类似 RecyclerView，但：
- 不需要 Adapter、ViewHolder、LayoutManager
- 直接在 Composable 中声明 `items(list) { item -> Card(item) }`
- 通过 `key = { it.id }` 帮助 Compose 正确复用（类似 RecyclerView 的 stable ID）
- 通过 `contentType` 帮助 Compose 选择正确的 Composable（类似多 ViewType）

### Q17：HorizontalPager 是做什么的？

**A：** 类似 ViewPager2，支持左右滑动切换页面。项目中用于频道切换：
- 5 个频道（精选/科技/本地/视频/电商）对应 5 个 Page
- 配合 `PrimaryScrollableTabRow` 实现 Tab + 滑动联动
- `pagerState.settledPage` 监听当前页，触发数据加载

### Q18：LaunchedEffect 和 DisposableEffect 有什么区别？

**A：**
- **LaunchedEffect(key)**：进入组合时启动协程，key 变化时取消旧协程并重新执行。适合一次性操作（加载数据、监听事件）
- **DisposableEffect**：进入组合时执行，`onDispose {}` 中清理资源。适合注册/注销监听器

项目中：
- `LaunchedEffect(viewModel)` → 进入 FeedScreen 时加载数据
- `DisposableEffect(lifecycleOwner)` → 监听 App 前后台，后台时暂停视频

### Q19：remember 和 rememberUpdatedState 有什么区别？

**A：**
- **remember**：跨重组缓存值，不会每次重组都重新计算
- **rememberUpdatedState**：在 LaunchedEffect 等长生命周期块中引用最新的回调，避免闭包捕获旧值

项目中 `rememberUpdatedState(onExposure)` 确保 LaunchedEffect 中调用的是最新的回调函数。

---

## 五、数据层：Room & DataStore

### Q20：DataStore 和 SharedPreferences 有什么区别？

**A：**

| 特性 | SharedPreferences | DataStore |
|------|-------------------|-----------|
| API | 同步（可能阻塞主线程） | 基于协程（挂起函数） |
| 线程安全 | 不保证 | 保证 |
| 类型安全 | 无（getString/getInt） | 有（Preferences.Key<T>） |
| 错误处理 | 异常可能崩溃 | Flow 错误传播 |
| 数据一致性 | 不保证 | 事务性写入 |

项目中 `TokenManager` 用 DataStore 存储 Token、服务器配置、AI 配置、Mock 开关等。

### Q21：Room 数据库在项目中存了什么？

**A：** 6 张表：

| 表 | 用途 |
|---|---|
| `AiCacheEntity` | AI 摘要/标签缓存（7 天过期） |
| `UserProfileEntity` | 用户兴趣画像（标签 + 分数） |
| `FeedItemInteractionEntity` | 点赞/收藏状态、最后浏览时间 |
| `FeedAnalyticsEntity` | 曝光/点击/播放/分享计数聚合 |
| `AnalyticsEventEntity` | 事件明细（每条行为记录） |
| `ChatMessageEntity` | AI 对话消息 |

### Q22：数据库迁移是怎么做的？

**A：** Room 的 Migration 机制：
1. `@Database(version = 6)` 定义当前版本
2. `addMigrations(MIGRATION_4_5, MIGRATION_5_6)` 注册迁移脚本
3. 迁移脚本中用 `db.execSQL()` 执行 SQL（CREATE TABLE IF NOT EXISTS、ALTER TABLE ADD COLUMN）
4. `fallbackToDestructiveMigration()` 作为兜底：迁移失败时清空重建（开发阶段可用）

项目中采用幂等迁移（多次执行不会出错），适合快速迭代。

### Q23：DataStore 中缓存 Feed JSON 的作用是什么？

**A：** 当网络请求失败时，可以从 DataStore 读取上次成功加载的 Feed JSON，作为降级数据。这样用户在断网时仍能看到上次的内容，而不是空白页面。

---

## 六、网络层：Retrofit & OkHttp

### Q24：Retrofit 接口是怎么定义的？

**A：** 用 interface + 注解描述 HTTP 请求：
```kotlin
interface FeedApi {
    @GET("api/feed")
    suspend fun getFeed(
        @Query("category") category: String? = null,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): FeedResponse

    @POST("api/items/{itemId}/like")
    suspend fun toggleLike(@Path("itemId") itemId: String): ItemInteraction
}
```
Retrofit 在运行时自动生成实现类，`suspend fun` 可以在协程中直接调用。

### Q25：OkHttp 拦截器做了什么？

**A：** 两个拦截器：
1. **HttpLoggingInterceptor**：日志拦截器，打印请求/响应信息（BASIC 级别）
2. **自定义拦截器**：
   - 自动添加 `Authorization: Bearer {token}` 请求头
   - 自动添加 `X-Device-Id` 请求头
   - 收到 HTTP 401 时触发未授权回调（清除 Token → 重启 App）

### Q26：@SerializedName 的作用是什么？

**A：** Gson 注解，把 JSON 的 snake_case 映射到 Kotlin 的 camelCase：
```kotlin
@SerializedName("source_name")
val sourceName: String?
```
服务端返回 `"source_name": "36氪"`，Kotlin 里用 `item.sourceName` 访问。

### Q27：为什么用 lambda 传 FeedApi 而不是直接传实例？

**A：** `feedApiProvider = { RetrofitClient.feedApi }` 而不是 `feedApi = RetrofitClient.feedApi`。因为服务器地址可能在运行时改变（用户在设置页修改），`RetrofitClient.feedApi` 会随 `updateBaseUrl()` 更新。用 lambda 延迟获取，确保每次调用都拿到最新的 API 实例。

---

## 七、状态管理：StateFlow & ViewModel

### Q28：ViewModel 的作用是什么？为什么不用普通的类？

**A：** ViewModel 的核心价值：
1. **生命周期感知**：屏幕旋转时不会丢失数据（Activity 重建但 ViewModel 不销毁）
2. **作用域管理**：`viewModelScope.launch` 在 ViewModel 销毁时自动取消协程
3. **Compose 集成**：`viewModel()` 函数自动管理实例，配合 `collectAsState()` 订阅

### Q29：MutableStateFlow 和 StateFlow 有什么区别？

**A：**
- `MutableStateFlow<T>`：可读可写，ViewModel 内部用
- `StateFlow<T>`：只读，对外暴露（通过 `asStateFlow()` 转换）
- 这是"封装"原则：外部只能订阅，不能直接修改状态

### Q30：`.update { it.copy(...) }` 是什么意思？

**A：** 三步合一：
1. `.update { }`：原子更新（线程安全，CAS 操作）
2. `it.copy(...)`：创建不可变副本，只修改指定字段
3. 整体效果：安全地更新 StateFlow 的值，触发 Compose 重组

### Q31：乐观更新是什么？项目中怎么用的？

**A：** 乐观更新（Optimistic Update）是 UI 设计模式：
1. 先立即更新本地 UI（用户看到即时反馈）
2. 同时发送网络请求
3. 成功 → 用服务端返回的权威数据替换
4. 失败 → 回滚到之前的状态（snapshot）

项目中点赞：
```kotlin
val snapshot = allItems  // 保存快照
allItems = allItems.map { if (it.id == itemId) it.copy(isLiked = !it.isLiked) else it }
updateFilteredItems()  // 立即更新 UI

userRepository.toggleLike(itemId).fold(
    onSuccess = { applyInteraction(itemId, it) },  // 用权威数据覆盖
    onFailure = { allItems = snapshot; updateFilteredItems() }  // 回滚
)
```

### Q32：InteractionSyncStore 是什么？为什么需要它？

**A：** 互动状态同步总线，用 `SharedFlow` 实现的事件总线。解决的问题：详情页点赞后，首页怎么同步更新？

方案对比：
- A：首页每次显示时重新请求服务端（慢，浪费流量）
- B：用事件总线实时广播（快，省内存）← 本项目采用

工作流程：详情页 ViewModel 调用 `publish()` → InteractionSyncStore 广播 → 首页 ViewModel 收到事件 → 更新 allItems → UI 自动刷新。

---

## 八、视频播放：ExoPlayer

### Q33：为什么用单个 ExoPlayer 实例？

**A：** 列表中可能有多个视频卡片，但：
- 每个 ExoPlayer 实例消耗大量内存和 CPU（解码器资源有限）
- 多个播放器同时解码会导致卡顿和 OOM（OutOfMemory）
- 状态管理复杂（哪个在播？哪个暂停？）

所以用 `PlayerManager` 单例，同一时间只播放一个视频。

### Q34：PlayerManager 的单例模式是怎么实现的？

**A：** Double-Check Locking：
```kotlin
companion object {
    @Volatile
    private var instance: PlayerManager? = null

    fun getInstance(context: Context): PlayerManager {
        return instance ?: synchronized(this) {
            instance ?: PlayerManager(context.applicationContext).also { instance = it }
        }
    }
}
```
- `@Volatile`：保证多线程可见性
- `synchronized`：保证只创建一个实例
- `applicationContext`：避免 Activity 泄漏

### Q35：视频播放状态是怎么管理的？

**A：** 通过 `StateFlow<VideoPlaybackState>` 暴露状态：
- `IDLE` → `BUFFERING` → `READY` → `PLAYING`（正常流程）
- 任何状态 → `ERROR`（播放失败）

`Player.Listener` 监听 ExoPlayer 事件，自动更新 StateFlow。UI 层订阅后自动显示对应的 loading/播放/错误状态。

### Q36：列表中视频是怎么自动播放/暂停的？

**A：** 在 `FeedScreen` 中：
1. `snapshotFlow { listState.isScrollInProgress }` 监听滚动状态
2. 滚动停止后（delay 1 秒防抖），计算哪个视频卡片最接近视口中心
3. 设置 `playingItemId`，对应的 `VideoFeedCard` 开始播放
4. 其他卡片暂停
5. App 进入后台时（`DisposableEffect` 监听生命周期），暂停所有视频

---

## 九、AI 集成

### Q37：AI 功能是怎么实现的？

**A：** 对接 OpenAI-compatible Chat Completions 接口：
1. 用户在设置页填写 Base URL、模型名、API Key
2. 客户端用 Retrofit 调用 `/v1/chat/completions`
3. 发送 FeedItem 的标题/摘要，获取 AI 生成的摘要、标签、推荐理由
4. 结果写入 Room 缓存，7 天过期

### Q38：AI 如何做到不影响首屏体验？

**A：** "原内容先展示、AI 结果渐进增强"策略：
1. Feed 加载完成后立即展示原始内容
2. 批量触发 AI 分析（`batchGenerateAi`）
3. AI 结果通过 Room 缓存 + StateFlow 渐进更新 UI
4. `Semaphore(8)` 限制并发，避免打爆 API
5. AI 未配置或失败时，显示原始摘要，不阻断浏览

### Q39：AI 搜索是怎么实现的？

**A：** 两层搜索：
1. **本地关键词匹配**：在 title、summary、tags 中做关键词匹配 + 评分排序（标题权重最高 3 分）
2. **AI 意图解析**：发送自然语言给 LLM，解析出关键词、标签、类型，在本地数据中匹配

AI 不可用时仍能完成基础搜索（降级方案）。

---

## 十、埋点与统计

### Q40：曝光是怎么统计的？

**A：** 曝光判定条件：
1. 卡片进入 `LazyColumn` 可见区域
2. 可见像素高度 / 卡片总高度 >= 50%
3. 同一会话内同一内容只记录一次（`exposedItems` Set 去重）

通过 `snapshotFlow { listState.layoutInfo }` 计算可见区域，滚动停止后批量处理。

### Q41：CTR 是怎么计算的？

**A：** `CTR = click events / exposure events`。分母为 0 时返回 0。统计数据从 Room 的事件表按时间窗口聚合（`combine(statsRange, analyticsEnvironment)` → `flatMapLatest` → `map(::aggregateStats)`）。

### Q42：Mock 数据和真实数据怎么隔离的？

**A：** 每个事件都标记 `environment` 字段（`mock` 或 `live`）。统计页根据当前模式过滤：
- Mock 模式下只看 Mock 数据
- 真实模式下只看真实数据
- 避免演示数据污染真实统计

---

## 十一、性能优化

### Q43：项目做了哪些性能优化？

**A：**

| 优化点 | 方案 |
|--------|------|
| 列表渲染 | LazyColumn 虚拟滚动，只渲染可见内容 |
| item 复用 | `key = { it.id }` + `contentType` 帮助 Compose 正确复用 |
| 图片加载 | Coil 内存缓存 25% + 磁盘缓存 2% |
| 视频播放 | 单 ExoPlayer 实例，100MB LRU 缓存 |
| AI 请求 | Semaphore(8) 并发限制 + Room 缓存 |
| Feed 分页 | 每次只加载 20 条，避免全量抓取 |
| 状态更新 | `update { it.copy() }` 原子操作，避免不必要的重组 |
| Lambda 缓存 | `remember { }` 缓存回调，避免每次重组创建新实例 |

### Q44：LazyColumn 的 key 和 contentType 有什么作用？

**A：**
- **key**：稳定标识，帮助 Compose 在数据变化时正确复用 item（避免错误复用导致的 UI 错乱）
- **contentType**：告诉 Compose 不同类型的 item 用不同的 Composable 渲染（类似 RecyclerView 的 ViewType）

---

## 十二、安全与隐私

### Q45：项目的安全措施有哪些？

**A：**
1. **密码哈希**：服务端哈希后存储，不存明文
2. **JWT Token**：Bearer Token 认证，401 时自动清除
3. **API Key 不入库**：仓库不提交 API Key、Token、数据库
4. **设备 ID**：UUID 生成，不包含设备硬件信息

### Q46：有什么安全不足？

**A：** 当前是演示级方案：
1. 允许 HTTP 明文流量（生产必须 HTTPS + Network Security Config）
2. AI API Key 存在客户端 DataStore（生产应由服务端代理）
3. 管理后台无认证（公网部署需加管理员认证 + CSRF）
4. 未做请求签名和防重放

---

## 十三、测试策略

### Q47：项目有哪些测试？

**A：**
- **JVM 单测**：分类规则（`FeedRulesTest`）、互动列表规则（`InteractionRulesTest`）、统计聚合与 CTR
- **Android instrumentation**：基础设备测试入口
- **服务端**：`compileall` 语法门禁
- **交付构建**：`testDebugUnitTest` + `assembleDebug` + `lintDebug`

### Q48：为什么把 shouldReloadFeedOnEnter 提取为顶层函数？

**A：** 提取为独立的 `internal fun` 而不是 ViewModel 的成员方法，方便单元测试——不需要创建 ViewModel 实例就能测试逻辑。

---

## 十四、踩坑与解决方案

### Q49：遇到过哪些棘手问题？

**A：**

| 问题 | 解决方案 |
|------|---------|
| 异构数据无法统一 UI | 服务端归一化为 FeedItem + item_type + card_type |
| 点赞跨页面不一致 | 服务端权威响应 + InteractionSyncStore 广播 |
| 列表中多个视频争抢资源 | PlayerManager 单 ExoPlayer + ownerId 管理 |
| AI 延迟阻塞首屏 | 原内容先展示 + AI 渐进增强 + 并发限制 + 缓存 |
| 曝光统计虚高 | 50% 可见阈值 + 同一会话去重 |
| 模拟器 localhost 指向问题 | 用 10.0.2.2 访问宿主机 |
| JSON snake_case 不一致 | @SerializedName 显式映射 |
| 乐观更新计数不一致 | 改用服务端权威响应 |

### Q50：Compose 中播放器放在哪里？为什么？

**A：** 放在 `PlayerManager` 单例（全局），而不是放在卡片 Composable 中。因为：
- 卡片随列表滚动不断组合/销毁，放在卡片里会反复创建播放器
- 全局单例保证只有一个 ExoPlayer 实例
- 通过 `ownerId` 追踪哪个卡片在播放

---

## 十五、未来演进

### Q51：如果继续做，你会改进什么？

**A：**
1. **分页**：用 Paging 3 + Room RemoteMediator 替换手工分页，实现离线优先
2. **数据库迁移**：用 Alembic 管理服务端数据库版本
3. **AI 密钥**：全部代理到服务端，加入限流和成本统计
4. **事件上传**：批量上传、幂等 event ID、服务端去重
5. **性能测试**：Macrobenchmark 测冷启动、Baseline Profile、滚动帧耗时
6. **UI 测试**：Compose UI 测试覆盖关键流程
7. **无障碍**：语义描述、动态字体、弱网测试
8. **推荐算法**：基于用户兴趣画像的个性化排序

### Q52：如果要接入真实的广告 SDK，你会怎么做？

**A：**
1. 在 `FeedItem` 中新增 `adId` 字段，对接广告平台的素材 ID
2. 在 `FeedRepository` 中增加广告数据源，与 RSS 数据合并
3. 曝光/点击事件上报到广告平台（同时保留本地统计）
4. 增加广告标识和"不感兴趣"功能
5. CTR 数据同步到广告平台做效果归因

---

## 十六、开放性问题

### Q53：项目最大的技术亮点是什么？

**A：**
1. **统一 FeedItem 模型**：一个数据模型承载文章/视频/广告/商品，客户端不需要为每种类型写单独逻辑
2. **AI 渐进增强**：AI 从主链路依赖变成可选增强，不影响核心浏览体验
3. **完整降级链路**：网络 → 缓存 → Mock 数据，任何环节失败都有兜底
4. **跨页面状态同步**：InteractionSyncStore 解决了多页面互动一致性问题

### Q54：项目最大的不足是什么？

**A：**
1. **手工分页**：没有用 Paging 3，分页逻辑手动管理，不够优雅
2. **性能数据缺失**：没有固定设备的 FPS、冷启动、内存峰值等量化数据
3. **测试覆盖不足**：缺少 Compose UI 测试和服务端集成测试
4. **AI 密钥安全**：密钥存在客户端，不适合生产环境

### Q55：如果团队扩大到 5 人，你会怎么分工？

**A：**
1. **客户端 UI**：负责 Compose 页面、组件、动画、无障碍
2. **客户端数据**：负责 Repository、Room、网络层、分页
3. **服务端 API**：负责 FastAPI、数据库、RSS 抓取
4. **AI/算法**：负责 LLM 集成、推荐算法、用户画像
5. **测试/DevOps**：负责自动化测试、CI/CD、性能测试

### Q56：为什么选择这些技术栈？有没有考虑过替代方案？

**A：**

| 选择 | 替代方案 | 选择理由 |
|------|---------|---------|
| Compose | XML + View | 声明式 UI 更适合动态数据驱动的页面 |
| Retrofit | Ktor Client | Retrofit 更成熟，注解式 API 定义更简洁 |
| Room | SQLDelight | Room 是 Google 官方推荐，与 Jetpack 集成更好 |
| DataStore | SharedPreferences | 协程安全，类型安全 |
| Coil | Glide | Coil 为 Compose 原生设计，API 更简洁 |
| ExoPlayer | MPV/ijkplayer | Media3 是 Google 官方方案，功能最全 |
| FastAPI | Spring Boot | Python 生态更适合 RSS 解析和 LLM 调用 |
| StateFlow | LiveData | Flow 更灵活，支持更多操作符 |

### Q57：这个项目你学到了什么？

**A：**
1. **架构设计**：MVVM 分层、Repository 模式、事件驱动的状态同步
2. **Kotlin 实践**：协程、Flow、扩展函数、密封类、data class
3. **Compose 思维**：声明式 UI、状态提升、重组优化
4. **工程化**：降级策略、埋点统计、数据库迁移、Git 提交规范
5. **问题拆解**：把复杂问题（信息流 + 广告 + AI + 统计）拆成可独立解决的小问题
