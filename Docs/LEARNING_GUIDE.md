# NekoFeed Kotlin/Android 学习手册

> 本手册为 Kotlin/Android 初学者设计，按"由浅入深"的顺序组织。
> 每个章节告诉你：去哪个文件、学什么、知识点是什么。
> 所有关键文件都已添加了中文学习注释。

---

## 目录

1. [数据模型 → 理解项目在处理什么数据](#1-数据模型)
2. [枚举与扩展函数 → Kotlin 语法糖](#2-枚举与扩展函数)
3. [网络层 → Retrofit 如何定义 API](#3-网络层)
4. [本地存储 → DataStore 与 Room](#4-本地存储)
5. [数据仓库 → Repository 模式](#5-数据仓库)
6. [事件总线 → SharedFlow 跨页面通信](#6-事件总线)
7. [ViewModel → 业务逻辑中心](#7-viewmodel)
8. [UI 状态 → UiState 数据类](#8-ui-状态)
9. [Compose UI → 声明式界面](#9-compose-ui)
10. [导航系统 → 页面路由](#10-导航系统)
11. [视频播放 → ExoPlayer 单例](#11-视频播放)
12. [App 入口 → 启动流程](#12-app-入口)

---

## 1. 数据模型

### 去哪里看
- `app/src/main/java/com/ico/nekofeed/data/model/FeedItem.kt`
- `app/src/main/java/com/ico/nekofeed/data/model/User.kt`

### 学什么
项目的"数据长什么样"。所有页面都围绕这些数据类来渲染。

### 知识点

| 概念 | 在哪个文件 | 说明 |
|------|-----------|------|
| `data class` | FeedItem.kt | 自动生成 equals/hashCode/copy/toString |
| `@Immutable` | FeedItem.kt | Compose 优化：标记不可变对象，跳过不必要的重组 |
| `@SerializedName` | FeedItem.kt, User.kt | Gson 注解：JSON snake_case → Kotlin camelCase |
| 计算属性 `get()=` | FeedItem.kt | 不存储，每次访问实时计算（如 displaySummary） |
| 默认参数值 | User.kt | `val bio: String? = null`，调用时可省略 |
| 可空类型 `?` | 全局 | Kotlin 的空安全：编译时防止 NullPointerException |

### 阅读顺序
1. 先读 `FeedItem.kt`（核心数据，理解字段分组）
2. 再读 `User.kt`（用户相关，更简单）

---

## 2. 枚举与扩展函数

### 去哪里看
- `app/src/main/java/com/ico/nekofeed/data/model/FeedItem.kt`（枚举类）
- `app/src/main/java/com/ico/nekofeed/util/FeedRules.kt`（扩展函数）

### 学什么
Kotlin 的语法糖，让代码更简洁、更安全。

### 知识点

| 概念 | 在哪个文件 | 说明 |
|------|-----------|------|
| `enum class` | FeedItem.kt | 枚举类，有限的命名常量（如 FeedCategory） |
| `companion object` | FeedItem.kt | 类似 Java 的 static 方法/属性 |
| 扩展函数 | FeedRules.kt | `fun FeedItem.matchesCategory()` 给已有类添加新方法 |
| `when` 表达式 | FeedRules.kt | 比 Java switch 更强大的模式匹配 |

---

## 3. 网络层

### 去哪里看
- `app/src/main/java/com/ico/nekofeed/data/remote/FolderApi.kt`
- `app/src/main/java/com/ico/nekofeed/data/remote/OetrofitClient.kt`

### 学什么
Retrofit 如何定义 HTTP 接口，OkHttp 拦截器如何工作。

### 知识点

| 概念 | 在哪个文件 | 说明 |
|------|-----------|------|
| Retrofit 接口定义 | FolderApi.kt | `interface` + 注解（`@GET`/`@POST`）描述 HTTP 请求 |
| `suspend fun` | FolderApi.kt | 挂起函数，可在协程中直接调用 |
| `@Query` / `@Path` / `@Body` | FolderApi.kt | 请求参数注解 |
| 单例 `object` | OetrofitClient.kt | 全局唯一的网络客户端 |
| OkHttp 拦截器 | OetrofitClient.kt | 自动添加 Token、Device-Id 请求头，处理 401 |
| 动态 BaseUrl | OetrofitClient.kt | 运行时切换服务器地址 |

### 阅读顺序
1. 先读 `FolderApi.kt`（理解 API 定义）
2. 再读 `OetrofitClient.kt`（理解网络配置）

---

## 4. 本地存储

### 去哪里看
- `app/src/main/java/com/ico/nekofeed/data/local/TokenManager.kt`（DataStore）
- `app/src/main/java/com/ico/nekofeed/data/local/db/NekoFeedDatabase.kt`（Room）

### 学什么
Android 的两种本地持久化方案。

### 知识点

| 概念 | 在哪个文件 | 说明 |
|------|-----------|------|
| DataStore | TokenManager.kt | 键值对存储（替代 SharedPreferences），基于协程 |
| `preferencesDataStore` | TokenManager.kt | 委托属性（`by`）创建 DataStore 实例 |
| `Flow<T>` | TokenManager.kt | 响应式数据流，值变化时自动推送 |
| Room 数据库 | NekoFeedDatabase.kt | SQLite 封装，类型安全的 SQL 操作 |
| `@Database` / `@Entity` / `@Dao` | NekoFeedDatabase.kt | Room 三件套：数据库、表、数据访问对象 |
| 数据库迁移 | NekoFeedDatabase.kt | `Migration` 脚本，表结构变化时的升级策略 |
| 单例模式 | NekoFeedDatabase.kt | `@Volatile` + `synchronized` 保证全局唯一实例 |

### 阅读顺序
1. 先读 `TokenManager.kt`（键值对，更简单）
2. 再读 `NekoFeedDatabase.kt`（关系型数据库，更复杂）

---

## 5. 数据仓库

### 去哪里看
- `app/src/main/java/com/ico/nekofeed/data/repository/FolderRepository.kt`

### 学什么
Repository 模式——ViewModel 如何获取数据。

### 知识点

| 概念 | 在哪个文件 | 说明 |
|------|-----------|------|
| Repository 模式 | FolderRepository.kt | 数据仓库，统一管理网络/本地/降级数据 |
| `withContext(Dispatchers.IO)` | FolderRepository.kt | 切换到 IO 线程（网络/磁盘操作） |
| `Result<T>` | FolderRepository.kt | Kotlin 标准库：成功/失败包装器 |
| `.fold(onSuccess, onFailure)` | FolderRepository.kt | 模式匹配处理成功和失败 |
| 降级策略 | FolderRepository.kt | 网络失败 → 本地缓存 → 硬编码数据 |

---

## 6. 事件总线

### 去哪里看
- `app/src/main/java/com/ico/nekofeed/data/repository/InteractionSyncStore.kt`

### 学什么
跨页面通信：详情页点赞后，首页怎么同步更新？

### 知识点

| 概念 | 在哪个文件 | 说明 |
|------|-----------|------|
| `SharedFlow` | InteractionSyncStore.kt | 事件流（无初始值，只收未来事件） |
| `MutableSharedFlow` | InteractionSyncStore.kt | 可写的事件流（内部用） |
| `tryEmit` | InteractionSyncStore.kt | 非挂起版本的 emit（缓冲区满时返回 false） |
| `object` 单例 | InteractionSyncStore.kt | 全局唯一的事件总线 |

---

## 7. ViewModel

### 去哪里看
- `app/src/main/java/com/ico/nekofeed/ui/feed/FolderViewModel.kt`（原名 FeedViewModel）

### 学什么
项目的核心业务逻辑——加载数据、分页、筛选、点赞、AI 分析、埋点。

### 知识点

| 概念 | 在哪个文件 | 说明 |
|------|-----------|------|
| `AndroidViewModel` | FolderViewModel.kt | 需要 Application Context 的 ViewModel |
| `MutableStateFlow` | FolderViewModel.kt | 可变的状态流，ViewModel 内部用 |
| `StateFlow` | FolderViewModel.kt | 不可变的状态流（对外暴露） |
| `.update { it.copy(...) }` | FolderViewModel.kt | 原子更新 + 不可变副本 |
| 乐观更新 | FolderViewModel.kt | 先更新 UI，再发请求，失败则回滚 |
| `viewModelScope.launch` | FolderViewModel.kt | 启动协程，ViewModel 销毁时自动取消 |
| `Semaphore` | FolderViewModel.kt | 并发控制（限制同时 8 个 AI 请求） |
| `combine` / `flatMapLatest` | FolderViewModel.kt | Flow 组合操作符 |

### 学习建议
这是最值得细读的文件。建议对照 FeedScreen.kt 一起看，理解 UI 如何调用 ViewModel。

---

## 8. UI 状态

### 去哪里看
- `app/src/main/java/com/ico/nekofeed/util/UiState.kt`

### 学什么
每个页面的"状态"长什么样。

### 知识点

| 概念 | 在哪个文件 | 说明 |
|------|-----------|------|
| `@Immutable data class` | UiState.kt | 不可变的 UI 状态，Compose 优化的基础 |
| 默认参数值 | UiState.kt | 所有字段都有默认值，创建时不需要填满 |
| 状态分类 | UiState.kt | 数据 / 加载状态 / 错误 / 选择 / AI 状态 |

---

## 9. Compose UI

### 去哪里看
- `app/src/main/java/com/ico/nekofeed/ui/feed/FolderScreen.kt`（FeedScreen）
- `app/src/main/java/com/ico/nekofeed/ui/feed/components/FeedItemCard.kt`（卡片路由）
- `app/src/main/java/com/ico/nekofeed/ui/components/BottomNavigationBar.kt`（底部导航）

### 学什么
Compose 声明式 UI 的写法。

### 知识点

| 概念 | 在哪个文件 | 说明 |
|------|-----------|------|
| `@Composable` 函数 | FolderScreen.kt | 用 Kotlin 代码写 UI（替代 XML） |
| `collectAsState()` | FolderScreen.kt | Flow → State，数据变化触发重组 |
| `LazyColumn` | FolderScreen.kt | 虚拟滚动列表（类似 RecyclerView） |
| `HorizontalPager` | FolderScreen.kt | 横向翻页（类似 ViewPager） |
| `LaunchedEffect` | FolderScreen.kt | 在 Composable 生命周期内启动协程 |
| `snapshotFlow` | FolderScreen.kt | Compose State → Flow |
| `DisposableEffect` | FolderScreen.kt | 可清理的副作用（生命周期结束时清理） |
| `remember` | FolderScreen.kt | 跨重组缓存值 |
| `rememberUpdatedState` | FolderScreen.kt | 在 LaunchedEffect 中引用最新回调 |
| 状态提升 | FolderScreen.kt | FeedScreenContent 无状态，FeedScreen 持有状态 |
| `sealed class` | BottomNavItem.kt | 密封类，限制子类集合 |
| `when` 分发 | FeedItemCard.kt | 根据枚举值分发到不同 Composable |
| `AnimatedContent` | FolderScreen.kt, BottomNavigationBar.kt | 内容切换动画 |

### 阅读顺序
1. 先读 `FeedItemCard.kt`（简单，理解卡片渲染）
2. 再读 `BottomNavigationBar.kt`（理解 sealed class + Material 3）
3. 最后读 `FolderScreen.kt`（复杂，理解列表 + 分页 + 刷新）

---

## 10. 导航系统

### 去哪里看
- `app/src/main/java/com/ico/nekofeed/navigation/AppNavHost.kt`

### 学什么
整个 App 的页面路由和跳转逻辑。

### 知识点

| 概念 | 在哪个文件 | 说明 |
|------|-----------|------|
| `NavHost` | AppNavHost.kt | 导航宿主，定义所有路由 |
| `composable("route")` | AppNavHost.kt | 注册路由，绑定 Composable |
| `navController.navigate()` | AppNavHost.kt | 跳转到指定路由 |
| `navController.popBackStack()` | AppNavHost.kt | 返回上一页 |
| `popUpTo` + `inclusive` | AppNavHost.kt | 清除路由栈中的指定页面 |
| 嵌套 NavHost | AppNavHost.kt | 两层导航：认证流程 + 主页功能 |
| `navArgument` | AppNavHost.kt | 路由参数（如 detail/{itemId}） |
| `viewModel()` | AppNavHost.kt | Compose 中获取 ViewModel |
| 页面动画 | AppNavHost.kt | spring 弹簧动画 |

---

## 11. 视频播放

### 去哪里看
- `app/src/main/java/com/ico/nekofeed/player/PlayerManager.kt`

### 学什么
ExoPlayer 单例管理、视频缓存、播放状态。

### 知识点

| 概念 | 在哪个文件 | 说明 |
|------|-----------|------|
| 单例模式 | PlayerManager.kt | `@Volatile` + `synchronized` + Double-Check Locking |
| ExoPlayer | PlayerManager.kt | Media3 视频播放器 |
| LRU 缓存 | PlayerManager.kt | 100MB 视频数据缓存 |
| `StateFlow` 状态管理 | PlayerManager.kt | 播放状态（IDLE/BUFFERING/READY/PLAYING/ERROR） |
| 匿名内部类 | PlayerManager.kt | `object : Player.Listener { }` 实现回调接口 |

---

## 12. App 入口

### 去哪里看
- `app/src/main/java/com/ico/nekofeed/MainActivity.kt`

### 学什么
App 启动后做了什么。

### 知识点

| 概念 | 在哪个文件 | 说明 |
|------|-----------|------|
| 单 Activity 架构 | MainActivity.kt | 整个 App 只有一个 Activity |
| `ComponentActivity` | MainActivity.kt | Compose 专用的 Activity 基类 |
| `enableEdgeToEdge()` | MainActivity.kt | 全面屏适配 |
| `lifecycleScope.launch` | MainActivity.kt | 生命周期感知的协程 |
| `withContext(Dispatchers.IO)` | MainActivity.kt | 切换到 IO 线程 |
| `AtomicReference` | MainActivity.kt | 线程安全的引用容器 |
| `setContent { }` | MainActivity.kt | Compose 的入口（替代 setContentView） |
| 401 处理 | MainActivity.kt | Token 过期 → 清除认证 → 重启 App |

---

## 推荐学习路线

### 第一天：理解数据
1. `data/model/FeedItem.kt` → 数据长什么样
2. `data/model/User.kt` → 用户数据
3. `util/FeedRules.kt` → 扩展函数

### 第二天：理解网络和存储
4. `data/remote/FolderApi.kt` → API 定义
5. `data/local/TokenManager.kt` → DataStore
6. `data/local/db/NekoFeedDatabase.kt` → Room

### 第三天：理解数据流
7. `data/repository/FolderRepository.kt` → Repository 模式
8. `data/repository/InteractionSyncStore.kt` → 事件总线
9. `util/UiState.kt` → UI 状态

### 第四天：理解业务逻辑
10. `ui/feed/FolderViewModel.kt` → ViewModel（最重要！）

### 第五天：理解 UI
11. `ui/feed/components/FeedItemCard.kt` → 卡片渲染
12. `ui/components/BottomNavigationBar.kt` → 底部导航
13. `ui/feed/FolderScreen.kt` → 主屏幕

### 第六天：理解架构
14. `navigation/AppNavHost.kt` → 导航系统
15. `MainActivity.kt` → App 入口
16. `player/PlayerManager.kt` → 视频播放

---

## 核心 Kotlin 语法速查

| 语法 | 含义 | 示例 |
|------|------|------|
| `val x: Type?` | 可空类型 | `val name: String? = null` |
| `?:` | Elvis 操作符（左边为 null 用右边） | `name ?: "默认"` |
| `?.` | 安全调用（左边为 null 则跳过） | `user?.name` |
| `!!` | 非空断言（确定不是 null） | `user!!.name` |
| `data class` | 数据类（自动生成方法） | `data class User(val name: String)` |
| `sealed class` | 密封类（限制子类集合） | `sealed class Result` |
| `object` | 单例 | `object Singleton { }` |
| `companion object` | 类似 static | `companion object { fun create() }` |
| `fun Type.ext()` | 扩展函数 | `fun String.isEmail(): Boolean` |
| `by` | 属性委托 | `val state by mutableStateOf(0)` |
| `{ it.copy(...) }` | 不可变副本 | `user.copy(name = "新名字")` |
| `suspend fun` | 挂起函数 | `suspend fun fetchData(): Data` |
| `Flow<T>` | 响应式数据流 | `val data: Flow<List<Item>>` |
| `StateFlow<T>` | 状态流（有初始值） | `val state: StateFlow<UiState>` |
| `SharedFlow<T>` | 事件流（无初始值） | `val events: SharedFlow<Event>` |
| `when` | 模式匹配 | `when (x) { is A -> ... }` |
| `list.map { }` | 列表变换 | `items.map { it.name }` |
| `list.filter { }` | 列表过滤 | `items.filter { it.isVideo }` |
| `?.let { }` | 非空时执行 | `user?.let { showUser(it) }` |

---

## MVVM 架构总览

```
┌─────────────────────────────────────────────────┐
│                  Compose UI                      │
│  (FolderScreen, FeedItemCard, BottomNav, ...)    │
│  职责：渲染界面，订阅状态，触发事件               │
└────────────────────┬────────────────────────────┘
                     │ collectAsState() / 调用方法
                     ▼
┌─────────────────────────────────────────────────┐
│                  ViewModel                       │
│  (FeedViewModel, AuthViewModel, ...)             │
│  职责：持有 UI 状态，调用 Repository，业务逻辑    │
└────────────────────┬────────────────────────────┘
                     │ suspend fun 调用
                     ▼
┌─────────────────────────────────────────────────┐
│                  Repository                      │
│  (FeedRepository, UserRepository, AiRepository)  │
│  职责：统一管理数据源（网络/本地/降级）           │
└──────┬──────────────────────────────┬───────────┘
       │                              │
       ▼                              ▼
┌──────────────┐            ┌──────────────────┐
│   Remote     │            │     Local        │
│  Retrofit    │            │  DataStore/Room  │
│  (FolderApi) │            │  (TokenManager)  │
└──────────────┘            └──────────────────┘
```

---

## 文件清单（已添加学习注释）

| 文件 | 学习主题 | 难度 |
|------|---------|------|
| `data/model/FeedItem.kt` | 数据模型、data class、枚举 | ★☆☆ |
| `data/model/User.kt` | 数据模型、DTO | ★☆☆ |
| `util/FeedRules.kt` | 扩展函数、when 表达式 | ★☆☆ |
| `util/UiState.kt` | UI 状态模式、不可变数据 | ★☆☆ |
| `data/remote/FolderApi.kt` | Retrofit 接口定义 | ★★☆ |
| `data/local/TokenManager.kt` | DataStore、Flow、协程 | ★★☆ |
| `data/local/db/NekoFeedDatabase.kt` | Room 数据库、迁移 | ★★☆ |
| `data/repository/FolderRepository.kt` | Repository 模式、Result | ★★☆ |
| `data/repository/InteractionSyncStore.kt` | SharedFlow、事件总线 | ★★☆ |
| `ui/components/BottomNavigationBar.kt` | sealed class、Compose 组件 | ★★☆ |
| `ui/feed/components/FeedItemCard.kt` | when 分发、Compose 模式 | ★★☆ |
| `player/PlayerManager.kt` | 单例模式、ExoPlayer | ★★★ |
| `ui/feed/FolderViewModel.kt` | ViewModel、StateFlow、乐观更新 | ★★★ |
| `navigation/AppNavHost.kt` | 导航系统、嵌套 NavHost | ★★★ |
| `ui/feed/FolderScreen.kt` | Compose UI、LazyColumn、Pager | ★★★ |
| `MainActivity.kt` | App 入口、协程、生命周期 | ★★★ |
