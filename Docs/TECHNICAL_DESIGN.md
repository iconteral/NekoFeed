# NekoFeed 技术设计文档

## 1. 目标与边界

NekoFeed 面向“AI 广告推荐信息流 App”题目，将单纯广告 Mock 列表扩展为内容与广告混排系统。核心目标是用统一数据模型承接异构内容，在 Android 端完成稳定渲染、互动、视频、AI 和可解释的统计链路。

系统是课程/演示级本地部署方案，不包含生产级推荐排序、分布式任务、云对象存储、支付和商业广告归因。

## 2. 总体架构

```text
RSS / Atom / 自定义文章与广告
              |
              v
FastAPI Feed Server
  抓取 -> 清洗 -> 分类归一 -> 媒体缓存 -> LLM 增强
              |
              v
        REST JSON API
              |
              v
Android Data Layer
  Retrofit + Repository + Room/DataStore + Fallback
              |
              v
ViewModel / StateFlow
              |
              v
Compose Feed / Detail / Search / Chat / Stats
              |
              +---- Media3 PlayerManager
```

设计原则：服务端消化上游字段差异，客户端只消费统一契约；远端不可用时允许缓存或 Mock 降级；互动与统计采用事件驱动，避免 UI 组件自行维护多份真相。

## 3. 核心数据模型

`FeedItem` 是跨端核心契约，包含以下字段组：

- 内容：`title`、`summary`、`content`、来源与发布时间
- 分类：`category`、`item_type`、`card_type`
- 媒体：`image_url`、`media_url`
- AI：`ai_summary`、`ai_tags`、`ai_reason`
- 广告：`brand`、`cta_text`、`price_text`、`is_sponsored`
- 互动与统计：点赞、收藏、分享、曝光、点击、播放计数

内容类型包括 `article`、`video`、`ad`、`product`、`local`；卡片类型包括 `large_image`、`small_image`、`video`、`text_only`、`product`。新增类型必须保证未知值可降级，当前客户端未知卡片默认使用大图卡片。

## 4. 服务端设计

### 4.1 组件职责

- `routers/api.py`：Feed 查询、单条内容、刷新和 RSS 输出。
- `routers/user.py`：注册、登录、用户资料和账号统计。
- `routers/user_interaction.py`：点赞、收藏、历史及用户互动列表。
- `routers/admin.py`：内容源、内容、用户和 LLM 管理后台。
- `services/feed_fetcher.py`：抓取 RSS/Atom。
- `services/item_normalizer.py`：将上游字段映射到 `FeedItem`。
- `services/category_normalizer.py`：统一频道枚举并迁移旧数据。
- `services/media_cache.py`：媒体下载和本地静态资源映射。
- `services/llm_enrichment.py`：结构化摘要、标签和推荐理由。

SQLite 保存内容、用户、互动和系统设置。启动时通过 SQLAlchemy 建表，并对新增统计列执行幂等轻量迁移。该方式适合演示；生产环境应改用 Alembic。

### 4.2 主要 API

| API | 用途 |
| --- | --- |
| `GET /api/feed` | 分类、类型、分页 Feed |
| `GET /api/items/{id}` | 单条详情 |
| `POST /api/refresh` | 刷新全部上游源 |
| `POST /api/auth/register`、`login` | 用户认证 |
| `POST /api/items/{id}/like`、`collect` | 互动切换 |
| `GET /api/user/likes`、`collections`、`history` | 用户列表 |

Bearer Token 表示登录身份；`X-Device-Id` 支持未登录设备维度。客户端收到 401 后清理本地认证状态。

## 5. Android 设计

### 5.1 数据与状态流

```text
Screen event
 -> ViewModel
 -> Repository
 -> Retrofit / Room / DataStore
 -> Result or Flow
 -> immutable UI state
 -> Compose recomposition
```

`FolderViewModel` 负责频道数据、分页、刷新、标签过滤、AI 加载、互动合并和本地埋点。`InteractionSyncStore` 将点赞/收藏结果广播到首页、详情和个人互动列表，降低页面间状态漂移。

DataStore 保存服务器地址、Token、设备 ID、AI 配置、Mock 开关和 Feed JSON 缓存；Room 保存 AI 缓存、聊天、互动、统计聚合和事件流水。

### 5.2 Feed 渲染

`FeedItemCard` 按 `card_type` 分发至不同卡片。列表采用 `LazyColumn`；分类支持点击和横向手势切换，标签筛选在当前结果上执行。远端失败时优先使用频道缓存，其次使用 `FallbackFeedData`。

