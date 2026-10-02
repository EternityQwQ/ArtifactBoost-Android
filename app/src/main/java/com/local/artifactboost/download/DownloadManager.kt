package com.local.artifactboost.download

import android.content.Context
import com.local.artifactboost.data.DownloadItem
import com.local.artifactboost.data.DownloadSource
import com.local.artifactboost.data.formatSpeed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.local.artifactboost.net.GitHubClient
import com.local.artifactboost.net.GitHubException
import java.io.File

/** 下载任务状态 */
sealed interface DownloadState {
    data object Idle : DownloadState
    data object Resolving : DownloadState
    data class Downloading(val progress: DownloadProgress) : DownloadState
    data class Finished(val file: File) : DownloadState
    data class Failed(val message: String) : DownloadState
}

/**
 * 下载编排：解析签名地址 → 选通道 → 下载，含整体重试与回退直连。
 * 与 iOS 版 DownloadManager 的逻辑保持一致。
 */
class DownloadManager(
    private val context: Context,
    private val session: com.local.artifactboost.SessionManager,
    private val outputDir: File,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    private val _states = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val states: StateFlow<Map<String, DownloadState>> = _states.asStateFlow()

    private val _routeSummary = MutableStateFlow<Map<String, String>>(emptyMap())
    val routeSummary: StateFlow<Map<String, String>> = _routeSummary.asStateFlow()

    /** 下载顺序：最新的在最前 */
    private val _order = MutableStateFlow<List<String>>(emptyList())
    val order: StateFlow<List<String>> = _order.asStateFlow()

    private val _items = MutableStateFlow<Map<String, DownloadItem>>(emptyMap())
    val items: StateFlow<Map<String, DownloadItem>> = _items.asStateFlow()

    private val engines = mutableMapOf<String, DownloadEngine>()
    private val jobs = mutableMapOf<String, kotlinx.coroutines.Job>()

    val activeCount: Int
        get() = _states.value.values.count {
            it is DownloadState.Downloading || it is DownloadState.Resolving
        }

    fun stateFor(itemId: String): DownloadState =
        _states.value[itemId] ?: DownloadState.Idle

    fun orderedItems(): List<DownloadItem> =
        _order.value.mapNotNull { _items.value[it] }

    fun start(item: DownloadItem, settings: AccelerationSettings) {
        val client = session.client.value ?: return
        val current = stateFor(item.id)
        if (current is DownloadState.Resolving || current is DownloadState.Downloading) return

        _items.value = _items.value + (item.id to item)
        if (_items.value[item.id] != null && item.id !in _order.value) {
            _order.value = listOf(item.id) + _order.value
        }
        updateState(item.id, DownloadState.Resolving)
        updateSummary(item.id, null)

        val engine = DownloadEngine()
        engines[item.id] = engine

        jobs[item.id] = scope.launch {
            try {
                val file = performDownload(item, client, engine, settings)
                updateState(item.id, DownloadState.Finished(file))
            } catch (e: Throwable) {
                if (isCancellation(e)) updateState(item.id, DownloadState.Idle)
                else updateState(item.id, DownloadState.Failed(e.message ?: "下载失败"))
            } finally {
                engines.remove(item.id)
                jobs.remove(item.id)
            }
        }
    }

    fun cancel(itemId: String) {
        engines[itemId]?.cancel()
        jobs[itemId]?.cancel()
    }

    fun remove(itemId: String) {
        cancel(itemId)
        _states.value = _states.value - itemId
        _routeSummary.value = _routeSummary.value - itemId
        _items.value = _items.value - itemId
        _order.value = _order.value - itemId
    }

    fun clearFinished() {
        _order.value.forEach { id ->
            when (_states.value[id]) {
                is DownloadState.Finished, is DownloadState.Failed, null -> remove(id)
                else -> Unit
            }
        }
    }

    // MARK: - 主流程

    /** 解析签名地址 → 选通道 → 下载。签名地址有时效，整体失败后重新解析再试一次。 */
    private suspend fun performDownload(
        item: DownloadItem,
        client: GitHubClient,
        engine: DownloadEngine,
        settings: AccelerationSettings,
    ): File {
        var lastError: Throwable = GitHubException.BadResponse
        for (attempt in 0..1) {
            try {
                val signed = client.resolveDownloadUrl(item.source)
                return run(item, engine, signed, settings)
            } catch (e: Throwable) {
                if (!shouldRetry(e)) throw e
                lastError = e
                if (attempt == 0) updateState(item.id, DownloadState.Resolving)
            }
        }
        throw lastError
    }

    private suspend fun run(
        item: DownloadItem,
        engine: DownloadEngine,
        signedUrl: String,
        settings: AccelerationSettings,
    ): File {
        val plan: List<ScoredRoute>
        val note: String

        val saved = settings.savedPlan(item.isPrivate)
        if (saved != null) {
            // 设置页已经测过速：直接用保存的最快通道
            plan = saved
            note = "${saved[0].route.name}（设置页测速 ${formatSpeed(saved[0].speed)}）"
        } else {
            val candidates = settings.candidateRoutes(item.isPrivate)
            if (candidates.size <= 1) {
                plan = listOf(ScoredRoute(candidates[0], 1.0))
                note = if (item.isPrivate && settings.mode == RouteMode.SMART)
                    "直连（私有仓库不走镜像）" else candidates[0].name
            } else {
                updateSummary(item.id, "正在测速选通道…")
                val measured = RouteProbe.measureAll(
                    candidates,
                    signedUrl,
                    item.size ?: RouteProbe.SAMPLE_BYTES,
                )
                val fastest = measured.firstOrNull()?.speed ?: 0.0
                // 只保留达到最快通道 40% 以上的，慢的通道并进来反而拖后腿
                val viable = measured.filter { it.speed >= fastest * 0.4 }
                if (viable.isEmpty()) {
                    plan = listOf(ScoredRoute(DownloadRoute.Direct, 1.0))
                    note = "直连（测速失败）"
                } else {
                    plan = viable
                    note = describe(viable) + "（实测 ${formatSpeed(fastest)}）"
                    if (settings.mode == RouteMode.SMART) {
                        // 顺手把结果存下来，下次下载和设置页都能直接复用
                        measured.firstOrNull()?.let {
                            AccelerationSettings.record(context, settings, it.route, it.speed)
                        }
                    }
                }
            }
        }

        val connections = settings.clampedConnections
        return try {
            val result = engine.download(
                signedUrl = signedUrl,
                routes = plan,
                fileName = item.fileName,
                connections = connections,
                outputDir = outputDir,
                onProgress = { progress ->
                    val state = stateFor(item.id)
                    if (state is DownloadState.Resolving ||
                        state is DownloadState.Downloading ||
                        state is DownloadState.Idle
                    ) {
                        updateState(item.id, DownloadState.Downloading(progress))
                    }
                },
            )
            updateSummary(item.id, "$note · 平均 ${formatSpeed(result.averageSpeed)}")
            result.file
        } catch (e: Throwable) {
            // 通道可能失效/被限流，整体回退直连再试一次
            if (!shouldRetry(e) || plan.none { !it.route.isDirect }) throw e
            val result = engine.download(
                signedUrl = signedUrl,
                routes = listOf(ScoredRoute(DownloadRoute.Direct, 1.0)),
                fileName = item.fileName,
                connections = connections,
                outputDir = outputDir,
                onProgress = { progress ->
                    updateState(item.id, DownloadState.Downloading(progress))
                },
            )
            updateSummary(
                item.id,
                "直连（$note 失败已回退） · 平均 ${formatSpeed(result.averageSpeed)}",
            )
            result.file
        }
    }

    /** 设置页测速用：在用户自己的仓库里找一个真实的下载目标 */
    suspend fun findTestTarget(client: GitHubClient): SpeedTestTarget? {
        val repos = runCatching { client.repos(1) }.getOrNull() ?: return null
        // 公开仓库优先：私有仓库的签名地址不应该交给镜像去测速
        val ordered = repos.sortedWith(compareBy({ if (it.isPrivate) 1 else 0 }, { it.name }))

        for (repo in ordered.take(5)) {
            val runs = runCatching { client.workflowRuns(repo) }.getOrNull()
            val run = runs?.firstOrNull() ?: continue

            val artifact = runCatching { client.artifacts(repo, run) }.getOrNull()
                ?.filter { !it.expired }
                ?.maxByOrNull { it.sizeInBytes }

            if (artifact != null) {
                val url = runCatching {
                    client.resolveDownloadUrl(DownloadSource.Artifact(repo.fullName, artifact.id))
                }.getOrNull()
                if (url != null) {
                    return SpeedTestTarget(url, "${repo.name} · ${artifact.name}", repo.isPrivate)
                }
            }

            val logsUrl = runCatching {
                client.resolveDownloadUrl(DownloadSource.RunLogs(repo.fullName, run.id))
            }.getOrNull()
            if (logsUrl != null) {
                return SpeedTestTarget(logsUrl, "${repo.name} · 构建日志", repo.isPrivate)
            }
        }
        return null
    }

    // MARK: - 工具

    private fun describe(plan: List<ScoredRoute>): String {
        val names = plan.map { if (it.route.isDirect) "直连" else it.route.name }
        return if (plan.size > 1) "多通道 " + names.joinToString(" + ") else names[0]
    }

    private fun updateState(id: String, state: DownloadState) {
        _states.value = _states.value + (id to state)
    }

    private fun updateSummary(id: String, summary: String?) {
        _routeSummary.value = if (summary == null) _routeSummary.value - id
        else _routeSummary.value + (id to summary)
    }

    private fun isCancellation(e: Throwable): Boolean =
        e is kotlinx.coroutines.CancellationException ||
            (e is DownloadException && e === DownloadException.Cancelled)

    /** 只有网络类错误才值得重试；权限、产物已删除等错误直接抛出 */
    private fun shouldRetry(e: Throwable): Boolean {
        if (isCancellation(e)) return false
        if (e is GitHubException) {
            return when (e) {
                is GitHubException.Http -> e.code >= 500 || e.code == 429
                GitHubException.BadResponse -> true
                else -> false
            }
        }
        return true
    }
}
