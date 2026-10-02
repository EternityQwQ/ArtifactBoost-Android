package com.artifactboost.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.artifactboost.app.data.GHRepo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 我的仓库列表的状态。
 *
 * 关键点：状态挂在 ViewModel 上而不是 Composable 的 remember 上。
 * 之前用 remember + LaunchedEffect(Unit) 时，切到别的 Tab 会让这个 Composable
 * 直接离开组合，回来时状态被清空、又重新拉一次数据；同时 PullToRefreshBox
 * 的 isRefreshing 会在空列表上一直转圈，看起来就像「刷新卡住了」。
 * ViewModel 的生命周期跟随 Activity，切 Tab 不会触发重复加载。
 */
class RepoListViewModel(
    private val session: com.artifactboost.app.data.SessionManager,
) : ViewModel() {

    private val _repos = MutableStateFlow<List<GHRepo>>(emptyList())
    val repos: StateFlow<List<GHRepo>> = _repos.asStateFlow()

    /** 首屏 / 手动下拉刷新时才为 true；用于驱动下拉指示器 */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /** 首次加载完成前保持 true，避免空列表闪一下「还没有仓库」 */
    private val _isInitialLoading = MutableStateFlow(true)
    val isInitialLoading: StateFlow<Boolean> = _isInitialLoading.asStateFlow()

    /** 同一时刻只允许一个加载任务在跑，防止多次进入 Tab 叠加请求 */
    private var loadJob: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch {
            // 冷启动时 Token 还在异步恢复，等 client 就绪再拉，
            // 否则会「加载了个寂寞」，然后一直空着。
            val client = session.client.filterNotNull().first()
            load(client)
        }
    }

    fun refresh() {
        if (loadJob?.isActive == true) return
        val client = session.client.value ?: run {
            _isInitialLoading.value = false
            return
        }
        load(client)
    }

    private fun load(client: com.artifactboost.app.data.GitHubClient) {
        loadJob = viewModelScope.launch {
            _isRefreshing.value = true
            _errorMessage.value = null
            try {
                val all = mutableListOf<GHRepo>()
                for (page in 1..3) {
                    val batch = client.repos(page)
                    all += batch
                    if (batch.size < 100) break
                }
                _repos.value = all
            } catch (e: Exception) {
                // 401 会自动登出，这里只把文案显示出来
                _errorMessage.value = session.message(e)
            } finally {
                _isRefreshing.value = false
                _isInitialLoading.value = false
            }
        }
    }

    companion object {
        fun factory(session: com.artifactboost.app.data.SessionManager) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    RepoListViewModel(session) as T
            }
    }
}

@androidx.compose.runtime.Composable
fun repoListViewModel(
    session: com.artifactboost.app.data.SessionManager,
): RepoListViewModel {
    // 登录态变化（换 Token）时重建，保证用的是新 client
    val tokenKey = session.client.value?.token?.hashCode() ?: 0
    return viewModel(
        key = "repoList-$tokenKey",
        factory = RepoListViewModel.factory(session),
    )
}
