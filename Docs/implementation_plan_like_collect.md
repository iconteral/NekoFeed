# NekoFeed 点赞/收藏修复 — 服务端统一管理 + 设备 UUID 方案

## 设计思路

将所有互动状态（点赞/收藏/历史）**完全交给服务端管理**，消除客户端本地 Room 互动表带来的同步和隔离问题：

1. **设备 UUID** — 客户端首次启动生成一个持久化 UUID 作为匿名用户身份
2. **服务端自动注册设备用户** — 收到未知 device_id 时自动创建一条 `User` 记录（`is_device = True`）
3. **统一互动路径** — 无论登录/匿名，都走同一个 `UserLike`/`UserCollect` 表，消除客户端 `if (!isLoggedIn())` 分支
4. **Feed API 返回互动状态** — `GET /api/feed` 通过 `X-Device-Id` header 识别用户，返回 `is_liked`/`like_count`
5. **FeedItem UUID** — 所有 item 用 `uuid4` 生成 ID，确保全局唯一

```
┌─────────────────── 修改前 ──────────────────┐    ┌────────────── 修改后 ──────────────────┐
│ Client                                      │    │ Client                                │
│ ├── 未登录: 本地 Room toggle + persist       │    │ ├── 生成 device_id, 持久化到 DataStore  │
│ ├── 已登录: POST /like → 服务端             │    │ ├── 每个请求带 X-Device-Id header       │
│ └── mergeLocalInteractions 合并             │    │ ├── 登录后绑定 device_id → user_id     │
│                                              │    │ └── 所有互动都走 POST /like → 服务端   │
│ Server                                      │    │                                        │
│ ├── GET /feed → 不返回互动状态              │    │ Server                                │
│ └── POST /like → 仅限登录用户               │    │ ├── GET /feed → 带 is_liked/like_count │
│                                              │    │ ├── POST /like → 登录或设备用户均可    │
│ Problem: 本地/服务端双源 → 不一致           │    │ └── 单一数据源 → 一致                  │
└──────────────────────────────────────────────┘    └────────────────────────────────────────┘
```

---

## Proposed Changes

### Server — 数据模型

---

#### [MODIFY] [models.py](file:///e:/NekoFeed/NekoFeedServer/app/models.py)

**1. `User` 表增加设备用户支持：**

```python
class User(Base):
    __tablename__ = "users"

    id = Column(Integer, primary_key=True, index=True)
    username = Column(String, unique=True, index=True, nullable=False)
    hashed_password = Column(String, nullable=True)  # ← nullable: 设备用户没有密码
    device_id = Column(String, unique=True, nullable=True, index=True)  # ← 新增
    is_device = Column(Boolean, default=False)  # ← 新增: 标记设备用户
    linked_user_id = Column(Integer, nullable=True)  # ← 新增: 登录后绑定到真实用户
    avatar = Column(String, nullable=True)
    bio = Column(Text, nullable=True)
    level = Column(String, default="Normal")
    is_active = Column(Boolean, default=True)
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)
```

设备用户的 username 自动生成为 `device_{device_id[:8]}`，`hashed_password = None`。

**2. `FeedItem.id` 改用 UUID：**

目前 RSS item 的 ID 是 `item_{md5(link)}`，custom ad 是 `custom_{md5(title)}`。改为 `uuid4`：

```python
import uuid

class FeedItem(Base):
    __tablename__ = "feed_items"
    id = Column(String, primary_key=True, index=True, default=lambda: str(uuid.uuid4()))
    # ... 其余不变 ...
```

> [!NOTE]
> 对已有数据无影响——旧的 `item_xxx`/`custom_xxx` ID 仍然合法（都是 String）。只是新写入的数据用 UUID 格式。`item_normalizer.py` 和 `seed.py` 里的 ID 生成也一并改为 UUID。
>
> 也可以保留 MD5 Hash 方式但加 namespace prefix（如 `rss:{md5}`），只要保证唯一性即可。看你偏好。

