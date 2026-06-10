package com.ico.nekofeed.data.model

import androidx.compose.runtime.Immutable
import com.google.gson.annotations.SerializedName

// ============================================================================
// 【第1站 · 核心数据模型】
// ============================================================================
//
// 📌 这个文件是整个项目的数据基石，所有页面（首页、详情、搜索、统计……）
//    都围绕 FeedItem 来渲染。理解了它，就理解了一半项目。
//
// 📌 Kotlin 初学者知识点速查：
//    1. data class    → 自动生成 equals/hashCode/copy/toString 等方法
//    2. @Immutable    → 告诉 Compose "这个对象创建后不会变"，帮助 Compose 跳过不必要的重组
//    3. @SerializedName → Gson 序列化注解：把 JSON 的 snake_case 映射到 Kotlin 的 camelCase
//       例：JSON 里 "source_name" → Kotlin 里 sourceName
//    4. val displaySummary: String get() = ... → 计算属性（不存储，每次访问时实时算）
//    5. enum class    → 枚举类，用有限的命名常量替代魔法字符串
// ============================================================================

/**
 * FeedItem —— 信息流中的"一条内容"
 *
 * 它可以是：文章、视频、广告、商品、本地生活资讯。
 * 服务端把所有类型统一成这个结构返回给客户端（这就是"归一化"）。
 *
 * 🔑 关键字段分组：
 *    - 基础内容：title / summary / content（标题、摘要、正文）
 *    - 来源：sourceName / sourceUrl（RSS 源名、原文链接）
 *    - 分类：category(频道) / itemType(内容类型) / cardType(卡片样式)
 *    - 媒体：imageUrl(封面图) / mediaUrl(视频地址)
 *    - AI：aiSummary / aiTags / aiReason（LLM 生成的摘要、标签、推荐理由）
 *    - 广告：brand / ctaText / priceText / isSponsored
 *    - 互动：isLiked / isCollected / likeCount / collectCount / shareCount
 *    - 统计：exposureCount / clickCount / playCount（曝光/点击/播放次数）
 */
@Immutable // ← Compose 优化注解：标记为不可变对象，Compose 可以安全地跳过未变化的重组
data class FeedItem(
    val id: String,          // 唯一标识，用于去重和路由跳转

    // ── 基础内容 ──────────────────────────────────────────────────────
    val title: String,       // 标题（必填）
    val summary: String?,    // 原始摘要（可空，? 表示 nullable）
    val content: String?,    // 正文（可空，列表页一般不展示）

    // ── 来源信息 ──────────────────────────────────────────────────────
    // @SerializedName 把 JSON 字段名映射到 Kotlin 属性名
    // 服务端返回 "source_name"，Kotlin 里用 sourceName（驼峰命名）
    @SerializedName("source_name")
    val sourceName: String?, // 来源名称，如 "36氪"、"虎嗅"
    @SerializedName("source_url")
    val sourceUrl: String?,  // 原文链接

    // ── 分类与类型 ────────────────────────────────────────────────────
    // category: 频道分类（精选/科技/本地/视频/电商）
    // itemType: 内容类型（article/video/ad/product/local）
    // cardType: 卡片样式（large_image/small_image/video/text_only/product）
    val category: String?,
    @SerializedName("item_type")
    val itemType: String?,
    @SerializedName("card_type")
    val cardType: String?,

    // ── 媒体资源 ──────────────────────────────────────────────────────
    @SerializedName("image_url")
    val imageUrl: String?,   // 封面图 URL
    @SerializedName("media_url")
    val mediaUrl: String?,   // 视频/音频 URL

    // ── 原始标签（来自服务器）──────────────────────────────────────────
    // tags 是一个字符串列表，用于筛选和展示，如 ["科技", "AI", "手机"]
    val tags: List<String>? = emptyList(),

    // ── AI 结果（LLM 生成）───────────────────────────────────────────
    // 这些字段可能一开始为 null，等 AI 分析完成后才填充
    @SerializedName("ai_summary")
    val aiSummary: String? = null,     // AI 生成的摘要（比原始摘要更精炼）
    @SerializedName("ai_tags")
    val aiTags: List<String>? = emptyList(), // AI 生成的标签
    @SerializedName("ai_reason")
    val aiReason: String? = null,      // AI 推荐理由

    // ── 广告相关字段 ──────────────────────────────────────────────────
    val brand: String? = null,         // 品牌名
    @SerializedName("cta_text")
    val ctaText: String? = null,       // 行动按钮文字，如 "立即购买"
    @SerializedName("price_text")
    val priceText: String? = null,     // 价格文字，如 "¥99"
    @SerializedName("is_sponsored")
    val isSponsored: Boolean = false,  // 是否为赞助内容

    // ── 互动状态（来自服务端）─────────────────────────────────────────
    @SerializedName("is_liked")
    val isLiked: Boolean = false,      // 当前用户是否已点赞
    @SerializedName("is_collected")
    val isCollected: Boolean = false,  // 当前用户是否已收藏
    @SerializedName("like_count")
    val likeCount: Int = 0,            // 点赞总数
    @SerializedName("collect_count")
    val collectCount: Int = 0,         // 收藏总数
    @SerializedName("share_count")
    val shareCount: Int = 0,           // 分享总数

    // ── 统计状态 ──────────────────────────────────────────────────────
    @SerializedName("exposure_count")
    val exposureCount: Int = 0,        // 曝光次数
    @SerializedName("click_count")
    val clickCount: Int = 0,           // 点击次数
    @SerializedName("play_count")
    val playCount: Int = 0,            // 播放次数（视频）

    // ── 时间 ─────────────────────────────────────────────────────────
    @SerializedName("published_at")
    val publishedAt: String?,          // 发布时间
    @SerializedName("created_at")
    val createdAt: String? = null,     // 创建时间
    val isAiLoading: Boolean = false   // AI 是否正在分析中（用于 UI 显示 loading）
) {
    // ====================================================================
    // 计算属性（Computed Properties）
    // ====================================================================
    // Kotlin 的 "get() = ..." 语法：每次访问时实时计算，不占用存储空间
    // 这些属性让 UI 层不用自己判断优先级逻辑

    /** 显示用的摘要：优先 AI 摘要 → 其次原始摘要 → 空字符串 */
    val displaySummary: String
        get() = aiSummary ?: summary ?: ""  // ?: 是 Elvis 操作符，左边为 null 就用右边

    /** 显示用的标签：优先 AI 标签 → 其次原始标签 */
    val displayTags: List<String>
        get() = if (!aiTags.isNullOrEmpty()) aiTags else tags.orEmpty()

    /** 是否为视频类型 */
    val isVideo: Boolean
        get() = itemType == "video" || cardType == "video"

    /** 是否为广告类型（广告 / 商品 / 赞助内容） */
    val isAd: Boolean
        get() = itemType == "ad" || itemType == "product" || isSponsored

    /** 品牌显示文本：赞助内容会追加 "· 赞助" 后缀 */
    val brandDisplay: String
        get() = if (isSponsored) "${brand ?: "未知"} · 赞助" else (brand ?: "")
}

