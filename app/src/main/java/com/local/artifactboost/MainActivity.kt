package com.local.artifactboost

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.local.artifactboost.data.GHRelease
import com.local.artifactboost.data.GHRepo
import com.local.artifactboost.data.GHWorkflowRun
import com.local.artifactboost.download.DownloadState
import com.local.artifactboost.runtime.AppViewModel
import com.local.artifactboost.ui.components.Hairline
import com.local.artifactboost.ui.screens.DownloadsScreen
import com.local.artifactboost.ui.screens.LoginScreen
import com.local.artifactboost.ui.screens.ReleaseDetailScreen
import com.local.artifactboost.ui.screens.RepoDetailScreen
import com.local.artifactboost.ui.screens.RepoListScreen
import com.local.artifactboost.ui.screens.RunDetailScreen
import com.local.artifactboost.ui.screens.SearchScreen
import com.local.artifactboost.ui.screens.SettingsScreen
import com.local.artifactboost.ui.theme.ArtifactBoostTheme
import com.local.artifactboost.ui.theme.accent
import com.local.artifactboost.ui.theme.canvas
import com.local.artifactboost.ui.theme.muted
import com.local.artifactboost.ui.theme.strongText
import com.local.artifactboost.ui.theme.subtle
import com.local.artifactboost.ui.theme.surface
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as ArtifactBoostApp
        setContent {
            ArtifactBoostTheme {
                Surface(color = canvas(), modifier = Modifier.fillMaxSize()) {
                    AppRoot(app)
                }
            }
        }
    }
}

/** 底部 Tab */
enum class HomeTab(val title: String, val icon: ImageVector) {
    REPOS("仓库", Icons.Filled.Folder),
    SEARCH("搜索", Icons.Filled.Search),
    DOWNLOADS("下载", Icons.Filled.Download),
    SETTINGS("设置", Icons.Filled.Settings),
}

/** 页面栈里的一层 */
sealed interface Route {
    data class RepoDetail(val repo: GHRepo) : Route
    data class RunDetail(val run: GHWorkflowRun, val repo: GHRepo) : Route
    data class ReleaseDetail(val release: GHRelease, val repo: GHRepo) : Route
}

@Composable
private fun AppRoot(app: ArtifactBoostApp) {
    var vm by remember { mutableStateOf<AppViewModel?>(null) }
    LaunchedEffect(Unit) {
        val created = AppContainer.createViewModel(app)
        vm = created
        // 恢复上次登录的 Token
        created.restore()
    }

    val model = vm
    if (model == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = accent())
        }
        return
    }

    val user = model.user
    if (user == null) {
        LoginScreen(model)
        return
    }

    HomeShell(model)
}

@Composable
private fun HomeShell(vm: AppViewModel) {
    var tab by remember { mutableStateOf(HomeTab.REPOS) }
    val stack = remember { mutableListOf<Route>() }
    var stackVersion by remember { mutableIntStateOf(0) }

    val scope = rememberCoroutineScope()

    fun push(route: Route) {
        stack.add(route)
        stackVersion++
    }

    fun pop() {
        if (stack.isNotEmpty()) {
            stack.removeAt(stack.lastIndex)
            stackVersion++
        }
    }

    // 处理系统返回键 / 返回手势
    androidx.activity.compose.BackHandler(enabled = stack.isNotEmpty()) { pop() }

    val current = stack.lastOrNull()

    Box(Modifier.fillMaxSize().background(canvas())) {
        if (current != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.navigationBars),
            ) {
                DetailTopBar(current, onBack = { pop() })
                when (current) {
                    is Route.RepoDetail -> RepoDetailScreen(
                        repo = current.repo,
                        vm = vm,
                        onOpenRun = { push(Route.RunDetail(it, current.repo)) },
                        onOpenRelease = { push(Route.ReleaseDetail(it, current.repo)) },
                    )

                    is Route.RunDetail -> RunDetailScreen(current.run, current.repo, vm)

                    is Route.ReleaseDetail -> ReleaseDetailScreen(current.release, current.repo, vm)
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    when (tab) {
                        HomeTab.REPOS -> RepoListScreen(vm) { push(Route.RepoDetail(it)) }
                        HomeTab.SEARCH -> SearchScreen(vm) { push(Route.RepoDetail(it)) }
                        HomeTab.DOWNLOADS -> DownloadsScreen(vm)
                        HomeTab.SETTINGS -> SettingsScreen(vm) {
                            scope.launch {
                                vm.logout()
                                stack.clear()
                                stackVersion++
                            }
                        }
                    }
                }
                BottomBar(tab, vm) { tab = it }
            }
        }
    }
}

@Composable
private fun DetailTopBar(route: Route, onBack: () -> Unit) {
    Column(Modifier.background(surface())) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = accent(),
                )
            }
            Text(
                text = when (route) {
                    is Route.RepoDetail -> route.repo.name
                    is Route.RunDetail -> "构建 #${route.run.runNumber}"
                    is Route.ReleaseDetail -> route.release.tagName
                },
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = strongText(),
                maxLines = 1,
            )
        }
        Hairline()
    }
}

@Composable
private fun BottomBar(selected: HomeTab, vm: AppViewModel, onSelect: (HomeTab) -> Unit) {
    val states by vm.downloadStates.collectAsState()
    val active = states.values.count {
        it is DownloadState.Downloading || it is DownloadState.Resolving
    }

    Column(Modifier.background(surface())) {
        Hairline()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(surface())
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            HomeTab.entries.forEach { item ->
                val isSelected = item == selected
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelect(item) }
                        .padding(vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Box {
                        Icon(
                            item.icon,
                            contentDescription = item.title,
                            tint = if (isSelected) accent() else subtle(),
                            modifier = Modifier.size(21.dp),
                        )
                        if (item == HomeTab.DOWNLOADS && active > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(accent()),
                            )
                        }
                    }
                    Text(
                        item.title,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) accent() else subtle(),
                    )
                }
            }
        }
    }
}
