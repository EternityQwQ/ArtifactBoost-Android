package com.artifactboost.app.ui.screens

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * 绝区零彩蛋（中国大陆官网）。
 *
 * 约定：只做“二次确认后用系统浏览器打开官网”，不在后台静默下载任何安装包。
 * 与原神彩蛋（见 GenshinEasterEgg.kt）行为一致。
 */
const val ZZZ_CN_URL = "https://zzz.mihoyo.com/"

@Composable
fun ZzzEasterEggDialog(
    onDismiss: () -> Unit,
    onOpenOfficialSite: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发现彩蛋 🎉") },
        text = {
            Text(
                "长按「我的仓库」发现了新艾利都的入口。\n\n" +
                    "点击「前往官网」将用浏览器打开绝区零中国大陆官网（$ZZZ_CN_URL），" +
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
