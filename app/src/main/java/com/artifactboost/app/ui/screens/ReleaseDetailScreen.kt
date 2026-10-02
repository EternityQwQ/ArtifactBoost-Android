package com.artifactboost.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artifactboost.app.data.ArchiveFormat
import com.artifactboost.app.data.GHRelease
import com.artifactboost.app.data.GHRepo
import com.artifactboost.app.data.releaseAssetItem
import com.artifactboost.app.data.sourceArchiveItem
import com.artifactboost.app.ui.components.CardSurface
import com.artifactboost.app.ui.components.EmptyStateView
import com.artifactboost.app.ui.components.IconBadge
import com.artifactboost.app.ui.components.StatusPill
import com.artifactboost.app.ui.theme.AppTheme
import com.artifactboost.app.util.formatTimestamp
import androidx.compose.material.icons.filled.Inventory2
/**
 * 发行版详情：附件 + 对应 tag 的源码包，都能加速下载。
 * 对应 iOS 版的 ReleaseDetailView。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReleaseDetailScreen(
    repo: GHRepo,
    release: GHRelease,
    onBack: () -> Unit,
) {
    val colors = AppTheme.colors
    val uriHandler = LocalUriHandler.current
    var showNotes by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = colors.canvas,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        release.tagName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = colors.strongText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = colors.strongText)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        uriHandler.openUri("https://github.com/${repo.fullName}/releases/tag/${release.tagName}")
                    }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "在 GitHub 打开", tint = colors.muted)
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
                                IconBadge(Icons.Filled.Inventory2, colors.purple, size = 38.dp)
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        release.displayName,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.strongText,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "${repo.fullName} · ${release.tagName}",
                                        fontSize = 11.sp,
                                        color = colors.subtle,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Spacer(Modifier.weight(1f))
                                when {
                                    release.prerelease -> StatusPill("预发布", colors.orange)
                                    release.draft -> StatusPill("草稿", colors.muted)
                                    else -> StatusPill("发行版", colors.green)
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                formatTimestamp(release.publishedAt)?.let { StatusPill(it, colors.muted) }
                                StatusPill("${release.assets.size} 个附件", colors.muted)
                            }
                        }
                    }
                }
            }

            val body = release.body
            if (!body.isNullOrEmpty()) {
                item { ReleaseSectionHeader("更新说明", null) }
                item {
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        CardSurface {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    body,
                                    fontSize = 13.sp,
                                    color = colors.muted,
                                    maxLines = if (showNotes) Int.MAX_VALUE else 6,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 19.sp,
                                )
                                Text(
                                    if (showNotes) "收起" else "展开全部",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.blue,
                                    modifier = Modifier.clickable { showNotes = !showNotes },
                                )
                            }
                        }
                    }
                }
            }

            item {
                ReleaseSectionHeader(
                    "附件（${release.assets.size}）",
                    "发行版附件通常托管在 GitHub 的 CDN 上，同样支持多通道并发加速。",
                )
            }

            if (release.assets.isEmpty()) {
                item {
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        CardSurface {
                            EmptyStateView(
                                icon = Icons.Filled.Warning,
                                title = "这个版本没有附件",
                                message = "可以直接下载下面的源码包",
                            )
                        }
                    }
                }
            } else {
                items(release.assets, key = { it.id }) { asset ->
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        CardSurface { DownloadItemRow(item = releaseAssetItem(asset, release, repo)) }
                    }
                }
            }

            item {
                ReleaseSectionHeader("这个版本的源码", "源码包由 GitHub 现场打包，不支持分段，只能单连接下载。")
            }
            item {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    CardSurface {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            DownloadItemRow(item = sourceArchiveItem(repo, release.tagName, ArchiveFormat.ZIP))
                            DownloadItemRow(item = sourceArchiveItem(repo, release.tagName, ArchiveFormat.TARBALL))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReleaseSectionHeader(title: String, footnote: String?) {
    val colors = AppTheme.colors
    Column(
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.muted)
        if (footnote != null) Text(footnote, fontSize = 11.sp, color = colors.subtle)
        com.artifactboost.app.ui.components.Hairline()
    }
}