---

### Server — 认证层

---

#### [MODIFY] [auth.py](file:///e:/NekoFeed/NekoFeedServer/app/auth.py)

新增 `get_or_create_device_user` 函数 + 修改 `get_current_user_or_device`：

```python
from fastapi import Header

def get_or_create_device_user(
    device_id: Optional[str] = Header(None, alias="X-Device-Id"),
    credentials: Optional[HTTPAuthorizationCredentials] = Depends(HTTPBearer(auto_error=False)),
    db: Session = Depends(get_db)
) -> Optional[User]:
    """
    优先用 Bearer token 识别登录用户；
    否则用 X-Device-Id 找到或创建设备用户。
    """
    # 1. 尝试 Bearer token
    if credentials:
        user = _resolve_token(credentials.credentials, db)
        if user:
            return user

    # 2. 尝试 device_id
    if device_id:
        user = db.query(User).filter(User.device_id == device_id).first()
        if not user:
            user = User(
                username=f"device_{device_id[:8]}",
                device_id=device_id,
                is_device=True,
                hashed_password=None
            )
            db.add(user)
            db.commit()
            db.refresh(user)
        return user

    return None
```

这样每个交互端点和 feed 端点都可以用 `Depends(get_or_create_device_user)` 统一获取用户身份。

---

### Server — Feed API

---

#### [MODIFY] [api.py](file:///e:/NekoFeed/NekoFeedServer/app/routers/api.py)

`GET /api/feed` 接受 `X-Device-Id` 或 `Authorization` header，返回互动状态：

```python
from app.auth import get_or_create_device_user
from app.models import UserLike, UserCollect

@router.get("/feed")
def get_feed(
    category: Optional[str] = None,
    limit: int = Query(20, ge=1, le=100),
    offset: int = Query(0, ge=0),
    base_url: Optional[str] = None,
    current_user: Optional[User] = Depends(get_or_create_device_user),
    db: Session = Depends(get_db)
):
    # ... 现有查询逻辑 ...
    
    # 批量查询当前用户的互动状态（避免 N+1）
    item_ids = [item.id for item in items]
    
    like_counts = {}
    collect_counts = {}
    user_likes = set()
    user_collects = set()
    
    if item_ids:
        # 聚合查询 like_count / collect_count
        from sqlalchemy import func
        for item_id, cnt in db.query(UserLike.item_id, func.count()).filter(
            UserLike.item_id.in_(item_ids)
        ).group_by(UserLike.item_id).all():
            like_counts[item_id] = cnt
            
        for item_id, cnt in db.query(UserCollect.item_id, func.count()).filter(
            UserCollect.item_id.in_(item_ids)
        ).group_by(UserCollect.item_id).all():
            collect_counts[item_id] = cnt
        
        # 当前用户的互动
        if current_user:
            user_likes = {row.item_id for row in db.query(UserLike.item_id).filter(
                UserLike.user_id == current_user.id,
                UserLike.item_id.in_(item_ids)
            ).all()}
            user_collects = {row.item_id for row in db.query(UserCollect.item_id).filter(
                UserCollect.user_id == current_user.id,
                UserCollect.item_id.in_(item_ids)
            ).all()}
    
    for item in items:
        item_dict = { ... }  # 现有字段
        item_dict['like_count'] = like_counts.get(item.id, 0)
        item_dict['collect_count'] = collect_counts.get(item.id, 0)
        item_dict['is_liked'] = item.id in user_likes
        item_dict['is_collected'] = item.id in user_collects
```

> [!TIP]
> 用 `IN` + `GROUP BY` 批量查，20 条 feed 只需 4 条额外 SQL（而不是 80 条），性能可控。

---

#### [MODIFY] [user_interaction.py](file:///e:/NekoFeed/NekoFeedServer/app/routers/user_interaction.py)

