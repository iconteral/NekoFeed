# NekoFeed 需求实现差距分析

基于 [原需求.md](file:///L:/NekoFeed/Docs/原需求.md) 和 [Design.md](file:///L:/NekoFeed/Docs/Design.md) 对照当前代码进行审计。

---

## 总览

| 分类 | 总计 | ✅ 已完成 | ⚠️ 部分完成 | ❌ 未实现 |
|------|------|----------|------------|----------|
| MVP 必须完成 | 9 | 7 | 1 | 1 |
| 建议完成 | 6 | 2 | 1 | 3 |
| 架构设计 | 5 | 2 | 1 | 2 |
| 已发现的 Bug | 3 | 3 (已修) | — | — |

---

## 一、MVP 必须完成（§20 清单）

### ✅ 已完成

| # | 需求 | 实现情况 |
|---|------|---------|
| 1 | 从 Feed Server 请求 `/api/feed` | [FeedApi.kt](file:///L:/NekoFeed/app/src/main/java/com/ico/nekofeed/data/remote/FolderApi.kt) + [FeedRepository.kt](file:///L:/NekoFeed/app/src/main/java/com/ico/nekofeed/data/repository/FolderRepository.kt) |
| 2 | 统一 `FeedItem` 数据模型 | [FeedItem.kt](file:///L:/NekoFeed/app/src/main/java/com/ico/nekofeed/data/model/FeedItem.kt) 包含所有字段 |
| 3 | FeedScreen 单列混合信息流 | [FolderScreen.kt](file:///L:/NekoFeed/app/src/main/java/com/ico/nekofeed/ui/feed/FolderScreen.kt) LazyColumn 实现 |
| 4 | 大图/小图/视频/商品卡片 | [components/](file:///L:/NekoFeed/app/src/main/java/com/ico/nekofeed/ui/feed/components) 下有 5 种卡片 |
| 5 | 详情页 | [FolderDetailScreen.kt](file:///L:/NekoFeed/app/src/main/java/com/ico/nekofeed/ui/detail/FolderDetailScreen.kt) |
| 6 | 点赞/收藏/分享 | ViewModel + Server API 均实现 |
| 8 | 下拉刷新 + 上拉加载 | PullToRefreshBox + 无限滚动（本次新增） |
| 9 | 本地 mock 数据兜底 | [FallbackFeedData.kt](file:///L:/NekoFeed/app/src/main/java/com/ico/nekofeed/data/local/FallbackFeedData.kt) |

### ⚠️ 部分完成

| # | 需求 | 差距 |
|---|------|------|
| 7 | **曝光/点击统计** | 统计字段存在于模型中、详情页和 StatsScreen 可展示，但 **缺少真正的曝光埋点**（Feed 列表中 item 可见时没有上报曝光；点击进入详情时没有递增 `clickCount`）。目前数据全是服务端写死的初始值或 fallback 假数据 |

### ❌ 未实现

| # | 需求 | 说明 |
|---|------|------|
| — | **真实曝光上报** | 列表滚动时应用 `LaunchedEffect` + 可见性检测，当 item 进入可视区域时调 API 上报。这是"埋点统计"的核心，**当前完全缺失** |

---

## 二、建议完成（§20 清单）

| # | 需求 | 状态 | 说明 |
|---|------|------|------|
| 1 | AI 摘要和标签 | ✅ | [AiRepository.kt](file:///L:/NekoFeed/app/src/main/java/com/ico/nekofeed/data/repository/AiRepository.kt) + Room 缓存 |
| 2 | 标签过滤 | ✅ | `filterByTag()` 已实现 |
| 3 | 对话式搜索 | ⚠️ | [SearchScreen.kt](file:///L:/NekoFeed/app/src/main/java/com/ico/nekofeed/ui/search/SearchScreen.kt) 存在，但搜索是**本地关键词匹配**，未实现需求中的 **AI 解析自然语言 query → 结构化查询** |
| 4 | **视频播放复用** | ❌ | **完全缺失**。无 `PlayerManager`，无 Media3/ExoPlayer 依赖。VideoFeedCard 只展示封面图和播放按钮 UI，**不能真正播放视频** |
| 5 | StatsScreen 统计可视化 | ✅ | [StatsScreen.kt](file:///L:/NekoFeed/app/src/main/java/com/ico/nekofeed/ui/stats/StatsScreen.kt)（但数据来源是假数据） |
| 6 | Feed Server 管理页面 | ❌ | 服务器有 admin 路由，但 App 端**无管理入口** |

---

## 三、架构设计差距（§10、§15）

| 设计要求 | 状态 | 现状 |
|---------|------|------|
| `FeedRepository` | ✅ | 已实现 |
| `AiRepository` | ✅ | 已实现 |
| `InteractionRepository` | ❌ | **不存在**。点赞/收藏逻辑耦合在 ViewModel 中，没有独立的互动状态层。需求要求"跨页面同步互动状态"，目前详情页的互动**不会同步回列表** |
| `AnalyticsRepository` | ❌ | **不存在**。曝光/点击统计逻辑没有独立 Repository，统计数据来自 FeedItem 的静态字段 |
| `PlayerManager` | ❌ | **不存在**。无 Media3 集成 |

---

## 四、详情页差距（§9）

| 需求 | 状态 | 说明 |
|------|------|------|
| ARTICLE 详情：标题/摘要/来源/时间 + AI 摘要 + 查看原文 | ⚠️ | 有来源展示，但 **sourceUrl 没有做成可点击的链接**（无 `uriHandler.openUri`），"查看原文"按钮缺失 |
| AD 详情：品牌/广告图/CTA + 点击计数 | ⚠️ | CTA 按钮存在但 `onClick` 是空的 `{ /* 处理CTA点击 */ }`；**进入详情页时不会递增 clickCount** |
| VIDEO 详情：顶部视频播放器 | ❌ | 只展示封面图，**无实际播放器**。需求要求播放/暂停/静音 |
| PRODUCT 详情：商品图/价格/CTA | ⚠️ | 有展示，但 CTA 同样空实现 |

---

## 五、已发现并修复的 Bug

> [!NOTE]
> 以下 bug 已在本次会话中修复。

| Bug | 根因 | 修复 |
|-----|------|------|
| 登录后 `getMe()` 401 | 服务端 JWT `sub` 是 int，python-jose 要求 string | `str(user.id)` |
| 重启 App 自动登录 401 | `TokenManager.getToken()` 用 `collect` 永不返回 | 改用 `first()` |
| Token 线程可见性 | `cachedToken` 是 `var`，OkHttp 线程可能看不到 | 改用 `AtomicReference` |

---

## 六、优先级建议

### 🔴 P0 — 影响核心功能演示

1. **曝光/点击埋点** — 训练营题目**明确要求**，目前只有壳没有实际上报
2. **视频播放** — 需求核心亮点之一，VideoFeedCard 目前不能播放
3. **详情页互动同步** — 详情页点赞后返回列表状态不同步

### 🟡 P1 — 影响完整性

4. **CTA 按钮空实现** — 商品/广告详情页的 CTA 没有实际跳转
5. **查看原文** — ARTICLE 详情页应打开 sourceUrl
6. **搜索 AI 化** — 对话式搜索目前只是关键词匹配，未接 LLM

### 🟢 P2 — 锦上添花

7. `InteractionRepository` 独立抽取
8. `AnalyticsRepository` 独立抽取
9. Feed Server 管理页面