// ============================================================================
// 枚举类（Enum Classes）
// ============================================================================
// 📌 Kotlin 枚举比 Java 更灵活，可以有属性和方法。
//    companion object 里的 fromString() 是安全的工厂方法：
//    遇到未知值时返回默认值，而不是崩溃。

/**
 * 卡片类型 —— 决定这条内容在列表里"长什么样"
 *
 * 大图卡片 / 小图卡片 / 视频卡片 / 纯文字卡片 / 商品卡片
 */
enum class FeedCardType(val value: String) {
    LARGE_IMAGE("large_image"),   // 大图卡片（一张大图 + 标题 + 摘要）
    SMALL_IMAGE("small_image"),   // 小图卡片（左侧文字 + 右侧小图）
    VIDEO("video"),               // 视频卡片（带播放按钮和进度条）
    TEXT_ONLY("text_only"),       // 纯文字卡片（无图）
    PRODUCT("product");           // 商品卡片（带价格和购买按钮）

    companion object {
        /**
         * 从字符串安全转换为枚举，未知值返回 LARGE_IMAGE（兜底策略）
         */
        fun fromString(value: String?): FeedCardType {
            return entries.find { it.value == value } ?: LARGE_IMAGE
        }
    }
}

/**
 * 内容类型 —— 这条内容"是什么"
 *
 * 文章 / 视频 / 广告 / 商品 / 本地生活
 */
enum class FeedItemType(val value: String) {
    ARTICLE("article"),   // 普通文章
    VIDEO("video"),       // 视频内容
    AD("ad"),             // 广告
    PRODUCT("product"),   // 商品
    LOCAL("local");       // 本地生活资讯

    companion object {
        fun fromString(value: String?): FeedItemType {
            return entries.find { it.value == value } ?: ARTICLE
        }
    }
}

/**
 * 频道分类 —— 顶部 Tab 切换用
 *
 * 精选 / 科技 / 本地 / 视频 / 电商
 * displayName 是中文名，直接显示在 UI 上
 */
enum class FeedCategory(val value: String, val displayName: String) {
    FEATURED("featured", "精选"),
    TECH("tech", "科技"),
    LOCAL("local", "本地"),
    VIDEO("video", "视频"),
    SHOPPING("shopping", "电商");

    companion object {
        fun fromString(value: String?): FeedCategory {
            return entries.find { it.value == value } ?: FEATURED
        }
    }
}
