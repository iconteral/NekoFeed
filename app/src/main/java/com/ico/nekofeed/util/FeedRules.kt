package com.ico.nekofeed.util

import com.ico.nekofeed.data.model.FeedCategory
import com.ico.nekofeed.data.model.FeedItem

// ============================================================================
// 【工具层 · 扩展函数（Extension Function）】
// ============================================================================
//
// 📌 扩展函数是 Kotlin 最强大的特性之一：
//    可以给已有的类"添加新方法"，而不需要继承或修改源码。
//
// 📌 语法：fun 类名.函数名(参数): 返回类型 { ... }
//
//    例：给 String 添加一个重复方法
//    fun String.repeat(n: Int): String = this.repeat(n)
//    "abc".repeat(3) → "abcabcabc"
//
// 📌 本文件给 FeedItem 添加了 matchesCategory() 方法：
//    feedItem.matchesCategory(FeedCategory.TECH) → true/false
//
// 📌 when 表达式（比 Java 的 switch 更强大）：
//    - 可以匹配枚举、字符串、数字、类型等
//    - 可以用任意表达式作为分支条件
//    - 是表达式（有返回值），不是语句
//    - 必须覆盖所有可能的值（或用 else 分支）
// ====================================================================

/**
 * 判断 FeedItem 是否属于指定分类
 *
 * 这是一个扩展函数：给 FeedItem 类添加了一个新方法 matchesCategory()。
 * 在 Composable 中可以直接调用：item.matchesCategory(FeedCategory.TECH)
 *
 * 🔑 匹配规则：
 *    - FEATURED（精选）: 所有内容都属于精选（返回 true）
 *    - VIDEO（视频）: itemType 或 cardType 是视频
 *    - SHOPPING（电商）: 分类是电商，或者是商品/广告类型
 *    - 其他: 按分类或 itemType 精确匹配
 */
fun FeedItem.matchesCategory(category: FeedCategory): Boolean {
    return when (category) {
        FeedCategory.FEATURED -> true  // 精选 = 全部
        FeedCategory.VIDEO -> isVideo || this.category == category.value
        FeedCategory.SHOPPING ->
            this.category == category.value || itemType == "product" || itemType == "ad"
        else -> this.category == category.value || itemType == category.value
    }
}
