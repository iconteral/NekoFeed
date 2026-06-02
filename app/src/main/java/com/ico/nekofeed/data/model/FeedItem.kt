package com.ico.nekofeed.data.model

import com.google.gson.annotations.SerializedName

data class FeedItem(
    val id: String,

    // 基础内容
    val title: String,
    val summary: String?,
    val content: String?,

    // 来源信息
    @SerializedName("source_name")
    val sourceName: String?,
    @SerializedName("source_url")
    val sourceUrl: String?,

    // 分类与类型
    val category: String?,
    @SerializedName("item_type")
    val itemType: String?,
    @SerializedName("card_type")
    val cardType: String?,

    // 媒体资源
    @SerializedName("image_url")
    val imageUrl: String?,
    @SerializedName("media_url")
    val mediaUrl: String?,

    // 原始标签（来自服务器）
    val tags: List<String> = emptyList(),

    // AI 结果
    @SerializedName("ai_summary")
    val aiSummary: String? = null,
    @SerializedName("ai_tags")
    val aiTags: List<String> = emptyList(),
    @SerializedName("ai_reason")
    val aiReason: String? = null,

    // 广告相关字段
    val brand: String? = null,
    @SerializedName("cta_text")
    val ctaText: String? = null,
    @SerializedName("price_text")
    val priceText: String? = null,
    @SerializedName("is_sponsored")
    val isSponsored: Boolean = false,

    // 互动状态
    @SerializedName("is_liked")
    var isLiked: Boolean = false,
    @SerializedName("is_collected")
    var isCollected: Boolean = false,
    @SerializedName("like_count")
    var likeCount: Int = 0,
    @SerializedName("collect_count")
    var collectCount: Int = 0,
    @SerializedName("share_count")
    var shareCount: Int = 0,

    // 统计状态
    @SerializedName("exposure_count")
    var exposureCount: Int = 0,
    @SerializedName("click_count")
    var clickCount: Int = 0,
    @SerializedName("play_count")
    var playCount: Int = 0,

    // 时间
    @SerializedName("published_at")
    val publishedAt: String?,
    @SerializedName("created_at")
    val createdAt: String? = null
) {
    // 便捷属性：获取显示用的摘要（优先AI摘要，其次原始摘要）
    val displaySummary: String
        get() = aiSummary ?: summary ?: ""

    // 便捷属性：获取显示用的标签（优先AI标签，其次原始标签）
    val displayTags: List<String>
        get() = when {
            !aiTags.isNullOrEmpty() -> aiTags
            !tags.isNullOrEmpty() -> tags
            else -> emptyList()
        }

    // 便捷属性：判断是否为视频类型
    val isVideo: Boolean
        get() = itemType == "video" || cardType == "video"

    // 便捷属性：判断是否为广告类型
    val isAd: Boolean
        get() = itemType == "ad" || itemType == "product" || isSponsored

    // 便捷属性：获取品牌显示文本
    val brandDisplay: String
        get() = if (isSponsored) "${brand ?: "未知"} · 赞助" else (brand ?: "")
}

// FeedCardType 枚举
enum class FeedCardType(val value: String) {
    LARGE_IMAGE("large_image"),
    SMALL_IMAGE("small_image"),
    VIDEO("video"),
    TEXT_ONLY("text_only"),
    PRODUCT("product");

    companion object {
        fun fromString(value: String?): FeedCardType {
            return entries.find { it.value == value } ?: LARGE_IMAGE
        }
    }
}

// FeedItemType 枚举
enum class FeedItemType(val value: String) {
    ARTICLE("article"),
    VIDEO("video"),
    AD("ad"),
    PRODUCT("product"),
    LOCAL("local");

    companion object {
        fun fromString(value: String?): FeedItemType {
            return entries.find { it.value == value } ?: ARTICLE
        }
    }
}

// FeedCategory 枚举
enum class FeedCategory(val value: String, val displayName: String) {
    FEATURED("featured", "精选"),
    TECH("tech", "科技"),
    AI("ai", "AI"),
    BUSINESS("business", "商业"),
    LOCAL("local", "本地"),
    VIDEO("video", "视频"),
    SHOPPING("shopping", "电商");

    companion object {
        fun fromString(value: String?): FeedCategory {
            return entries.find { it.value == value } ?: FEATURED
        }
    }
}
