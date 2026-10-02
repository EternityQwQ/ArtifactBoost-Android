package com.local.artifactboost.net

import com.local.artifactboost.data.ArchiveFormat
import com.local.artifactboost.data.ArtifactsResponse
import com.local.artifactboost.data.DownloadSource
import com.local.artifactboost.data.GHArtifact
import com.local.artifactboost.data.GHBranch
import com.local.artifactboost.data.GHReadme
import com.local.artifactboost.data.GHRelease
import com.local.artifactboost.data.GHRepo
import com.local.artifactboost.data.GHUser
import com.local.artifactboost.data.GHWorkflowRun
import com.local.artifactboost.data.RepoSearchResponse
import com.local.artifactboost.data.RunsResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

sealed class GitHubException(message: String) : Exception(message) {
    object BadUrl : GitHubException("无效的请求地址")
    object BadResponse : GitHubException("服务器响应异常")
    object ArtifactExpired : GitHubException("该产物已过期，GitHub 已将其删除")
    object DownloadUrlNotFound : GitHubException("未能获取产物下载地址")

    class Http(val code: Int, val detail: String) : GitHubException(describe(code, detail)) {
        companion object {
            private fun describe(code: Int, detail: String): String = when (code) {
                401 -> "Token 无效或已过期（401），请重新登录"
                403 -> "权限不足或触发限流（403）$detail\n" +
                    "如果是别人的公开仓库：fine-grained Token 需要勾选 Public Repositories 只读；" +
                    "classic Token 勾了 repo 即可。"
                404 -> "未找到（404）：仓库不存在、是私有仓库，或你的 Token 没有被授权访问它。"
                else -> "请求失败（$code）$detail"
            }
        }
    }
}

private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/**
 * GitHub REST API v3 客户端。
 * 全程走 api.github.com，Token 只在本机使用，不经过任何第三方。
 */
