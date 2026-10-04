package com.artifactboost.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artifactboost.app.ArtifactBoostApp
import com.artifactboost.app.data.ArchiveFormat
import com.artifactboost.app.data.GHBranch
import com.artifactboost.app.data.GHReadme
import com.artifactboost.app.data.GHRelease
import com.artifactboost.app.data.GHRepo
import com.artifactboost.app.data.GHWorkflowRun
import com.artifactboost.app.data.sourceArchiveItem
import com.artifactboost.app.ui.components.CardSurface
import com.artifactboost.app.ui.components.EmptyStateView
import com.artifactboost.app.ui.components.Hairline
import com.artifactboost.app.ui.components.IconBadge
import com.artifactboost.app.ui.components.InlineBanner
import com.artifactboost.app.ui.components.LanguageLabel
import com.artifactboost.app.ui.components.RepoAvatarView
import com.artifactboost.app.ui.components.SkeletonBlock
import com.artifactboost.app.ui.components.StatLabel
import com.artifactboost.app.ui.components.StatusPill
import com.artifactboost.app.ui.theme.AppColors
import com.artifactboost.app.ui.theme.AppTheme
import com.artifactboost.app.util.formatBytes
import com.artifactboost.app.util.formatCount
import com.artifactboost.app.util.formatRelative
import com.artifactboost.app.util.formatTimestamp
import com.artifactboost.app.util.parseIso8601
import kotlinx.coroutines.launch

private enum class RepoTab(val title: String) {
    OVERVIEW("概览"),
    BUILDS("构建"),
    RELEASES("发行版"),
    SOURCE("源码"),
}

