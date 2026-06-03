package com.ico.nekofeed.ui.feed.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ico.nekofeed.data.model.FeedItem

@Composable
fun FeedItemCard(
    item: FeedItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        when (item.cardType) {
            "small_image" -> SmallImageCard(item)
            else -> LargeImageCard(item)
        }
    }
}

@Composable
private fun LargeImageCard(item: FeedItem) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        ImageSection(item)
        ContentSection(item)
    }
}

@Composable
private fun SmallImageCard(item: FeedItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            TitleAndBadge(item)
            item.summary?.let { summary ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            SourceAndTags(item)
        }
        Spacer(modifier = Modifier.width(12.dp))
        item.imageUrl?.let { imageUrl ->
            AsyncImage(
                model = imageUrl,
                contentDescription = item.title,
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
private fun ImageSection(item: FeedItem) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (item.imageUrl != null) {
            AsyncImage(
                model = item.imageUrl,
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = "No Image",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (item.itemType == "video" || item.cardType == "video") {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        Color.Black.copy(alpha = 0.6f),
                        RoundedCornerShape(24.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "▶",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }
    }
}

@Composable
private fun ContentSection(item: FeedItem) {
    Column(
        modifier = Modifier.padding(12.dp)
    ) {
        TitleAndBadge(item)
        item.summary?.let { summary ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        SourceAndTags(item)
    }
}

@Composable
private fun TitleAndBadge(item: FeedItem) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (item.itemType == "ad" || item.itemType == "product") {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Sponsored",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun SourceAndTags(item: FeedItem) {
    Column {
        item.sourceName?.let { source ->
            Text(
                text = source,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!item.tags.isNullOrEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.tags.joinToString(" ") { "#$it" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
