package com.ico.nekofeed

import com.ico.nekofeed.data.local.FallbackFeedData
import com.ico.nekofeed.data.model.FeedCategory
import com.ico.nekofeed.ui.feed.shouldReloadFeedOnEnter
import com.ico.nekofeed.util.matchesCategory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedRulesTest {
    @Test
    fun videoCategoryMatchesVideoCard() {
        val video = FallbackFeedData.items.first { it.isVideo }
        assertTrue(video.matchesCategory(FeedCategory.VIDEO))
    }

    @Test
    fun shoppingCategoryMatchesProductsAndAds() {
        val product = FallbackFeedData.items.first { it.itemType == "product" }
        val article = FallbackFeedData.items.first { it.itemType == "article" }

        assertTrue(product.matchesCategory(FeedCategory.SHOPPING))
        assertFalse(article.matchesCategory(FeedCategory.SHOPPING))
    }

    @Test
    fun firstFeedEntryLoadsData() {
        assertTrue(
            shouldReloadFeedOnEnter(
                sourceChanged = false,
                hasLoadedItems = false
            )
        )
    }

    @Test
    fun sourceChangeReloadsExistingFeed() {
        assertTrue(
            shouldReloadFeedOnEnter(
                sourceChanged = true,
                hasLoadedItems = true
            )
        )
    }

    @Test
    fun returningFromDetailKeepsLoadedPages() {
        assertFalse(
            shouldReloadFeedOnEnter(
                sourceChanged = false,
                hasLoadedItems = true
            )
        )
    }
}
