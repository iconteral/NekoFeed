# NekoFeed 阶段一详细学习文档

> 本文档详细解释每个文件的每一行代码，适合初学者学习 Kotlin、Jetpack Compose、Retrofit、MVVM 等技术栈。

---

## 目录

- [项目结构总览](#项目结构总览)
- [1. Gradle 构建系统](#1-gradle-构建系统)
- [2. AndroidManifest 权限](#2-androidmanifest-权限)
- [3. 数据模型层](#3-数据模型层)
- [4. 网络层](#4-网络层)
- [5. 本地数据层](#5-本地数据层)
- [6. Repository 仓库层](#6-repository-仓库层)
- [7. UI 状态定义](#7-ui-状态定义)
- [8. ViewModel 层](#8-viewmodel-层)
- [9. Compose UI 组件](#9-compose-ui-组件)
- [10. 页面层](#10-页面层)
- [11. 导航层](#11-导航层)
- [12. 入口 Activity](#12-入口-activity)
- [MVVM 架构总结](#mvvm-架构总结)
- [运行与验证](#运行与验证)

---

## 项目结构总览

```
app/src/main/java/com/ico/nekofeed/
├── MainActivity.kt                    # App 入口
├── navigation/
│   └── AppNavHost.kt                  # 导航图：定义页面跳转规则
├── data/                              # 数据层
│   ├── model/                         # 数据模型
│   │   ├── FeedItem.kt                # 单条 Feed 数据
│   │   └── FeedResponse.kt            # API 响应包装
│   ├── remote/                        # 远程数据源
│   │   ├── FeedApi.kt                 # Retrofit 接口定义
│   │   └── RetrofitClient.kt          # Retrofit 实例配置
│   ├── local/                         # 本地数据源
│   │   └── FallbackFeedData.kt        # 网络失败时的兜底数据
│   └── repository/
│       └── FeedRepository.kt          # 数据仓库：协调远程和本地数据
├── ui/                                # UI 层
│   ├── theme/                         # 主题（已有）
│   ├── feed/
│   │   ├── FeedScreen.kt              # 首页
│   │   ├── FeedViewModel.kt           # 首页的 ViewModel
│   │   └── components/
│   │       └── FeedItemCard.kt        # Feed 卡片组件
│   └── detail/
│       └── FeedDetailScreen.kt        # 详情页
└── util/
    └── UiState.kt                     # UI 状态类定义
```

---

## 1. Gradle 构建系统

### 1.1 版本目录 `gradle/libs.versions.toml`

这是 Android 项目的依赖版本管理中心。

```toml
[versions]
# 已有版本
agp = "9.2.1"
kotlin = "2.2.10"
composeBom = "2026.02.01"

# 新增版本定义
navigationCompose = "2.9.0"           # Navigation Compose 版本
lifecycleViewModelCompose = "2.9.1"   # ViewModel Compose 版本
retrofit = "2.11.0"                   # Retrofit 版本
okhttp = "5.0.0-alpha.16"             # OkHttp 版本
gson = "2.11.0"                       # Gson 版本
coil = "2.7.0"                        # Coil 图片加载版本

[libraries]
# 新增库定义
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycleViewModelCompose" }
retrofit = { group = "com.squareup.retrofit2", name = "retrofit", version.ref = "retrofit" }
retrofit-converter-gson = { group = "com.squareup.retrofit2", name = "converter-gson", version.ref = "retrofit" }
okhttp-logging = { group = "com.squareup.okhttp3", name = "logging-interceptor", version.ref = "okhttp" }
coil-compose = { group = "io.coil-kt", name = "coil-compose", version.ref = "coil" }
```

**Kotlin 语法点：**
- `version.ref = "xxx"`：引用上面 `[versions]` 中定义的版本，避免重复
- `{ group = "...", name = "..." }`：TOML 的表格语法，等价于 JSON 对象

**知识点：**
- `BOM` (Bill of Materials)：Compose 的依赖管理方式，统一管理 Compose 组件版本
- `navigation-compose`：Jetpack Navigation 的 Compose 版本
- `coil-compose`：Coil 的 Compose 适配，提供 `AsyncImage` 组件

---

### 1.2 应用构建文件 `app/build.gradle.kts`

```kotlin
dependencies {
    // 已有的依赖
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    // ... 其他已有依赖

    // 新增依赖
    implementation(libs.androidx.lifecycle.viewmodel.compose)  // ViewModel 支持
    implementation(libs.androidx.navigation.compose)           // 导航支持
    implementation(libs.retrofit)                              // 网络请求
    implementation(libs.retrofit.converter.gson)               // JSON 转换
    implementation(libs.okhttp.logging)                        // 日志拦截器
    implementation(libs.coil.compose)                          // 图片加载
}
```

**每个依赖的作用：**

| 依赖 | 作用 | 用在哪 |
|------|------|--------|
| `lifecycle-viewmodel-compose` | 提供 `viewModel()` 函数 | `FeedScreen` 中获取 ViewModel |
| `navigation-compose` | 页面路由和跳转 | `AppNavHost` 中定义导航 |
| `retrofit` | HTTP 客户端框架 | `FeedApi` 接口定义 |
| `converter-gson` | JSON 自动转 Kotlin 对象 | `RetrofitClient` 配置 |
| `logging-interceptor` | 打印网络请求日志 | `RetrofitClient` 配置 |
| `coil-compose` | 加载网络图片 | `FeedItemCard` 中显示图片 |

**知识点：**
- `implementation` vs `api`：implementation 不传递依赖，api 会传递给依赖你的模块
- `platform(...)`：引入 BOM，让 Compose 组件版本自动对齐

---

## 2. AndroidManifest 权限

**文件：** `app/src/main/AndroidManifest.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- 新增：允许网络访问 -->
    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:allowBackup="true"
        android:usesCleartextTraffic="true"  <!-- 新增：允许 HTTP 明文请求 -->
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:theme="@style/Theme.NekoFeed">
        
        <activity android:name=".MainActivity" ...>
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

**关键点解释：**

| 属性 | 含义 |
|------|------|
| `uses-permission` | 声明 App 需要的权限 |
| `INTERNET` | 网络访问权限，必须声明才能发起网络请求 |
| `usesCleartextTraffic` | 是否允许明文 HTTP（未加密），Android 9+ 默认禁止 |
| `intent-filter` + `MAIN` + `LAUNCHER` | 定义 App 启动入口 |

**知识点：**
- Android 从 9 (API 28) 开始默认禁止 HTTP，推荐使用 HTTPS
- 本地开发可以用 HTTP，生产环境必须用 HTTPS
- `cleartext` = 明文，即没有 TLS/SSL 加密的 HTTP

---

## 3. 数据模型层

### 3.1 FeedItem.kt

**文件：** `data/model/FeedItem.kt`

```kotlin
package com.ico.nekofeed.data.model

data class FeedItem(
    val id: String,                    // 唯一标识
    val title: String,                 // 标题
    val summary: String?,              // 摘要，可能为空
    val content: String?,              // 正文内容，可能为空
    val sourceName: String?,           // 来源名称，如"36Kr"
    val sourceUrl: String?,            // 原文链接
    val category: String?,             // 分类：tech, ad, local
    val itemType: String?,             // 类型：article, ad, video, product
    val cardType: String?,             // 卡片样式：large_image, small_image, video
    val imageUrl: String?,             // 图片 URL
    val mediaUrl: String?,             // 视频 URL
    val tags: List<String> = emptyList(),  // 标签列表，默认空列表
    val publishedAt: String?           // 发布时间
)
```

**逐行解释：**

```kotlin
data class FeedItem(...)
```
- `data class`：Kotlin 的数据类，自动生成：
  - `equals()`：比较两个对象是否相等
  - `hashCode()`：哈希值
  - `toString()`：打印友好格式
  - `copy()`：复制并修改部分字段
  - 解构：`val (id, title, ...) = feedItem`

```kotlin
val id: String
```
- `val`：不可变变量（只读），一旦赋值不能修改
- `String`：非空字符串类型，不能传 null

```kotlin
val summary: String?
```
- `String?`：可空字符串，可以是 `null` 或 `String`
- `?` 表示这个字段可能没有值

```kotlin
val tags: List<String> = emptyList()
```
- `List<String>`：字符串列表
- `= emptyList()`：默认值是空列表，调用时可以不传

**为什么要用 String 而不是 Enum？**

```kotlin
// 方案 A：用 String（当前方案）
val itemType: String?  // 可以是 "article", "ad", "video", "product", 或任何值

// 方案 B：用 Enum（后续优化）
enum class FeedItemType { ARTICLE, AD, VIDEO, PRODUCT }
val itemType: FeedItemType?
```

| 方案 | 优点 | 缺点 |
|------|------|------|
| String | 灵活，服务端返回未知值不会崩溃 | 没有编译时检查 |
| Enum | 类型安全，IDE 自动补全 | 服务端返回未知值会崩溃 |

第一阶段用 String 更安全。

---

### 3.2 FeedResponse.kt

**文件：** `data/model/FeedResponse.kt`

```kotlin
package com.ico.nekofeed.data.model

data class FeedResponse(
    val items: List<FeedItem>,  // Feed 列表
    val limit: Int,             // 每页数量
    val offset: Int,            // 偏移量
    val total: Int              // 总数量
)
```

**为什么需要单独的 Response 类？**

API 返回的 JSON 结构是：
```json
{
  "items": [...],     // 列表数据
  "limit": 20,        // 分页信息
  "offset": 0,
  "total": 150
}
```

`FeedResponse` 是 API 契约，对应 JSON 结构。`FeedItem` 是业务实体，只表示单条数据。

**知识点：**
- 关注点分离：API 契约和业务实体分开
- 后续可以给 `FeedItem` 添加更多字段（如点赞状态），不影响 API 契约

---

## 4. 网络层

### 4.1 FeedApi.kt

**文件：** `data/remote/FeedApi.kt`

```kotlin
package com.ico.nekofeed.data.remote

import com.ico.nekofeed.data.model.FeedResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface FeedApi {
    @GET("api/feed")
    suspend fun getFeed(
        @Query("category") category: String? = null,
        @Query("item_type") itemType: String? = null,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0,
        @Query("base_url") baseUrl: String = "http://10.0.2.2:8000"
    ): FeedResponse
}
```

**逐行解释：**

```kotlin
interface FeedApi {
```
- `interface`：接口，只定义方法签名，没有实现
- Retrofit 会在运行时自动生成实现代码

```kotlin
@GET("api/feed")
```
- `@GET`：Retrofit 注解，表示这是一个 GET 请求
- `"api/feed"`：请求路径，会拼接到 base URL 后面
- 最终 URL：`http://10.0.2.2:8000/api/feed`

```kotlin
suspend fun getFeed(...)
```
- `suspend`：挂起函数，只能在协程中调用
- 挂起函数不会阻塞线程，而是暂停等待
- 必须在协程作用域（如 `viewModelScope.launch`）中调用

```kotlin
@Query("category") category: String? = null
```
- `@Query`：把参数拼接到 URL 查询字符串
- `@Query("category")`：URL 参数名是 `category`
- `category: String?`：参数类型可空
- `= null`：默认值，调用时可以不传
- 调用 `getFeed(category = "tech")` → URL 拼接 `?category=tech`

```kotlin
): FeedResponse
```
- 返回 `FeedResponse`，Retrofit 自动把 JSON 解析成对象

**完整 URL 示例：**
```
GET http://10.0.2.2:8000/api/feed?limit=20&offset=0&base_url=http://10.0.2.2:8000
```

---

### 4.2 RetrofitClient.kt

**文件：** `data/remote/RetrofitClient.kt`

```kotlin
package com.ico.nekofeed.data.remote

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
```

**`object` 关键字：**
- Kotlin 的单例模式
- 整个 App 只会有一个 `RetrofitClient` 实例
- 线程安全，懒加载

```kotlin
    private const val BASE_URL = "http://10.0.2.2:8000/"
```
- `private`：只在本文件内访问
- `const val`：编译时常量，不能再修改
- `10.0.2.2`：Android 模拟器访问宿主机的特殊地址

**为什么用 10.0.2.2 而不是 localhost？**

| 环境 | 本机地址 | 模拟器访问本机 |
|------|---------|---------------|
| 浏览器 | `localhost` | - |
| 模拟器 | `localhost` = 模拟器自己 | `10.0.2.2` = 宿主机 |

```kotlin
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }
```
- **日志拦截器**：打印网络请求和响应的详细信息
- `.apply { ... }`：Kotlin 作用域函数，配置对象
- `Level.BODY`：打印完整的请求/响应体

**HttpLoggingInterceptor.Level 枚举：**

| Level | 打印内容 |
|-------|---------|
| `NONE` | 不打印 |
| `BASIC` | 请求方法、URL、响应码 |
| `HEADERS` | BASIC + 请求/响应头 |
| `BODY` | HEADERS + 请求/响应体 |

```kotlin
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
```
- **OkHttpClient**：HTTP 客户端，处理实际的网络请求
- `.Builder()`：Builder 模式，链式调用配置
- `addInterceptor`：添加拦截器，可以在请求/响应前后做处理
- `connectTimeout`：连接超时时间
- `readTimeout`：读取响应超时时间
- `writeTimeout`：写入请求超时时间

**Builder 模式：**
```kotlin
// 链式调用
client.addInterceptor(a)
      .addInterceptor(b)
      .connectTimeout(15)
      .build()
```

```kotlin
    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
```
- **Retrofit**：把 HTTP API 转换成 Kotlin 接口
- `baseUrl`：基础 URL，接口中的路径会拼接到后面
- `client(okHttpClient)`：使用自定义的 HTTP 客户端
- `addConverterFactory`：添加 JSON 转换器

**GsonConverterFactory 的作用：**
```
服务器返回 JSON → GsonConverterFactory → FeedResponse 对象
```

```kotlin
    val feedApi: FeedApi = retrofit.create(FeedApi::class.java)
```
- `retrofit.create(...)`：Retrofit 根据接口定义生成实现类
- `FeedApi::class.java`：获取 FeedApi 的 Class 对象
- 返回的 `feedApi` 就是 `getFeed()` 方法的实际调用者

---

## 5. 本地数据层

### 5.1 FallbackFeedData.kt

**文件：** `data/local/FallbackFeedData.kt`

```kotlin
package com.ico.nekofeed.data.local

import com.ico.nekofeed.data.model.FeedItem

object FallbackFeedData {
    val items: List<FeedItem> = listOf(
        FeedItem(
            id = "fallback_001",
            title = "AI 技术突破：新一代大语言模型发布",
            summary = "最新一代大语言模型在多项基准测试中取得了突破性进展",
            content = "随着人工智能技术的快速发展...",
            sourceName = "科技日报",
            sourceUrl = "https://example.com/tech/ai",
            category = "tech",
            itemType = "article",
            cardType = "large_image",
            imageUrl = null,
            mediaUrl = null,
            tags = listOf("AI", "技术", "大语言模型"),
            publishedAt = "2026-05-30T10:00:00"
        ),
        // ... 更多 fallback 数据
    )
}
```

**关键语法点：**

```kotlin
object FallbackFeedData
```
- `object`：单例，整个 App 只有一个实例
- 适合作为静态数据容器

```kotlin
listOf(...)
```
- 创建不可变 List
- Kotlin 提供的集合创建函数

```kotlin
listOf("AI", "技术", "大语言模型")
```
- 列表字面量语法
- 等价于 Java 的 `Arrays.asList("AI", "技术", "大语言模型")`

```kotlin
imageUrl = null
```
- 显式传 null，表示这个字段没有值
- Kotlin 的 `null` 是安全的，编译时就会检查

**知识点：**
- 降级策略：网络失败时展示本地数据，保证 App 仍能演示
- 内存缓存 vs 持久化：当前是内存中的静态数据，后续可用 Room 数据库

---

## 6. Repository 仓库层

**文件：** `data/repository/FeedRepository.kt`

```kotlin
package com.ico.nekofeed.data.repository

import com.ico.nekofeed.data.local.FallbackFeedData
import com.ico.nekofeed.data.model.FeedItem
import com.ico.nekofeed.data.remote.FeedApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FeedRepository(private val feedApi: FeedApi) {
```

**构造函数参数：**
- `private val feedApi: FeedApi`：构造函数参数直接声明为属性
- 等价于 Java 的：
```java
class FeedRepository {
    private FeedApi feedApi;
    FeedRepository(FeedApi feedApi) {
        this.feedApi = feedApi;
    }
}
```

```kotlin
    private val cachedItems = mutableListOf<FeedItem>()
```
- `mutableListOf`：创建可变列表
- 私有缓存，存储最近一次加载的数据

```kotlin
    suspend fun loadFeed(
        category: String? = null,
        itemType: String? = null,
        limit: Int = 20,
        offset: Int = 0
    ): Result<List<FeedItem>> {
```
- `suspend`：挂起函数
- `Result<List<FeedItem>>`：返回成功或失败的结果

**Result 类型：**
```kotlin
Result.success(items)   // 成功时
Result.failure(error)   // 失败时
```

```kotlin
        return withContext(Dispatchers.IO) {
```
- `withContext(Dispatchers.IO)`：切换到 IO 线程执行
- 网络请求必须在后台线程，不能在主线程

**Dispatchers 枚举：**

| Dispatcher | 用途 |
|------------|------|
| `Main` | 主线程，用于 UI 操作 |
| `IO` | IO 密集型，如网络请求、文件读写 |
| `Default` | CPU 密集型，如大量计算 |

```kotlin
            try {
                val response = feedApi.getFeed(
                    category = category,
                    itemType = itemType,
                    limit = limit,
                    offset = offset
                )
                val items = response.items
                if (offset == 0) {
                    cachedItems.clear()
                }
                cachedItems.addAll(items)
                Result.success(items)
            } catch (e: Exception) {
                Result.failure(e)
            }
```
- `try-catch`：捕获异常，网络请求可能失败
- `offset == 0`：第一页加载时清空缓存
- `addAll`：把新数据追加到缓存列表

```kotlin
    fun getCachedItemById(id: String): FeedItem? {
        return cachedItems.find { it.id == id }
    }
```
- `find`：在列表中查找第一个满足条件的元素
- `it.id == id`：lambda 表达式，`it` 是当前元素
- 返回 `FeedItem?`：可能找不到（返回 null）

**Lambda 表达式：**
```kotlin
// 完整写法
cachedItems.find { item -> item.id == id }

// 简写（只有一个参数时可以用 it）
cachedItems.find { it.id == id }
```

```kotlin
    fun getFallbackData(): List<FeedItem> {
        return FallbackFeedData.items
    }
}
```
- 提供 fallback 数据，供 ViewModel 在网络失败时使用

---

## 7. UI 状态定义

**文件：** `util/UiState.kt`

```kotlin
package com.ico.nekofeed.util

import com.ico.nekofeed.data.model.FeedItem

data class FeedUiState(
    val isLoading: Boolean = false,      // 是否正在加载
    val items: List<FeedItem> = emptyList(),  // Feed 列表
    val errorMessage: String? = null,    // 错误信息
    val usingFallback: Boolean = false   // 是否在用 fallback 数据
)
```

**为什么需要单独的 UiState 类？**

```
UI 状态 = isLoading + items + errorMessage + usingFallback
```

把所有 UI 状态集中在一个类里：
1. Composable 只需观察一个 StateFlow
2. 状态变化时自动触发 UI 重组
3. 方便预览和测试

**Kotlin 语法点：**
- `= false`：默认值，创建时可以不传
- `emptyList()`：创建空的不可变列表

---

## 8. ViewModel 层

**文件：** `ui/feed/FeedViewModel.kt`

```kotlin
package com.ico.nekofeed.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ico.nekofeed.data.model.FeedItem
import com.ico.nekofeed.data.remote.RetrofitClient
import com.ico.nekofeed.data.repository.FeedRepository
import com.ico.nekofeed.util.FeedUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FeedViewModel : ViewModel() {
```

- `ViewModel`：Android Jetpack 的 ViewModel，管理 UI 相关数据
- 生命周期感知：屏幕旋转时不会销毁
- `: ViewModel()`：继承 ViewModel 类

```kotlin
    private val repository = FeedRepository(RetrofitClient.feedApi)
```
- 创建 Repository 实例
- 注入 RetrofitClient 的 feedApi

```kotlin
    private val _uiState = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()
```

**StateFlow 命名约定：**
- `_uiState`（下划线开头）：私有的可变状态
- `uiState`：公开的只读状态

**为什么这样写？**
```kotlin
private val _uiState = MutableStateFlow(...)  // ViewModel 内部可以修改
val uiState: StateFlow<...> = _uiState.asStateFlow()  // 外部只能读
```

- 外部（Composable）只能观察状态，不能修改
- 只有 ViewModel 能修改状态

**StateFlow vs LiveData：**

| 特性 | StateFlow | LiveData |
|------|-----------|----------|
| 依赖 | Kotlin 协程库 | Android Lifecycle |
| 初始值 | 必须有 | 可以没有 |
| Compose 支持 | 原生支持 | 需要额外适配 |
| 测试 | 不依赖 Android | 依赖 Android |

```kotlin
    init {
        loadFeed()
    }
```
- `init`：初始化块，对象创建时执行
- 相当于 Java 构造函数中的代码

```kotlin
    fun loadFeed() {
        viewModelScope.launch {
```
- `viewModelScope`：ViewModel 的协程作用域
- ViewModel 销毁时自动取消协程
- `launch`：启动一个新的协程

```kotlin
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null,
                usingFallback = false
            )
```
- `copy(...)`：data class 的复制方法，只修改指定字段
- StateFlow 通过 `.value` 读取和修改值

```kotlin
            repository.loadFeed().fold(
                onSuccess = { items ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        items = items,
                        errorMessage = null,
                        usingFallback = false
                    )
                },
                onFailure = { error ->
                    val fallbackItems = repository.getFallbackData()
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        items = fallbackItems,
                        errorMessage = "无法连接服务器: ${error.message}",
                        usingFallback = true
                    )
                }
            )
```

**Result.fold() 方法：**
```kotlin
result.fold(
    onSuccess = { value -> /* 成功时执行 */ },
    onFailure = { error -> /* 失败时执行 */ }
)
```

**字符串模板：**
```kotlin
"无法连接服务器: ${error.message}"
```
- `${...}`：在字符串中插入变量值
- 等价于 Java 的 `"无法连接服务器: " + error.getMessage()`

```kotlin
    fun getItemById(id: String): FeedItem? {
        return repository.getCachedItemById(id) 
            ?: repository.getFallbackData().find { it.id == id }
    }
}
```
- `?:`（Elvis 操作符）：如果左边是 null，返回右边
- 优先从缓存找，找不到再从 fallback 数据找

---

## 9. Compose UI 组件

### 9.1 FeedItemCard.kt

**文件：** `ui/feed/components/FeedItemCard.kt`

```kotlin
package com.ico.nekofeed.ui.feed.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ico.nekofeed.data.model.FeedItem

@Composable
fun FeedItemCard(
    item: FeedItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
```

**`@Composable` 注解：**
- 标记这是一个 Compose 组件
- 只能在其他 `@Composable` 函数中调用
- 函数名首字母大写（约定）

**参数类型：**
- `item: FeedItem`：数据
- `onClick: () -> Unit`：回调函数，点击时触发
- `() -> Unit`：无参数、无返回值的函数类型

**函数类型语法：**
```kotlin
val onClick: () -> Unit = { /* 无参数 */ }
val onValueChange: (String) -> Unit = { newValue -> /* 有一个参数 */ }
val calculate: (Int, Int) -> Int = { a, b -> a + b }
```

```kotlin
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
```

**Modifier 链式调用：**
```kotlin
Modifier
    .fillMaxWidth()        // 宽度填满父容器
    .clickable(onClick)    // 添加点击事件
```

**`.clickable(onClick = onClick)`：**
- 为组件添加点击事件
- 等价于 Android View 的 `setOnClickListener`

```kotlin
        when (item.cardType) {
            "small_image" -> SmallImageCard(item)
            else -> LargeImageCard(item)
        }
    }
}
```

**`when` 表达式：**
```kotlin
when (表达式) {
    值1 -> 结果1
    值2 -> 结果2
    else -> 默认结果
}
```
- 类似 switch-case，但更强大
- 可以返回值
- `else` 是兜底分支

---

**`@Composable` ImageSection：**
```kotlin
@Composable
private fun ImageSection(item: FeedItem) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
```

**Box 布局：**
- 类似 Android 的 FrameLayout
- 子元素可以叠放
- `contentAlignment`：子元素的对齐方式

```kotlin
        if (item.imageUrl != null) {
            AsyncImage(
                model = item.imageUrl,
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(text = "No Image", ...)
        }
```

**AsyncImage：**
- Coil 提供的 Compose 图片组件
- 自动异步加载网络图片
- `model`：图片 URL
- `contentDescription`：无障碍描述
- `ContentScale.Crop`：裁剪填满

```kotlin
        if (item.itemType == "video" || item.cardType == "video") {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        Color.Black.copy(alpha = 0.6f),
                        RoundedCornerShape(24.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "▶", color = Color.White, ...)
            }
        }
    }
}
```

**Color.copy(alpha)：**
- `Color.Black`：黑色
- `.copy(alpha = 0.6f)`：复制颜色，只修改透明度
- `0.6f`：60% 不透明

**RoundedCornerShape：**
- 圆角形状
- `24.dp`：圆角半径

---

## 10. 页面层

### 10.1 FeedScreen.kt

**文件：** `ui/feed/FeedScreen.kt`

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    onItemClick: (String) -> Unit,
    viewModel: FeedViewModel = viewModel()
) {
```

**`@OptIn(ExperimentalMaterial3Api::class)`：**
- 允许使用实验性 API
- Material 3 的一些组件还在实验阶段

**`viewModel: FeedViewModel = viewModel()`：**
- 默认参数，调用时可以不传
- `viewModel()`：获取或创建 ViewModel 实例
- 屏幕旋转时返回同一个实例

```kotlin
    val uiState by viewModel.uiState.collectAsState()
```

**`by` 委托和 `collectAsState()`：**
```kotlin
// 完整写法
val uiState = viewModel.uiState.collectAsState().value

// 简写（用 by 委托）
val uiState by viewModel.uiState.collectAsState()
```

- `collectAsState()`：把 Flow 转成 Compose State
- `by`：属性委托，自动取 `.value`

```kotlin
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = "AdFlow AI", ...)
                        Text(text = "Local Feed Server: ...", ...)
                    }
                }
            )
        }
    ) { paddingValues ->
```

**Scaffold 布局：**
- Material Design 的基本页面结构
- 提供 topBar、bottomBar、fab 等插槽
- `paddingValues`：系统栏的内边距

```kotlin
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(...)
                }
                uiState.errorMessage != null && uiState.items.isEmpty() -> {
                    ErrorContent(...)
                }
                else -> {
                    FeedContent(...)
                }
            }
        }
    }
}
```

**状态驱动 UI：**
```
状态 → UI
isLoading = true → 显示加载指示器
errorMessage != null → 显示错误内容
else → 显示 Feed 列表
```

---

**LazyColumn 列表：**
```kotlin
LazyColumn(
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
) {
    if (usingFallback) {
        item {
            // 提示卡片
        }
    }
    items(
        items = uiState.items,
        key = { it.id }
    ) { item ->
        FeedItemCard(...)
    }
}
```

**LazyColumn vs Column：**
| 特性 | Column | LazyColumn |
|------|--------|------------|
| 渲染 | 全部渲染 | 只渲染可见项 |
| 性能 | 适合少量固定内容 | 适合长列表 |
| 类似 | LinearLayout | RecyclerView |

**`items(..., key = { it.id })`：**
- `items`：列表数据
- `key`：唯一标识，帮助 Compose 高效更新
- 没有 key 时，Compose 只能通过位置判断；有 key 后可以通过 id 判断

---

### 10.2 FeedDetailScreen.kt

**文件：** `ui/detail/FeedDetailScreen.kt`

```kotlin
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FeedDetailScreen(
    item: FeedItem?,
    onBack: () -> Unit
) {
```

**`item: FeedItem?`：**
- 可空类型，详情页可能找不到对应的 item
- 传 null 表示"未找到"

```kotlin
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = item?.sourceName ?: "详情", ...) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
```

**`Icons.AutoMirrored.Filled.ArrowBack`：**
- Material Icons 库中的返回箭头图标
- `AutoMirrored`：自动镜像，RTL 语言会翻转

```kotlin
        if (item == null) {
            Box(...) {
                Text("未找到该 FeedItem，请返回首页重新加载")
            }
        } else {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                // 内容
            }
        }
    }
}
```

**`Modifier.verticalScroll(rememberScrollState())`：**
- 添加垂直滚动能力
- `rememberScrollState()`：创建并记住滚动状态

**`remember` 函数：**
```kotlin
val scrollState = rememberScrollState()
```
- 在重组时保持同一个对象
- 没有 remember，每次重组都会创建新对象

---

**FlowRow 流动布局：**
```kotlin
@Composable
private fun ContentSection(item: FeedItem) {
    // ...
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item.tags.forEach { tag ->
            AssistChip(
                onClick = { },
                label = { Text(text = tag) }
            )
        }
    }
}
```

**FlowRow：**
- 流动布局，子元素自动换行
- `spacedBy(8.dp)`：子元素间距

**`forEach` 循环：**
```kotlin
item.tags.forEach { tag ->
    // 对每个 tag 执行
}
```

**AssistChip：**
- Material 3 的标签组件
- `onClick`：点击回调
- `label`：标签内容

---

## 11. 导航层

**文件：** `navigation/AppNavHost.kt`

```kotlin
@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController()
) {
```

**`rememberNavController()`：**
- 创建导航控制器
- `remember` 确保重组时使用同一个实例

```kotlin
    val feedViewModel: FeedViewModel = viewModel()
```

**共享 ViewModel：**
- 在 AppNavHost 层获取 ViewModel
- 两个页面共享同一个 ViewModel 实例
- 保证数据一致

```kotlin
    NavHost(
        navController = navController,
        startDestination = "feed"
    ) {
```

**NavHost：**
- 导航图容器
- `startDestination`：起始页面路由

```kotlin
        composable("feed") {
            FeedScreen(
                viewModel = feedViewModel,
                onItemClick = { itemId ->
                    val encodedId = Uri.encode(itemId)
                    navController.navigate("detail/$encodedId")
                }
            )
        }
```

**路由定义：**
- `"feed"`：固定路由
- `navController.navigate("detail/xxx")`：导航到详情页

**URL 编码：**
```kotlin
val encodedId = Uri.encode("item/123")  // → "item%2F123"
```
- 防止特殊字符破坏 URL 结构

```kotlin
        composable(
            route = "detail/{itemId}",
            arguments = listOf(
                navArgument("itemId") {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            val itemId = backStackEntry.arguments?.getString("itemId") ?: ""
            val decodedId = Uri.decode(itemId)
            val item = remember(decodedId) {
                feedViewModel.getItemById(decodedId)
            }
            FeedDetailScreen(
                item = item,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
```

**动态参数路由：**
```kotlin
route = "detail/{itemId}"  // {itemId} 是占位符
```

**获取参数：**
```kotlin
backStackEntry.arguments?.getString("itemId")
```

**`remember(decodedId) { ... }`：**
- 只有当 decodedId 变化时才重新计算
- 避免每次重组都查找 item

**`navController.popBackStack()`：**
- 返回上一页
- 等价于按返回键

---

## 12. 入口 Activity

**文件：** `MainActivity.kt`

```kotlin
package com.ico.nekofeed

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ico.nekofeed.navigation.AppNavHost
import com.ico.nekofeed.ui.theme.NekoFeedTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NekoFeedTheme {
                AppNavHost()
            }
        }
    }
}
```

**逐行解释：**

```kotlin
class MainActivity : ComponentActivity()
```
- `ComponentActivity`：支持 Compose 的 Activity 基类

```kotlin
override fun onCreate(savedInstanceState: Bundle?)
```
- `override`：重写父类方法
- `savedInstanceState`：保存的状态（旋转屏幕时）

```kotlin
enableEdgeToEdge()
```
- 启用全面屏显示
- 内容延伸到状态栏和导航栏后面

```kotlin
setContent { ... }
```
- 设置 Compose 内容
- 替代传统的 `setContentView(R.layout.xxx)`

```kotlin
NekoFeedTheme {
    AppNavHost()
}
```
- `NekoFeedTheme`：应用主题
- `AppNavHost()`：导航入口

---

## MVVM 架构总结

### 数据流

```
┌─────────────────────────────────────────────────────────┐
│                      UI Layer (Compose)                  │
│  FeedScreen ←→ FeedDetailScreen                         │
│       ↑ collectAsState()                                │
├─────────────────────────────────────────────────────────┤
│                  ViewModel Layer                         │
│  FeedViewModel (StateFlow<FeedUiState>)                │
│       ↓ 调用                                             │
├─────────────────────────────────────────────────────────┤
│                 Repository Layer                         │
│  FeedRepository                                         │
│       ↓ 调用                                             │
├─────────────────────────────────────────────────────────┤
│                   Data Layer                             │
│  FeedApi (远程) ←→ FallbackFeedData (本地)              │
└─────────────────────────────────────────────────────────┘
```

### 单向数据流

```
用户点击 → 事件回调 → ViewModel → 修改 StateFlow → Compose 自动重组
```

**关键原则：**
1. 数据向下流动（StateFlow → UI）
2. 事件向上传递（UI → ViewModel）
3. 状态是不可变的（用 `copy()` 修改）

---

## 运行与验证

### 1. 启动 Feed Server

```bash
cd feed_server
pip install -r requirements.txt
python seed.py
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

### 2. 验证 API

浏览器访问：
```
http://localhost:8000/api/feed?limit=5&offset=0&base_url=http://localhost:8000
```

应该看到 JSON 响应。

### 3. 运行 Android App

1. 打开 Android Studio
2. 启动模拟器
3. 点击 Run

### 4. 测试降级

关闭 Feed Server，App 应显示 fallback 数据和提示信息。

---

## 附录：Kotlin 语法速查

| 语法 | 含义 | 示例 |
|------|------|------|
| `val` | 不可变变量 | `val x = 1` |
| `var` | 可变变量 | `var x = 1` |
| `?` | 可空类型 | `val s: String? = null` |
| `?.` | 安全调用 | `item?.title` |
| `?:` | Elvis 操作符 | `x ?: defaultValue` |
| `data class` | 数据类 | `data class User(val name: String)` |
| `object` | 单例 | `object Config { val url = "..." }` |
| `when` | 条件表达式 | `when(x) { 1 -> "one" }` |
| `suspend` | 挂起函数 | `suspend fun fetch()` |
| `it` | Lambda 隐式参数 | `list.find { it.id == 1 }` |
| `listOf` | 不可变列表 | `listOf(1, 2, 3)` |
| `mutableListOf` | 可变列表 | `mutableListOf(1, 2, 3)` |
| `copy()` | 复制 data class | `item.copy(title = "new")` |
| `apply` | 作用域函数 | `Builder().apply { x = 1 }.build()` |
| `by` | 委托 | `val x by lazy { compute() }` |
