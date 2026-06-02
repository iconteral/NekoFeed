# NekoFeed 阶段一：手把手开发教程

> 本文档是一个从零开始的开发教程，假设你是从头手写这个项目。
> 每一步都会解释：做什么、为什么、怎么做。

---

## 目录

- [开发前的准备](#开发前的准备)
- [第一步：理解项目需求](#第一步理解项目需求)
- [第二步：配置 Gradle 依赖](#第二步配置-gradle-依赖)
- [第三步：配置 AndroidManifest](#第三步配置-androidmanifest)
- [第四步：设计数据模型](#第四步设计数据模型)
- [第五步：搭建网络层](#第五步搭建网络层)
- [第六步：创建本地兜底数据](#第六步创建本地兜底数据)
- [第七步：实现 Repository](#第七步实现-repository)
- [第八步：定义 UI 状态](#第八步定义-ui-状态)
- [第九步：实现 ViewModel](#第九步实现-viewmodel)
- [第十步：编写 Feed 卡片组件](#第十步编写-feed-卡片组件)
- [第十一步：编写 FeedScreen](#第十一步编写-feedscreen)
- [第十二步：编写 FeedDetailScreen](#第十二步编写-feeddetailscreen)
- [第十三步：配置导航](#第十三步配置导航)
- [第十四步：修改 MainActivity](#第十四步修改-mainactivity)
- [第十五步：运行与调试](#第十五步运行与调试)
- [常见问题排查](#常见问题排查)
- [踩坑记录](#踩坑记录)

---

## 开发前的准备

### 工具要求

- Android Studio（最新版）
- 本地 Feed Server 已启动（端口 8000）

### 项目结构（最终）

```
app/src/main/java/com/ico/nekofeed/
├── MainActivity.kt                    # App 入口
├── navigation/
│   └── AppNavHost.kt                  # 导航配置
├── data/
│   ├── model/
│   │   ├── FeedItem.kt                # 数据模型
│   │   └── FeedResponse.kt            # API 响应模型
│   ├── remote/
│   │   ├── FeedApi.kt                 # Retrofit 接口
│   │   └── RetrofitClient.kt          # Retrofit 配置
│   ├── local/
│   │   └── FallbackFeedData.kt        # 兜底数据
│   └── repository/
│       └── FeedRepository.kt          # 数据仓库
├── ui/
│   ├── theme/                         # 主题（已有）
│   ├── feed/
│   │   ├── FeedScreen.kt              # 首页
│   │   ├── FeedViewModel.kt           # 首页 ViewModel
│   │   └── components/
│   │       └── FeedItemCard.kt        # 卡片组件
│   └── detail/
│       └── FeedDetailScreen.kt        # 详情页
└── util/
    └── UiState.kt                     # UI 状态类
```

---

## 第一步：理解项目需求

### 我们要做什么？

做一个类似今日头条的信息流 App：
1. 从服务器获取 Feed 数据
2. 以卡片列表形式展示
3. 点击卡片进入详情页
4. 网络失败时显示兜底数据

### 数据从哪来？

```
本地 Feed Server (http://10.0.2.2:8000)
    ↓ GET /api/feed
JSON 响应
    ↓ Gson 解析
FeedItem 列表
    ↓ Compose 渲染
UI 展示
```

### 先测试 API

在浏览器访问：
```
http://localhost:8000/api/feed?limit=2&offset=0&base_url=http://localhost:8000
```

确认返回 JSON 结构：
```json
{
  "items": [
    {
      "id": "xxx",
      "title": "标题",
      "image_url": "http://...",
      ...
    }
  ],
  "limit": 2,
  "offset": 0,
  "total": 100
}
```

**关键发现：** API 返回的字段名是 **snake_case**（如 `image_url`），不是 camelCase。
后面需要用 `@SerializedName` 注解来映射。

---

## 第二步：配置 Gradle 依赖

### 做什么？

添加项目需要的依赖库。

### 需要哪些库？

| 库名 | 用途 |
|------|------|
| Navigation Compose | 页面跳转 |
| Lifecycle ViewModel Compose | ViewModel 支持 |
| Retrofit | 网络请求 |
| Retrofit Gson Converter | JSON 解析 |
| OkHttp Logging | 网络日志 |
| Coil Compose | 图片加载 |
| Material Icons Extended | 图标 |

### 怎么做？

#### 2.1 编辑 `gradle/libs.versions.toml`

添加版本定义：
```toml
[versions]
# 在已有的版本后面添加
navigationCompose = "2.9.0"
lifecycleViewModelCompose = "2.9.1"
retrofit = "2.11.0"
okhttp = "5.0.0-alpha.16"
coil = "2.7.0"
```

添加库定义：
```toml
[libraries]
# 在已有的库定义后面添加
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycleViewModelCompose" }
retrofit = { group = "com.squareup.retrofit2", name = "retrofit", version.ref = "retrofit" }
retrofit-converter-gson = { group = "com.squareup.retrofit2", name = "converter-gson", version.ref = "retrofit" }
okhttp-logging = { group = "com.squareup.okhttp3", name = "logging-interceptor", version.ref = "okhttp" }
coil-compose = { group = "io.coil-kt", name = "coil-compose", version.ref = "coil" }
androidx-compose-material-icons = { group = "androidx.compose.material", name = "material-icons-extended" }
```

#### 2.2 编辑 `app/build.gradle.kts`

在 dependencies 块中添加：
```kotlin
dependencies {
    // ... 已有的依赖

    // 新增
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.logging)
    implementation(libs.coil.compose)
    implementation(libs.androidx.compose.material.icons)
}
```

#### 2.3 同步项目

点击 Android Studio 弹出的 **Sync Now** 按钮。

**为什么要同步？**
Gradle 需要下载新添加的依赖库。

---

## 第三步：配置 AndroidManifest

### 做什么？

让 App 能访问网络。

### 为什么？

Android 默认禁止网络访问，必须声明权限。

### 怎么做？

编辑 `app/src/main/AndroidManifest.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- 1. 添加网络权限 -->
    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:allowBackup="true"
        <!-- 2. 允许 HTTP 明文请求（本地开发用） -->
        android:usesCleartextTraffic="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:theme="@style/Theme.NekoFeed">
        <!-- ... -->
    </application>
</manifest>
```

**知识点：**
- `INTERNET` 权限是普通权限，不需要运行时申请
- `usesCleartextTraffic="true"` 允许 HTTP（不加密），生产环境应该用 HTTPS

---

## 第四步：设计数据模型

### 做什么？

定义 Kotlin 数据类来对应 API 返回的 JSON。

### 为什么需要数据类？

```
JSON 字符串 → Gson 解析 → Kotlin 对象
```

没有数据类，Gson 不知道怎么解析。

### 怎么做？

#### 4.1 先分析 API 返回的 JSON

```json
{
  "id": "custom_xxx",
  "title": "标题",
  "summary": "摘要",
  "content": "",
  "source_name": "来源",
  "source_url": "https://...",
  "category": "tech",
  "item_type": "article",
  "card_type": "large_image",
  "image_url": "http://...",
  "media_url": null,
  "tags": ["tech"],
  "published_at": "2026-05-29T10:00:00"
}
```

**关键发现：** 字段名是 snake_case！

#### 4.2 创建 FeedItem.kt

路径：`data/model/FeedItem.kt`

```kotlin
package com.ico.nekofeed.data.model

import com.google.gson.annotations.SerializedName

data class FeedItem(
    val id: String,
    val title: String,
    val summary: String?,
    val content: String?,

    // snake_case → camelCase 的映射
    @SerializedName("source_name")
    val sourceName: String?,

    @SerializedName("source_url")
    val sourceUrl: String?,

    val category: String?,

    @SerializedName("item_type")
    val itemType: String?,

    @SerializedName("card_type")
    val cardType: String?,

    @SerializedName("image_url")
    val imageUrl: String?,

    @SerializedName("media_url")
    val mediaUrl: String?,

    val tags: List<String> = emptyList(),

    @SerializedName("published_at")
    val publishedAt: String?
)
```

**逐行解释：**

```kotlin
data class FeedItem(...)
```
- `data class`：Kotlin 数据类
- 自动生成 `equals()`、`hashCode()`、`toString()`、`copy()`

```kotlin
val summary: String?
```
- `String?`：可空类型，表示可能为 null
- API 返回的字段可能没有值

```kotlin
@SerializedName("image_url")
val imageUrl: String?
```
- `@SerializedName`：告诉 Gson JSON 中的字段名
- JSON 中是 `image_url`，Kotlin 中用 `imageUrl`

```kotlin
val tags: List<String> = emptyList()
```
- `= emptyList()`：默认值，调用时不传就用空列表

#### 4.3 创建 FeedResponse.kt

路径：`data/model/FeedResponse.kt`

```kotlin
package com.ico.nekofeed.data.model

data class FeedResponse(
    val items: List<FeedItem>,
    val limit: Int,
    val offset: Int,
    val total: Int
)
```

**为什么要单独的 Response 类？**

API 返回的完整 JSON：
```json
{
  "items": [...],    // 列表数据
  "limit": 20,       // 分页参数
  "offset": 0,
  "total": 100
}
```

`FeedResponse` 对应整个 JSON，`FeedItem` 对应列表中的每一项。

---

## 第五步：搭建网络层

### 做什么？

用 Retrofit 定义 API 接口，配置网络客户端。

### 为什么用 Retrofit？

- 把 HTTP 请求变成 Kotlin 函数调用
- 自动 JSON 解析
- 类型安全

### 怎么做？

#### 5.1 创建 FeedApi.kt

路径：`data/remote/FeedApi.kt`

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
interface FeedApi
```
- `interface`：接口，只定义方法签名
- Retrofit 会在运行时自动生成实现

```kotlin
@GET("api/feed")
```
- `@GET`：HTTP GET 请求
- `"api/feed"`：请求路径，会拼接到 baseUrl 后面

```kotlin
suspend fun getFeed(...)
```
- `suspend`：挂起函数，只能在协程中调用
- 不会阻塞线程

```kotlin
@Query("category") category: String? = null
```
- `@Query`：URL 查询参数
- `= null`：默认值，不传就不加这个参数

**最终生成的 URL：**
```
GET http://10.0.2.2:8000/api/feed?limit=20&offset=0&base_url=http://10.0.2.2:8000
```

#### 5.2 创建 RetrofitClient.kt

路径：`data/remote/RetrofitClient.kt`

```kotlin
package com.ico.nekofeed.data.remote

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    // 模拟器访问本机的地址
    private const val BASE_URL = "http://10.0.2.2:8000/"

    // 日志拦截器：打印请求/响应详情
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    // HTTP 客户端
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // Retrofit 实例
    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    // API 接口实例
    val feedApi: FeedApi = retrofit.create(FeedApi::class.java)
}
```

**逐行解释：**

```kotlin
object RetrofitClient
```
- `object`：Kotlin 单例
- 整个 App 只有一个实例

```kotlin
private const val BASE_URL = "http://10.0.2.2:8000/"
```
- `const val`：编译时常量
- `10.0.2.2`：模拟器访问宿主机的特殊地址
- 结尾必须有 `/`

```kotlin
private val loggingInterceptor = HttpLoggingInterceptor().apply {
    level = HttpLoggingInterceptor.Level.BODY
}
```
- `.apply { }`：作用域函数，在对象上执行代码块
- `Level.BODY`：打印完整的请求/响应体

```kotlin
private val okHttpClient = OkHttpClient.Builder()
    .addInterceptor(loggingInterceptor)
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .build()
```
- OkHttp：实际执行网络请求的客户端
- Builder 模式：链式调用配置
- 超时设置：避免无限等待

```kotlin
.addConverterFactory(GsonConverterFactory.create())
```
- Gson 转换器：自动把 JSON 转成 Kotlin 对象

```kotlin
val feedApi: FeedApi = retrofit.create(FeedApi::class.java)
```
- `create()`：根据接口定义生成实现
- 返回的 `feedApi` 就是 API 调用者

---

## 第六步：创建本地兜底数据

### 做什么？

当网络请求失败时，显示预设的本地数据。

### 为什么？

保证 App 在离线状态下仍能演示。

### 怎么做？

创建 `data/local/FallbackFeedData.kt`：

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
        FeedItem(
            id = "fallback_002",
            title = "智能降噪耳机推荐",
            summary = "专为学生和通勤人群设计的高性价比降噪耳机",
            content = "这款降噪耳机采用了先进的主动降噪技术...",
            sourceName = "数码评测",
            sourceUrl = null,
            category = "ad",
            itemType = "ad",
            cardType = "large_image",
            imageUrl = null,
            mediaUrl = null,
            tags = listOf("耳机", "降噪", "学生党"),
            publishedAt = "2026-05-29T15:30:00"
        )
        // ... 可以添加更多数据
    )
}
```

**知识点：**
- `object`：单例，静态数据容器
- `listOf()`：创建不可变列表
- `listOf("AI", "技术")`：列表字面量

---

## 第七步：实现 Repository

### 做什么？

封装数据获取逻辑，协调远程 API 和本地兜底数据。

### 为什么需要 Repository？

```
ViewModel 不直接调用 API
    ↓
通过 Repository 获取数据
    ↓
Repository 决定从哪获取（网络/缓存/兜底）
```

这叫**关注点分离**：ViewModel 只关心"要数据"，不关心"数据从哪来"。

### 怎么做？

创建 `data/repository/FeedRepository.kt`：

```kotlin
package com.ico.nekofeed.data.repository

import com.ico.nekofeed.data.local.FallbackFeedData
import com.ico.nekofeed.data.model.FeedItem
import com.ico.nekofeed.data.remote.FeedApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FeedRepository(private val feedApi: FeedApi) {

    // 内存缓存
    private val cachedItems = mutableListOf<FeedItem>()

    suspend fun loadFeed(
        category: String? = null,
        limit: Int = 20,
        offset: Int = 0
    ): Result<List<FeedItem>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = feedApi.getFeed(
                    category = category,
                    limit = limit,
                    offset = offset
                )
                val items = response.items

                // 更新缓存
                if (offset == 0) {
                    cachedItems.clear()
                }
                cachedItems.addAll(items)

                Result.success(items)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    fun getCachedItemById(id: String): FeedItem? {
        return cachedItems.find { it.id == id }
    }

    fun getFallbackData(): List<FeedItem> {
        return FallbackFeedData.items
    }
}
```

**逐行解释：**

```kotlin
class FeedRepository(private val feedApi: FeedApi)
```
- 构造函数参数直接声明为属性
- 等价于 Java 的成员变量 + 构造函数赋值

```kotlin
private val cachedItems = mutableListOf<FeedItem>()
```
- 可变列表，存储缓存数据
- 详情页可以从缓存查找 item

```kotlin
suspend fun loadFeed(...): Result<List<FeedItem>>
```
- `suspend`：挂起函数
- `Result<T>`：成功/失败的结果容器

```kotlin
return withContext(Dispatchers.IO) {
    // 在 IO 线程执行
}
```
- `withContext`：切换线程
- `Dispatchers.IO`：IO 线程池，适合网络请求
- 网络请求不能在主线程执行，会抛异常

```kotlin
Result.success(items)  // 成功时
Result.failure(e)      // 失败时
```
- Result 的两种状态

```kotlin
fun getCachedItemById(id: String): FeedItem? {
    return cachedItems.find { it.id == id }
}
```
- `find`：查找第一个匹配的元素
- `{ it.id == id }`：lambda 表达式，`it` 是当前元素
- 返回 `FeedItem?`：可能找不到（返回 null）

---

## 第八步：定义 UI 状态

### 做什么？

定义一个类来描述 UI 的所有状态。

### 为什么需要 UI 状态类？

```
UI 状态 = 加载中 + 数据列表 + 错误信息 + 是否用兜底数据
```

集中管理状态，Compose 只需观察一个对象。

### 怎么做？

创建 `util/UiState.kt`：

```kotlin
package com.ico.nekofeed.util

import com.ico.nekofeed.data.model.FeedItem

data class FeedUiState(
    val isLoading: Boolean = false,
    val items: List<FeedItem> = emptyList(),
    val errorMessage: String? = null,
    val usingFallback: Boolean = false
)

data class FeedDetailUiState(
    val item: FeedItem? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
```

**状态含义：**
- `isLoading = true`：显示加载动画
- `items`：要显示的数据列表
- `errorMessage != null`：显示错误信息
- `usingFallback = true`：显示"当前使用本地演示数据"提示

---

## 第九步：实现 ViewModel

### 做什么？

管理 UI 状态，协调数据加载。

### 为什么需要 ViewModel？

```
用户操作 → ViewModel → 修改状态 → UI 自动更新
```

ViewModel 是 UI 和数据之间的桥梁。

### 怎么做？

创建 `ui/feed/FeedViewModel.kt`：

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

    private val repository = FeedRepository(RetrofitClient.feedApi)

    // 私有的可变状态
    private val _uiState = MutableStateFlow(FeedUiState())

    // 公开的只读状态
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    init {
        loadFeed()
    }

    fun loadFeed() {
        viewModelScope.launch {
            // 1. 设置加载中状态
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null,
                usingFallback = false
            )

            // 2. 调用 Repository 加载数据
            repository.loadFeed().fold(
                onSuccess = { items ->
                    // 3a. 成功：更新状态
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        items = items,
                        errorMessage = null,
                        usingFallback = false
                    )
                },
                onFailure = { error ->
                    // 3b. 失败：使用兜底数据
                    val fallbackItems = repository.getFallbackData()
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        items = fallbackItems,
                        errorMessage = "无法连接服务器: ${error.message}",
                        usingFallback = true
                    )
                }
            )
        }
    }

    fun retry() {
        loadFeed()
    }

    fun getItemById(id: String): FeedItem? {
        return repository.getCachedItemById(id)
            ?: repository.getFallbackData().find { it.id == id }
    }
}
```

**逐行解释：**

```kotlin
private val repository = FeedRepository(RetrofitClient.feedApi)
```
- 创建 Repository 实例
- 注入 RetrofitClient 的 feedApi

```kotlin
private val _uiState = MutableStateFlow(FeedUiState())
val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()
```

**命名约定：**
- `_uiState`（下划线）：私有可变
- `uiState`：公开只读

**StateFlow：**
- `MutableStateFlow`：可变，ViewModel 内部修改
- `StateFlow`：只读，外部观察
- `asStateFlow()`：返回只读视图

```kotlin
init {
    loadFeed()
}
```
- `init`：初始化块
- ViewModel 创建时自动加载数据

```kotlin
viewModelScope.launch {
    // 在主线程启动协程
}
```
- `viewModelScope`：ViewModel 的协程作用域
- ViewModel 销毁时自动取消协程
- `launch`：启动协程

```kotlin
_uiState.value = _uiState.value.copy(
    isLoading = true,
    errorMessage = null
)
```
- StateFlow 通过 `.value` 读写
- `copy()`：复制并修改部分字段
- 不可变对象的修改方式

```kotlin
repository.loadFeed().fold(
    onSuccess = { items -> /* 成功处理 */ },
    onFailure = { error -> /* 失败处理 */ }
)
```
- `fold()`：分别处理成功和失败

```kotlin
fun getItemById(id: String): FeedItem? {
    return repository.getCachedItemById(id)
        ?: repository.getFallbackData().find { it.id == id }
}
```
- `?:`（Elvis 操作符）：左边是 null 就返回右边
- 优先从缓存找，找不到再从兜底数据找

---

## 第十步：编写 Feed 卡片组件

### 做什么？

创建一个 Composable 组件来显示单个 FeedItem。

### Composable 是什么？

```kotlin
@Composable
fun MyComponent() {
    // 这是一个 UI 组件
}
```

- 用函数定义 UI
- 函数名首字母大写（约定）
- 可以组合其他 Composable

### 怎么做？

创建 `ui/feed/components/FeedItemCard.kt`：

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
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        when (item.cardType) {
            "small_image" -> SmallImageCard(item)
            else -> LargeImageCard(item)
        }
    }
}
```

**逐行解释：**

```kotlin
@Composable
fun FeedItemCard(
    item: FeedItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
)
```
- `@Composable`：标记这是一个 Compose 组件
- `item: FeedItem`：要显示的数据
- `onClick: () -> Unit`：点击回调（函数类型参数）
- `modifier: Modifier = Modifier`：可选的修饰符

```kotlin
modifier = modifier
    .fillMaxWidth()
    .clickable(onClick = onClick)
```
- Modifier 链式调用
- `fillMaxWidth()`：宽度填满
- `clickable()`：添加点击事件

```kotlin
when (item.cardType) {
    "small_image" -> SmallImageCard(item)
    else -> LargeImageCard(item)
}
```
- `when`：条件分支，类似 switch
- 根据 cardType 选择不同布局

**大图卡片：**

```kotlin
@Composable
private fun LargeImageCard(item: FeedItem) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // 图片区域
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (item.imageUrl != null) {
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(text = "No Image")
            }

            // 视频标识
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
                    Text(text = "▶", color = Color.White)
                }
            }
        }

        // 文字区域
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // Sponsored 标签
                if (item.itemType == "ad" || item.itemType == "product") {
                    Text(
                        text = "Sponsored",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.primaryContainer,
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // 摘要
            item.summary?.let { summary ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 来源和标签
            Spacer(modifier = Modifier.height(8.dp))
            item.sourceName?.let { source ->
                Text(text = source, style = MaterialTheme.typography.labelSmall)
            }
            if (item.tags.isNotEmpty()) {
                Text(
                    text = item.tags.joinToString(" ") { "#$it" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
```

**关键知识点：**

```kotlin
AsyncImage(
    model = item.imageUrl,
    contentDescription = item.title,
    modifier = Modifier.fillMaxSize(),
    contentScale = ContentScale.Crop
)
```
- Coil 的图片组件
- 自动异步加载网络图片
- `ContentScale.Crop`：裁剪填满

```kotlin
Color.Black.copy(alpha = 0.6f)
```
- 复制颜色，只修改透明度
- 60% 不透明度的黑色

```kotlin
TextOverflow.Ellipsis
```
- 文字超出时显示省略号

```kotlin
item.summary?.let { summary ->
    // 只有 summary 不为 null 时才执行
}
```
- `?.let { }`：安全调用 + 作用域函数

```kotlin
item.tags.joinToString(" ") { "#$it" }
```
- 把列表转成字符串
- 每个元素用 lambda 处理
- 结果：`"#AI #技术 #大语言模型"`

**小图卡片：**

```kotlin
@Composable
private fun SmallImageCard(item: FeedItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
    ) {
        // 左边文字
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.title, fontWeight = FontWeight.Bold)
            item.summary?.let { Text(text = it, maxLines = 2) }
        }

        // 右边图片
        Spacer(modifier = Modifier.width(12.dp))
        item.imageUrl?.let { imageUrl ->
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
        }
    }
}
```

```kotlin
modifier = Modifier.weight(1f)
```
- `weight(1f)`：占据剩余空间
- Row 中的文字部分占据剩余宽度，图片固定宽度

```kotlin
modifier = Modifier
    .size(100.dp)
    .clip(RoundedCornerShape(8.dp))
```
- `size(100.dp)`：100dp 的正方形
- `clip()`：裁剪成圆角矩形

---

## 第十一步：编写 FeedScreen

### 做什么？

创建首页，显示 Feed 列表。

### 怎么做？

创建 `ui/feed/FeedScreen.kt`：

```kotlin
package com.ico.nekofeed.ui.feed

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ico.nekofeed.ui.feed.components.FeedItemCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    onItemClick: (String) -> Unit,
    viewModel: FeedViewModel = viewModel()
) {
    // 观察 ViewModel 的状态
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = "AdFlow AI")
                        Text(
                            text = "Local Feed Server: http://10.0.2.2:8000",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    // 加载中
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                uiState.errorMessage != null && uiState.items.isEmpty() -> {
                    // 错误（且没有数据）
                    ErrorContent(
                        message = uiState.errorMessage ?: "未知错误",
                        onRetry = viewModel::retry,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    // 正常显示列表
                    FeedList(
                        items = uiState.items,
                        usingFallback = uiState.usingFallback,
                        onItemClick = onItemClick
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedList(
    items: List<com.ico.nekofeed.data.model.FeedItem>,
    usingFallback: Boolean,
    onItemClick: (String) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 兜底数据提示
        if (usingFallback) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Text(
                        text = "当前使用本地演示数据",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Feed 列表
        items(
            items = items,
            key = { it.id }
        ) { item ->
            FeedItemCard(
                item = item,
                onClick = { onItemClick(item.id) }
            )
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "加载失败", color = MaterialTheme.colorScheme.error)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = message, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text(text = "重试")
        }
    }
}
```

**逐行解释：**

```kotlin
val uiState by viewModel.uiState.collectAsState()
```
- `collectAsState()`：把 Flow 转成 Compose 状态
- `by`：委托语法，自动取 `.value`

```kotlin
viewModel: FeedViewModel = viewModel()
```
- 默认参数：调用时可以不传
- `viewModel()`：获取或创建 ViewModel 实例
- 屏幕旋转时返回同一个实例

```kotlin
Scaffold(
    topBar = { TopAppBar(...) }
) { paddingValues ->
    // 内容
}
```
- Scaffold：Material Design 页面骨架
- `paddingValues`：系统栏的内边距

```kotlin
when {
    uiState.isLoading -> { /* 加载动画 */ }
    uiState.errorMessage != null -> { /* 错误内容 */ }
    else -> { /* 正常内容 */ }
}
```
- 状态驱动 UI
- 不同状态显示不同内容

```kotlin
LazyColumn(
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
) {
    items(items = items, key = { it.id }) { item ->
        FeedItemCard(...)
    }
}
```

**LazyColumn vs Column：**

| Column | LazyColumn |
|--------|------------|
| 全部渲染 | 只渲染可见项 |
| 适合少量内容 | 适合长列表 |
| 类似 LinearLayout | 类似 RecyclerView |

```kotlin
key = { it.id }
```
- 唯一标识，帮助 Compose 高效更新
- 没有 key 只能通过位置判断
- 有 key 可以通过 id 判断

```kotlin
viewModel::retry
```
- 方法引用，等价于 `{ viewModel.retry() }`

---

## 第十二步：编写 FeedDetailScreen

### 做什么？

创建详情页，显示单个 FeedItem 的详细内容。

### 怎么做？

创建 `ui/detail/FeedDetailScreen.kt`：

```kotlin
package com.ico.nekofeed.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ico.nekofeed.data.model.FeedItem

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FeedDetailScreen(
    item: FeedItem?,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = item?.sourceName ?: "详情") },
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
        if (item == null) {
            // 找不到 item
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "未找到该 FeedItem，请返回首页重新加载")
            }
        } else {
            // 正常内容
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
            ) {
                // 图片
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (item.imageUrl != null) {
                        AsyncImage(
                            model = item.imageUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(text = "No Image")
                    }

                    // 视频标识
                    if (item.itemType == "video" || item.cardType == "video") {
                        Box(
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(12.dp)
                        ) {
                            Text(text = "▶ Video")
                        }
                    }
                }

                // 文字内容
                Column(modifier = Modifier.padding(16.dp)) {
                    // 标题
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    // 摘要
                    item.summary?.let { summary ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // 正文
                    item.content?.let { content ->
                        if (content.isNotBlank()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(text = content)
                        }
                    }

                    // 来源
                    item.sourceName?.let { source ->
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = "来源: $source")
                    }

                    // 原文链接
                    item.sourceUrl?.let { url ->
                        Text(text = url, color = MaterialTheme.colorScheme.primary)
                    }

                    // 标签
                    if (item.tags.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
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

                    // 发布时间
                    item.publishedAt?.let { time ->
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = "发布时间: $time")
                    }

                    // 底部间距
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}
```

**关键知识点：**

```kotlin
item: FeedItem?
```
- 可空参数，详情页可能找不到对应的 item

```kotlin
Modifier.verticalScroll(rememberScrollState())
```
- 添加垂直滚动
- `rememberScrollState()`：记住滚动位置

```kotlin
Icons.AutoMirrored.Filled.ArrowBack
```
- Material Icons 中的返回箭头
- AutoMirrored：RTL 语言会自动翻转

```kotlin
FlowRow(
    horizontalArrangement = Arrangement.spacedBy(8.dp)
) {
    item.tags.forEach { tag ->
        AssistChip(onClick = { }, label = { Text(tag) })
    }
}
```
- FlowRow：流动布局，子元素自动换行
- AssistChip：Material 3 的标签组件

---

## 第十三步：配置导航

### 做什么？

定义页面路由和跳转逻辑。

### 为什么需要导航？

```
首页 (feed) → 点击卡片 → 详情页 (detail/{itemId})
详情页 → 点击返回 → 回到首页
```

### 怎么做？

创建 `navigation/AppNavHost.kt`：

```kotlin
package com.ico.nekofeed.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ico.nekofeed.ui.detail.FeedDetailScreen
import com.ico.nekofeed.ui.feed.FeedScreen
import com.ico.nekofeed.ui.feed.FeedViewModel

@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController()
) {
    // 共享 ViewModel
    val feedViewModel: FeedViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = "feed"
    ) {
        // 首页
        composable("feed") {
            FeedScreen(
                viewModel = feedViewModel,
                onItemClick = { itemId ->
                    val encodedId = Uri.encode(itemId)
                    navController.navigate("detail/$encodedId")
                }
            )
        }

        // 详情页
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

            // 从 ViewModel 获取 item
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

**逐行解释：**

```kotlin
val feedViewModel: FeedViewModel = viewModel()
```
- 在 NavHost 层获取 ViewModel
- 两个页面共享同一个实例

```kotlin
NavHost(
    navController = navController,
    startDestination = "feed"
)
```
- NavHost：导航容器
- `startDestination`：起始页面路由

```kotlin
composable("feed") { FeedScreen(...) }
```
- 注册路由 `"feed"` 对应的页面

```kotlin
navController.navigate("detail/$encodedId")
```
- 导航到详情页
- `Uri.encode()`：编码特殊字符

```kotlin
composable(
    route = "detail/{itemId}",
    arguments = listOf(
        navArgument("itemId") { type = NavType.StringType }
    )
)
```
- `{itemId}`：动态参数占位符
- `navArgument`：声明参数类型

```kotlin
val itemId = backStackEntry.arguments?.getString("itemId")
```
- 从路由参数获取 itemId

```kotlin
val item = remember(decodedId) {
    feedViewModel.getItemById(decodedId)
}
```
- `remember(decodedId)`：只有 decodedId 变化时才重新计算
- 避免每次重组都查找 item

```kotlin
navController.popBackStack()
```
- 返回上一页

---

## 第十四步：修改 MainActivity

### 做什么？

把 App 的入口改为使用 Compose + 导航。

### 怎么做？

修改 `MainActivity.kt`：

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
- ComponentActivity：支持 Compose 的 Activity

```kotlin
enableEdgeToEdge()
```
- 启用全面屏显示
- 内容延伸到状态栏后面

```kotlin
setContent { ... }
```
- 设置 Compose 内容
- 替代 `setContentView(R.layout.xxx)`

```kotlin
NekoFeedTheme {
    AppNavHost()
}
```
- 应用主题
- AppNavHost：导航入口

---

## 第十五步：运行与调试

### 运行前检查清单

- [ ] Feed Server 已启动（端口 8000）
- [ ] Gradle 已同步
- [ ] 模拟器已启动

### 运行步骤

1. 点击 Android Studio 的 **Run** 按钮
2. 等待 App 启动
3. 观察 Logcat 日志

### 验证功能

| 功能 | 验证方法 |
|------|---------|
| 加载数据 | 首页显示 Feed 列表 |
| 显示图片 | 卡片上有图片 |
| 点击跳转 | 点击卡片进入详情页 |
| 返回首页 | 详情页点返回回到首页 |
| 兜底数据 | 关闭 Server 后显示 fallback 数据 |

### 查看网络日志

在 Logcat 中搜索 `OkHttp`，可以看到请求/响应详情。

---

## 常见问题排查

| 问题 | 原因 | 解决 |
|------|------|------|
| 网络请求失败 | Server 未启动 | 启动 Feed Server |
| 连接被拒绝 | 端口错误 | 确认端口是 8000 |
| Cleartext HTTP 错误 | Android 禁止明文 | manifest 添加 cleartext 配置 |
| 模拟器连不上 | 用了 localhost | 模拟器用 `10.0.2.2` |
| 图片不显示 | URL 为空或无效 | 检查 API 返回的 image_url |
| 字段值全是 null | snake_case 不匹配 | 添加 @SerializedName 注解 |
| 详情页"未找到" | 缓存为空 | 从首页正常进入后缓存生效 |
| 重复声明错误 | 同一类定义了两次 | 删除重复的 .kt 文件 |

---

## 踩坑记录

### 坑 1：API 字段名不匹配

**现象：** 所有字段都是 null 或默认值

**原因：** API 返回 `image_url`，Kotlin 模型是 `imageUrl`

**解决：** 添加 `@SerializedName("image_url")` 注解

### 坑 2：网络请求在主线程

**现象：** 抛出 `NetworkOnMainThreadException`

**原因：** 网络请求必须在后台线程

**解决：** 用 `withContext(Dispatchers.IO)` 切换线程

### 坑 3：模拟器访问 localhost

**现象：** 连接超时

**原因：** 模拟器的 localhost 是模拟器自己，不是宿主机

**解决：** 用 `10.0.2.2` 代替 `localhost`

### 坑 4：HTTP 被禁止

**现象：** 抛出 `Cleartext HTTP traffic not permitted`

**原因：** Android 9+ 默认禁止 HTTP

**解决：** manifest 添加 `android:usesCleartextTraffic="true"`

### 坑 5：文件名写错导致重复声明

**现象：** `Redeclaration: data class FeedItem`

**原因：** 创建了两个文件定义同一个类

**解决：** 删除重复的文件

---

## 总结

### 架构图

```
┌─────────────────────────────────────────────────┐
│                 MainActivity                     │
│                   ↓ setContent                  │
│                AppNavHost                        │
│               ↙          ↘                      │
│         FeedScreen    FeedDetailScreen           │
│              ↓                                 │
│         FeedViewModel                           │
│              ↓                                 │
│         FeedRepository                          │
│           ↙      ↘                             │
│     FeedApi    FallbackFeedData                 │
│        ↓                                       │
│    Feed Server                                  │
└─────────────────────────────────────────────────┘
```

### 核心知识点

| 技术 | 用途 |
|------|------|
| Kotlin data class | 数据模型 |
| @SerializedName | JSON 字段映射 |
| Retrofit + OkHttp | 网络请求 |
| suspend 函数 | 协程异步 |
| Result 类型 | 成功/失败处理 |
| StateFlow | 响应式状态 |
| @Composable | UI 组件 |
| LazyColumn | 长列表 |
| Navigation | 页面跳转 |

### 下一步扩展

1. 下拉刷新 + 上拉加载
2. 点赞收藏功能
3. 曝光点击统计
4. Media3 视频播放
5. AI 摘要标签
6. Room 持久化缓存
