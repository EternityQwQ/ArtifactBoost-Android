package com.artifactboost.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * 导出 / 分享已下载的文件。
 * 对应 iOS 的 ShareLink / UIActivityViewController：
 * 通过 FileProvider 暴露 URI，交给系统分享面板。
 */
fun shareFile(context: Context, file: File) {
    if (!file.exists()) return
    try {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeTypeFor(file.name)
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "导出 / 保存到文件").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    } catch (e: Exception) {
        android.util.Log.w("ArtifactBoost", "分享失败", e)
    }
}

private fun mimeTypeFor(name: String): String = when {
    name.endsWith(".zip") -> "application/zip"
    name.endsWith(".tar.gz") || name.endsWith(".tgz") -> "application/gzip"
    name.endsWith(".apk") -> "application/vnd.android.package-archive"
    name.endsWith(".ipa") -> "application/octet-stream"
    name.endsWith(".txt") || name.endsWith(".log") -> "text/plain"
    else -> "*/*"
}
