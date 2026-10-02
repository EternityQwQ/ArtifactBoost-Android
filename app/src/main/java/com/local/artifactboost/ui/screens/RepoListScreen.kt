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
import androidx.compose.material.icons.filled.ForkRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.artifactboost.data.GHRepo
import com.local.artifactboost.data.formatCount
import com.local.artifactboost.data.formatRelative
import com.local.artifactboost.runtime.AppViewModel
import com.local.artifactboost.ui.components.CardBox
import com.local.artifactboost.ui.components.EmptyState
import com.local.artifactboost.ui.components.ErrorBanner
import com.local.artifactboost.ui.components.Hairline
import com.local.artifactboost.ui.components.LanguageLabel
import com.local.artifactboost.ui.components.RepoAvatar
import com.local.artifactboost.ui.components.SkeletonBlock
import com.local.artifactboost.ui.components.StatLabel
import com.local.artifactboost.ui.theme.accent
import com.local.artifactboost.ui.theme.canvas
import com.local.artifactboost.ui.theme.muted
import com.local.artifactboost.ui.theme.orange
import com.local.artifactboost.ui.theme.strongText
import com.local.artifactboost.ui.theme.subtle
import com.local.artifactboost.ui.theme.warning

/** 我的仓库列表：自己 + 协作 + 组织 */
@Composable
fun RepoListScreen(vm: AppViewModel, onOpen: (GHRepo) -> Unit) {
    val repos = vm.repos

    Column(Modifier.fillMaxSize().background(canvas())) {
        // 顶部标题栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(canvas())
                .padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "我的仓库",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = strongText(),
                )
                vm.user?.let {
                    Text("@${it.login}", fontSize = 12.sp, color = subtle())
                }
            }
            Spacer(Modifier.weight(1f))
            if (vm.isRefreshing || vm.isLoadingRepos) {
                CircularProgressIndicator(
                    modifier = Modifier.size(17.dp).padding(end = 6.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                IconButton(onClick = { vm.refresh() }) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "刷新",
                        tint = muted(),
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
        }

        when {
            repos == null && vm.reposError != null -> Box(Modifier.padding(16.dp)) {
                ErrorBanner(vm.reposError ?: "", orange())
            }

            repos == null -> LazyColumn(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(5) { CardBox { SkeletonBlock(3) } }
            }

            repos.isEmpty() -> EmptyState(
                Icons.Outlined.FolderOpen,
                "这里还没有仓库",
                "换个 Token，或者去「搜索」里找别人的公开仓库。",
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 2.dp,
                    bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(repos, key = { it.id }) { repo ->
                    RepoRow(repo) { onOpen(repo) }
                }
            }
        }
    }
}

@Composable
fun RepoRow(repo: GHRepo, onClick: () -> Unit) {
    CardBox(modifier = Modifier.clickable(onClick = onClick), padding = 14.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                RepoAvatar(repo.isPrivate)
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${repo.owner}/",
                            fontSize = 15.sp,
                            color = muted(),
                        )
                        Text(
                            repo.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = accent(),
                        )
                        if (repo.isPrivate) {
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Filled.Lock,
                                contentDescription = "私有",
                                tint = warning(),
                                modifier = Modifier.size(11.dp),
                            )
                        }
                    }
                    formatRelative(repo.updatedAt)?.let {
                        Text(it, fontSize = 11.sp, color = subtle())
                    }
                }
                Spacer(Modifier.weight(1f))
            }

            repo.description?.takeIf { it.isNotEmpty() }?.let {
                Text(it, fontSize = 12.sp, color = muted(), maxLines = 2)
            }

            Hairline()

            Row(
                horizontalArrangement = Arrangement.spacedBy(15.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repo.language?.let { LanguageLabel(it) }
                repo.stargazersCount?.takeIf { it > 0 }?.let {
                    StatLabel(Icons.Filled.Star, formatCount(it))
                }
                repo.forksCount?.takeIf { it > 0 }?.let {
                    StatLabel(Icons.Filled.ForkRight, formatCount(it))
                }
            }
        }
    }
}