`toggle_like` / `toggle_collect` 把 `get_current_user` 改为 `get_or_create_device_user`：

```python
@router.post("/items/{item_id}/like", response_model=ItemInteraction)
def toggle_like(
    item_id: str,
    current_user: User = Depends(get_or_create_device_user),  # ← 改这里
    db: Session = Depends(get_db)
):
    if not current_user:
        raise HTTPException(status_code=400, detail="需要登录或提供设备 ID")
    # ... 其余逻辑不变 ...
```

---

#### [MODIFY] [schemas.py](file:///e:/NekoFeed/NekoFeedServer/app/schemas.py)

`FeedItemResponse` 增加互动字段：

```python
class FeedItemResponse(BaseModel):
    # ... 现有字段 ...
    is_liked: bool = Field(False, alias="is_liked")
    is_collected: bool = Field(False, alias="is_collected")
    like_count: int = Field(0, alias="like_count")
    collect_count: int = Field(0, alias="collect_count")
```

---

### Server — 登录绑定设备

---

#### [MODIFY] [user.py](file:///e:/NekoFeed/NekoFeedServer/app/routers/user.py)

登录/注册成功后，如果请求带了 `X-Device-Id`，将设备用户的互动记录迁移到真实用户：

```python
@router.post("/login", response_model=Token)
def login(
    user_data: UserLogin,
    device_id: Optional[str] = Header(None, alias="X-Device-Id"),
    db: Session = Depends(get_db)
):
    # ... 现有登录验证 ...
    token = create_access_token({"sub": str(user.id)})
    
    # 迁移设备用户的互动到登录用户
    if device_id:
        _merge_device_interactions(db, device_id, user.id)
    
    return {"access_token": token, "token_type": "bearer"}

def _merge_device_interactions(db, device_id: str, real_user_id: int):
    """将设备用户的 like/collect/history 迁移到真实用户"""
    device_user = db.query(User).filter(User.device_id == device_id).first()
    if not device_user or device_user.id == real_user_id:
        return
    
    # 迁移 likes（跳过已存在的）
    for like in db.query(UserLike).filter(UserLike.user_id == device_user.id).all():
        exists = db.query(UserLike).filter(
            UserLike.user_id == real_user_id,
            UserLike.item_id == like.item_id
        ).first()
        if not exists:
            like.user_id = real_user_id
        else:
            db.delete(like)
    
    # 同理迁移 collects 和 history ...
    
    # 标记设备用户已绑定
    device_user.linked_user_id = real_user_id
    db.commit()
```

---

### Client — 设备 UUID 生成与传递

---

#### [MODIFY] [TokenManager.kt](file:///e:/NekoFeed/app/src/main/java/com/ico/nekofeed/data/local/TokenManager.kt)

新增 `deviceId` 的生成和持久化：

```kotlin
class TokenManager(private val context: Context) {
    companion object {
        // ... 现有 keys ...
        private val DEVICE_ID_KEY = stringPreferencesKey("device_id")
    }
    
    /**
     * 获取或生成设备唯一 ID，首次调用时自动生成并持久化。
     */
    suspend fun getDeviceId(): String {
        val existing = context.dataStore.data.first()[DEVICE_ID_KEY]
        if (existing != null) return existing
        
        val newId = java.util.UUID.randomUUID().toString()
        context.dataStore.edit { it[DEVICE_ID_KEY] = newId }
        return newId
    }
}
```

> [!NOTE]
> 用 `UUID.randomUUID()` 而不是 `ANDROID_ID`，因为 ANDROID_ID 在某些设备上会重复（特别是低端平板），且 Android 10+ 限制了跨应用读取。UUID 首次生成后持久化到 DataStore，卸载重装才会变。

---

#### [MODIFY] [OetrofitClient.kt](file:///e:/NekoFeed/app/src/main/java/com/ico/nekofeed/data/remote/OetrofitClient.kt)

