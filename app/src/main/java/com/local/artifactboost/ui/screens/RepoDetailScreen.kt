package com.local.artifactboost.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ForkRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.artifactboost.data.ArchiveFormat
import com.local.artifactboost.data.GHRelease
import com.local.artifactboost.data.GHRepo
import com.local.artifactboost.data.GHWorkflowRun
import com.local.artifactboost.data.DownloadItem
import com.local.artifactboost.data.formatBytes
import com.local.artifactboost.data.formatCount
import com.local.artifactboost.data.formatRelative
import com.local.artifactboost.runtime.AppViewModel
import com.local.artifactboost.ui.components.CardBox
import com.local.artifactboost.ui.components.DownloadItemRow
import com.local.artifactboost.ui.components.EmptyState
import com.local.artifactboost.ui.components.ErrorBanner
import com.local.artifactboost.ui.components.GitHubTabBar
import com.local.artifactboost.ui.components.Hairline
import com.local.artifactboost.ui.components.IconBadge
import com.local.artifactboost.ui.components.LanguageLabel
import com.local.artifactboost.ui.components.MarkdownView
import com.local.artifactboost.ui.components.RepoAvatar
import com.local.artifactboost.ui.components.SectionLabel
import com.local.artifactboost.ui.components.SkeletonBlock
import com.local.artifactboost.ui.components.StatLabel
import com.local.artifactboost.ui.components.StatusPill
import com.local.artifactboost.ui.theme.accent
import com.local.artifactboost.ui.theme.border
import com.local.artifactboost.ui.theme.canvas
import com.local.artifactboost.ui.theme.green
import com.local.artifactboost.ui.theme.muted
import com.local.artifactboost.ui.theme.orange
import com.local.artifactboost.ui.theme.purple
import com.local.artifactboost.ui.theme.strongText
import com.local.artifactboost.ui.theme.subtle
import com.local.artifactboost.ui.theme.surface
import com.local.artifactboost.ui.theme.warning

enum class RepoTab(val title: String) {
    OVERVIEW("概览"),
    BUILDS("构建"),
    RELEASES("正式版"),
    SOURCE("源码"),
}

