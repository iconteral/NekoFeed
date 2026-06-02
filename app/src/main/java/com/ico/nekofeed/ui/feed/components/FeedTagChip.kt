package com.ico.nekofeed.ui.feed.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun FeedTagChip(
    tag: String,
    onClick: () -> Unit,
    small: Boolean = false,
    modifier: Modifier = Modifier
) {
    AssistChip(
        onClick = onClick,
        label = {
            Text(
                text = tag,
                style = if (small) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium
            )
        },
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f),
            labelColor = MaterialTheme.colorScheme.primary
        ),
        border = null
    )
}
