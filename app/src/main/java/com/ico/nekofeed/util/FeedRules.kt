package com.ico.nekofeed.util

import com.ico.nekofeed.data.model.FeedCategory
import com.ico.nekofeed.data.model.FeedItem

fun FeedItem.matchesCategory(category: FeedCategory): Boolean {
    return when (category) {
        FeedCategory.FEATURED -> true
        FeedCategory.VIDEO -> isVideo || this.category == category.value
        FeedCategory.SHOPPING ->
            this.category == category.value || itemType == "product" || itemType == "ad"
        else -> this.category == category.value || itemType == category.value
    }
}
