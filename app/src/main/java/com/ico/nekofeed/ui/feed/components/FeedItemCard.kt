package com.ico.nekofeed.ui.feed.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.ico.nekofeed.data.model.FeedCardType
import com.ico.nekofeed.data.model.FeedItem

@Composable
fun FeedItemCard(
    item: FeedItem,
    onClick: () -> Unit,
    onLikeClick: ((String) -> Unit)? = null,
    onCollectClick: ((String) -> Unit)? = null,
    onShareClick: ((String) -> Unit)? = null,
    onTagClick: ((String) -> Unit)? = null,
    isAiEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "card_scale"
    )

    val cardType = FeedCardType.fromString(item.cardType)

    Card(
        onClick = {
            isPressed = true
            onClick()
        },
        modifier = modifier
            .fillMaxWidth()
            .scale(scale),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 4.dp
        )
    ) {
        when (cardType) {
            FeedCardType.LARGE_IMAGE -> LargeImageFeedCard(
                item = item,
                onLikeClick = onLikeClick,
                onCollectClick = onCollectClick,
                onShareClick = onShareClick,
                onTagClick = onTagClick,
                isAiEnabled = isAiEnabled
            )
            FeedCardType.SMALL_IMAGE -> SmallImageFeedCard(
                item = item,
                onLikeClick = onLikeClick,
                onCollectClick = onCollectClick,
                onTagClick = onTagClick,
                isAiEnabled = isAiEnabled
            )
            FeedCardType.VIDEO -> VideoFeedCard(
                item = item,
                onLikeClick = onLikeClick,
                onCollectClick = onCollectClick,
                onShareClick = onShareClick,
                onTagClick = onTagClick,
                isAiEnabled = isAiEnabled
            )
            FeedCardType.PRODUCT -> ProductFeedCard(
                item = item,
                onLikeClick = onLikeClick,
                onCollectClick = onCollectClick,
                onShareClick = onShareClick,
                onTagClick = onTagClick,
                isAiEnabled = isAiEnabled
            )
            FeedCardType.TEXT_ONLY -> SmallImageFeedCard(
                item = item,
                onLikeClick = onLikeClick,
                onCollectClick = onCollectClick,
                onTagClick = onTagClick,
                isAiEnabled = isAiEnabled
            )
        }
    }
}
