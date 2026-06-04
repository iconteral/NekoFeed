package com.ico.nekofeed.util

import android.content.Context
import android.content.Intent
import android.net.Uri

object IntentUtils {
    fun shareContent(context: Context, title: String, content: String, url: String?) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            val shareText = buildString {
                appendLine(title)
                if (content.isNotBlank()) appendLine(content.take(100) + "...")
                if (!url.isNullOrBlank()) appendLine(url)
            }
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_TITLE, title)
        }
        context.startActivity(Intent.createChooser(shareIntent, "分享到..."))
    }

    fun openUrl(context: Context, url: String?) {
        if (url.isNullOrBlank()) return
        try {
            val uri = Uri.parse(url)
            val intent = Intent(Intent.ACTION_VIEW, uri)
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
