# NekoFeed Kotlin/Android 学习手册

> 本手册为 Kotlin/Android 初学者设计，按学习顺序组织，每个文件标注了要学的核心知识点。
> 所有标注文件的头部和关键代码处都有详细的中文注释。

---

## 学习路线总览

```
阶段 1：数据基础          阶段 2：网络与存储         阶段 3：业务逻辑         阶段 4：UI 渲染
─────────────────      ─────────────────       ─────────────────      ─────────────────
FeedItem.kt             FolderApi.kt             FolderViewModel.kt     FeedScreen.kt
User.kt                 TokenManager.kt          FeedRepository.kt      FeedItemCard.kt
UiState.kt              NekoFeedDatabase.kt      InteractionSyncStore.kt BottomNavigationBar.kt
FeedRules.kt            RetrofitClient.kt        PlayerManager.kt       AppNavHost.kt
                                                                       MainActivity.kt
```

---

## 阶段 1：数据基础（先理解"数据长什么样"）

### `data/model/FeedItem.kt` — 全项目的数据基石

| 知识点 | 说明 |
|--------|------|
| `data class` | Kotlin 数据类，自动生成 equals/hashCode/copy/toString |
| `@Immutable` | Compose 优化注解，标记不可变对象 |
| `@SerializedName` | Gson 注解，JSON snake_case → Kotlin camelCase 映射 |
| `val displaySummary get() = ...` | 计算属性（不存储，实时计算） |
| `enum class` + `companion object` | 枚举类 + 工厂方法 |
| `?: ` (Elvis 操作符) | null 合并：`a ?: b` → a 不为 null 用 a，否则用 b |

### `data/model/User.kt` — 用户相关的数据模型

| 知识点 | 说明 |
|--------|------|
| 多个 `data class` 在同一文件 | 相关的小数据类放一起 |
| 默认参数值 | `val level: String = "Normal"` 防止 null |
| DTO 模式 | 纯数据容器，不包含业务逻辑 |

### `util/UiState.kt` — UI 状态模式

| 知识点 | 说明 |
|--------|------|
| UiState 模式 | ViewModel 用不可变 data class 持有 UI 状态 |
| `@Immutable` + `copy()` | 每次更新创建新对象，Compose 通过 == 判断是否重组 |
| 一个页面一个 UiState | FeedUiState / SearchUiState / ChatUiState |

### `util/FeedRules.kt` — 扩展函数

| 知识点 | 说明 |
|--------|------|
| 扩展函数 `fun FeedItem.matchesCategory()` | 给已有类添加新方法，不需要继承 |
| `when` 表达式 | 比 Java switch 更强大，可以匹配任意条件 |

---

## 阶段 2：网络与存储（理解"数据从哪来，存哪去"）

### `data/remote/FolderApi.kt` — Retrofit API 接口

| 知识点 | 说明 |
|--------|------|
| Retrofit 注解 `@GET` `@POST` `@PUT` `@DELETE` | 声明式 HTTP 接口 |
| `@Query` `@Path` `@Body` | URL 参数、路径参数、请求体 |
| `suspend fun` | 挂起函数，可以在协程中直接调用 |
| interface + 注解 → 自动生成实现 | Retrofit 运行时生成代理类 |

### `data/remote/OetrofitClient.kt` — Retrofit 客户端单例

| 知识点 | 说明 |
|--------|------|
| `object` 单例 | Kotlin 的 object 关键字，全局只有一个实例 |
| OkHttp 拦截器 | 自动添加 Token 和 Device-Id 请求头 |
| 401 处理 | Token 过期时自动清除认证状态 |
| 动态切换 BaseUrl | 运行时更换服务器地址 |

### `data/local/TokenManager.kt` — DataStore 键值存储

| 知识点 | 说明 |
|--------|------|
| `preferencesDataStore(name)` | 属性委托（by）创建 DataStore |
| `Flow<T>` | 数据变化时自动通知订阅者 |
| `suspend fun` + `edit { }` | 挂起函数 + 原子写入 |
| `first()` | 从 Flow 取一次值 |
| `stringPreferencesKey` / `booleanPreferencesKey` | 类型安全的键 |
| JSON 序列化缓存 | Gson 转 JSON 字符串存入 DataStore |

### `data/local/db/NekoFeedDatabase.kt` — Room 数据库

