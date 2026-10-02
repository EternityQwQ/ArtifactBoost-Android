package com.artifactboost.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artifactboost.app.ArtifactBoostApp
import com.artifactboost.app.data.GHArtifact
import com.artifactboost.app.data.GHRepo
import com.artifactboost.app.data.GHWorkflowRun
import com.artifactboost.app.data.artifactItem
import com.artifactboost.app.data.runLogsItem
import com.artifactboost.app.ui.components.CardSurface
import com.artifactboost.app.ui.components.EmptyStateView
import com.artifactboost.app.ui.components.Hairline
import com.artifactboost.app.ui.components.IconBadge
import com.artifactboost.app.ui.components.InlineBanner
import com.artifactboost.app.ui.components.StatusPill
import com.artifactboost.app.ui.theme.AppTheme
import com.artifactboost.app.util.formatTimestamp
import kotlinx.coroutines.launch

/**
 * 单次构建：构建日志 + 所有产物，都能加速下载。
 * 对应 iOS 版的 RunDetailView。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunDetailScreen(
    repo: GHRepo,
    run: GHWorkflowRun,
    onBack: () -> Unit,
) {
    val colors = AppTheme.colors
    val session = ArtifactBoostApp.instance.session
    val scope = rememberCoroutineScope()

    var artifacts by remember { mutableStateOf<List<GHArtifact>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }

    val downloadable = remember(artifacts) { artifacts.filter { !it.expired } }

    suspend fun load() {
        val client = session.client.value ?: return
        isLoading = true
        errorMessage = null
        try {
            artifacts = client.artifacts(repo, run)
            loaded = true
        } catch (e: Exception) {
            errorMessage = session.message(e)
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(run.id) { load() }

    Scaffold(
        containerColor = colors.canvas,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "构建 #${run.runNumber}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = colors.strongText,
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = colors.strongText)
                    }
                },
                actions = {
                    IconButton(onClick = { scope.launch { load() } }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新", tint = colors.muted)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    CardSurface {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                IconBadge(RunStatus.icon(run), RunStatus.color(run, colors), size = 38.dp)
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        run.displayTitle ?: run.name ?: "Workflow",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.strongText,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "${repo.fullName} · #${run.runNumber}",
                                        fontSize = 11.sp,
                                        color = colors.subtle,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Spacer(Modifier.weight(1f))
                                StatusPill(RunStatus.text(run), RunStatus.color(run, colors))
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatusPill(run.headBranch ?: "-", colors.muted, Icons.AutoMirrored.Filled.CallSplit)
                                run.event?.let { StatusPill(it, colors.muted) }
                            }

                            formatTimestamp(run.createdAt)?.let {
                                Text(it, fontSize = 11.sp, color = colors.subtle)
                            }
                        }
                    }
                }
            }

            if (errorMessage != null) {
                item {
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        CardSurface { InlineBanner(errorMessage!!, colors.orange, Icons.Filled.Warning) }
                    }
                }
            }

            item {
                SectionHeader("构建日志", "日志为 GitHub 打包好的 zip，体积一般很小。")
            }
            item {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    CardSurface { DownloadItemRow(item = runLogsItem(run, repo)) }
                }
            }

            item {
                SectionHeader(
                    "构建产物",
                    if (artifacts.isNotEmpty() && downloadable.size < artifacts.size) {
                        "有 ${artifacts.size - downloadable.size} 个产物已过期，无法下载。"
                    } else {
                        null
                    },
                )
            }

            when {
                isLoading && !loaded -> item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                }

                downloadable.isEmpty() && loaded -> item {
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        CardSurface {
                            EmptyStateView(
                                icon = Icons.Filled.Warning,
                                title = "没有可下载的产物",
                                message = "这次运行没有产物，或者产物已经过期被 GitHub 删除",
                            )
                        }
                    }
                }

                else -> items(downloadable, key = { it.id }) { artifact ->
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        CardSurface { DownloadItemRow(item = artifactItem(artifact, repo)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, footnote: String?) {
    val colors = AppTheme.colors
    Column(
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.muted)
        if (footnote != null) {
            Text(footnote, fontSize = 11.sp, color = colors.subtle)
        }
        Hairline()
    }
}

/** 运行状态的配色 / 文案 / 图标（RunDetailView 头部与列表行共用） */
internal object RunStatus {
    fun color(run: GHWorkflowRun, colors: com.artifactboost.app.ui.theme.AppColors) = when {
        run.status != "completed" -> colors.yellow
        run.conclusion == "success" -> colors.green
        run.conclusion == "failure" -> colors.red
        run.conclusion == "cancelled" || run.conclusion == "skipped" -> colors.muted
        else -> colors.orange
    }

    fun text(run: GHWorkflowRun): String {
        if (run.status != "completed") {
            return when (run.status) {
                "in_progress" -> "运行中"
                "queued" -> "排队中"
                else -> run.status ?: "进行中"
            }
        }
        return when (run.conclusion) {
            "success" -> "成功"
            "failure" -> "失败"
            "cancelled" -> "已取消"
            "skipped" -> "已跳过"
            "timed_out" -> "超时"
            else -> run.conclusion ?: "已完成"
        }
    }

    fun icon(run: GHWorkflowRun): androidx.compose.ui.graphics.vector.ImageVector {
        if (run.status != "completed") return Icons.Filled.Refresh
        return when (run.conclusion) {
            "success" -> Icons.Filled.CheckCircle
            "failure" -> Icons.Filled.Warning
            else -> Icons.Filled.Description
        }
    }
}
