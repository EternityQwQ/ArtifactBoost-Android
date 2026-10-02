package com.local.artifactboost.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.artifactboost.data.DownloadItem
import com.local.artifactboost.data.DownloadSource
import com.local.artifactboost.data.formatBytes
import com.local.artifactboost.data.formatSpeed
import com.local.artifactboost.download.DownloadState
import com.local.artifactboost.ui.theme.accent
import com.local.artifactboost.ui.theme.danger
import com.local.artifactboost.ui.theme.muted
import com.local.artifactboost.ui.theme.subtle
import com.local.artifactboost.ui.theme.success

/** 通用「可下载项」行：产物 / 构建日志 / 正式版附件 / 源码包 共用 */
@Composable
fun DownloadItemRow(
    item: DownloadItem,
    state: DownloadState,
    routeSummary: String?,
    onStart: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 标题区
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBadge(iconFor(item), accent(), size = 34.dp)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(item.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = muted())
                Text(item.subtitle, fontSize = 11.sp, color = subtle())
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(item.source.kindName, fontSize = 11.sp, color = subtle())
                    if (item.size != null) {
                        Text("·", fontSize = 11.sp, color = subtle())
                        Text(formatBytes(item.size), fontSize = 11.sp, color = subtle())
                    }
                    if (!item.source.supportsChunkedDownload) {
                        Text("·", fontSize = 11.sp, color = subtle())
                        Text("不支持分段", fontSize = 11.sp, color = subtle())
                    }
                }
            }
            Spacer(Modifier.weight(1f))
        }

        // 操作区
        when (state) {
            DownloadState.Idle -> Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = success()),
            ) {
                Icon(Icons.Filled.ArrowDownward, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("加速下载", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }

            DownloadState.Resolving -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                Text(
                    routeSummary ?: "正在解析下载地址…",
                    fontSize = 12.sp,
                    color = subtle(),
                )
            }

            is DownloadState.Downloading -> Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val progress = state.progress
                if (progress.totalBytes > 0) {
                    LinearProgressIndicator(
                        progress = { progress.fraction },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            "${formatBytes(progress.downloadedBytes)} / ${formatBytes(progress.totalBytes)}",
                            fontSize = 11.sp,
                            color = subtle(),
                        )
                        Spacer(Modifier.weight(1f))
                        Text("${(progress.fraction * 100).toInt()}%", fontSize = 11.sp, color = subtle())
                        Spacer(Modifier.width(8.dp))
                        Text(
                            formatSpeed(progress.speedBytesPerSecond),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = success(),
                        )
                    }
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text("该资源不支持分段，正在单连接下载…", fontSize = 11.sp, color = subtle())
                }

                if (routeSummary != null) {
                    Text(routeSummary, fontSize = 11.sp, color = subtle())
                }

                TextButton(onClick = onCancel) {
                    Text("取消", fontSize = 12.sp, color = danger())
                }
            }

            is DownloadState.Finished -> Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Filled.CheckCircle, null, Modifier.size(16.dp), tint = success())
                    Text("下载完成", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = muted())
                    Spacer(Modifier.weight(1f))
                }
                routeSummary?.let { Text(it, fontSize = 11.sp, color = subtle()) }
                Text(
                    "已保存到：${state.file.absolutePath}",
                    fontSize = 11.sp,
                    color = subtle(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onStart,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = success()),
                    ) {
                        Text("重新下载", fontSize = 13.sp)
                    }
                }
            }

            is DownloadState.Failed -> Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.Warning, null, Modifier.size(16.dp), tint = danger())
                    Text(state.message, fontSize = 12.sp, color = subtle())
                }
                Button(onClick = onStart) {
                    Icon(Icons.Filled.Refresh, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("重试", fontSize = 13.sp)
                }
            }
        }
    }
}

private fun iconFor(item: DownloadItem) = when (item.source) {
    is DownloadSource.Artifact -> Icons.Filled.Archive
    is DownloadSource.RunLogs -> Icons.Filled.Description
    is DownloadSource.ReleaseAsset -> Icons.Filled.Inventory2
    is DownloadSource.SourceArchive -> Icons.Filled.Code
}
