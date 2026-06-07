package com.ico.nekofeed.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object IntentUtils {
    fun shareContent(context: Context, title: String, content: String, url: String?) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            val shareText = buildString {
                appendLine(title)
                if (content.isNotBlank()) appendLine(content.take(100))
                if (!url.isNullOrBlank()) appendLine(url)
            }.trim()
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_TITLE, title)
        }
        val chooser = Intent.createChooser(shareIntent, "分享到...").apply {
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(chooser)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "未找到可用的分享应用", Toast.LENGTH_SHORT).show()
        }
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
