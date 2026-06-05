package com.ico.nekofeed.ui.chat

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * Lightweight Markdown text renderer for chat bubbles.
 * Supports: **bold**, numbered lists, bullet lists (- / *), inline `code`.
 * No external library needed.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge
) {
    val annotatedString = parseMarkdown(text, color)
    SelectionContainer {
        Text(
            text = annotatedString,
            modifier = modifier,
            style = style,
            color = color
        )
    }
}

/**
 * Parse markdown text into AnnotatedString.
 * Handles:
 * - **bold** text
 * - Numbered lists (1. 2. 3.)
 * - Bullet lists (- or *)
 * - Inline `code`
 */
private fun parseMarkdown(text: String, defaultColor: Color): AnnotatedString {
    return buildAnnotatedString {
        val lines = text.split("\n")
        lines.forEachIndexed { lineIndex, line ->
            val trimmedLine = line.trimStart()

            // Check if it's a list item
            val bulletMatch = Regex("""^(\d+\.\s+|[-*]\s+)(.*)""").find(trimmedLine)
            if (bulletMatch != null) {
                val prefix = bulletMatch.groupValues[1]
                val content = bulletMatch.groupValues[2]
                // Indent for readability
                append("  ")
                // Bullet/number prefix
                withStyle(SpanStyle(fontWeight = FontWeight.Medium)) {
                    append(prefix.replace(Regex("""\d+\."""), "•"))
                }
                // Parse inline formatting for the content
                appendInlineFormatted(content)
            } else {
                // Regular line — parse inline formatting
                appendInlineFormatted(line)
            }

            // Add newline between lines (not after last)
            if (lineIndex < lines.size - 1) {
                append("\n")
            }
        }
    }
}

/**
 * Parse inline markdown formatting: **bold** and `code`
 */
private fun AnnotatedString.Builder.appendInlineFormatted(text: String) {
    var i = 0
    while (i < text.length) {
        when {
            // Bold: **text**
            text.startsWith("**", i) -> {
                val endIndex = text.indexOf("**", i + 2)
                if (endIndex != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, endIndex))
                    }
                    i = endIndex + 2
                } else {
                    append("**")
                    i += 2
                }
            }
            // Inline code: `text`
            text[i] == '`' -> {
                val endIndex = text.indexOf('`', i + 1)
                if (endIndex != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Medium)) {
                        append(text.substring(i + 1, endIndex))
                    }
                    i = endIndex + 1
                } else {
                    append('`')
                    i++
                }
            }
            else -> {
                append(text[i])
                i++
            }
        }
    }
}
