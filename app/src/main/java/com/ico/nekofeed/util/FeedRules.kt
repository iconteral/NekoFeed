package com.ico.nekofeed.util

import com.ico.nekofeed.data.model.FeedCategory
import com.ico.nekofeed.data.model.FeedItem

// ============================================================================
// 【工具层 · 扩展函数 & 分类匹配规则】
// ============================================================================
//
// 📌 Kotlin 扩展函数（Extension Function）是 Kotlin 最强大的特性之一。
//    它允许你"给已有的类添加新方法"，而不需要继承或修改源码。
//
// 📌 语法：
//    fun 接收者类型.函数名(参数): 返回类型 {
//        // this 指向接收者对象
//    }
//
// 📌 示例：
//    fun String.isEmail(): Boolean = this.contains("@")
//    用法："hello@test.com".isEmail() → true
//
// 📌 本文件给 FeedItem 添加了 matchesCategory() 扩展函数，
//    用于判断一条 Feed 是否属于指定的频道分类。
//    这个逻辑被提取为独立函数，方便单元测试（FeedRulesTest.kt）。
// ====================================================================

/**
 * 判断 FeedItem 是否匹配指定的频道分类
 *
 * 这是 FeedItem 的扩展函数，调用时 this 就是 FeedItem 实例。
 *
 * 🔑 匹配规则：
 *    - 精选：所有内容都匹配（返回 true）
 *    - 视频：isVideo 或 category == "video"
 *    - 电商：category == "shopping" 或 itemType 是 product/ad
 *    - 其他：category 或 itemType 匹配
 *
 * 🔑 when 表达式：
 *    Kotlin 的 when 比 Java 的 switch 更强大：
 *    - 可以匹配枚举、字符串、范围、类型等
 *    - 可以有返回值（作为表达式使用）
 *    - else 分支相当于 default
 */
fun FeedItem.matchesCategory(category: FeedCategory): Boolean {
    return when (category) {
        FeedCategory.FEATURED -> true  // 精选：全部显示
        FeedCategory.VIDEO -> isVideo || this.category == category.value
        FeedCategory.SHOPPING ->
            this.category == category.value || itemType == "product" || itemType == "ad"
        else -> this.category == category.value || itemType == category.value
    }
}
