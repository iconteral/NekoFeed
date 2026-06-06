package com.ico.nekofeed.ui.interaction

import com.ico.nekofeed.data.model.FeedItem
import com.ico.nekofeed.data.model.ItemInteraction

fun applyInteractionToList(
    items: List<FeedItem>,
    itemId: String,
    interaction: ItemInteraction,
    type: InteractionType?
): List<FeedItem> {
    return items.map { item ->
        if (item.id == itemId) {
            item.copy(
                isLiked = interaction.isLiked,
                isCollected = interaction.isCollected,
                likeCount = interaction.likeCount,
                collectCount = interaction.collectCount
            )
        } else {
            item
        }
    }.filter { item ->
        when (type) {
            InteractionType.LIKES -> item.isLiked
            InteractionType.COLLECTIONS -> item.isCollected
            else -> true
        }
    }
}
