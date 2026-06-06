package com.ico.nekofeed.ui.feed.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import coil.size.Size
import kotlinx.coroutines.delay

/**
 * Material 3 风格的骨架屏图片加载组件
 *
 * 特点：
 * 1. 加载时显示骨架屏脉冲动画
 * 2. 加载完成后平滑淡入
 * 3. 防抖机制，避免快速滑动时加载不可见图片
 * 4. 支持自定义尺寸和圆角
 */
@Composable
fun SkeletonImage(
    imageUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    height: Dp = 220.dp,
    cornerRadius: Dp = 0.dp,
    contentScale: ContentScale = ContentScale.Crop,
    loadDelayMs: Long = 100  // 防抖延迟
) {
    var isLoading by remember { mutableStateOf(true) }
    var isError by remember { mutableStateOf(false) }
    var shouldLoad by remember { mutableStateOf(false) }

    // 防抖：组件可见后延迟一小段时间再加载
    LaunchedEffect(imageUrl) {
        if (imageUrl != null) {
            shouldLoad = false
            delay(loadDelayMs)
            shouldLoad = true
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(cornerRadius))
    ) {
        // 骨架屏 - 在加载时显示
        if (isLoading) {
            SkeletonPulse(
                modifier = Modifier.fillMaxSize()
            )
        }

        // 实际图片 - 只在防抖后加载
        if (shouldLoad && imageUrl != null && !isError) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageUrl)
                    .size(Size.ORIGINAL)
                    .crossfade(300)  // 300ms 交叉淡入
                    .allowHardware(true)  // 硬件加速
                    .build(),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
                onState = { state ->
                    when (state) {
                        is AsyncImagePainter.State.Loading -> {
                            isLoading = true
                        }
                        is AsyncImagePainter.State.Success -> {
                            isLoading = false
                        }
                        is AsyncImagePainter.State.Error -> {
                            isLoading = false
                            isError = true
                        }
                        else -> {}
                    }
                }
            )
        } else if (isError || imageUrl == null) {
            // 错误或无图片时的占位符
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF3F8FC)),  // LightSurfaceVariant
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                androidx.compose.material3.Text(
                    text = "📷",
                    style = androidx.compose.material3.MaterialTheme.typography.displayMedium
                )
            }
        }
    }
}

/**
 * Material 3 骨架屏脉冲动画
 *
 * 实现从左上到右下的渐变脉冲效果
 */
@Composable
fun SkeletonPulse(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "skeleton")

    // 脉冲动画 - 从左上到右下移动
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -300f,
        targetValue = 1300f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1200,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    // 渐变色 - 符合 M3 风格的柔和灰色
    val shimmerColors = listOf(
        Color(0xFFE7EEF3),  // 浅灰
        Color(0xFFF5F7FA),  // 更浅灰（脉冲色）
        Color(0xFFE7EEF3)   // 浅灰
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(shimmerOffset - 200f, shimmerOffset - 200f),
        end = Offset(shimmerOffset + 200f, shimmerOffset + 200f)
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(brush)
    )
}

/**
 * 小图卡片专用的骨架屏
 */
@Composable
fun SmallSkeletonImage(
    imageUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    loadDelayMs: Long = 100
) {
    var isLoading by remember { mutableStateOf(true) }
    var isError by remember { mutableStateOf(false) }
    var shouldLoad by remember { mutableStateOf(false) }

    // 防抖
    LaunchedEffect(imageUrl) {
        if (imageUrl != null) {
            shouldLoad = false
            delay(loadDelayMs)
            shouldLoad = true
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
    ) {
        if (isLoading) {
            SkeletonPulse(
                modifier = Modifier.fillMaxSize()
            )
        }

        if (shouldLoad && imageUrl != null && !isError) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageUrl)
                    .size(Size.ORIGINAL)
                    .crossfade(300)
                    .allowHardware(true)
                    .build(),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onState = { state ->
                    when (state) {
                        is AsyncImagePainter.State.Loading -> isLoading = true
                        is AsyncImagePainter.State.Success -> isLoading = false
                        is AsyncImagePainter.State.Error -> {
                            isLoading = false
                            isError = true
                        }
                        else -> {}
                    }
                }
            )
        } else if (isError || imageUrl == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF3F8FC)),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                androidx.compose.material3.Text(
                    text = "📷",
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}
