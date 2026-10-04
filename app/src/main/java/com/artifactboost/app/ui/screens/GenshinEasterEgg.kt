package com.artifactboost.app.ui.screens

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * 原神彩蛋（中国大陆官网）。
 *
 * 约定：只做“二次确认后用系统浏览器打开官网”，不在后台静默下载任何安装包。
 * 原因：原神包体几十 GB，静默下载会消耗流量/存储，且应用商店对后台下载 APK 很敏感。
 */
const val GENSHIN_CN_URL = "https://ys.mihoyo.com/"

@Composable
fun GenshinEasterEggDialog(
    onDismiss: () -> Unit,
    onOpenOfficialSite: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发现彩蛋 🎉") },
        text = {
            Text(
                "长按「设置」发现了提瓦特大陆的入口。\n\n" +
                    "点击「前往官网」将用浏览器打开原神中国大陆官网（$GENSHIN_CN_URL），" +
                    "是否前往由你决定，App 不会在后台自动下载任何内容。",
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onOpenOfficialSite()
                    onDismiss()
                },
            ) { Text("前往官网") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("再逛逛") }
        },
    )
}
