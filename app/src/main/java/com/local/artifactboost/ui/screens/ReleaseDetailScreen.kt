package com.local.artifactboost.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.artifactboost.data.DownloadItem
import com.local.artifactboost.data.GHRelease
import com.local.artifactboost.data.GHRepo
import com.local.artifactboost.data.formatBytes
import com.local.artifactboost.data.formatDateTime
import com.local.artifactboost.data.formatRelative
import com.local.artifactboost.runtime.AppViewModel
import com.local.artifactboost.ui.components.CardBox
import com.local.artifactboost.ui.components.DownloadItemRow
import com.local.artifactboost.ui.components.EmptyState
import com.local.artifactboost.ui.components.Hairline
import com.local.artifactboost.ui.components.IconBadge
import com.local.artifactboost.ui.components.MarkdownView
import com.local.artifactboost.ui.components.SectionLabel
import com.local.artifactboost.ui.components.StatusPill
import com.local.artifactboost.ui.theme.accent
import com.local.artifactboost.ui.theme.canvas
import com.local.artifactboost.ui.theme.green
import com.local.artifactboost.ui.theme.muted
import com.local.artifactboost.ui.theme.orange
import com.local.artifactboost.ui.theme.purple
import com.local.artifactboost.ui.theme.strongText
import com.local.artifactboost.ui.theme.subtle

/** 正式版详情：发布说明 + 所有附件，逐个加速下载 */
@Composable
fun ReleaseDetailScreen(release: GHRelease, repo: GHRepo, vm: AppViewModel) {
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
                        IconBadge(Icons.Filled.Inventory2, purple(), size = 40.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                release.displayName,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = strongText(),
                            )
                            Text(repo.fullName, fontSize = 12.sp, color = subtle())
                        }
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

                    Hairline()

                    InfoRow("标签", release.tagName)
                    InfoRow("发布时间", formatDateTime(release.publishedAt) ?: "-")
                    formatRelative(release.publishedAt)?.let { InfoRow("距今", it) }
                    if (release.assets.isNotEmpty()) {
                        InfoRow(
                            "附件",
                            "${release.assets.size} 个 · 共 ${formatBytes(release.assets.sumOf { it.size })}",
                        )
                    }
                }
            }
        }

        // 发布说明
        release.body?.takeIf { it.isNotBlank() }?.let { body ->
            item {
                CardBox(padding = 16.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionLabel(Icons.Filled.Inventory2, "更新说明", purple())
                        Hairline()
                        MarkdownView(body)
                    }
                }
            }
        }

        // 附件
        item {
            CardBox(padding = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SectionLabel(
                        Icons.Outlined.Inventory2,
                        "下载附件（${release.assets.size}）",
                        accent(),
                    )
                    if (release.assets.isEmpty()) {
                        EmptyState(Icons.Outlined.Inventory2, "这个版本没有附件")
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            release.assets.forEachIndexed { index, asset ->
                                if (index > 0) Hairline()
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            asset.name,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = strongText(),
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(
                                            formatBytes(asset.size),
                                            fontSize = 11.sp,
                                            color = subtle(),
                                        )
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Icon(
                                            Icons.Outlined.Inventory2,
                                            null,
                                            Modifier.size(11.dp),
                                            tint = subtle(),
                                        )
                                        Text(
                                            "被下载 ${asset.downloadCount} 次",
                                            fontSize = 11.sp,
                                            color = subtle(),
                                        )
                                    }
                                    val item = DownloadItem.releaseAsset(asset, release, repo)
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
                    }
                }
            }
        }

        item { androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, fontSize = 12.sp, color = muted(), modifier = Modifier.width(64.dp))
        Text(value, fontSize = 12.sp, color = subtle(), modifier = Modifier.weight(1f))
    }
}
