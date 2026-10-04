package com.artifactboost.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.artifactboost.app.ArtifactBoostApp
import com.artifactboost.app.data.GHRepo
import com.artifactboost.app.ui.components.CardSurface
import com.artifactboost.app.ui.components.EmptyStateView
import com.artifactboost.app.ui.components.InlineBanner
import com.artifactboost.app.ui.components.RepoCardRow
import com.artifactboost.app.ui.theme.AppTheme

/**
 * 我的仓库：支持本地筛选 + 下拉刷新。
 * 对应 iOS 版的 RepoListView。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RepoListScreen(onOpenRepo: (GHRepo) -> Unit = {}) {
    val colors = AppTheme.colors
    val session = ArtifactBoostApp.instance.session
    val viewModel = repoListViewModel(session)
    val uriHandler = LocalUriHandler.current
    val haptics = LocalHapticFeedback.current

    val repos by viewModel.repos.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val isInitialLoading by viewModel.isInitialLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    var filter by remember { mutableStateOf("") }
    // 绝区零彩蛋：长按顶部「我的仓库」标题触发，二次确认后才跳官网，不做后台静默下载
    var showZzzEgg by remember { mutableStateOf(false) }

    val shown = remember(repos, filter) {
        if (filter.isBlank()) repos
        else repos.filter { it.fullName.contains(filter, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "我的仓库",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.combinedClickable(
                            onClick = {},
                            onLongClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                showZzzEgg = true
                            },
                            onLongClickLabel = "发现彩蛋",
                        ),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    OutlinedTextField(
                        value = filter,
                        onValueChange = { filter = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("筛选我的仓库") },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = null,
                            )
                        },
                        singleLine = true,
                        shape = MaterialTheme.shapes.extraLarge,
                    )
                }

                if (errorMessage != null) {
                    item {
                        CardSurface {
                            InlineBanner(errorMessage!!, colors.orange, Icons.Filled.Warning)
                        }
                    }
                }

                if (isInitialLoading && repos.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.padding(24.dp))
                        }
                    }
                }

                if (shown.isEmpty() && !isInitialLoading) {
                    item {
                        EmptyStateView(
                            icon = Icons.Filled.GridView,
                            title = if (filter.isBlank()) "还没有仓库" else "本地没有匹配的仓库",
                            message = if (filter.isBlank()) {
                                "下拉刷新；想下载别人的公开仓库，去「搜索」Tab 直接搜"
                            } else {
                                "换个关键词，或去「搜索」Tab 搜全站"
                            },
                        )
                    }
                }

                if (shown.isNotEmpty()) {
                    item {
                        Text(
                            "共 ${shown.size} 个仓库",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(shown, key = { it.id }) { repo ->
                        CardSurface {
                            Box(modifier = Modifier.clickable { onOpenRepo(repo) }) {
                                RepoCardRow(repo)
                            }
                        }
                    }
                }

                if (isRefreshing && repos.isNotEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                        }
                    }
                }
            }
        }
    }

    if (showZzzEgg) {
        ZzzEasterEggDialog(
            onDismiss = { showZzzEgg = false },
            onOpenOfficialSite = { uriHandler.openUri(ZZZ_CN_URL) },
        )
    }
}