OkHttp Interceptor 加上 `X-Device-Id` header：

```kotlin
object RetrofitClient {
    // ... 现有字段 ...
    private var deviceIdProvider: (() -> String?)? = null
    
    fun setDeviceIdProvider(provider: () -> String?) {
        deviceIdProvider = provider
    }
    
    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor { chain ->
                val original = chain.request()
                val builder = original.newBuilder()
                
                // 添加 Bearer token（如果有）
                tokenProvider?.invoke()?.let {
                    builder.header("Authorization", "Bearer $it")
                }
                // 始终添加 device_id
                deviceIdProvider?.invoke()?.let {
                    builder.header("X-Device-Id", it)
                }
                
                chain.proceed(builder.build())
            }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }
    // ...
}
```

---

#### [MODIFY] [MainActivity.kt](file:///e:/NekoFeed/app/src/main/java/com/ico/nekofeed/MainActivity.kt)

初始化时设置 deviceIdProvider：

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val tokenManager = TokenManager(applicationContext)
        val cachedToken = AtomicReference<String?>(null)
        val cachedDeviceId = AtomicReference<String?>(null)  // 新增
        
        runBlocking {
            val serverConfig = tokenManager.getServerConfig()
            RetrofitClient.updateBaseUrl(serverConfig.baseUrl)
            cachedDeviceId.set(tokenManager.getDeviceId())  // 新增
        }
        
        RetrofitClient.setTokenProvider { cachedToken.get() }
        RetrofitClient.setDeviceIdProvider { cachedDeviceId.get() }  // 新增
        
        // ... 其余不变 ...
    }
}
```

---

### Client — ViewModel 简化

---

#### [MODIFY] [FolderViewModel.kt](file:///e:/NekoFeed/app/src/main/java/com/ico/nekofeed/ui/feed/FolderViewModel.kt)

**核心变化：删除所有本地互动逻辑，统一走服务端。**

```kotlin
class FeedViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FeedRepository(RetrofitClient.feedApi)
    private val userRepository = UserRepository(RetrofitClient.feedApi)
    // ... 其他依赖 ...
    
    // ❌ 删除: private val interactionDao
    // ❌ 删除: mergeLocalInteractions()
    // ❌ 删除: persistInteraction()
    
    fun loadFeed() {
        viewModelScope.launch {
            // ...
            repository.loadFeed(limit = pageSize, offset = 0).fold(
                onSuccess = { items ->
                    // ❌ 删除: val merged = mergeLocalInteractions(items)
                    allItems = items  // 服务端已经返回了正确的 is_liked/like_count
                    // ...
                }
            )
        }
    }
    
    fun toggleLike(itemId: String) {
        // 1. 乐观更新
        val snapshot = allItems
        allItems = allItems.map { item ->
            if (item.id == itemId) {
                item.copy(
                    isLiked = !item.isLiked,
                    likeCount = if (item.isLiked) item.likeCount - 1 else item.likeCount + 1
                )
            } else item
        }
        updateFilteredItems()
        
        // 2. 服务端同步（不区分登录/匿名，都走 API）
        viewModelScope.launch {
            userRepository.toggleLike(itemId).fold(
                onSuccess = { interaction ->
                    // 用服务端权威值覆盖
                    allItems = allItems.map { item ->
                        if (item.id == itemId) {
                            item.copy(
                                isLiked = interaction.isLiked,
                                likeCount = interaction.likeCount,
                                isCollected = interaction.isCollected,
                                collectCount = interaction.collectCount
                            )
                        } else item
                    }
                    updateFilteredItems()
                },
                onFailure = {
                    // 回滚
                    allItems = snapshot
                    updateFilteredItems()
                }
            )
            
            // 用户画像
            allItems.find { it.id == itemId }?.let {
                userProfileRepository.recordInteraction(it, InteractionType.LIKE)
            }
        }
    }
    
    // toggleCollect 同理简化
}
```

**删除的文件/逻辑：**
- `FeedItemInteractionEntity.kt` — 不再需要本地互动表
- `FeedItemInteractionDao.kt` — 不再需要
- `NekoFeedDatabase` 中移除 `feedItemInteractionDao()` 和 `FeedItemInteractionEntity`
- `mergeLocalInteractions()` 整个方法删除
- `persistInteraction()` 整个方法删除
- `toggleLike` / `toggleCollect` 中 `if (!isLoggedIn())` 分支删除

---

#### [MODIFY] [AppNavHost.kt](file:///e:/NekoFeed/app/src/main/java/com/ico/nekofeed/navigation/AppNavHost.kt#L206-L223)

DetailScreen 的 item 改为从 `uiState` 响应式获取：

```kotlin
composable("detail/{itemId}") { backStackEntry ->
    val itemId = backStackEntry.arguments?.getString("itemId") ?: ""
    val decodedId = Uri.decode(itemId)
    val uiState by feedViewModel.uiState.collectAsState()
    
    // 从 uiState.items 或 allItems 查找，不用 remember 缓存
    val item = uiState.items.find { it.id == decodedId }
        ?: feedViewModel.getItemById(decodedId)
    
    FeedDetailScreen(
        item = item,
        onBack = { nestedNavController.popBackStack() },
        onLikeClick = { id -> feedViewModel.toggleLike(id) },
        onCollectClick = { id -> feedViewModel.toggleCollect(id) },
        onShareClick = { id -> feedViewModel.toggleShare(id) },
        isAiEnabled = uiState.isAiEnabled,
        onAiRequest = { feedViewModel.requestAiAnalysis(it) }
    )
}
```

---

#### [MODIFY] [FeedItem.kt](file:///e:/NekoFeed/app/src/main/java/com/ico/nekofeed/data/model/FeedItem.kt#L52-L70)

互动字段全部改为 `val`（不可变）：

```kotlin
// 互动状态 — 来自服务端，不可变
@SerializedName("is_liked")
val isLiked: Boolean = false,      // ← var → val
@SerializedName("is_collected")
val isCollected: Boolean = false,   // ← var → val
@SerializedName("like_count")
val likeCount: Int = 0,             // ← var → val
@SerializedName("collect_count")
val collectCount: Int = 0,          // ← var → val
@SerializedName("share_count")
val shareCount: Int = 0,            // ← var → val
```

---

### Server — Item ID 改 UUID

---

#### [MODIFY] [item_normalizer.py](file:///e:/NekoFeed/NekoFeedServer/app/services/item_normalizer.py#L26)

```python
import uuid