| 知识点 | 说明 |
|--------|------|
| Room 三件套 | Entity（表） + DAO（操作） + Database（容器） |
| `@Database` 注解 | 声明 Entity 列表和版本号 |
| 单例模式 `@Volatile + synchronized` | 与 PlayerManager 相同的双重检查锁定 |
| Migration 迁移脚本 | 数据库 Schema 升级时的 SQL 操作 |
| `fallbackToDestructiveMigration` | 迁移失败时销毁重建（开发用） |

---

## 阶段 3：业务逻辑（理解"数据怎么流转"）

### `ui/feed/FolderViewModel.kt` — 核心 ViewModel（最重要！）

| 知识点 | 说明 |
|--------|------|
| `AndroidViewModel` vs `ViewModel` | 需要 Application Context 时用 AndroidViewModel |
| `MutableStateFlow` / `StateFlow` | 可变/不可变的状态流 |
| `.collectAsState()` | Flow → Compose State |
| `.update { it.copy(...) }` | 原子更新 + 不可变副本 |
| `viewModelScope.launch { }` | 生命周期感知的协程 |
| 乐观更新模式 | 先更新 UI → 再发网络请求 → 失败回滚 |
| `Result.fold(onSuccess, onFailure)` | 函数式错误处理 |
| `Semaphore` 并发控制 | 限制同时进行的 AI 请求数 |
| 分页加载 | offset/limit + loadMore |
| `InteractionSyncStore` 跨页面同步 | 事件总线模式 |

### `data/repository/FolderRepository.kt` — Repository 模式

| 知识点 | 说明 |
|--------|------|
| Repository 模式 | ViewModel 不直接调用网络/数据库，通过 Repository 中转 |
| 数据获取策略 | Mock → 网络 → 本地缓存 → 错误 |
| `Result<T>` | Kotlin 标准库的结果包装 |
| `withContext(Dispatchers.IO)` | 切换到 IO 线程池 |
| Lambda 构造函数参数 | `feedApiProvider: () -> FeedApi` 延迟获取 |

### `data/repository/InteractionSyncStore.kt` — 事件总线

| 知识点 | 说明 |
|--------|------|
| `SharedFlow` vs `StateFlow` | SharedFlow 无初始值，适合事件广播 |
| `replay = 0` | 新订阅者不收历史事件 |
| `tryEmit()` | 非挂起版本的 emit |
| `object` 单例 | 全局唯一实例 |

### `player/PlayerManager.kt` — ExoPlayer 单例

| 知识点 | 说明 |
|--------|------|
| 单例模式（双重检查锁定） | `@Volatile + synchronized + companion object` |
| 懒初始化 `get()` | 自定义属性访问器 |
| `StateFlow` 暴露状态 | 数据驱动 UI |
| LRU 缓存 | 最近使用的视频数据缓存到本地 |
| `Player.Listener` | 接口的匿名实现 |

---

## 阶段 4：UI 渲染（理解"数据怎么变成画面"）

### `MainActivity.kt` — App 入口

| 知识点 | 说明 |
|--------|------|
| 单 Activity 架构 | 整个 App 只有一个 Activity |
| `enableEdgeToEdge()` | 全面屏适配 |
| `lifecycleScope.launch { }` | 生命周期感知的协程 |
| `withContext(Dispatchers.IO)` | 线程切换 |
| `AtomicReference` | 线程安全的引用容器 |
| `setContent { }` | Compose 的入口（替代 setContentView） |

### `navigation/AppNavHost.kt` — 导航系统

| 知识点 | 说明 |
|--------|------|
| 两级 NavHost | 外层（认证流程） + 内层（主页功能） |
| `composable("route") { Screen() }` | 注册路由 |
| `navController.navigate()` / `popBackStack()` | 页面跳转 / 返回 |
| `viewModel()` | 自动管理 ViewModel 生命周期 |
| `collectAsState()` | 订阅 ViewModel 状态 |
| Spring 动画 | `spring(dampingRatio, stiffness)` |

### `ui/feed/FolderScreen.kt` — 首页主屏幕（最大的 Composable）

| 知识点 | 说明 |
|--------|------|
| `LazyColumn` + `items()` | 高性能列表 |
| `HorizontalPager` | 横向翻页（频道切换） |
| `LaunchedEffect(key) { }` | 生命周期内的一次性操作 |
| `snapshotFlow { }` | Compose State → Kotlin Flow |
| `DisposableEffect` | 可清理的副作用（监听器注册/注销） |
| `remember` / `rememberUpdatedState` | 缓存和更新引用 |
| `PullToRefreshBox` | 下拉刷新 |
| 无限滚动检测 | 最后可见 item 接近总数时加载更多 |
| 视口曝光计算 | 可见像素 >= 50% 时记录曝光 |

