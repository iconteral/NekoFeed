package com.ico.nekofeed

import com.ico.nekofeed.data.local.FallbackFeedData
import com.ico.nekofeed.data.model.ItemInteraction
import com.ico.nekofeed.ui.interaction.InteractionType
import com.ico.nekofeed.ui.interaction.applyInteractionToList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InteractionRulesTest {
    @Test
    fun unlikedItemIsRemovedFromLikesList() {
        val item = FallbackFeedData.items.first().copy(isLiked = true)
        val result = applyInteractionToList(
            items = listOf(item),
            itemId = item.id,
            interaction = ItemInteraction(isLiked = false),
            type = InteractionType.LIKES
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun collectionUpdateUsesAuthoritativeCounts() {
        val item = FallbackFeedData.items.first()
        val result = applyInteractionToList(
            items = listOf(item),
            itemId = item.id,
            interaction = ItemInteraction(
                isCollected = true,
                collectCount = 7
            ),
            type = InteractionType.COLLECTIONS
        )

        assertEquals(1, result.size)
        assertEquals(7, result.single().collectCount)
    }
}