/**
 * 仓库详情：概览（README）/ 构建产物 / 发行版 / 源码，都能加速下载。
 * 对应 iOS 版的 RepoDetailView。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun RepoDetailScreen(
    repo: GHRepo,
    onBack: () -> Unit,
    onOpenRun: (GHWorkflowRun) -> Unit,
    onOpenRelease: (GHRelease) -> Unit,
) {
    val colors = AppTheme.colors
    val session = ArtifactBoostApp.instance.session
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    var tabIndex by remember { mutableIntStateOf(0) }
    val tab = RepoTab.entries[tabIndex]

    var readme by remember { mutableStateOf<GHReadme?>(null) }
    var readmeLoading by remember { mutableStateOf(false) }
    var readmeFailed by remember { mutableStateOf(false) }

    var runs by remember { mutableStateOf<List<GHWorkflowRun>>(emptyList()) }
    var runsLoaded by remember { mutableStateOf(false) }

    var releases by remember { mutableStateOf<List<GHRelease>>(emptyList()) }
    var releasesLoaded by remember { mutableStateOf(false) }

    var branches by remember { mutableStateOf<List<GHBranch>>(emptyList()) }
    var branchesLoaded by remember { mutableStateOf(false) }
    var selectedRef by remember { mutableStateOf("") }

    var commitCount by remember { mutableStateOf<Int?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }

    suspend fun load(target: RepoTab, force: Boolean = false) {
        val client = session.client.value ?: return
        errorMessage = null
        try {
            when (target) {
                RepoTab.OVERVIEW -> {
                    if (readme != null && !force) return
                    readmeLoading = true
                    readmeFailed = false
                    readme = client.readme(repo)
                    readmeLoading = false
                }

                RepoTab.BUILDS -> {
                    if (runsLoaded && !force) return
                    isLoading = true
                    runs = client.workflowRuns(repo)
                    runsLoaded = true
                    isLoading = false
                }

                RepoTab.RELEASES -> {
                    if (releasesLoaded && !force) return
                    isLoading = true
                    releases = client.releases(repo)
                    releasesLoaded = true
                    isLoading = false
                }

                RepoTab.SOURCE -> {
                    if (branchesLoaded && !force) return
                    isLoading = true
                    branches = client.branches(repo)
                    branchesLoaded = true
                    if (selectedRef.isEmpty()) {
                        selectedRef = branches.firstOrNull()?.name ?: repo.defaultBranch.orEmpty()
                    }
                    isLoading = false
                }
            }
        } catch (e: Exception) {
            if (target == RepoTab.OVERVIEW) readmeFailed = true
            readmeLoading = false
            isLoading = false
            errorMessage = session.message(e)
        }
    }

    /** 顶部「提交数」这类装饰性统计：下拉刷新时也要一起更新，否则会一直显示旧值 */
    suspend fun loadHeaderStats() {
        val client = session.client.value ?: return
        commitCount = try {
            client.commitCount(repo)
        } catch (_: Exception) {
            null
        }
    }

    // 返回不被阻塞的关键：这些加载全部是异步协程，Compose 不会等在它们上面。
    // 老问题出在 README 的 Markdown 解析跑在主线程 —— 那才是「点了返回没反应」的元凶，
    // 与网络请求本身无关。解析已经挪到后台线程（见 MarkdownView）。
    androidx.compose.runtime.LaunchedEffect(tab) { load(tab) }
    androidx.compose.runtime.LaunchedEffect(repo.fullName) { loadHeaderStats() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(repo.name, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { uriHandler.openUri("https://github.com/${repo.fullName}") }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "在 GitHub 打开")
                    }
                    IconButton(onClick = { scope.launch { load(tab, force = true) } }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { padding ->
        // 下拉刷新：把当前 tab 的内容 + 顶部统计一起刷掉
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    isRefreshing = true
                    load(tab, force = true)
                    loadHeaderStats()
                    isRefreshing = false
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
            item {
                RepoHeroHeader(repo = repo, commitCount = commitCount, branchCount = branches.size.takeIf { branchesLoaded })
            }

            item {
                TabRow(
                    selectedTabIndex = tabIndex,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.primary,
                ) {
                    RepoTab.entries.forEachIndexed { index, entry ->
                        Tab(
                            selected = tabIndex == index,
                            onClick = { tabIndex = index },
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    Text(
                                        entry.title,
                                        style = if (tabIndex == index) {
                                            MaterialTheme.typography.titleSmall
                                        } else {
                                            MaterialTheme.typography.bodyMedium
                                        },
                                        color = if (tabIndex == index) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                    val badge = badgeFor(entry, runs, releases, branches)
                                    if (badge != null) {
                                        Text(
                                            badge,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                                .padding(horizontal = 5.dp, vertical = 1.dp),
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
            }

            if (errorMessage != null) {
                item {
                    Box(Modifier.padding(16.dp)) {
                        CardSurface { InlineBanner(errorMessage!!, colors.orange, Icons.Filled.Warning) }
                    }
                }
            }

            when (tab) {
                RepoTab.OVERVIEW -> item {
                    Box(Modifier.padding(16.dp)) {
                        when {
                            readmeLoading && readme == null -> CardSurface { SkeletonBlock(6) }
                            readme != null -> CardSurface(padding = 0.dp) {
                                Column {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                                    ) {
                                        Icon(
                                            Icons.Filled.Description,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(14.dp),
                                        )
                                        Text(
                                            readme!!.path,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Hairline()
                                    MarkdownContent(markdown = readme!!.text, modifier = Modifier.padding(12.dp))
                                }
                            }
                            readmeFailed -> CardSurface {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("README 读取失败", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "跳过 README 直接看下面的构建 / 发行版 / 源码即可。",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            else -> CardSurface {
                                EmptyStateView(
                                    icon = Icons.Filled.Description,
                                    title = "这个仓库没有 README",
                                    message = "切到「构建」「发行版」「源码」开始加速下载。",
                                )
                            }
                        }
                    }
                }

                RepoTab.BUILDS -> when {
                    isLoading && !runsLoaded -> item { Box(Modifier.padding(16.dp)) { CardSurface { SkeletonBlock(5) } } }
                    runs.isEmpty() && runsLoaded -> item {
                        Box(Modifier.padding(16.dp)) {
                            CardSurface {
                                EmptyStateView(
                                    icon = Icons.Filled.Warning,
                                    title = "还没有构建记录",
                                    message = "该仓库最近没有 Actions 运行",
                                )
                            }
                        }
                    }
                    else -> items(runs, key = { it.id }) { run ->
                        Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            CardSurface(padding = 0.dp) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) { RunRowContent(run = run, onClick = { onOpenRun(run) }) }
                            }
                        }
                    }
                }

                RepoTab.RELEASES -> when {
                    isLoading && !releasesLoaded -> item { Box(Modifier.padding(16.dp)) { CardSurface { SkeletonBlock(5) } } }
                    releases.isEmpty() && releasesLoaded -> item {
                        Box(Modifier.padding(16.dp)) {
                            CardSurface {
                                EmptyStateView(
                                    icon = Icons.Filled.Warning,
                                    title = "还没有发行版",
                                    message = "该仓库没有发布过 Release",
                                )
                            }
                        }
                    }
                    else -> items(releases, key = { it.id }) { release ->
                        Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            CardSurface(padding = 0.dp) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) { ReleaseRowContent(release = release, onClick = { onOpenRelease(release) }) }
                            }
                        }
                    }
                }

                RepoTab.SOURCE -> item {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        CardSurface {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("分支 / 标签", style = MaterialTheme.typography.titleSmall)

                                if (branches.isEmpty()) {
                                    Text(
                                        "默认分支",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        branches.take(12).forEach { branch ->
                                            val selected = branch.name == selectedRef
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(MaterialTheme.shapes.small)
                                                    .background(
                                                        if (selected) {
                                                            MaterialTheme.colorScheme.primaryContainer
                                                        } else {
                                                            MaterialTheme.colorScheme.surfaceContainerHighest
                                                        },
                                                    )
                                                    .border(
                                                        1.dp,
                                                        if (selected) {
                                                            MaterialTheme.colorScheme.primary
                                                        } else {
                                                            MaterialTheme.colorScheme.outlineVariant
                                                        },
                                                        MaterialTheme.shapes.small,
                                                    )
                                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Text(
                                                    branch.name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = if (selected) {
                                                        MaterialTheme.colorScheme.onPrimaryContainer
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurface
                                                    },
                                                )
                                                Spacer(Modifier.weight(1f))
                                                if (selected) {
                                                    Icon(
                                                        Icons.Filled.CheckCircle,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(14.dp),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        CardSurface {
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Text("源码压缩包", style = MaterialTheme.typography.titleSmall)
                                DownloadItemRow(item = sourceArchiveItem(repo, selectedRef, ArchiveFormat.ZIP))
                                Hairline()
                                DownloadItemRow(item = sourceArchiveItem(repo, selectedRef, ArchiveFormat.TARBALL))
                            }
                        }

                        Text(
                            "源码包由 GitHub 现场打包，不支持 Range 分段，只能单连接下载（依然会走最快通道）。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
            }
        }
    }
}

private fun badgeFor(
    tab: RepoTab,
    runs: List<GHWorkflowRun>,
    releases: List<GHRelease>,
    branches: List<GHBranch>,
): String? = when (tab) {
    RepoTab.OVERVIEW -> null
    RepoTab.BUILDS -> runs.size.takeIf { it > 0 }?.toString()
    RepoTab.RELEASES -> releases.size.takeIf { it > 0 }?.toString()
    RepoTab.SOURCE -> branches.size.takeIf { it > 0 }?.toString()
}

/** 仓库详情页顶部信息卡：M3 表面容器 + M3 排版 */
@Composable
private fun RepoHeroHeader(
    repo: GHRepo,
    commitCount: Int?,
    branchCount: Int?,
) {
    val colors = AppTheme.colors
    val scheme = MaterialTheme.colorScheme
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.surfaceContainerLow)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RepoAvatarView(isPrivate = repo.isPrivate, size = 46.dp, owner = repo.owner)

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = scheme.onSurfaceVariant)) {
                            append("${repo.owner}/")
                        }
                        withStyle(SpanStyle(color = scheme.primary)) {
                            append(repo.name)
                        }
                    },
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(
                        if (repo.isPrivate) Icons.Filled.Lock else Icons.Filled.Public,
                        contentDescription = null,
                        tint = if (repo.isPrivate) colors.yellow else scheme.onSurfaceVariant,
                        modifier = Modifier.size(11.dp),
                    )
                    Text(
                        if (repo.isPrivate) "私有仓库" else "公开仓库",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (repo.isPrivate) colors.yellow else scheme.onSurfaceVariant,
                    )
                    parseIso8601(repo.updatedAt)?.let { millis ->
                        Text("·", style = MaterialTheme.typography.labelSmall, color = scheme.outline)
                        Text(
                            formatRelative(millis),
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.outline,
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
        }

        if (!repo.description.isNullOrEmpty()) {
            Text(
                repo.description,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        Spacer(Modifier.height(6.dp))
        Hairline()
        Spacer(Modifier.height(6.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repo.language?.let { LanguageLabel(it) }
            if ((repo.stargazersCount ?: 0) > 0) {
                StatLabel(Icons.Filled.Star, formatCount(repo.stargazersCount!!))
            }
            if ((repo.forksCount ?: 0) > 0) {
                StatLabel(Icons.AutoMirrored.Filled.CallSplit, formatCount(repo.forksCount!!))
            }
            if ((commitCount ?: 0) > 0) {
                StatLabel(Icons.AutoMirrored.Filled.TrendingUp, formatCount(commitCount!!))
            }
            if ((branchCount ?: 0) > 0) {
                StatLabel(Icons.AutoMirrored.Filled.CallSplit, "$branchCount")
            }
            Spacer(Modifier.weight(1f))
        }

        Row(
            modifier = Modifier.padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TextButton(
                onClick = { uriHandler.openUri("https://github.com/${repo.fullName}") },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text("在 GitHub 打开")
            }
            TextButton(onClick = { }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text("分享")
            }
        }
    }
}

/** 单次运行的行内容（构建列表用）：M3 排版 */
@Composable
fun RunRowContent(run: GHWorkflowRun, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val scheme = MaterialTheme.colorScheme
    val statusColor = runStatusColor(run, colors)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconBadge(runStatusIcon(run), statusColor)
        Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
            Text(
                run.displayTitle ?: run.name ?: "Workflow",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
            )
            Text(
                "${run.headBranch ?: "-"} · #${run.runNumber}",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.outline,
            )
            formatTimestamp(run.createdAt)?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = scheme.outline)
            }
        }
        StatusPill(runStatusText(run), statusColor)
    }
}

/** 单个 Release 的行内容：M3 排版 */
@Composable
fun ReleaseRowContent(release: GHRelease, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val scheme = MaterialTheme.colorScheme
    val totalSize = release.assets.sumOf { it.size }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconBadge(Icons.Filled.CheckCircle, colors.purple)
        Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
            Text(release.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 2)
            if (release.assets.isNotEmpty()) {
                Text(
                    "${release.tagName} · ${release.assets.size} 个附件 · ${formatBytes(totalSize)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.outline,
                )
            } else {
                Text(release.tagName, style = MaterialTheme.typography.labelSmall, color = scheme.outline)
            }
            formatTimestamp(release.publishedAt)?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = scheme.outline)
            }
        }
        when {
            release.prerelease -> StatusPill("预发布", colors.orange)
            release.draft -> StatusPill("草稿", scheme.outline)
            else -> StatusPill("发行版", colors.green)
        }
    }
}

private fun runStatusColor(run: GHWorkflowRun, colors: AppColors) = when {
    run.status != "completed" -> colors.yellow
    run.conclusion == "success" -> colors.green
    run.conclusion == "failure" -> colors.red
    run.conclusion == "cancelled" || run.conclusion == "skipped" -> colors.muted
    else -> colors.orange
}

private fun runStatusText(run: GHWorkflowRun): String {
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

private fun runStatusIcon(run: GHWorkflowRun): androidx.compose.ui.graphics.vector.ImageVector {
    if (run.status != "completed") return Icons.Filled.Refresh
    return when (run.conclusion) {
        "success" -> Icons.Filled.CheckCircle
        "failure" -> Icons.Filled.Warning
        else -> Icons.Filled.Description
    }
}
