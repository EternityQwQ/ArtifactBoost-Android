package com.artifactboost.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.artifactboost.app.ArtifactBoostApp
import com.artifactboost.app.data.AccelerationSettings
import com.artifactboost.app.data.DownloadItem
import com.artifactboost.app.data.DownloadSource
import com.artifactboost.app.download.DownloadState
import com.artifactboost.app.ui.components.IconBadge
import com.artifactboost.app.ui.components.StatusPill
import com.artifactboost.app.ui.theme.AppColors
import com.artifactboost.app.ui.theme.AppTheme
import com.artifactboost.app.util.formatBytes
import com.artifactboost.app.util.formatSpeed

/** 按下载类型取语义色 */
fun sourceColor(source: DownloadSource, colors: AppColors): Color = when (source) {
    is DownloadSource.Artifact -> colors.blue
    is DownloadSource.RunLogs -> colors.orange
    is DownloadSource.ReleaseAsset -> colors.purple
    is DownloadSource.SourceArchive -> colors.green
}

/** 按下载类型取图标 */
fun sourceIcon(source: DownloadSource): ImageVector = when (source) {
    is DownloadSource.Artifact -> Icons.Filled.Archive
    is DownloadSource.RunLogs -> Icons.Filled.Description
    is DownloadSource.ReleaseAsset -> Icons.Filled.Inventory2
    is DownloadSource.SourceArchive -> Icons.Filled.Code
}

/**
 * M3 通用「可下载项」行：产物 / 构建日志 / 发行版附件 / 源码包共用。
 * 主操作走 FilledButton（primary），次操作走 FilledTonalButton/TextButton，
 * 进度条走 M3 LinearProgressIndicator + surfaceContainerHighest 轨道。
 */
@Composable
fun DownloadItemRow(
    item: DownloadItem,
    disabled: Boolean = false,
    disabledNote: String? = null,
) {
    val colors = AppTheme.colors
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val downloads = ArtifactBoostApp.instance.downloads

    val states by downloads.states.collectAsStateWithLifecycle()
    val summaries by downloads.routeSummary.collectAsStateWithLifecycle()
    val state = states[item.id] ?: DownloadState.Idle
    val summary = summaries[item.id]

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // 头部
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBadge(sourceIcon(item.source), sourceColor(item.source, colors))

            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.onSurface,
                    maxLines = 2,
                )
                Text(
                    item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(item.kindName, style = MaterialTheme.typography.labelSmall, color = scheme.outline)
                    if (item.size != null) {
                        Text("·", style = MaterialTheme.typography.labelSmall, color = scheme.outline)
                        Text(
                            formatBytes(item.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.outline,
                        )
                    }
                    if (!item.source.supportsChunkedDownload) {
                        Text("·", style = MaterialTheme.typography.labelSmall, color = scheme.outline)
                        Text("不支持分段", style = MaterialTheme.typography.labelSmall, color = scheme.outline)
                    }
                }
            }

            if (disabled && disabledNote != null) {
                StatusPill(disabledNote, scheme.outline)
            }
        }

        // 操作区
        when (state) {
            DownloadState.Idle -> {
                Button(
                    onClick = {
                        downloads.start(item, AccelerationSettings.load(context))
                        com.artifactboost.app.download.DownloadService.start(context)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !disabled,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("加速下载")
                }
            }

            DownloadState.Resolving -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(
                        summary ?: "正在解析下载地址…",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }

            is DownloadState.Downloading -> {
                var showDetails by remember { mutableStateOf(false) }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val progress = state.progress
                    if (progress.totalBytes > 0) {
                        LinearProgressIndicator(
                            progress = { progress.fraction },
                            modifier = Modifier.fillMaxWidth(),
                            trackColor = scheme.surfaceContainerHighest,
                        )
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "${formatBytes(progress.downloadedBytes)} / ${formatBytes(progress.totalBytes)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                "${(progress.fraction * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                formatSpeed(progress.speedBytesPerSecond),
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.green,
                            )
                        }
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            trackColor = scheme.surfaceContainerHighest,
                        )
                        Text(
                            "该资源不支持分段，正在单连接下载…",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }

                    if (summary != null) {
                        Text(summary, style = MaterialTheme.typography.bodySmall, color = scheme.outline)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        TextButton(onClick = { showDetails = true }) {
                            Icon(
                                Icons.Filled.List,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("详细信息")
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { downloads.cancel(item) }) {
                            Text("取消", color = colors.red)
                        }
                    }
                }

                if (showDetails) {
                    DownloadDetailsSheet(
                        title = item.title,
                        // 边下边看：面板里的数据每次都从最新一帧进度里取，
                        // 所以重开面板看到的永远是「此刻」的明细。
                        diagnostics = state.progress.diagnostics,
                        onDismiss = { showDetails = false },
                    )
                }
            }

            is DownloadState.Finished -> {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = colors.green,
                            modifier = Modifier.size(16.dp),
                        )
                        Text("下载完成", style = MaterialTheme.typography.titleSmall)
                    }
                    if (state.publicPath != null) {
                        Text(
                            "已保存到系统目录：${state.publicPath}",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    if (summary != null) {
                        Text(summary, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    } else if (state.publicPath == null) {
                        Text(
                            "公共目录写入失败，已保留在 App 私有目录，可手动导出。",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilledTonalButton(
                            onClick = {
                                val uri = state.publicUri
                                if (uri != null) sharePublicUri(context, uri, state.file.name)
                                else shareFile(context, state.file)
                            },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text(
                                if (state.publicUri != null) "分享 / 打开系统文件" else "导出 / 保存到文件",
                            )
                        }
                        com.artifactboost.app.ui.components.IconBadgeButton(
                            icon = Icons.Filled.Refresh,
                            onClick = {
                                downloads.start(item, AccelerationSettings.load(context))
                                com.artifactboost.app.download.DownloadService.start(context)
                            },
                        )
                    }
                }
            }

            is DownloadState.Failed -> {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = colors.red,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            state.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = {
                        downloads.start(item, AccelerationSettings.load(context))
                        com.artifactboost.app.download.DownloadService.start(context)
                    }) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("重试")
                    }
                }
            }
        }
    }
}