/** 仓库详情：概览（README）/ 构建产物 / 正式版 / 源码，都能加速下载 */
@Composable
fun RepoDetailScreen(
    repo: GHRepo,
    vm: AppViewModel,
    onOpenRun: (GHWorkflowRun) -> Unit,
    onOpenRelease: (GHRelease) -> Unit,
) {
    var tab by remember { mutableStateOf(RepoTab.OVERVIEW) }

    // 切 Tab 时按需加载，避免一次性打太多请求
    androidx.compose.runtime.LaunchedEffect(tab) { vm.loadTab(repo, tab) }
    androidx.compose.runtime.LaunchedEffect(repo.fullName) { vm.loadHeaderStats(repo) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(canvas()),
    ) {
        item { RepoHero(repo, vm) }

        stickyHeader {
            GitHubTabBar(
                tabs = RepoTab.entries.toList(),
                selected = tab,
                title = { it.title },
                badge = { vm.badgeFor(repo, it)?.toString() },
                onSelect = { tab = it },
            )
        }

        vm.errorFor(repo)?.let { message ->
            item {
                Box(Modifier.padding(16.dp)) {
                    ErrorBanner(message, orange())
                }
            }
        }

        when (tab) {
            RepoTab.OVERVIEW -> {
                val readme = vm.readmeFor(repo)
                when {
                    vm.isReadmeLoading(repo) -> item {
                        CardBox(Modifier.padding(16.dp)) { SkeletonBlock(6) }
                    }
                    readme != null -> item {
                        CardBox(Modifier.padding(16.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                                ) {
                                    Icon(
                                        Icons.Filled.Description,
                                        null,
                                        Modifier.size(14.dp),
                                        tint = muted(),
                                    )
                                    Text(
                                        readme.path,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = muted(),
                                    )
                                }
                                Hairline()
                                MarkdownView(readme.text)
                            }
                        }
                    }
                    else -> item {
                        CardBox(Modifier.padding(16.dp)) {
                            EmptyState(
                                Icons.AutoMirrored.Outlined.MenuBook,
                                "这个仓库没有 README",
                                "切到「构建」「正式版」「源码」开始加速下载。",
                            )
                        }
                    }
                }
            }

            RepoTab.BUILDS -> {
                val runs = vm.runsFor(repo)
                when {
                    vm.isTabLoading(repo, RepoTab.BUILDS) && runs == null -> item {
                        CardBox(Modifier.padding(16.dp)) { SkeletonBlock(5) }
                    }
                    runs.isNullOrEmpty() -> item {
                        CardBox(Modifier.padding(16.dp)) {
                            EmptyState(Icons.Filled.History, "还没有构建记录", "该仓库最近没有 Actions 运行")
                        }
                    }
                    else -> item {
                        CardBox(Modifier.padding(0.dp)) {
                            Column {
                                runs.forEachIndexed { index, run ->
                                    if (index > 0) Hairline(Modifier.padding(start = 58.dp))
                                    RunRow(run) { onOpenRun(run) }
                                }
                            }
                        }
                    }
                }
            }

            RepoTab.RELEASES -> {
                val releases = vm.releasesFor(repo)
                when {
                    vm.isTabLoading(repo, RepoTab.RELEASES) && releases == null -> item {
                        CardBox(Modifier.padding(16.dp)) { SkeletonBlock(5) }
                    }
                    releases.isNullOrEmpty() -> item {
                        CardBox(Modifier.padding(16.dp)) {
                            EmptyState(Icons.Filled.Inventory2, "还没有正式版", "该仓库没有发布过 Release")
                        }
                    }
                    else -> item {
                        CardBox(Modifier.padding(0.dp)) {
                            Column {
                                releases.forEachIndexed { index, release ->
                                    if (index > 0) Hairline(Modifier.padding(start = 58.dp))
                                    ReleaseRow(release) { onOpenRelease(release) }
                                }
                            }
                        }
                    }
                }
            }

            RepoTab.SOURCE -> {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        CardBox(Modifier.padding(16.dp), padding = 0.dp) {
                            BranchPicker(repo, vm)
                        }
                        CardBox(Modifier.padding(16.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                SectionLabel(
                                    Icons.Filled.Code,
                                    "源码压缩包",
                                    purple(),
                                )
                                val ref = vm.selectedRefFor(repo)
                                DownloadItemRow(
                                    item = DownloadItem.sourceArchive(repo, ref, ArchiveFormat.ZIP),
                                    state = vm.downloadState(DownloadItem.sourceArchive(repo, ref, ArchiveFormat.ZIP).id),
                                    routeSummary = vm.routeSummaryFor(DownloadItem.sourceArchive(repo, ref, ArchiveFormat.ZIP).id),
                                    onStart = { vm.startDownload(DownloadItem.sourceArchive(repo, ref, ArchiveFormat.ZIP)) },
                                    onCancel = { vm.cancelDownload(DownloadItem.sourceArchive(repo, ref, ArchiveFormat.ZIP).id) },
                                )
                                Hairline()
                                DownloadItemRow(
                                    item = DownloadItem.sourceArchive(repo, ref, ArchiveFormat.TARBALL),
                                    state = vm.downloadState(DownloadItem.sourceArchive(repo, ref, ArchiveFormat.TARBALL).id),
                                    routeSummary = vm.routeSummaryFor(DownloadItem.sourceArchive(repo, ref, ArchiveFormat.TARBALL).id),
                                    onStart = { vm.startDownload(DownloadItem.sourceArchive(repo, ref, ArchiveFormat.TARBALL)) },
                                    onCancel = { vm.cancelDownload(DownloadItem.sourceArchive(repo, ref, ArchiveFormat.TARBALL).id) },
                                )
                            }
                        }
                        Text(
                            "源码包由 GitHub 现场打包，不支持 Range 分段，只能单连接下载（依然会走最快通道）。",
                            fontSize = 11.sp,
                            color = subtle(),
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** 顶部信息卡（对齐 GitHub 移动端 App 的 header） */
@Composable
private fun RepoHero(repo: GHRepo, vm: AppViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(surface())
            .padding(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RepoAvatar(repo.isPrivate, size = 46.dp)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row {
                    Text(
                        "${repo.owner}/",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = muted(),
                    )
                    Text(
                        repo.name,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = accent(),
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        if (repo.isPrivate) "🔒 私有仓库" else "🌐 公开仓库",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (repo.isPrivate) warning() else muted(),
                    )
                    formatRelative(repo.updatedAt)?.let {
                        Text("·", fontSize = 11.sp, color = subtle())
                        Text(it, fontSize = 11.sp, color = subtle())
                    }
                }
            }
            Spacer(Modifier.weight(1f))
        }

        repo.description?.takeIf { it.isNotEmpty() }?.let {
            Text(
                it,
                fontSize = 14.sp,
                color = muted(),
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        Hairline(Modifier.padding(vertical = 13.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repo.language?.let { LanguageLabel(it) }
            repo.stargazersCount?.takeIf { it > 0 }?.let {
                StatLabel(Icons.Filled.Star, formatCount(it))
            }
            repo.forksCount?.takeIf { it > 0 }?.let {
                StatLabel(Icons.Filled.ForkRight, formatCount(it))
            }
            vm.commitCountFor(repo)?.takeIf { it > 0 }?.let {
                StatLabel(Icons.Filled.History, formatCount(it))
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

/** 分支选择 */
@Composable
private fun BranchPicker(repo: GHRepo, vm: AppViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val branches = vm.branchesFor(repo).orEmpty()
    val selected = vm.selectedRefFor(repo)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = branches.isNotEmpty()) { expanded = true }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Filled.ForkRight, null, Modifier.size(16.dp), tint = muted())
        Text("分支 / 标签", fontSize = 14.sp, color = strongText())
        Spacer(Modifier.weight(1f))
        Text(
            selected.ifEmpty { repo.defaultBranch ?: "默认分支" },
            fontSize = 14.sp,
            color = accent(),
        )
        Icon(Icons.Filled.ArrowDropDown, null, Modifier.size(20.dp), tint = accent())

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            branches.forEach { branch ->
                DropdownMenuItem(
                    text = { Text(branch.name, fontSize = 14.sp) },
                    onClick = {
                        vm.selectRef(repo, branch.name)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun RunRow(run: GHWorkflowRun, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(iconForRun(run), runColor(run))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                run.displayTitle ?: run.name ?: "Workflow",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = strongText(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("⑂ ${run.headBranch ?: "-"}", fontSize = 11.sp, color = subtle())
                Text("·", fontSize = 11.sp, color = subtle())
                Text("#${run.runNumber}", fontSize = 11.sp, color = subtle())
            }
            formatRelative(run.createdAt)?.let {
                Text(it, fontSize = 11.sp, color = subtle())
            }
        }
        Spacer(Modifier.weight(1f))
        StatusPill(runText(run), runColor(run))
    }
}

@Composable
private fun ReleaseRow(release: GHRelease, onClick: () -> Unit) {
    val totalSize = release.assets.sumOf { it.size }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(Icons.Filled.Inventory2, purple())
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                release.displayName,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = strongText(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(release.tagName, fontSize = 11.sp, color = subtle())
                if (release.assets.isNotEmpty()) {
                    Text("·", fontSize = 11.sp, color = subtle())
                    Text("${release.assets.size} 个附件", fontSize = 11.sp, color = subtle())
                    Text("·", fontSize = 11.sp, color = subtle())
                    Text(formatBytes(totalSize), fontSize = 11.sp, color = subtle())
                }
            }
            formatRelative(release.publishedAt)?.let {
                Text(it, fontSize = 11.sp, color = subtle())
            }
        }
        Spacer(Modifier.weight(1f))
        StatusPill(
            when {
                release.prerelease -> "预发布"
                release.draft -> "草稿"
                else -> "正式版"
            },
            when {
                release.prerelease -> orange()
                release.draft -> muted()
                else -> green()
            },
        )
    }
}

private val GHWorkflowRun.isSuccess: Boolean get() = status == "completed" && conclusion == "success"

private fun iconForRun(run: GHWorkflowRun) = when {
    run.status != "completed" -> Icons.Filled.History
    run.conclusion == "success" -> Icons.Filled.CheckCircle
    else -> Icons.Filled.Warning
}

@Composable
private fun runColor(run: GHWorkflowRun) = when {
    run.status != "completed" -> warning()
    run.conclusion == "success" -> green()
    run.conclusion == "cancelled" || run.conclusion == "skipped" -> muted()
    else -> orange()
}

private fun runText(run: GHWorkflowRun): String {
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
