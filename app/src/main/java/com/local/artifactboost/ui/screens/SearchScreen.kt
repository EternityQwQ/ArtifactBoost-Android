package com.local.artifactboost.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.artifactboost.runtime.AppViewModel
import com.local.artifactboost.ui.components.ErrorBanner
import com.local.artifactboost.ui.components.SkeletonBlock
import com.local.artifactboost.ui.theme.accent
import com.local.artifactboost.ui.theme.canvas
import com.local.artifactboost.ui.theme.muted
import com.local.artifactboost.ui.theme.orange
import com.local.artifactboost.ui.theme.strongText
import com.local.artifactboost.ui.theme.subtle
import com.local.artifactboost.ui.theme.surface

/** 搜索全站仓库：别人的公开仓库也能搜到，直接进去下产物 */
@Composable
fun SearchScreen(vm: AppViewModel, onOpen: (com.local.artifactboost.data.GHRepo) -> Unit) {
    Column(Modifier.fillMaxSize().background(canvas())) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(canvas())
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "搜索仓库",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = strongText(),
            )
            OutlinedTextField(
                value = vm.searchQuery,
                onValueChange = { vm.updateQuery(it) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                placeholder = { Text("仓库名、关键词、owner/repo", fontSize = 14.sp, color = subtle()) },
                leadingIcon = {
                    Icon(Icons.Filled.Search, null, Modifier.size(18.dp), tint = subtle())
                },
                trailingIcon = {
                    if (vm.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { vm.updateQuery("") }) {
                            Icon(Icons.Filled.Close, null, Modifier.size(17.dp), tint = subtle())
                        }
                    }
                },
            )
        }

        Spacer(Modifier.padding(top = 12.dp))

        val results = vm.searchResults

        when {
            vm.isSearching && results == null -> LazyColumn(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(4) { com.local.artifactboost.ui.components.CardBox { SkeletonBlock(3) } }
            }

            vm.searchError != null -> Box(Modifier.padding(16.dp)) {
                ErrorBanner(vm.searchError ?: "", orange())
            }

            results == null -> HintText(
                "输入关键词开始搜索。搜到的公开仓库可以直接下载它的构建产物、正式版和源码包。",
            )

            results.isEmpty() -> com.local.artifactboost.ui.components.EmptyState(
                Icons.Outlined.SearchOff,
                "没有匹配的仓库",
                "换个关键词试试，或者加上语言过滤，例如「swift 下载器」",
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("找到 ${results.size} 个仓库", fontSize = 12.sp, color = subtle())
                        Spacer(Modifier.weight(1f))
                        if (vm.isSearching) {
                            CircularProgressIndicator(Modifier.size(13.dp), strokeWidth = 2.dp)
                        }
                    }
                }
                items(results, key = { it.id }) { repo ->
                    RepoRow(repo) { onOpen(repo) }
                }
            }
        }
    }
}

@Composable
private fun HintText(text: String) {
    Row(Modifier.padding(28.dp)) {
        Icon(Icons.Filled.Search, null, Modifier.size(15.dp), tint = subtle())
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 13.sp, color = subtle())
    }
}