def normalize_feed_item(entry, feed_id, feed_name, feed_category):
    link = entry.get('link', '')
    # 用 UUID 替代 MD5 hash，但保留 link 的去重能力
    item_id = str(uuid.uuid4())
    # ...
    return { 'id': item_id, ... }
```

> [!IMPORTANT]
> **去重问题**：原来的 `item_{md5(link)}` 方案天然保证同一链接不会重复写入。换 UUID 后需要在 `feed_fetcher.py` 的写入逻辑加 `source_url` 去重检查：
> ```python
> existing = db.query(FeedItem).filter(FeedItem.source_url == link).first()
> if existing:
>     continue  # 跳过已存在的 item
> ```

#### [MODIFY] [seed.py](file:///e:/NekoFeed/NekoFeedServer/seed.py#L109)

```python
import uuid
# 改为 UUID
item_id = str(uuid.uuid4())
```

---

## 完整改动文件清单

| 层 | 文件 | 改动 |
|----|------|------|
| **Server** | `models.py` | User 加 `device_id`, `is_device`, `linked_user_id`; FeedItem.id 默认 uuid4 |
| **Server** | `auth.py` | 新增 `get_or_create_device_user()` |
| **Server** | `schemas.py` | `FeedItemResponse` 加互动字段 |
| **Server** | `api.py` | `GET /feed` 用批量查询返回 is_liked/like_count |
| **Server** | `user_interaction.py` | `toggle_like`/`toggle_collect` 改用 `get_or_create_device_user` |
| **Server** | `user.py` | 登录时迁移设备用户互动 |
| **Server** | `item_normalizer.py` | ID 改 UUID + source_url 去重 |
| **Server** | `seed.py` | ID 改 UUID |
| **Client** | `TokenManager.kt` | 新增 `getDeviceId()` |
| **Client** | `OetrofitClient.kt` | Interceptor 加 `X-Device-Id` header |
| **Client** | `MainActivity.kt` | 初始化 deviceIdProvider |
| **Client** | `FolderViewModel.kt` | 删除本地互动逻辑，统一走 API |
| **Client** | `FeedItem.kt` | `var` → `val` |
| **Client** | `AppNavHost.kt` | DetailScreen 去掉 `remember` 缓存 |
| **Client** | ~~`FeedItemInteractionEntity.kt`~~ | [DELETE] |
| **Client** | ~~`FeedItemInteractionDao.kt`~~ | [DELETE] |
| **Client** | `NekoFeedDatabase.kt` | 移除 InteractionEntity/Dao |

---

## Open Questions

> [!IMPORTANT]
> **1. FeedItem ID 迁移策略**：现有数据库里已经有 `item_xxx` / `custom_xxx` 格式的 ID，关联了 UserLike/UserCollect/UserHistory 记录。改 UUID 后旧数据怎么处理？
> - **方案 A**：只对新数据用 UUID，旧数据保持不变（最安全，ID 字段本就是 String 类型）
> - **方案 B**：写一个迁移脚本把旧 ID 替换为 UUID（需要级联更新所有关联表）
> 
> 建议**方案 A**，影响最小。

> [!IMPORTANT]
> **2. 离线场景**：服务端统一管理后，完全无网络时用户点赞会直接失败（onFailure 回滚）。是否需要保留一个轻量的本地队列，网络恢复后重试？还是直接接受"无网络不可点赞"？

> [!IMPORTANT]
> **3. `loadMore` 也需要带互动状态**：目前 `loadMore()` 调用 `repository.loadFeed(offset=currentOffset)` 拉取新数据。服务端修复后这些新数据已自带 `is_liked`/`like_count`，不需要额外处理。确认这个理解正确？

---

## Verification Plan

### Automated Tests

1. **服务端**：启动 FastAPI 后用 curl 测试完整流程：
   ```bash
   # 1. 无 token 无 device_id → feed 返回 is_liked=false, like_count=0
   curl http://localhost:8000/api/feed?limit=2
   
   # 2. 带 device_id → 自动创建设备用户 → 点赞 → feed 返回 is_liked=true
   curl -H "X-Device-Id: test-device-001" -X POST http://localhost:8000/api/items/{id}/like
   curl -H "X-Device-Id: test-device-001" http://localhost:8000/api/feed?limit=2
   
   # 3. 注册真实用户 + 带 device_id → 迁移互动 → 用 token 查 feed
   curl -H "X-Device-Id: test-device-001" -X POST http://localhost:8000/api/auth/register \
        -H "Content-Type: application/json" -d '{"username":"test","password":"123456"}'
   curl -H "Authorization: Bearer {token}" http://localhost:8000/api/feed?limit=2
   # → 应看到之前设备用户的点赞已迁移
   ```

2. **客户端**：在 Android 端验证：
   - 首次打开（未登录）→ 点赞 → 退出重启 → 点赞状态应保持
   - 注册/登录 → 之前的点赞应迁移过来
   - 换设备/卸载重装 → 点赞清零（符合预期，UUID 已变）

### Manual Verification

- 两个模拟器分别用不同 device_id → 互不影响
- 同一模拟器登录不同账号 → 各自看到自己的点赞
