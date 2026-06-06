package com.ico.nekofeed.ui.feed.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
fun SkeletonImage(
    imageUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    height: Dp = 220.dp,
    cornerRadius: Dp = 0.dp,
    contentScale: ContentScale = ContentScale.Crop
) {
    ExpressiveAsyncImage(
        imageUrl = imageUrl,
        contentDescription = contentDescription,
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        cornerRadius = cornerRadius,
        contentScale = contentScale,
        initialScale = 0.97f
    )
}

@Composable
fun SmallSkeletonImage(
    imageUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    ExpressiveAsyncImage(
        imageUrl = imageUrl,
        contentDescription = contentDescription,
        modifier = modifier,
        cornerRadius = 12.dp,
        contentScale = ContentScale.Crop,
        initialScale = 0.94f
    )
}

@Composable
private fun ExpressiveAsyncImage(
    imageUrl: String?,
    contentDescription: String?,
    modifier: Modifier,
    cornerRadius: Dp,
    contentScale: ContentScale,
    initialScale: Float
) {
    var isLoaded by remember(imageUrl) { mutableStateOf(false) }
    val revealProgress by animateFloatAsState(
        targetValue = if (isLoaded) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "image_reveal"
    )
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f),
                    RoundedCornerShape(
                        topStart = cornerRadius,
                        topEnd = cornerRadius,
                        bottomEnd = cornerRadius + 28.dp,
                        bottomStart = cornerRadius
                    )
                )
        )

        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(imageUrl)
                .crossfade(false)
                .allowHardware(true)
                .build(),
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = revealProgress
                    val scale = initialScale + (1f - initialScale) * revealProgress
                    scaleX = scale
                    scaleY = scale
                },
            contentScale = contentScale,
            onSuccess = { isLoaded = true },
            onError = { isLoaded = false }
        )
    }
}
