package com.local.artifactboost.runtime

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.artifactboost.SessionManager
import com.local.artifactboost.data.DownloadItem
import com.local.artifactboost.data.GHBranch
import com.local.artifactboost.data.GHReadme
import com.local.artifactboost.data.GHRelease
import com.local.artifactboost.data.GHRepo
import com.local.artifactboost.data.GHUser
import com.local.artifactboost.data.GHWorkflowRun
import com.local.artifactboost.download.AccelerationSettings
import com.local.artifactboost.download.DownloadManager
import com.local.artifactboost.download.DownloadState
import com.local.artifactboost.net.GitHubClient
import com.local.artifactboost.net.GitHubException
import com.local.artifactboost.net.RepoSort
import com.local.artifactboost.ui.screens.RepoTab
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 全局状态容器。iOS 里对应一堆 `@Published` + ObservableObject 的组合，
 * Android 侧统一收到一个 ViewModel 里，Compose 直接观察。
 *
 * 关键设计：**没有仓库的数据一律用 `null` 表示「未加载」**，
 * 而不是空列表——空列表要区分「加载完了确实是空的」和「还没开始加载」，
 * 前者渲染空态，后者渲染骨架屏。
 */
class AppViewModel(
    val session: SessionManager,
    val downloads: DownloadManager,
    private val settings: AccelerationSettings,
    /** 加速设置的读写需要 Context（DataStore），设置页测速时要用 */
    private val appContext: android.content.Context,
) : ViewModel() {

    fun context(): android.content.Context = appContext

    // MARK: - 会话

    var user by mutableStateOf<GHUser?>(null)
        private set

    var loginError by mutableStateOf<String?>(null)
        private set

    var isLoggingIn by mutableStateOf(false)
        private set

    // MARK: - 仓库

    var repos by mutableStateOf<List<GHRepo>?>(null)
        private set

    var isLoadingRepos by mutableStateOf(false)
        private set

    var reposError by mutableStateOf<String?>(null)
        private set

    var repoSort by mutableStateOf(RepoSort.UPDATED)
        private set

    var isRefreshing by mutableStateOf(false)
        private set

    // MARK: - 搜索

    var searchQuery by mutableStateOf("")
        private set

    var searchResults by mutableStateOf<List<GHRepo>?>(null)
        private set

    var isSearching by mutableStateOf(false)
        private set

    var searchError by mutableStateOf<String?>(null)
        private set

    private var searchJob: Job? = null

    // MARK: - 每个仓库的缓存

    private var readmeCache by mutableStateOf<Map<String, GHReadme?>>(emptyMap())
    private var readmeLoading by mutableStateOf<Set<String>>(emptySet())
    private var runsCache by mutableStateOf<Map<String, List<GHWorkflowRun>?>>(emptyMap())
    private var releasesCache by mutableStateOf<Map<String, List<GHRelease>?>>(emptyMap())
    private var branchesCache by mutableStateOf<Map<String, List<GHBranch>>>(emptyMap())
    private var commitCounts by mutableStateOf<Map<String, Int>>(emptyMap())
    private var selectedRefs by mutableStateOf<Map<String, String>>(emptyMap())
    private var tabLoading by mutableStateOf<Set<String>>(emptySet())
    private var repoErrors by mutableStateOf<Map<String, String>>(emptyMap())

    // MARK: - 下载

    val downloadStates: StateFlow<Map<String, DownloadState>> get() = downloads.states
    val downloadOrder: StateFlow<List<String>> get() = downloads.order

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    // MARK: - 登录

    fun login(token: String) {
        if (isLoggingIn) return
        isLoggingIn = true
        loginError = null
        viewModelScope.launch {
            session.login(token)
                .onSuccess {
                    user = it
                    repos = null
                    loadRepos()
                }
                .onFailure { loginError = SessionManager.messageFor(it) }
            isLoggingIn = false
        }
    }

    fun restore() {
        viewModelScope.launch {
            session.restore()
            user = session.user.value
        }
    }

    suspend fun logout() {
        session.logout()
        user = null
        repos = null
        searchResults = null
        searchQuery = ""
        readmeCache = emptyMap()
        runsCache = emptyMap()
        releasesCache = emptyMap()
        branchesCache = emptyMap()
        commitCounts = emptyMap()
        selectedRefs = emptyMap()
        repoErrors = emptyMap()
    }

    // MARK: - 仓库列表

    fun loadRepos() {
        val client = session.client.value ?: return
        if (isLoadingRepos) return
        isLoadingRepos = true
        reposError = null
        viewModelScope.launch {
            try {
                repos = client.repos(sort = repoSort)
            } catch (e: Throwable) {
                reposError = friendly(e)
            } finally {
                isLoadingRepos = false
                isRefreshing = false
            }
        }
    }

    fun refresh() {
        if (isRefreshing) return
        isRefreshing = true
        loadRepos()
    }

    fun changeSort(sort: RepoSort) {
        if (repoSort == sort) return
        repoSort = sort
        repos = null
        loadRepos()
    }

    // MARK: - 搜索

    fun updateQuery(query: String) {
        searchQuery = query
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            searchResults = null
            searchError = null
            isSearching = false
            return
        }
        searchJob = viewModelScope.launch {
            // 轻量防抖，避免每敲一个字母都打一次 API
            kotlinx.coroutines.delay(320)
            val client = session.client.value ?: return@launch
            isSearching = true
            searchError = null
            try {
                searchResults = client.searchRepos(trimmed)
            } catch (e: Throwable) {
                searchError = friendly(e)
            } finally {
                isSearching = false
            }
        }
    }

    // MARK: - 详情页

    fun loadHeaderStats(repo: GHRepo) {
        if (commitCounts.containsKey(repo.fullName)) return
        val client = session.client.value ?: return
        viewModelScope.launch {
            val count = runCatching { client.commitCount(repo, repo.defaultBranch) }.getOrNull()
            if (count != null) commitCounts = commitCounts + (repo.fullName to count)
        }
    }

    fun loadTab(repo: GHRepo, tab: RepoTab) {
        when (tab) {
            RepoTab.OVERVIEW -> loadOverview(repo)
            RepoTab.BUILDS -> loadRuns(repo)
            RepoTab.RELEASES -> loadReleases(repo)
            RepoTab.SOURCE -> loadBranches(repo)
        }
    }

    private fun loadOverview(repo: GHRepo) {
        val key = repo.fullName
        if (readmeCache.containsKey(key) || key in readmeLoading) return
        val client = session.client.value ?: return
        readmeLoading = readmeLoading + key
        viewModelScope.launch {
            try {
                readmeCache = readmeCache + (key to client.readme(repo))
            } catch (e: Throwable) {
                // README 不存在是常态，不当成错误页
                readmeCache = readmeCache + (key to null)
                if (e !is GitHubException.Http || e.code != 404) setError(key, friendly(e))
            } finally {
                readmeLoading = readmeLoading - key
            }
        }
    }

    private fun loadRuns(repo: GHRepo) {
        val key = repo.fullName
        if (runsCache.containsKey(key) || key in tabLoading) return
        val client = session.client.value ?: return
        tabLoading = tabLoading + key
        viewModelScope.launch {
            try {
                runsCache = runsCache + (key to client.workflowRuns(repo))
            } catch (e: Throwable) {
                runsCache = runsCache + (key to emptyList())
                setError(key, friendly(e))
            } finally {
                tabLoading = tabLoading - key
            }
        }
    }

    private fun loadReleases(repo: GHRepo) {
        val key = repo.fullName
        if (releasesCache.containsKey(key) || key in tabLoading) return
        val client = session.client.value ?: return
        tabLoading = tabLoading + key
        viewModelScope.launch {
            try {
                releasesCache = releasesCache + (key to client.releases(repo))
            } catch (e: Throwable) {
                releasesCache = releasesCache + (key to emptyList())
                setError(key, friendly(e))
            } finally {
                tabLoading = tabLoading - key
            }
        }
    }

    private fun loadBranches(repo: GHRepo) {
        val key = repo.fullName
        if (branchesCache.containsKey(key)) return
        val client = session.client.value ?: return
        viewModelScope.launch {
            val branches = runCatching { client.branches(repo) }.getOrElse { emptyList() }
            branchesCache = branchesCache + (key to branches)
            // 默认选中仓库的默认分支
            if (!selectedRefs.containsKey(key)) {
                val fallback = repo.defaultBranch ?: branches.firstOrNull()?.name ?: "main"
                selectedRefs = selectedRefs + (key to fallback)
            }
        }
    }

    fun selectRef(repo: GHRepo, ref: String) {
        selectedRefs = selectedRefs + (repo.fullName to ref)
    }

    fun badgeFor(repo: GHRepo, tab: RepoTab): Int? = when (tab) {
        RepoTab.BUILDS -> runsCache[repo.fullName]?.size?.takeIf { it > 0 }
        RepoTab.RELEASES -> releasesCache[repo.fullName]?.size?.takeIf { it > 0 }
        else -> null
    }

    fun errorFor(repo: GHRepo): String? = repoErrors[repo.fullName]

    fun readmeFor(repo: GHRepo): GHReadme? = readmeCache[repo.fullName]

    fun isReadmeLoading(repo: GHRepo): Boolean =
        repo.fullName in readmeLoading || !readmeCache.containsKey(repo.fullName)

    fun runsFor(repo: GHRepo): List<GHWorkflowRun>? = runsCache[repo.fullName]

    fun releasesFor(repo: GHRepo): List<GHRelease>? = releasesCache[repo.fullName]

    fun branchesFor(repo: GHRepo): List<GHBranch>? = branchesCache[repo.fullName]

    fun selectedRefFor(repo: GHRepo): String =
        selectedRefs[repo.fullName] ?: repo.defaultBranch ?: ""

    fun commitCountFor(repo: GHRepo): Int? = commitCounts[repo.fullName]

    fun isTabLoading(repo: GHRepo, tab: RepoTab): Boolean {
        val key = repo.fullName
        return when (tab) {
            RepoTab.BUILDS -> runsCache[key] == null && key in tabLoading
            RepoTab.RELEASES -> releasesCache[key] == null && key in tabLoading
            else -> false
        }
    }

    private fun setError(key: String, message: String) {
        repoErrors = repoErrors + (key to message)
    }

    // MARK: - 下载

    fun downloadState(itemId: String): DownloadState = downloads.stateFor(itemId)

    fun routeSummaryFor(itemId: String): String? = downloads.routeSummary.value[itemId]

    fun startDownload(item: DownloadItem) {
        downloads.start(item, settings)
    }

    fun cancelDownload(itemId: String) {
        downloads.cancel(itemId)
    }

    fun removeDownload(itemId: String) {
        downloads.remove(itemId)
    }

    fun clearFinishedDownloads() {
        downloads.clearFinished()
    }

    fun pushToast(message: String) {
        _toast.value = message
    }

    fun consumeToast() {
        _toast.value = null
    }

    // MARK: - 工具

    /** 把异常翻译成人能看懂的一句话 */
    private fun friendly(e: Throwable): String = when (e) {
        is GitHubException -> e.message ?: "请求失败"
        is java.io.IOException -> "网络连接失败，请检查网络"
        else -> e.message ?: "未知错误"
    }

    override fun onCleared() {
        super.onCleared()
        searchJob?.cancel()
    }
}
