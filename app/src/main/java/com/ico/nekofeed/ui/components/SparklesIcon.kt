package com.ico.nekofeed.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import com.ico.nekofeed.R

@Composable
fun SparklesIcon(
    size: Dp,
    modifier: Modifier = Modifier,
    monochrome: Boolean = false,
    tint: Color = Color.Unspecified
) {
    AsyncImage(
        model = if (monochrome) R.raw.sparkles_mono_bold else R.raw.sparkles_color,
        contentDescription = null,
        colorFilter = if (monochrome && tint != Color.Unspecified) {
            ColorFilter.tint(tint)
        } else {
            null
        },
        modifier = modifier.size(size)
    )
}
