package com.local.artifactboost.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.artifactboost.data.formatSpeed
import com.local.artifactboost.download.DownloadState
import com.local.artifactboost.runtime.AppViewModel
import com.local.artifactboost.ui.components.CardBox
import com.local.artifactboost.ui.components.DownloadItemRow
import com.local.artifactboost.ui.components.EmptyState
import com.local.artifactboost.ui.theme.accent
import com.local.artifactboost.ui.theme.canvas
import com.local.artifactboost.ui.theme.muted
import com.local.artifactboost.ui.theme.strongText
import com.local.artifactboost.ui.theme.subtle

/** 下载管理：所有任务的实时进度、已完成的落盘位置 */
@Composable
fun DownloadsScreen(vm: AppViewModel) {
    val order = vm.downloadOrder.collectAsStateCompat()
    val states = vm.downloadStates.collectAsStateCompat()
    val summaries = vm.downloads.routeSummary.collectAsStateCompat()
    val items = vm.downloads.items.collectAsStateCompat()

    val active = states.value.values.count {
        it is DownloadState.Downloading || it is DownloadState.Resolving
    }
    val totalSpeed = states.value.values
        .filterIsInstance<DownloadState.Downloading>()
        .sumOf { it.progress.speedBytesPerSecond }

    Column(Modifier.fillMaxSize().background(canvas())) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "下载",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = strongText(),
                )
                val subtitle = when {
                    active > 0 && totalSpeed > 0 ->
                        "$active 个进行中 · 合计 ${formatSpeed(totalSpeed)}"
                    active > 0 -> "$active 个进行中"
                    else -> "共 ${order.value.size} 条记录"
                }
                Text(subtitle, fontSize = 12.sp, color = subtle())
            }
            Spacer(Modifier.weight(1f))
            if (order.value.isNotEmpty()) {
                TextButton(onClick = { vm.clearFinishedDownloads() }) {
                    Icon(Icons.Filled.DeleteSweep, null, Modifier.size(16.dp), tint = muted())
                    Spacer(Modifier.padding(start = 4.dp))
                    Text("清除已完成", fontSize = 12.sp, color = muted())
                }
            }
        }

        val list = order.value.mapNotNull { id ->
            items.value[id]?.let { Triple(it, states.value[id] ?: DownloadState.Idle, summaries.value[id]) }
        }

        if (list.isEmpty()) {
            EmptyState(
                Icons.Outlined.Download,
                "还没有下载任务",
                "去仓库详情页，找一个构建产物点「加速下载」。",
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(list, key = { it.first.id }) { (item, state, summary) ->
                    CardBox(padding = 14.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    item.source.repo,
                                    fontSize = 11.sp,
                                    color = accent(),
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(
                                    onClick = { vm.removeDownload(item.id) },
                                    modifier = Modifier.size(18.dp),
                                ) {
                                    Icon(
                                        Icons.Filled.DeleteSweep,
                                        contentDescription = "移除",
                                        tint = subtle(),
                                        modifier = Modifier.size(15.dp),
                                    )
                                }
                            }
                            DownloadItemRow(
                                item = item,
                                state = state,
                                routeSummary = summary,
                                onStart = { vm.startDownload(item) },
                                onCancel = { vm.cancelDownload(item.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}
