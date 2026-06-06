package com.ico.nekofeed.data.repository

import com.ico.nekofeed.data.model.ItemInteraction
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class ItemInteractionUpdate(
    val itemId: String,
    val interaction: ItemInteraction
)

object InteractionSyncStore {
    private val _updates = MutableSharedFlow<ItemInteractionUpdate>(
        replay = 0,
        extraBufferCapacity = 32
    )
    val updates: SharedFlow<ItemInteractionUpdate> = _updates.asSharedFlow()

    fun publish(itemId: String, interaction: ItemInteraction) {
        _updates.tryEmit(ItemInteractionUpdate(itemId, interaction))
    }
}