### `ui/feed/components/FeedItemCard.kt` — 卡片路由

| 知识点 | 说明 |
|--------|------|
| `when` 分发策略 | 根据 cardType 渲染不同 Composable |
| Lambda 可选参数 | `onLikeClick: ((String) -> Unit)? = null` |
| 函数作为参数 | 把"做什么"的决定权交给外部 |

### `ui/components/BottomNavigationBar.kt` — 底部导航栏

| 知识点 | 说明 |
|--------|------|
| `sealed class`（密封类） | 比 enum 更灵活，每个子类可有不同属性 |
| `data object` | 单例子类 |
| Material 3 组件 | `NavigationBar` / `NavigationBarItem` |
| `AnimatedContent` | 内容切换动画 |

---

## 按知识点索引

| 知识点 | 去哪看 |
|--------|--------|
| data class | `FeedItem.kt`, `User.kt` |
| enum class | `FeedItem.kt`, `PlayerManager.kt` |
| sealed class | `BottomNavigationBar.kt` |
| 扩展函数 | `FeedRules.kt` |
| when 表达式 | `FeedRules.kt`, `FeedItemCard.kt` |
| companion object / 单例 | `PlayerManager.kt`, `NekoFeedDatabase.kt` |
| Retrofit 网络请求 | `FolderApi.kt`, `OetrofitClient.kt` |
| DataStore 键值存储 | `TokenManager.kt` |
| Room 数据库 | `NekoFeedDatabase.kt` |
| ViewModel + StateFlow | `FolderViewModel.kt` |
| Repository 模式 | `FolderRepository.kt` |
| SharedFlow 事件总线 | `InteractionSyncStore.kt` |
| 乐观更新 | `FolderViewModel.kt` 的 toggleLike |
| 分页加载 | `FolderViewModel.kt` 的 loadMore |
| Compose 基础 | `FolderScreen.kt`, `FeedItemCard.kt` |
| LazyColumn 列表 | `FolderScreen.kt` |
| HorizontalPager 翻页 | `FolderScreen.kt` |
| LaunchedEffect / DisposableEffect | `FolderScreen.kt` |
| Navigation 导航 | `AppNavHost.kt` |
| Material 3 组件 | `BottomNavigationBar.kt` |
| Spring 动画 | `AppNavHost.kt`, `BottomNavigationBar.kt` |
| ExoPlayer 视频播放 | `PlayerManager.kt` |
| 协程与线程切换 | `MainActivity.kt`, `FolderViewModel.kt` |

---

## 文件目录速查

```
app/src/main/java/com/ico/nekofeed/
├── MainActivity.kt                    ← App 入口，协程，setContent
├── NekoFeedApp.kt                     ← Application，Coil 图片配置
├── data/
│   ├── model/
│   │   ├── FeedItem.kt                ← 核心数据模型，data class，enum
│   │   └── User.kt                    ← 用户模型，DTO 模式
│   ├── remote/
│   │   ├── FolderApi.kt               ← Retrofit 接口，suspend fun
│   │   └── OetrofitClient.kt          ← Retrofit 单例，拦截器
│   ├── local/
│   │   ├── TokenManager.kt            ← DataStore，Flow，键值存储
│   │   └── db/
│   │       └── NekoFeedDatabase.kt    ← Room 数据库，单例，Migration
│   └── repository/
│       ├── FolderRepository.kt        ← Repository 模式，数据获取策略
│       └── InteractionSyncStore.kt    ← SharedFlow 事件总线
├── navigation/
│   └── AppNavHost.kt                  ← 导航系统，两级 NavHost
├── player/
│   └── PlayerManager.kt               ← ExoPlayer 单例，StateFlow
├── ui/
│   ├── feed/
│   │   ├── FolderScreen.kt            ← 首页 Composable，LazyColumn，Pager
│   │   ├── FolderViewModel.kt         ← 核心 ViewModel，StateFlow，乐观更新
│   │   └── components/
│   │       └── FeedItemCard.kt        ← 卡片路由，when 分发
│   └── components/
│       └── BottomNavigationBar.kt     ← 底部导航栏，sealed class
└── util/
    ├── UiState.kt                     ← UI 状态数据类
    └── FeedRules.kt                   ← 扩展函数，when 表达式
```