图片由 Coil 加载，启用内存和磁盘缓存。加载失败显示占位内容，不阻断列表。

### 5.3 视频播放

`PlayerManager` 复用单个 ExoPlayer，使用 `ownerId` 标识当前卡片，避免列表中创建多个播放器。可见项计算选择当前视频；视频离开播放范围时暂停。播放事件只在播放器实际进入播放态后上报一次，加载失败时保留封面和重试入口。

### 5.4 AI 链路

客户端 AI 请求通过兼容 Chat Completions 的接口生成摘要、标签或回答；服务端还支持后台批量增强。客户端设置并发上限，结果写入 Room 缓存，失败时显示原摘要并允许重试。智能搜索同时使用结构化内容上下文和本地关键词匹配，AI 不可用时仍能完成基础搜索。

密钥存入本机 DataStore 仅适合演示。生产方案应由服务端托管密钥，并为请求增加用户鉴权、额度和审计。

## 6. 互动与一致性

点赞、收藏以服务端响应为权威结果，不在客户端盲目累加计数。未登录时页面引导认证；成功后 Repository 更新当前列表并通过同步流通知其他页面。浏览历史由服务端记录，本地仍保留必要展示状态。

分享使用 Android Intent。点击详情、CTA 和原文入口在执行跳转前记录点击事件，外部 URL 使用统一安全打开逻辑。

## 7. 埋点与统计

事件表记录 `itemId`、事件类型、时间、会话、环境、标题、图片、分类和内容类型。环境字段隔离 `mock` 与真实服务器数据。

曝光判定为卡片可见像素占自身高度至少 50%；同一页面会话对同一内容去重。点击、播放、点赞、收藏、分享按用户实际动作记录。统计页按时间窗口读取事件并聚合：

```text
CTR = click events / exposure events
```

分母为 0 时 CTR 为 0。统计页可按曝光、点击、播放等指标排行。详细验证见 [METRICS_AND_VALIDATION.md](METRICS_AND_VALIDATION.md)。

## 8. 异常与降级

| 场景 | 行为 |
| --- | --- |
| Feed Server 不可达 | 使用本地缓存，必要时进入 Mock 数据 |
| RSS 源失败 | 单源失败不影响数据库已有内容和其他源 |
| 图片失败 | 占位图/骨架，不中断卡片交互 |
| 视频失败 | 封面、错误状态和重试入口 |
| LLM 未配置或超时 | 原摘要/标签回退，基础搜索继续工作 |
| Token 过期 | 401 清理认证并回到登录流程 |

## 9. 性能策略

- LazyColumn 仅组合可见内容，稳定 item key 降低错误复用。
- Coil 内存缓存上限为应用可用内存的 25%，磁盘缓存上限为应用缓存目录的 2%。
- 单 ExoPlayer 避免多视频同时解码。
- AI 结果缓存并限制并发，减少重复请求。
- Feed 使用分页与频道缓存，避免每次切换全量抓取。
- Room 统计采用事件索引和 SQL 原子增量更新。

当前交付未声明真实设备 FPS、冷启动或网络 P95 数据；这些指标需要固定设备、数据集和采样工具后再给出，避免将开发机一次构建结果误作端侧性能结论。

## 10. 安全与隐私

- 仓库不提交 API Key、Token、数据库和用户数据。
- 密码由服务端哈希后保存，认证使用 JWT/Bearer Token。
- 当前演示允许 HTTP 明文流量，生产必须启用 HTTPS 并限制 Network Security Config。
- 发送 LLM 前应脱敏，隐私政策需说明内容和查询可能被第三方模型处理。
- 管理后台当前定位为本地工具，公网部署前必须增加管理员认证和 CSRF 防护。

## 11. 测试策略

- JVM 单测：分类规则、互动列表规则、统计聚合与 CTR。
- Android instrumentation：保留基础设备测试入口，关键流程可继续补 Compose UI 测试。
- Server：使用 `compileall` 做语法门禁；建议后续引入 pytest + FastAPI TestClient 覆盖 API 与数据库。
- 交付构建：`testDebugUnitTest`、`assembleDebug`、`lintDebug`。

## 12. 演进方向

1. 用 Paging 3 + Room RemoteMediator 替换手工分页与 JSON Feed 缓存。
2. 用 Alembic 管理数据库版本，引入后台任务队列刷新 RSS/LLM。
3. 将 AI 密钥和调用统一收口服务端，加入限流与成本统计。
4. 采用事件批量上传、幂等 event ID 和服务端口径校验。
5. 增加 Macrobenchmark、Baseline Profile、Compose UI 和 API 集成测试。
