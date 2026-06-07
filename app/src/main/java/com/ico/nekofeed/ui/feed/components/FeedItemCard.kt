package com.ico.nekofeed.ui.feed.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ico.nekofeed.data.model.FeedCardType
import com.ico.nekofeed.data.model.FeedItem
import com.ico.nekofeed.util.IntentUtils

@Composable
fun FeedItemCard(
    item: FeedItem,
    onClick: () -> Unit,
    onLikeClick: ((String) -> Unit)? = null,
    onCollectClick: ((String) -> Unit)? = null,
    onShareClick: ((String) -> Unit)? = null,
    onTagClick: ((String) -> Unit)? = null,
    isAiEnabled: Boolean = true,
    isPlaying: Boolean = false,
    onPlaybackStarted: (String) -> Unit = {},
    onMuteToggle: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cardType = FeedCardType.fromString(item.cardType)
    val shareItem: (String) -> Unit = { itemId ->
        onShareClick?.invoke(itemId)
        IntentUtils.shareContent(
            context = context,
            title = item.title,
            content = item.displaySummary,
            url = item.sourceUrl
        )
    }

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
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
                onShareClick = shareItem,
                onTagClick = onTagClick,
                isAiEnabled = isAiEnabled
            )
            FeedCardType.SMALL_IMAGE -> SmallImageFeedCard(
                item = item,
                onLikeClick = onLikeClick,
                onCollectClick = onCollectClick,
                onShareClick = shareItem,
                onTagClick = onTagClick,
                isAiEnabled = isAiEnabled
            )
            FeedCardType.VIDEO -> VideoFeedCard(
                item = item,
                onLikeClick = onLikeClick,
                onCollectClick = onCollectClick,
                onShareClick = shareItem,
                onTagClick = onTagClick,
                isAiEnabled = isAiEnabled,
                isPlaying = isPlaying,
                onPlaybackStarted = onPlaybackStarted,
                onMuteToggle = onMuteToggle
            )
            FeedCardType.PRODUCT -> ProductFeedCard(
                item = item,
                onLikeClick = onLikeClick,
                onCollectClick = onCollectClick,
                onShareClick = shareItem,
                onTagClick = onTagClick,
                isAiEnabled = isAiEnabled
            )
            FeedCardType.TEXT_ONLY -> SmallImageFeedCard(
                item = item,
                onLikeClick = onLikeClick,
                onCollectClick = onCollectClick,
                onShareClick = shareItem,
                onTagClick = onTagClick,
                isAiEnabled = isAiEnabled
            )
        }
    }
}
