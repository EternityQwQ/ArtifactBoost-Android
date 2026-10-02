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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.artifactboost.data.DownloadItem
import com.local.artifactboost.data.GHArtifact
import com.local.artifactboost.data.GHRepo
import com.local.artifactboost.data.GHWorkflowRun
import com.local.artifactboost.data.formatBytes
import com.local.artifactboost.data.formatDateTime
import com.local.artifactboost.data.formatRelative
import com.local.artifactboost.runtime.AppViewModel
import com.local.artifactboost.ui.components.CardBox
import com.local.artifactboost.ui.components.DownloadItemRow
import com.local.artifactboost.ui.components.EmptyState
import com.local.artifactboost.ui.components.ErrorBanner
import com.local.artifactboost.ui.components.Hairline
import com.local.artifactboost.ui.components.IconBadge
import com.local.artifactboost.ui.components.SectionLabel
import com.local.artifactboost.ui.components.SkeletonBlock
import com.local.artifactboost.ui.components.StatusPill
import com.local.artifactboost.ui.theme.accent
import com.local.artifactboost.ui.theme.canvas
import com.local.artifactboost.ui.theme.green
import com.local.artifactboost.ui.theme.muted
import com.local.artifactboost.ui.theme.orange
import com.local.artifactboost.ui.theme.strongText
import com.local.artifactboost.ui.theme.subtle
import com.local.artifactboost.ui.theme.warning

/** 单次构建详情：这次跑出来的产物 + 构建日志，都可以加速下载 */
@Composable
fun RunDetailScreen(run: GHWorkflowRun, repo: GHRepo, vm: AppViewModel) {
    var artifacts by remember { mutableStateOf<List<GHArtifact>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(run.id) {
        val client = vm.session.client.value
        if (client == null) {
            error = "登录状态已失效，请重新登录"
            artifacts = emptyList()
            return@LaunchedEffect
        }
        try {
            artifacts = client.artifacts(repo, run)
        } catch (e: Throwable) {
            error = e.message ?: "读取产物失败"
            artifacts = emptyList()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(canvas()),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            CardBox(padding = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconBadge(
                            when {
                                run.status != "completed" -> Icons.Filled.History
                                run.conclusion == "success" -> Icons.Filled.CheckCircle
                                else -> Icons.Filled.Warning
                            },
                            when {
                                run.status != "completed" -> warning()
                                run.conclusion == "success" -> green()
                                else -> orange()
                            },
                            size = 40.dp,
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                run.displayTitle ?: run.name ?: "Workflow",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = strongText(),
                            )
                            Text(
                                "${repo.fullName} · #${run.runNumber}",
                                fontSize = 12.sp,
                                color = subtle(),
                            )
                        }
                        StatusPill(
                            if (run.status != "completed") "运行中"
                            else when (run.conclusion) {
                                "success" -> "成功"
                                "failure" -> "失败"
                                "cancelled" -> "已取消"
                                else -> run.conclusion ?: "已完成"
                            },
                            if (run.status != "completed") warning()
                            else if (run.conclusion == "success") green() else orange(),
                        )
                    }

                    Hairline()

                    InfoRow("分支", run.headBranch ?: "-")
                    InfoRow("触发事件", eventText(run.event))
                    InfoRow("开始时间", formatDateTime(run.createdAt) ?: "-")
                    formatRelative(run.createdAt)?.let { InfoRow("距今", it) }
                }
            }
        }

        // 构建日志
        item {
            CardBox(padding = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionLabel(Icons.Filled.Description, "构建日志", accent())
                    val item = DownloadItem.runLogs(run, repo)
                    DownloadItemRow(
                        item = item,
                        state = vm.downloadState(item.id),
                        routeSummary = vm.routeSummaryFor(item.id),
                        onStart = { vm.startDownload(item) },
                        onCancel = { vm.cancelDownload(item.id) },
                    )
                }
            }
        }

        // 构建产物
        val list = artifacts
        when {
            list == null -> item {
                CardBox(padding = 16.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionLabel(Icons.Filled.Archive, "构建产物", accent())
                        SkeletonBlock(4)
                    }
                }
            }

            list.isEmpty() -> item {
                CardBox(padding = 16.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionLabel(Icons.Filled.Archive, "构建产物", accent())
                        EmptyState(
                            Icons.Filled.Archive,
                            "这次构建没有产物",
                            "可能是 workflow 没配 upload-artifact，或者产物已经过期被 GitHub 删除。",
                        )
                    }
                }
            }

            else -> item {
                CardBox(padding = 16.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        SectionLabel(Icons.Filled.Archive, "构建产物（${list.size}）", accent())
                        error?.let { ErrorBanner(it, orange()) }
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            list.forEachIndexed { index, artifact ->
                                if (index > 0) Hairline()
                                ArtifactBlock(artifact, repo, vm)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtifactBlock(artifact: GHArtifact, repo: GHRepo, vm: AppViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Text(
                artifact.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = strongText(),
                modifier = Modifier.weight(1f),
            )
            if (artifact.expired) StatusPill("已过期", orange())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(formatBytes(artifact.sizeInBytes), fontSize = 11.sp, color = subtle())
            Text("·", fontSize = 11.sp, color = subtle())
            Text("创建于 ${formatDateTime(artifact.createdAt) ?: "未知"}", fontSize = 11.sp, color = subtle())
        }

        if (artifact.expired) {
            Text(
                "GitHub 已删除这个产物，无法下载。",
                fontSize = 11.sp,
                color = orange(),
            )
        } else {
            val item = DownloadItem.artifact(artifact, repo)
            DownloadItemRow(
                item = item,
                state = vm.downloadState(item.id),
                routeSummary = vm.routeSummaryFor(item.id),
                onStart = { vm.startDownload(item) },
                onCancel = { vm.cancelDownload(item.id) },
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, fontSize = 12.sp, color = muted(), modifier = Modifier.width(64.dp))
        Text(value, fontSize = 12.sp, color = subtle(), modifier = Modifier.weight(1f))
    }
}

private fun eventText(event: String?): String = when (event) {
    "push" -> "push（推送代码）"
    "pull_request" -> "pull_request（合并请求）"
    "workflow_dispatch" -> "workflow_dispatch（手动触发）"
    "schedule" -> "schedule（定时任务）"
    "release" -> "release（发布版本）"
    null -> "-"
    else -> event
}