class GitHubClient(private val token: String) {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** 不自动跟随跳转的客户端：用于拿到 302 的签名地址 */
    private val noRedirectClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private fun request(path: String, query: Map<String, String> = emptyMap()): Request.Builder {
        val url = buildString {
            append("https://api.github.com/")
            append(path)
            if (query.isNotEmpty()) {
                append("?")
                append(
                    query.entries.joinToString("&") { (k, v) ->
                        "${encode(k)}=${encode(v)}"
                    },
                )
            }
        }
        return Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private inline fun <reified T> decode(response: Response): T {
        val body = response.body?.string().orEmpty()
        if (response.code == 410) throw GitHubException.ArtifactExpired
        if (response.code !in 200..299) {
            val detail = runCatching { json.parseToJsonElement(body).toString() }.getOrNull()
                ?.let { Regex("\"message\"\\s*:\\s*\"([^\"]*)\"").find(it)?.groupValues?.get(1) }
                ?: ""
            throw GitHubException.Http(response.code, detail)
        }
        return json.decodeFromString(body)
    }

    private suspend inline fun <reified T> get(
        path: String,
        query: Map<String, String> = emptyMap(),
    ): T = withContext(Dispatchers.IO) {
        client.newCall(request(path, query).build()).execute().use { decode<T>(it) }
    }

    /** 验证 Token 并返回当前用户 */
    suspend fun currentUser(): GHUser = get("user")

    /** 兼容旧调用点 */
    suspend fun validateToken(): GHUser = currentUser()

    /** 当前用户可见的仓库（自己 + 协作 + 组织），按排序方式取第一页 */
    suspend fun repos(page: Int = 1): List<GHRepo> = repos(sort = RepoSort.UPDATED, page = page)

    /**
     * 当前用户可见的仓库。
     * `sort` 只影响 GitHub 侧的返回顺序，而 `pushed_at` 这种字段 GitHub 不支持直接排，
     * 所以在客户端再排一次兜底，保证「最近更新」真的按更新时间排。
     */
    suspend fun repos(sort: RepoSort, page: Int = 1): List<GHRepo> {
        val query = mutableMapOf(
            "per_page" to "100",
            "page" to "$page",
            "affiliation" to "owner,collaborator,organization_member",
        )
        when (sort) {
            RepoSort.STARS -> {
                query["sort"] = "stargazers"; query["direction"] = "desc"
            }
            RepoSort.UPDATED -> {
                query["sort"] = "updated"; query["direction"] = "desc"
            }
            RepoSort.BEST_MATCH -> {
                query["sort"] = "updated"; query["direction"] = "desc"
            }
        }
        val list = get<List<GHRepo>>("user/repos", query)
        return when (sort) {
            RepoSort.STARS -> list.sortedByDescending { it.stargazersCount ?: 0 }
            else -> list.sortedByDescending { it.updatedAt ?: "" }
        }
    }

    /**
     * 搜索全站仓库：不限于自己的仓库，别人的公开仓库也能搜到并下载。
     * `sort` 只作用在 GitHub 侧的搜索结果排序上。
     */
    suspend fun searchRepos(keyword: String, sort: RepoSort = RepoSort.BEST_MATCH): List<GHRepo> {
        val query = mutableMapOf("q" to keyword, "per_page" to "40")
        when (sort) {
            RepoSort.STARS -> {
                query["sort"] = "stars"; query["order"] = "desc"
            }
            RepoSort.UPDATED -> {
                query["sort"] = "updated"; query["order"] = "desc"
            }
            RepoSort.BEST_MATCH -> Unit
        }
        return get<RepoSearchResponse>("search/repositories", query).items
    }

    /** 按 owner/repo 取单个仓库（「直接打开仓库」用） */
    suspend fun repo(fullName: String): GHRepo = get("repos/$fullName")

    /** 仓库最近的 workflow 运行记录 */
    suspend fun workflowRuns(repo: GHRepo): List<GHWorkflowRun> =
        get<RunsResponse>("repos/${repo.fullName}/actions/runs", mapOf("per_page" to "30"))
            .workflowRuns

    /** 某次运行产生的产物列表 */
    suspend fun artifacts(repo: GHRepo, run: GHWorkflowRun): List<GHArtifact> =
        get<ArtifactsResponse>(
            "repos/${repo.fullName}/actions/runs/${run.id}/artifacts",
            mapOf("per_page" to "100"),
        ).artifacts

    /** 某个仓库的正式版（Release） */
    suspend fun releases(repo: GHRepo): List<GHRelease> =
        get("repos/${repo.fullName}/releases", mapOf("per_page" to "50"))

    /** 仓库分支（用于下载任意分支的源码包） */
    suspend fun branches(repo: GHRepo): List<GHBranch> =
        get("repos/${repo.fullName}/branches", mapOf("per_page" to "100"))

    /**
     * 取仓库 README 的 Markdown 原文。
     * `?ref=` 跟随当前选中的分支；`Accept: application/vnd.github.raw` 直接返回纯文本，
     * 省掉一次 base64 解码。404 表示没有 README，返回 null 而不是抛错。
     */
    suspend fun readme(repo: GHRepo, ref: String? = null): GHReadme? = withContext(Dispatchers.IO) {
        val target = if (!ref.isNullOrEmpty()) ref else (repo.defaultBranch ?: "")
        val query = if (target.isNotEmpty()) mapOf("ref" to target) else emptyMap()

        val builder = request("repos/${repo.fullName}/readme", query)
        builder.header("Accept", "application/vnd.github.raw")

        client.newCall(builder.build()).execute().use { response ->
            when {
                response.code == 404 -> null
                response.code !in 200..299 -> {
                    val body = response.body?.string().orEmpty()
                    throw GitHubException.Http(response.code, body)
                }
                else -> {
                    val text = response.body?.string().orEmpty()
                    if (text.isEmpty()) null
                    else GHReadme(
                        path = response.header("Content-Location")
                            ?.substringAfterLast("/")
                            ?: "README.md",
                        text = text,
                    )
                }
            }
        }
    }

    /**
     * 默认分支上的提交数（GitHub 用 Link 头的 last 页号给出，per_page=1 只需一次请求）。
     * 拿不到就返回 null，界面自动隐藏这一项。
     */
    suspend fun commitCount(repo: GHRepo, ref: String? = null): Int? = withContext(Dispatchers.IO) {
        val target = ref?.takeIf { it.isNotEmpty() } ?: repo.defaultBranch ?: "HEAD"
        val query = mapOf(
            "sha" to target,
            "per_page" to "1",
        )
        runCatching {
            client.newCall(request("repos/${repo.fullName}/commits", query).build())
                .execute().use { response ->
                    if (response.code !in 200..299) return@use null
                    val link = response.header("Link") ?: return@use null
                    lastPageNumber(link)
                }
        }.getOrNull()
    }

    /** 从 `Link: <...?page=42>; rel="last"` 里抠出 42 */
    private fun lastPageNumber(linkHeader: String): Int? =
        linkHeader.split(",")
            .firstOrNull { it.contains("rel=\"last\"") }
            ?.let { segment ->
                Regex("page=(\\d+)").find(segment)?.groupValues?.get(1)?.toIntOrNull()
            }

    /**
     * 解析任意下载项的签名地址。
     * GitHub 对这些接口都会 302 跳转到带签名的真实地址（产物/日志在 Azure Blob，
     * 源码包在 codeload），这里拦下跳转拿到真实地址，后续分段下载直接打这个地址
     * （不再需要 Token，也不再经过 api.github.com）。
     */
    suspend fun resolveDownloadUrl(source: DownloadSource): String = withContext(Dispatchers.IO) {
        val path = when (source) {
            is DownloadSource.Artifact -> "repos/${source.repo}/actions/artifacts/${source.id}/zip"
            is DownloadSource.RunLogs -> "repos/${source.repo}/actions/runs/${source.runId}/logs"
            is DownloadSource.ReleaseAsset -> "repos/${source.repo}/releases/assets/${source.assetId}"
            is DownloadSource.SourceArchive -> {
                val repo = source.repo
                val encoded = encode(source.ref)
                if (encoded.isEmpty()) "repos/$repo/${source.format.path}"
                else "repos/$repo/${source.format.path}/$encoded"
            }
        }

        val builder = request(path)
        // 附件接口默认返回 JSON 元数据，必须显式要二进制才会 302
        if (source is DownloadSource.ReleaseAsset) {
            builder.header("Accept", "application/octet-stream")
        }

        noRedirectClient.newCall(builder.build()).execute().use { response ->
            when {
                response.code == 410 -> throw GitHubException.ArtifactExpired
                response.code == 302 || response.code == 303 -> {
                    response.header("Location") ?: throw GitHubException.DownloadUrlNotFound
                }
                response.code !in 200..299 -> {
                    val body = response.body?.string().orEmpty()
                    throw GitHubException.Http(response.code, body)
                }
                else -> throw GitHubException.DownloadUrlNotFound
            }
        }
    }
}

enum class RepoSort(val title: String) {
    BEST_MATCH("最佳匹配"),
    STARS("星标最多"),
    UPDATED("最近更新"),
}
