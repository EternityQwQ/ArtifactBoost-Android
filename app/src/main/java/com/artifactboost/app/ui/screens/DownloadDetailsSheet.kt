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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artifactboost.app.download.DownloadDiagnostics
import com.artifactboost.app.download.LaneSnapshot
import com.artifactboost.app.download.SegmentState
import com.artifactboost.app.ui.theme.AppTheme
import com.artifactboost.app.util.formatBytes
import com.artifactboost.app.util.formatSpeed

/**
 * 下载详情面板 —— 对应 Neat Download Manager 的「连接」视图。
 *
 * 展示三类信息（用户选定的口径）：
 *  1. 分段进度与速度：每条连接啃哪个字节区间、跑到百分比、当前多快；
 *  2. 分段连接状态：等待/下载中/重试中/完成/失败，第几次重试，服务端回了什么码；
 *  3. 下载地址与通道：每段走的是哪条镜像，以及当前实际请求的完整 URL（可一键复制）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadDetailsSheet(
    title: String,
    diagnostics: DownloadDiagnostics?,
    onDismiss: () -> Unit,
) {
    val colors = AppTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 标题栏
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "下载详情",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.strongText,
                    )
                    Text(
                        title,
                        fontSize = 12.sp,
                        color = colors.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭", tint = colors.muted)
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
                        fontSize = 13.sp,
                        color = colors.muted,
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
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.strongText,
            )
            if (diagnostics.lanes.isEmpty()) {
                Text("当前没有活跃分段", fontSize = 12.sp, color = colors.muted)
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
                tint = colors.blue,
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
                tint = if (diagnostics.throttles > 0) colors.orange else colors.muted,
                modifier = Modifier.weight(1f),
            )
        }
        if (diagnostics.throttles > 0) {
            Text(
                "检测到服务端限流（429/503）：引擎已按指数退避自动降速重试，"
                    + "并对该通道临时降低并发。这属于正常的自我节流，不是下载失败。",
                fontSize = 11.sp,
                color = colors.orange,
            )
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, tint: Color, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    Column(
        modifier = modifier
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, fontSize = 10.sp, color = colors.muted)
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = tint)
    }
}

@Composable
private fun RouteSection(diagnostics: DownloadDiagnostics) {
    val colors = AppTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "通道",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.strongText,
        )
        diagnostics.routes.forEach { route ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Filled.Speed,
                    contentDescription = null,
                    tint = if (route.isActive) colors.green else colors.muted,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    route.name,
                    fontSize = 12.sp,
                    color = colors.strongText,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    formatSpeed(route.speedBytesPerSecond),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (route.isActive) colors.green else colors.muted,
                )
            }
        }
    }
}

@Composable
private fun LaneCard(lane: LaneSnapshot) {
    val colors = AppTheme.colors
    val tint = stateColor(lane.state)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "#${lane.laneId}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = colors.strongText,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                lane.routeName,
                fontSize = 11.sp,
                color = colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                lane.state.label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = tint,
            )
        }

        LinearProgressIndicator(
            progress = { lane.fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
            color = tint,
            trackColor = colors.border,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${formatBytes(lane.start)} – ${formatBytes(lane.end)}",
                fontSize = 10.sp,
                color = colors.subtle,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.weight(1f))
            if (lane.attempt > 1) {
                Text("第 ${lane.attempt} 次", fontSize = 10.sp, color = colors.orange)
                Spacer(Modifier.width(8.dp))
            }
            if (lane.lastStatus != null) {
                Text("HTTP ${lane.lastStatus}", fontSize = 10.sp, color = colors.muted)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                formatSpeed(lane.speedBytesPerSecond),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.strongText,
            )
        }
    }
}

@Composable
private fun UrlSection(url: String) {
    val colors = AppTheme.colors
    val clipboard = LocalClipboardManager.current

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "当前下载地址",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.strongText,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { clipboard.setText(AnnotatedString(url)) },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.Filled.ContentCopy,
                    contentDescription = "复制地址",
                    tint = colors.blue,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 120.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                url,
                fontSize = 11.sp,
                color = colors.muted,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

/** 分段状态 → 语义色：完成绿、下载中蓝、重试橙、失败红 */
@Composable
private fun stateColor(state: SegmentState): Color {
    val colors = AppTheme.colors
    return when (state) {
        SegmentState.PENDING -> colors.muted
        SegmentState.DOWNLOADING -> colors.blue
        SegmentState.RETRYING -> colors.orange
        SegmentState.DONE -> colors.green
        SegmentState.FAILED -> colors.red
    }
}
