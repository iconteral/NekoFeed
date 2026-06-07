# NekoFeed 项目入门指南 —— 写给 Android 初学者

> 📖 **阅读须知**：本文假设你了解 Kotlin 基础语法，但对 Android 开发还很陌生。我会用 **生活类比 → 核心概念 → 项目代码** 的方式带你理解整个项目。

---

## 目录

1. [这个项目到底在做什么？](#1-这个项目到底在做什么)
2. [项目的「骨架」—— 它长什么样？](#2-项目的骨架--它长什么样)
3. [第一个关键概念：MVVM 是什么？](#3-第一个关键概念mvvm-是什么)
4. [从打开 App 开始 —— MainActivity](#4-从打开-app-开始--mainactivity)
5. [页面怎么切换？—— Navigation 导航](#5-页面怎么切换--navigation-导航)
6. [数据从哪来？—— Repository + Retrofit](#6-数据从哪来--repository--retrofit)
7. [核心数据长什么样？—— FeedItem 模型](#7-核心数据长什么样--feeditem-模型)
8. [UI 怎么画的？—— Jetpack Compose 入门](#8-ui-怎么画的--jetpack-compose-入门)
9. [不同卡片怎么渲染？—— 条件渲染](#9-不同卡片怎么渲染--条件渲染)
10. [ViewModel 在干什么？—— 状态管理](#10-viewmodel-在干什么--状态管理)
11. [点赞怎么实现的？—— 乐观更新](#11-点赞怎么实现的--乐观更新)
12. [数据怎么存到手机上？—— Room 和 DataStore](#12-数据怎么存到手机上--room-和-datastore)
13. [视频怎么播放的？—— ExoPlayer](#13-视频怎么播放的--exoplayer)
14. [AI 功能怎么接的？—— LLM API 调用](#14-ai-功能怎么接的--llm-api-调用)
15. [后端 Server 简介 —— 数据从哪来](#15-后端-server-简介--数据从哪来)
16. [新手常见问题 FAQ](#16-新手常见问题-faq)
17. [推荐学习路线](#17-推荐学习路线)

---

## 1. 这个项目到底在做什么？

### 一句话总结

> NekoFeed 是一个**信息流 App**，类似你手机上的今日头条、小红书的首页 —— 一个列表里混合展示新闻文章、广告、视频、商品推荐，你可以滑动浏览、点赞、收藏、搜索。

### 用一张图说明

```
你打开 App 看到的：

┌────────────────────────┐
│  NekoFeed    🔍  📊     │  ← 顶部标题栏
├────────────────────────┤
│ 精选 | 科技 | AI | 本地  │  ← Tab 频道切换
├────────────────────────┤
│ ┌────────────────────┐ │
│ │  [大图]             │ │  ← 一条新闻文章（大图卡片）
│ │  AI 手机新品发布     │ │
│ │  36Kr · 2小时前     │ │
│ │  #科技 #AI #手机    │ │
│ │  ❤️ 12  ⭐ 3  📤 1  │ │
│ └────────────────────┘ │
│ ┌────────────────────┐ │
│ │ 星巴克双杯   [小图] │ │  ← 一条广告（小图卡片）
│ │ 赞助 · Starbucks    │ │
│ └────────────────────┘ │
│ ┌────────────────────┐ │
│ │  [视频播放器 ▶️]     │ │  ← 一条视频（视频卡片）
│ │  iPhone 15 Pro 广告  │ │
│ └────────────────────┘ │
│        ...更多内容...    │
└────────────────────────┘
│  🏠首页  🔍搜索  👤我的  │  ← 底部导航栏
└────────────────────────┘
```

### 这个 App 包含哪些功能？

| 功能 | 说明 |
|------|------|
| 📰 信息流首页 | 一个可滚动的列表，展示各种内容 |
| 🔍 搜索 | 输入关键词搜索内容，还能用 AI 理解你的意思 |
| 📄 详情页 | 点击一条内容后看到完整信息 |
| ❤️ 点赞/收藏 | 喜欢的内容可以点赞和收藏 |
| 🎬 视频播放 | 视频内容可以直接在列表里播放 |
| 📊 统计页 | 展示曝光次数、点击次数等数据 |
| 👤 用户系统 | 可以注册、登录，管理自己的点赞/收藏 |
| 🤖 AI 分析 | 用大模型为每条内容生成摘要和标签 |

---

## 2. 项目的「骨架」—— 它长什么样？

### 类比：把项目想象成一个餐厅

```
后厨（NekoFeedServer）                     前厅（Android App）
┌──────────────────────┐              ┌──────────────────────┐
│  采购员 → RSS 抓取     │              │  服务员 → UI 界面     │
│  洗菜工 → 数据清洗     │  ──菜单──→  │  领班 → ViewModel    │
│  厨师 → AI 加工       │  (JSON API)  │  仓库管理 → Repository │
│  食材库 → SQLite 数据库 │              │  保鲜柜 → Room 缓存   │
└──────────────────────┘              └──────────────────────┘
```

- **后厨**（NekoFeedServer）负责准备数据 —— 从各个新闻网站抓内容、清洗整理、加上 AI 标签
- **前厅**（Android App）负责展示 —— 把数据漂亮地显示给用户看

### 项目文件夹一览

```
NekoFeed/                       # 项目根目录
├── app/                        # 📱 Android App 代码在这里！
│   └── src/main/java/com/ico/nekofeed/
│       ├── MainActivity.kt     # 🚪 App 的「大门」
│       ├── navigation/         # 🗺️ 页面路由（去哪个页面）
│       ├── data/               # 📦 数据层（获取/存储数据）
│       │   ├── model/          #    └── 数据模型（数据长什么样）
│       │   ├── remote/         #    └── 网络请求（从服务器拿数据）
│       │   ├── local/          #    └── 本地存储（存在手机上）
│       │   └── repository/     #    └── 数据仓库（协调网络和本地）
│       ├── ui/                 # 🎨 界面层（用户看到的东西）
│       │   ├── feed/           #    └── 首页信息流
│       │   ├── detail/         #    └── 详情页
│       │   ├── search/         #    └── 搜索页
│       │   ├── stats/          #    └── 统计页
│       │   ├── auth/           #    └── 登录/注册
│       │   ├── profile/        #    └── 个人中心
│       │   └── theme/          #    └── 主题（颜色/字体）
│       ├── player/             # 🎬 视频播放器
│       └── util/               # 🔧 工具类
├── NekoFeedServer/             # 🖥️ 后端服务（Python）
└── Docs/                       # 📝 设计文档
```

> [!TIP]
> **新手建议**：先不看后端代码！重点看 `app/` 目录下的 Android 代码就好。

---

## 3. 第一个关键概念：MVVM 是什么？

### 类比：去餐厅点餐

```
你（View/UI）：「我要一份宫保鸡丁」          → 用户操作
     ↓
服务员（ViewModel）：记下来，去后厨传话       → 处理逻辑
     ↓
后厨（Model/Repository）：查原料、做菜        → 获取数据
     ↓
服务员 通知 你：「菜好了！」                   → 数据更新
     ↓
你 看到 盘子里的菜                            → UI 刷新
```

### 在代码里对应什么？

| 角色 | 项目中对应 | 举例 |
|------|-----------|------|
| **View (UI)** | `XXXScreen.kt` | [FolderScreen.kt](file:///l:/NekoFeed/app/src/main/java/com/ico/nekofeed/ui/feed/FolderScreen.kt) —— 画出信息流列表 |
| **ViewModel** | `XXXViewModel.kt` | [FolderViewModel.kt](file:///l:/NekoFeed/app/src/main/java/com/ico/nekofeed/ui/feed/FolderViewModel.kt) —— 管理数据和状态 |
| **Model (数据)** | `Repository` + `Model` | [FeedRepository.kt](file:///l:/NekoFeed/app/src/main/java/com/ico/nekofeed/data/repository/FolderRepository.kt) + [FeedItem.kt](file:///l:/NekoFeed/app/src/main/java/com/ico/nekofeed/data/model/FeedItem.kt) |

### 数据流动方向

```
用户操作（点击、滑动）
    ↓  调用 ViewModel 的方法
ViewModel 处理逻辑
    ↓  调用 Repository 获取数据
Repository 返回数据
    ↓  ViewModel 更新状态（StateFlow）
UI 自动感知变化
    ↓  重新绘制界面
用户看到新内容
```

> [!IMPORTANT]
> **核心原则**：UI 层**永远不直接**去请求网络或读数据库。它只跟 ViewModel 打交道，ViewModel 再去找 Repository 拿数据。这样代码职责清晰，容易维护。

---

## 4. 从打开 App 开始 —— MainActivity

当用户点击 App 图标，系统第一个执行的代码就是 [MainActivity.kt](file:///l:/NekoFeed/app/src/main/java/com/ico/nekofeed/MainActivity.kt)：

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()  // ① 全屏模式（状态栏透明）

        // ② 初始化各种管理器
        val tokenManager = TokenManager(applicationContext)  // 管理登录 Token
        val cachedToken = AtomicReference<String?>(null)      // 缓存 Token（线程安全）
        val cachedDeviceId = AtomicReference<String?>(null)   // 缓存设备 ID

        // ③ 恢复之前保存的服务器地址和设备 ID
        runBlocking {
            val serverConfig = tokenManager.getServerConfig()
            RetrofitClient.updateBaseUrl(serverConfig.baseUrl) // 设置服务器地址
            cachedDeviceId.set(tokenManager.getDeviceId())
        }

        // ④ 告诉网络库「每次请求时带上这些信息」
        RetrofitClient.setTokenProvider { cachedToken.get() }     // 带上登录凭证
        RetrofitClient.setDeviceIdProvider { cachedDeviceId.get() } // 带上设备 ID

        // ⑤ 设置认证仓库
        val authRepository = AuthRepository(...) { newToken ->
            cachedToken.set(newToken)  // 登录成功后缓存新 Token
        }

        // ⑥ 最终展示 UI
        setContent {
            NekoFeedTheme {     // 应用主题（颜色、字体）
                AppNavHost(     // 导航系统（控制显示哪个页面）
                    authRepository = authRepository,
                    ...
                )
            }
        }
    }
}
```

### 🔑 你需要理解的关键点

1. **`setContent { }`** —— 这是 Jetpack Compose 的入口。花括号里写的就是 UI 代码
2. **`NekoFeedTheme { }`** —— 把所有 UI 包裹在主题里，统一颜色和字体风格
3. **`AppNavHost(...)`** —— 这是导航系统，决定用户看到哪个页面
4. **`runBlocking { }`** —— 等待异步操作完成。这里在 App 启动时恢复之前保存的配置

---

## 5. 页面怎么切换？—— Navigation 导航

### 类比：App 就像一栋楼

```
App 这栋楼：
├── 1F 大厅（main）—— 有电梯（底部导航栏）
│   ├── 左边：🏠 首页（feed）
│   ├── 中间：🔍 搜索（search）
│   └── 右边：👤 我的（profile）
│   └── 楼上：📄 详情页（detail/xxx）
│   └── 楼上：📊 统计页（stats）
├── 地下一层：🔑 登录页（login）
└── 地下二层：📝 注册页（register）
```

### 代码实现

在 [AppNavHost.kt](file:///l:/NekoFeed/app/src/main/java/com/ico/nekofeed/navigation/AppNavHost.kt) 里，有**两层导航**：

```kotlin
// 第一层：根导航（控制 登录/注册/主页面 之间的切换）
NavHost(navController, startDestination = "main") {
    composable("login")    { LoginScreen(...) }      // 登录页
    composable("register") { RegisterScreen(...) }   // 注册页
    composable("main")     { MainScreen(...) }       // 主页面（包含底部导航）
}

// 第二层：在 MainScreen 里，还有一个嵌套导航
// （控制 首页/搜索/统计/详情 之间的切换）
NavHost(nestedNavController, startDestination = "feed") {
    composable("feed")         { FeedScreen(...) }         // 首页
    composable("detail/{id}")  { FeedDetailScreen(...) }   // 详情页
    composable("search")       { SearchScreen(...) }       // 搜索页
    composable("stats")        { StatsScreen(...) }        // 统计页
    composable("profile")      { ProfileScreen(...) }      // 个人中心
    // ...更多页面
}
```

### 怎么跳转到另一个页面？

```kotlin
// 跳转到详情页，传入内容 ID
navController.navigate("detail/$itemId")

// 返回上一页
navController.popBackStack()

// 跳转到搜索页
navController.navigate("search")
```

> [!NOTE]
> **路由参数**：`"detail/{itemId}"` 中的 `{itemId}` 是一个占位符。实际跳转时写 `"detail/item_abc123"`，目标页面就能拿到 `itemId = "item_abc123"`。

---

## 6. 数据从哪来？—— Repository + Retrofit

### 类比：网购流程

```
你（ViewModel）下单 → 快递公司（Retrofit）从仓库取货 → 仓库（服务器）发货
                                                 ↓
                  如果仓库断货 → 从本地小超市（Fallback）拿

Repository 就像一个「购物代理」，帮你决定从网上买还是从楼下超市买。
```

### 第一步：定义「菜单」—— API 接口

在 [FolderApi.kt](file:///l:/NekoFeed/app/src/main/java/com/ico/nekofeed/data/remote/FolderApi.kt) 中，定义了所有可以跟服务器交流的方式：

```kotlin
interface FeedApi {
    // 获取信息流列表
    // 相当于告诉服务器：「给我 20 条科技类的内容」
    @GET("api/feed")
    suspend fun getFeed(
        @Query("category") category: String? = null,  // 分类过滤
        @Query("limit") limit: Int = 20,               // 每页数量
        @Query("offset") offset: Int = 0,              // 从第几条开始
        @Query("base_url") baseUrl: String? = null      // 图片地址前缀
    ): FeedResponse

    // 点赞/取消点赞
    @POST("api/items/{itemId}/like")
    suspend fun toggleLike(@Path("itemId") itemId: String): ItemInteraction

    // 收藏/取消收藏
    @POST("api/items/{itemId}/collect")
    suspend fun toggleCollect(@Path("itemId") itemId: String): ItemInteraction

    // ...更多接口
}
```

### 🔑 注解说明

| 注解 | 含义 | 例子 |
|------|------|------|
| `@GET("api/feed")` | 发送 GET 请求到 `/api/feed` | 获取数据时用 |
| `@POST(...)` | 发送 POST 请求 | 修改数据时用（点赞、登录） |
| `@Query("category")` | 把参数加到 URL 后面 | `?category=tech` |
| `@Path("itemId")` | 把参数嵌入 URL 路径 | `api/items/abc123/like` |
| `suspend` | 异步函数，不会卡住界面 | Kotlin 协程 |

### 第二步：配置「快递公司」—— RetrofitClient

在 [OetrofitClient.kt](file:///l:/NekoFeed/app/src/main/java/com/ico/nekofeed/data/remote/OetrofitClient.kt) 中：

```kotlin
object RetrofitClient {
    // 服务器地址（默认是模拟器访问本机的特殊 IP）
    private var baseUrl: String = "http://10.0.2.2:8000"

    // OkHttp 客户端 —— 负责发送 HTTP 请求
    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)  // 打印请求日志，方便调试
            .addInterceptor { chain ->           // 自动加上认证信息
                val builder = chain.request().newBuilder()
                // 每个请求都自动带上 Token 和 Device ID
                tokenProvider?.invoke()?.let {
                    builder.header("Authorization", "Bearer $it")
                }
                deviceIdProvider?.invoke()?.let {
                    builder.header("X-Device-Id", it)
                }
                chain.proceed(builder.build())
            }
            .build()
    }

    // Retrofit —— 把 FeedApi 接口变成可以调用的对象
    var feedApi: FeedApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())  // JSON → Kotlin 对象
        .build()
        .create(FeedApi::class.java)
}
```

> [!NOTE]
> **`10.0.2.2` 是什么？** Android 模拟器运行在虚拟网络里，不能直接用 `localhost` 访问电脑。Google 规定用 `10.0.2.2` 来代替 `localhost`。

### 第三步：「购物代理」—— FeedRepository

[FeedRepository.kt](file:///l:/NekoFeed/app/src/main/java/com/ico/nekofeed/data/repository/FolderRepository.kt) 的核心逻辑很简单：

```kotlin
class FeedRepository(private val feedApi: FeedApi) {
    // 内存缓存
    private val cachedItems = mutableListOf<FeedItem>()

    suspend fun loadFeed(
        category: String? = null,
        limit: Int = 20,
        offset: Int = 0
    ): Result<List<FeedItem>> {
        return withContext(Dispatchers.IO) {       // 在后台线程执行
            try {
                // 尝试从服务器获取
                val response = feedApi.getFeed(
                    category = category,
                    limit = limit,
                    offset = offset,
                    baseUrl = RetrofitClient.getBaseUrl()
                )
                cachedItems.addAll(response.items)
                Result.success(response.items)      // 成功！返回数据
            } catch (e: Exception) {
                Result.failure(e)                   // 失败！返回错误
            }
        }
    }

    // 当服务器挂了，用内置的假数据兜底
    fun getFallbackData(): List<FeedItem> {
        return FallbackFeedData.items
    }
}
```

### 🔑 你需要理解的关键点

1. **`Result<T>`** —— Kotlin 的结果包装类。`Result.success(数据)` 表示成功，`Result.failure(错误)` 表示失败
2. **`Dispatchers.IO`** —— 网络请求必须在后台线程执行，否则会卡死界面
3. **兜底数据** —— 如果服务器没启动，App 不会白屏，而是显示预置的假数据

---

## 7. 核心数据长什么样？—— FeedItem 模型

打开 [FeedItem.kt](file:///l:/NekoFeed/app/src/main/java/com/ico/nekofeed/data/model/FeedItem.kt)，这是整个项目**最重要的数据类**：

```kotlin
data class FeedItem(
    val id: String,              // 每条内容的唯一 ID

    // ---- 你在卡片上看到的内容 ----
    val title: String,           // 标题：「AI 手机新品发布」
    val summary: String?,        // 摘要：「这是一条科技资讯...」
    val imageUrl: String?,       // 封面图地址
    val mediaUrl: String?,       // 视频地址

    // ---- 来源信息 ----
    val sourceName: String?,     // 来自哪里：「36Kr」
    val sourceUrl: String?,      // 原文链接

    // ---- 类型标记（非常重要！）----
    val itemType: String?,       // 内容类型：article / video / ad / product
    val cardType: String?,       // 卡片样式：large_image / small_image / video / product

    // ---- AI 生成的内容 ----
    val aiSummary: String?,      // AI 一句话摘要
    val aiTags: List<String>?,   // AI 智能标签：["科技", "AI", "手机"]

    // ---- 互动数据 ----
    val isLiked: Boolean,        // 当前用户是否点赞了
    val isCollected: Boolean,    // 当前用户是否收藏了
    val likeCount: Int,          // 总点赞数
    val collectCount: Int,       // 总收藏数

    // ...更多字段
)
```

### 类比：`FeedItem` 就像一张「万能卡片」

同一个数据结构，通过 `itemType` 和 `cardType` 的不同值，可以变成不同的样子：

```
itemType="article" + cardType="large_image"  →  📰 新闻大图卡片
itemType="ad"      + cardType="small_image"  →  📢 广告小图卡片
itemType="video"   + cardType="video"        →  🎬 视频播放卡片
itemType="product" + cardType="product"      →  🛒 商品广告卡片
```

### 便捷属性（Computed Properties）

```kotlin
// 优先显示 AI 摘要，没有就显示原始摘要
val displaySummary: String
    get() = aiSummary ?: summary ?: ""

// 优先显示 AI 标签，没有就显示原始标签
val displayTags: List<String>
    get() = if (!aiTags.isNullOrEmpty()) aiTags else tags.orEmpty()

// 快速判断是不是视频
val isVideo: Boolean
    get() = itemType == "video" || cardType == "video"

// 快速判断是不是广告
val isAd: Boolean
    get() = itemType == "ad" || itemType == "product" || isSponsored
```

### `@SerializedName` 注解

```kotlin
@SerializedName("source_name")
val sourceName: String?
```

服务器返回的 JSON 字段名是 `source_name`（蛇形命名），但 Kotlin 里我们习惯用 `sourceName`（驼峰命名）。`@SerializedName` 告诉 Gson 库怎么对应。

---

## 8. UI 怎么画的？—— Jetpack Compose 入门

### 传统 Android vs Compose

```
传统方式（XML）：                    Compose 方式（Kotlin）：
在 XML 文件里画界面                  在 Kotlin 代码里「描述」界面
找 View 的 ID                       直接写函数
手动更新 View                        数据变了 → 自动重画
```

### Compose 核心概念

**① 一切都是函数**

```kotlin
@Composable                          // 这个注解表示「这是一个 UI 组件」
fun Greeting(name: String) {         // 接收参数
    Text(text = "Hello, $name!")     // 显示一段文字
}
```

**② 常用布局组件**

```kotlin
// 垂直排列（从上到下）
Column {
    Text("第一行")
    Text("第二行")
    Text("第三行")
}

// 水平排列（从左到右）
Row {
    Text("左边")
    Text("右边")
}

// 层叠排列（叠在一起）
Box {
    Image(...)        // 底层：一张图
    Text("图上文字")   // 上层：图上的文字
}
```

**③ 列表组件**

```kotlin
// LazyColumn = 可滚动的列表（只渲染屏幕上可见的项）
LazyColumn {
    items(feedItems) { item ->    // 遍历数据列表
        FeedItemCard(item = item) // 每条数据渲染一个卡片
    }
}
```

### 项目中的实际代码

以首页 `FeedScreen` 的简化版为例：

```kotlin
@Composable
fun FeedScreen(viewModel: FeedViewModel) {
    val uiState by viewModel.uiState.collectAsState()  // ① 监听状态

    Scaffold(                                            // ② 页面骨架
        topBar = {
            TopAppBar(title = { Text("NekoFeed") })      // 顶部标题栏
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            // ③ Tab 频道切换
            ScrollableTabRow(...) {
                FeedCategory.entries.forEach { category ->
                    Tab(
                        text = { Text(category.displayName) },
                        onClick = { viewModel.selectCategory(category) }
                    )
                }
            }

            // ④ 信息流列表
            LazyColumn {
                items(uiState.items) { item ->
                    FeedItemCard(
                        item = item,
                        onClick = { onItemClick(item.id) },
                        onLikeClick = { viewModel.toggleLike(item.id) }
                    )
                }

                // ⑤ 加载更多指示器
                if (uiState.isLoadingMore) {
                    item { CircularProgressIndicator() }
                }
            }
        }
    }
}
```

### 🔑 你需要理解的关键点

1. **`collectAsState()`** —— 把 ViewModel 的 StateFlow 转换成 Compose 能感知的状态。数据一变，UI 自动更新
2. **`Scaffold`** —— Material 3 的页面骨架，自动处理 TopBar 和 BottomBar 的布局
3. **`LazyColumn`** —— 高性能列表，类似传统的 RecyclerView，只渲染可见项
4. **`items(list)`** —— LazyColumn 的循环，把每条数据渲染成一个 UI 组件

---

## 9. 不同卡片怎么渲染？—— 条件渲染

[FeedItemCard.kt](file:///l:/NekoFeed/app/src/main/java/com/ico/nekofeed/ui/feed/components/FeedItemCard.kt) 是一个「路由组件」，根据卡片类型分发到不同的渲染组件：

```kotlin
@Composable
fun FeedItemCard(item: FeedItem, onClick: () -> Unit, ...) {
    // 根据 cardType 选择不同的卡片组件
    when (FeedCardType.fromString(item.cardType)) {

        FeedCardType.LARGE_IMAGE -> {
            // 大图卡片：顶部一张大图 + 标题 + 摘要
            LargeImageFeedCard(item, onClick, ...)
        }

        FeedCardType.SMALL_IMAGE -> {
            // 小图卡片：左边文字 + 右边小图
            SmallImageFeedCard(item, onClick, ...)
        }

        FeedCardType.VIDEO -> {
            // 视频卡片：视频播放器 + 标题
            VideoFeedCard(item, onClick, ...)
        }

        FeedCardType.PRODUCT -> {
            // 商品卡片：商品图 + 价格 + 购买按钮
            ProductFeedCard(item, onClick, ...)
        }

        FeedCardType.TEXT_ONLY -> {
            // 纯文本（无图时退化）
            SmallImageFeedCard(item, onClick, ...)
        }
    }
}
```

### 类比：同一份数据，穿不同的「衣服」

```
FeedItem 数据 ──→ cardType="large_image" ──→ 👗 穿大图样式
              ──→ cardType="video"       ──→ 🎬 穿视频样式
              ──→ cardType="product"     ──→ 🛍️ 穿商品样式
```

这就是**数据驱动 UI** —— 界面长什么样完全由数据决定，而不是写死在代码里。

---

## 10. ViewModel 在干什么？—— 状态管理

### 什么是「状态」？

UI 上所有可能变化的东西都叫**状态**：

```kotlin
data class FeedUiState(
    val selectedCategory: FeedCategory = FeedCategory.FEATURED,  // 当前选中的分类
    val items: List<FeedItem> = emptyList(),     // 当前显示的列表数据
    val isLoading: Boolean = false,               // 是否在加载中
    val isRefreshing: Boolean = false,            // 是否在下拉刷新
    val isLoadingMore: Boolean = false,           // 是否在加载更多
    val hasMore: Boolean = true,                  // 还有没有更多数据
    val playingItemId: String? = null,            // 当前播放的视频 ID
    val errorMessage: String? = null,             // 错误提示
    val usingFallback: Boolean = false,           // 是否在用兜底数据
    val isAiEnabled: Boolean = false,             // AI 功能是否开启
    // ...
)
```

### StateFlow —— ViewModel 和 UI 之间的「广播」

```kotlin
class FeedViewModel : AndroidViewModel(application) {
    // ① 创建一个「广播频道」
    private val _uiState = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    // ② 更新状态 = 发出新广播
    fun loadFeed() {
        viewModelScope.launch {
            // 告诉 UI：「我开始加载了」
            _uiState.update { it.copy(isLoading = true) }

            // 去拿数据
            repository.loadFeed().fold(
                onSuccess = { items ->
                    // 告诉 UI：「数据拿到了，给你」
                    _uiState.update { it.copy(
                        isLoading = false,
                        items = items
                    )}
                },
                onFailure = { error ->
                    // 告诉 UI：「出错了」
                    _uiState.update { it.copy(
                        isLoading = false,
                        errorMessage = error.message
                    )}
                }
            )
        }
    }
}
```

```kotlin
// ③ UI 监听这个广播
@Composable
fun FeedScreen(viewModel: FeedViewModel) {
    val uiState by viewModel.uiState.collectAsState()  // 订阅广播

    if (uiState.isLoading) {
        // 显示加载动画
        CircularProgressIndicator()
    } else {
        // 显示列表
        LazyColumn { items(uiState.items) { ... } }
    }
}
```

### 🔑 核心机制

```
_uiState.update { it.copy(isLoading = true) }
    ↓
StateFlow 发出新值
    ↓
collectAsState() 自动感知到变化
    ↓
Compose 重新执行 UI 函数（重组）
    ↓
屏幕上显示新内容
```

> [!TIP]
> **`it.copy(isLoading = true)`** 是 Kotlin data class 的特性。它创建一个副本，只修改指定的字段，其他字段保持不变。这样你不需要一个个手动赋值。

---

## 11. 点赞怎么实现的？—— 乐观更新

### 什么是乐观更新？

```
普通做法（悲观）：
点击 ❤️ → 发请求给服务器 → 等 2 秒 → 服务器说 OK → 界面变红
                                      ↑
                        用户等了 2 秒，体验差 😞

乐观做法（本项目用的）：
点击 ❤️ → 界面立刻变红 ← 同时 → 发请求给服务器
                                       ↓
                           成功？✓ 保持红色
                           失败？✗ 变回灰色
```

### 代码实现

在 [FolderViewModel.kt](file:///l:/NekoFeed/app/src/main/java/com/ico/nekofeed/ui/feed/FolderViewModel.kt) 中：

```kotlin
fun toggleLike(itemId: String) {
    // ① 先拍一张「照片」（保存当前状态，用于失败回滚）
    val snapshot = allItems

    // ② 立刻更新界面（乐观更新）—— 用户瞬间看到效果
    allItems = allItems.map { item ->
        if (item.id == itemId) {
            item.copy(
                isLiked = !item.isLiked,                    // 翻转点赞状态
                likeCount = if (item.isLiked)
                    item.likeCount - 1 else item.likeCount + 1  // 数字 ±1
            )
        } else item
    }
    updateFilteredItems()  // 刷新 UI

    // ③ 异步告诉服务器
    viewModelScope.launch {
        userRepository.toggleLike(itemId).fold(
            onSuccess = { interaction ->
                // ④ 成功：用服务器的真实数据覆盖
                allItems = allItems.map { item ->
                    if (item.id == itemId) {
                        item.copy(
                            isLiked = interaction.isLiked,
                            likeCount = interaction.likeCount
                        )
                    } else item
                }
                updateFilteredItems()
            },
            onFailure = {
                // ⑤ 失败：恢复照片（回滚到点赞前）
                allItems = snapshot
                updateFilteredItems()
            }
        )
    }
}
```

> [!IMPORTANT]
> **为什么不直接用乐观更新的值？** 因为可能有其他用户同时点赞了，服务器上的计数才是最准确的。乐观更新只是为了让**你自己**感觉操作响应很快。

---

## 12. 数据怎么存到手机上？—— Room 和 DataStore

### 两种本地存储

| 存储方式 | 适合存什么 | 项目中的用途 |
|---------|-----------|------------|
| **DataStore** | 简单的键值对（设置项） | Token、服务器地址、AI 配置 |
| **Room** | 结构化数据（表格数据） | AI 缓存、用户画像、互动记录 |

### Room 数据库 —— 手机里的「小型数据库」

**第一步：定义数据表**

```kotlin
// 这是一张表，表名叫 "ai_cache"
@Entity(tableName = "ai_cache")
data class AiCacheEntity(
    @PrimaryKey val itemId: String,      // 主键
    val aiSummary: String?,              // AI 摘要
    val aiTags: String,                  // AI 标签（JSON 字符串）
    val aiReason: String?,               // AI 理由
    val modelUsed: String? = null,       // 用的哪个模型
    val createdAt: Long = System.currentTimeMillis()  // 创建时间
)
```

**第二步：定义操作方法（DAO）**

```kotlin
@Dao
interface AiCacheDao {
    @Query("SELECT * FROM ai_cache WHERE itemId = :itemId")
    suspend fun getCache(itemId: String): AiCacheEntity?      // 查询

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCache(entity: AiCacheEntity)             // 插入

    @Query("DELETE FROM ai_cache WHERE createdAt < :before")
    suspend fun deleteOldCache(before: Long)                   // 删除过期缓存

    @Query("DELETE FROM ai_cache")
    suspend fun clearAll()                                      // 清空
}
```

**第三步：创建数据库**

```kotlin
@Database(
    entities = [AiCacheEntity::class, UserProfileEntity::class, FeedItemInteractionEntity::class],
    version = 2
)
abstract class NekoFeedDatabase : RoomDatabase() {
    abstract fun aiCacheDao(): AiCacheDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun feedItemInteractionDao(): FeedItemInteractionDao
}
```

### DataStore —— 简单的「配置文件」

```kotlin
class TokenManager(private val context: Context) {
    // 保存 Token
    suspend fun saveToken(token: String) {
        context.dataStore.edit { prefs ->
            prefs[TOKEN_KEY] = token
        }
    }

    // 读取 Token
    suspend fun getToken(): String? {
        return context.dataStore.data.first()[TOKEN_KEY]
    }
}
```

> [!TIP]
> **何时用 Room，何时用 DataStore？** 简单记忆：如果你的数据可以用「表格」来表示（有多行多列），用 Room；如果只是几个开关或字符串设置，用 DataStore。

---

## 13. 视频怎么播放的？—— ExoPlayer

[PlayerManager.kt](file:///l:/NekoFeed/app/src/main/java/com/ico/nekofeed/player/PlayerManager.kt) 是一个**全局单例**，整个 App 只有一个播放器实例：

```kotlin
class PlayerManager private constructor(context: Context) {

    // 单例模式 —— 全 App 只有一个 PlayerManager
    companion object {
        private var instance: PlayerManager? = null
        fun getInstance(context: Context): PlayerManager {
            return instance ?: synchronized(this) {
                instance ?: PlayerManager(context).also { instance = it }
            }
        }
    }

    val exoPlayer: ExoPlayer  // 播放器实例

    init {
        // 设置 100MB 的视频缓存
        val cacheSize: Long = 100 * 1024 * 1024
        val simpleCache = SimpleCache(cacheDir, LeastRecentlyUsedCacheEvictor(cacheSize), ...)

        // 创建播放器
        exoPlayer = ExoPlayer.Builder(context)
            .setMediaSourceFactory(...)  // 使用缓存数据源
            .build()

        exoPlayer.repeatMode = Player.REPEAT_MODE_ONE  // 循环播放
        exoPlayer.volume = 0f                           // 默认静音
    }

    // 播放视频
    fun play(mediaUrl: String?) {
        if (currentMediaUrl != mediaUrl) {
            // 换了一个新视频，重新准备
            exoPlayer.setMediaItem(MediaItem.fromUri(mediaUrl))
            exoPlayer.prepare()
        }
        exoPlayer.play()
    }

    fun pause() { exoPlayer.pause() }

    fun toggleMute() {
        isMuted = !isMuted
        exoPlayer.volume = if (isMuted) 0f else 1f
    }
}
```

### 为什么用单例？

因为视频播放器很「重」（占内存、占 CPU），所以：
- **全 App 只创建一个** —— 避免浪费资源
- **滚动到新视频时复用** —— 不会每次都创建新的
- **滚出屏幕自动暂停** —— 通过 `playingItemId` 状态控制

---

## 14. AI 功能怎么接的？—— LLM API 调用

### 接口定义

[LlmApi.kt](file:///l:/NekoFeed/app/src/main/java/com/ico/nekofeed/data/remote/LlmApi.kt)：

```kotlin
interface LlmApi {
    @POST("v1/chat/completions")          // OpenAI 兼容接口
    suspend fun chatCompletion(
        @Header("Authorization") auth: String,  // API Key
        @Body request: ChatRequest               // 请求体
    ): ChatResponse
}

// 请求格式
data class ChatRequest(
    val model: String,                    // 模型名
    val messages: List<ChatMessage>,      // 对话消息
    val max_tokens: Int = 512,
    val temperature: Float = 0.3f,        // 温度（越低越确定性）
    val response_format: ResponseFormat?  // 要求输出 JSON
)
```

### 调用流程

在 [AiRepository.kt](file:///l:/NekoFeed/app/src/main/java/com/ico/nekofeed/data/repository/AiRepository.kt) 中：

```kotlin
suspend fun generateFeedAi(item: FeedItem): AiResult? {
    // ① 检查是否启用了 AI
    if (!config.aiEnabled) return null

    // ② 先查 Room 缓存，有就直接返回
    val cached = aiCacheDao.getCache(item.id)
    if (cached != null) {
        return AiResult(cached.aiSummary, parseTagsFromJson(cached.aiTags), ...)
    }

    // ③ 构造 Prompt（提示词）
    val systemPrompt = """你是一个内容分析助手，请对以下信息流内容进行分析，
        用中文输出 JSON。格式：
        {"summary":"一句话摘要","tags":["标签1","标签2"],"reason":"推荐理由"}"""

    val userPrompt = """标题：${item.title}
        类型：${item.itemType}
        原始摘要：${item.summary}"""

    // ④ 调用 LLM API
    val response = api.chatCompletion(auth, ChatRequest(model, messages, ...))
    val content = response.choices.first().message.content  // 拿到 AI 回复

    // ⑤ 解析 JSON 结果
    val parsed = parseAiResponse(content)

    // ⑥ 存入 Room 缓存（下次不用再调 API）
    aiCacheDao.insertCache(AiCacheEntity(itemId = item.id, ...))

    return parsed
}
```

### 为什么要缓存 AI 结果？

每次调用 LLM API 都需要**时间和成本**。缓存后：
- ✅ 相同内容不重复请求
- ✅ 切换 Tab 再回来时不用等
- ✅ 7 天自动清理，不会无限占空间

---

## 15. 后端 Server 简介 —— 数据从哪来

> [!NOTE]
> 后端用 Python 写的，Android 初学者暂时不需要深入理解，只需要知道它**提供了什么数据**。

### 后端做了什么？

```
1. 从互联网抓取新闻
   36Kr、SSPAI、IT Home、TechCrunch、Hacker News 等 RSS 源
       ↓
2. 清洗和整理
   提取标题、摘要、图片、视频，去掉 HTML 标签
       ↓
3. 下载图片/视频到本地
   避免 App 直接请求外部不稳定的图片链接
       ↓
4. 用 AI 分类
   判断内容是文章还是商品，提取品牌信息
       ↓
5. 通过 API 提供给 App
   GET /api/feed → 返回 JSON 数据
```

### 本地启动方法

```bash
cd NekoFeedServer
pip install -r requirements.txt    # 安装依赖
python seed.py                     # 初始化数据库
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000  # 启动
```

启动后访问 `http://localhost:8000/admin` 可以看到管理后台。

### 如果不想启动 Server？

**完全没问题！** App 内置了 [FallbackFeedData.kt](file:///l:/NekoFeed/app/src/main/java/com/ico/nekofeed/data/local/FallbackFeedData.kt)，里面有预置的假数据。当连不上 Server 时，App 会自动使用这些数据，你照样可以看到完整的界面效果。

---

## 16. 新手常见问题 FAQ

### Q1：`suspend` 是什么意思？

`suspend` 标记的函数是**协程函数**——它可以暂停执行而不阻塞线程。简单理解就是「这个函数需要等待（比如等网络请求返回），但不会卡住界面」。

```kotlin
suspend fun loadFeed(): List<FeedItem> {
    // 这里会等 1-2 秒，但 UI 线程不会卡
    val response = feedApi.getFeed()
    return response.items
}
```

### Q2：`by lazy` 是什么？

延迟初始化。第一次使用时才创建，之后复用。

```kotlin
// 不会立刻创建 OkHttpClient，而是第一次用到时才创建
private val okHttpClient by lazy {
    OkHttpClient.Builder().build()
}
```

### Q3：`viewModelScope.launch` 是什么？

在 ViewModel 的生命周期范围内启动一个协程。页面销毁时协程会自动取消。

```kotlin
viewModelScope.launch {
    // 这里的代码不会阻塞 UI
    val data = repository.loadFeed()  // 等网络请求
    _uiState.update { it.copy(items = data) }  // 更新状态
}
```

### Q4：`MutableStateFlow` 和 `StateFlow` 有什么区别？

```kotlin
private val _uiState = MutableStateFlow(...)  // 可以修改（ViewModel 内部用）
val uiState: StateFlow<...> = _uiState        // 只读（暴露给 UI 层）
```

类比：`MutableStateFlow` 是一个**读写白板**（ViewModel 可以擦写），`StateFlow` 是**只读窗口**（UI 只能看，不能改）。

### Q5：`object` 关键字是什么？

Kotlin 的单例模式。整个 App 中只有一个实例。

```kotlin
object RetrofitClient {  // 整个 App 只有一个 RetrofitClient
    val feedApi: FeedApi = ...
}
```

### Q6：为什么到处都是 `?.` 和 `?:` ？

Kotlin 的空安全语法：

```kotlin
item.summary?.take(100)     // 如果 summary 不为空，取前 100 个字符；否则返回 null
item.aiSummary ?: summary   // 如果 aiSummary 不为空用它，否则用 summary
item.tags.orEmpty()         // 如果 tags 为空返回空列表而不是 null
```

### Q7：`.fold(onSuccess = ..., onFailure = ...)` 是什么？

对 `Result<T>` 类型的分支处理：

```kotlin
repository.loadFeed().fold(
    onSuccess = { items ->
        // 请求成功，处理数据
    },
    onFailure = { error ->
        // 请求失败，处理错误
    }
)
```

### Q8：`it.copy(isLoading = true)` 是什么？

Kotlin `data class` 的特性。创建一个副本，只修改指定字段：

```kotlin
// 原始状态
val state = FeedUiState(isLoading = false, items = [...], hasMore = true)

// 创建副本，只改 isLoading，其他字段不变
val newState = state.copy(isLoading = true)
// newState = FeedUiState(isLoading = true, items = [...], hasMore = true)
```

---

## 17. 推荐学习路线

### 第一阶段：看懂项目（1-2 天）

按以下顺序阅读代码：

```
① FeedItem.kt          ← 理解核心数据长什么样
② FolderApi.kt         ← 理解从哪拿数据
③ FolderRepository.kt  ← 理解数据怎么获取
④ FolderViewModel.kt   ← 理解业务逻辑
⑤ FolderScreen.kt      ← 理解 UI 怎么画的
⑥ FeedItemCard.kt      ← 理解卡片怎么分发的
```

### 第二阶段：能改代码（3-5 天）

尝试以下小任务：

- [ ] 在 `FeedItem` 里加一个新字段 `readCount`
- [ ] 新增一个 Tab 分类（如「游戏」）
- [ ] 修改卡片样式（改颜色、字体大小）
- [ ] 在详情页增加一个「查看原文」按钮

### 第三阶段：能写新功能（1-2 周）

尝试以下中型任务：

- [ ] 添加一个新的卡片类型（如「纯文字卡片」）
- [ ] 实现「标签云」功能（展示所有热门标签）
- [ ] 添加暗色模式切换
- [ ] 实现简单的推荐算法（根据用户点赞/收藏偏好排序）

### 推荐学习资源

| 资源 | 链接 |
|------|------|
| Kotlin 官方教程 | [kotlinlang.org](https://kotlinlang.org/docs/getting-started.html) |
| Jetpack Compose 官方教程 | [developer.android.com/compose](https://developer.android.com/develop/ui/compose) |
| Compose 示例项目 | [android/compose-samples](https://github.com/android/compose-samples) |
| Room 官方指南 | [developer.android.com/room](https://developer.android.com/training/data-storage/room) |
| Navigation 官方指南 | [developer.android.com/navigation](https://developer.android.com/guide/navigation/get-started) |
| Retrofit 教程 | [square.github.io/retrofit](https://square.github.io/retrofit/) |

---

> [!TIP]
> **最重要的建议**：不要试图一次看懂所有代码。先跑起来 App，然后沿着一条用户操作路径（比如「打开首页 → 点击卡片 → 进入详情」）去阅读对应的代码，这样最容易理解。
