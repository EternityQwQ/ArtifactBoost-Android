package com.artifactboost.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.artifactboost.app.download.DownloadDiagnostics
import com.artifactboost.app.download.LaneSnapshot
import com.artifactboost.app.download.SegmentState
import com.artifactboost.app.ui.theme.AppTheme
import com.artifactboost.app.util.formatBytes
import com.artifactboost.app.util.formatSpeed

/**
 * M3 下载详情面板 —— 对应 Neat Download Manager 的「连接」视图。
 * ModalBottomSheet + FilledTonal 统计卡 + OutlinedCard 分段卡。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadDetailsSheet(
    title: String,
    diagnostics: DownloadDiagnostics?,
    onDismiss: () -> Unit,
) {
    val colors = AppTheme.colors
    val scheme = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 标题栏：M3 排版
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("下载详情", style = MaterialTheme.typography.titleLarge)
                    Text(
                        title,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭")
                }
            }

            if (diagnostics == null) {
                // 还没跑起来（正在解析地址 / 还没分段）：给个体面的占位，别显示空白
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "正在建立连接，稍候即可看到各分段明细…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
                return@Column
            }

            // 汇总
            SummaryHeader(diagnostics)

            // 各通道
            if (diagnostics.routes.isNotEmpty()) {
                RouteSection(diagnostics)
            }

            // 分段明细
            Text(
                "分段明细（${diagnostics.lanes.size}/${diagnostics.targetLanes} 条连接）",
                style = MaterialTheme.typography.titleSmall,
            )
            if (diagnostics.lanes.isEmpty()) {
                Text(
                    "当前没有活跃分段",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(diagnostics.lanes, key = { it.laneId }) { lane ->
                        LaneCard(lane)
                    }
                }
            }

            // 下载地址
            if (diagnostics.activeUrl.isNotBlank()) {
                UrlSection(diagnostics.activeUrl)
            }
        }
    }
}

@Composable
private fun SummaryHeader(diagnostics: DownloadDiagnostics) {
    val colors = AppTheme.colors
    val scheme = MaterialTheme.colorScheme
    val totalSpeed = diagnostics.lanes.sumOf { it.speedBytesPerSecond }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(
                label = "实时总速度",
                value = formatSpeed(totalSpeed),
                tint = colors.green,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "活跃 / 目标连接",
                value = "${diagnostics.lanes.size} / ${diagnostics.targetLanes}",
                tint = scheme.primary,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(
                label = "切片 完成 / 累计",
                value = "${diagnostics.doneSlices} / ${diagnostics.totalSlices}",
                tint = colors.purple,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "重试 / 限流 / 切分",
                value = "${diagnostics.retries} / ${diagnostics.throttles} / ${diagnostics.splits}",
                tint = if (diagnostics.throttles > 0) colors.orange else scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
        if (diagnostics.throttles > 0) {
            Text(
                "检测到服务端限流（429/503）：引擎已按指数退避自动降速重试，"
                    + "并对该通道临时降低并发。这属于正常的自我节流，不是下载失败。",
                style = MaterialTheme.typography.bodySmall,
                color = colors.orange,
            )
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, tint: Color, modifier: Modifier = Modifier) {
    OutlinedCard(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, color = tint)
        }
    }
}

@Composable
private fun RouteSection(diagnostics: DownloadDiagnostics) {
    val colors = AppTheme.colors
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("通道", style = MaterialTheme.typography.titleSmall)
        diagnostics.routes.forEach { route ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Filled.Speed,
                    contentDescription = null,
                    tint = if (route.isActive) colors.green else scheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    route.name,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    formatSpeed(route.speedBytesPerSecond),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (route.isActive) colors.green else scheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LaneCard(lane: LaneSnapshot) {
    val colors = AppTheme.colors
    val scheme = MaterialTheme.colorScheme
    val tint = stateColor(lane.state)

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.outlinedCardColors(
            containerColor = scheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("#${lane.laneId}", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.width(8.dp))
                Text(
                    lane.routeName,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(lane.state.label, style = MaterialTheme.typography.labelSmall, color = tint)
            }

            LinearProgressIndicator(
                progress = { lane.fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = tint,
                trackColor = scheme.surfaceContainerHighest,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${formatBytes(lane.start)} – ${formatBytes(lane.end)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.outline,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.weight(1f))
                if (lane.attempt > 1) {
                    Text("第 ${lane.attempt} 次", style = MaterialTheme.typography.labelSmall, color = colors.orange)
                    Spacer(Modifier.width(8.dp))
                }
                if (lane.lastStatus != null) {
                    Text(
                        "HTTP ${lane.lastStatus}",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    formatSpeed(lane.speedBytesPerSecond),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun UrlSection(url: String) {
    val scheme = MaterialTheme.colorScheme
    val clipboard = LocalClipboardManager.current

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "当前下载地址",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            FilledTonalButton(onClick = { clipboard.setText(AnnotatedString(url)) }) {
                Icon(
                    Icons.Filled.ContentCopy,
                    contentDescription = "复制地址",
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("复制")
            }
        }
        Text(
            url,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 120.dp)
                .verticalScroll(rememberScrollState()),
        )
    }
}

/** 分段状态 → 语义色：完成绿、下载中蓝、重试橙、失败红 */
@Composable
private fun stateColor(state: SegmentState): Color {
    val colors = AppTheme.colors
    val scheme = MaterialTheme.colorScheme
    return when (state) {
        SegmentState.PENDING -> scheme.onSurfaceVariant
        SegmentState.DOWNLOADING -> scheme.primary
        SegmentState.RETRYING -> colors.orange
        SegmentState.DONE -> colors.green
        SegmentState.FAILED -> colors.red
    }
}
