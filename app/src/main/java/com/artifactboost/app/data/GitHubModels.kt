package com.artifactboost.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * GitHub REST API 数据模型。
 * 全部字段可空或带默认值：GitHub 不同接口返回的字段并不一致，
 * 宽松解析可以避免某个字段缺失就把整个响应判为失败。
 */

@Serializable
data class GHUser(
    val login: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

@Serializable
data class GHRepo(
    val id: Long,
    val name: String,
    @SerialName("full_name") val fullName: String,
    @SerialName("private") val isPrivate: Boolean = false,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("default_branch") val defaultBranch: String? = null,
    val language: String? = null,
    @SerialName("stargazers_count") val stargazersCount: Int? = null,
    @SerialName("forks_count") val forksCount: Int? = null,
    val description: String? = null,
    /** 未关闭的 issue 数（仅详情接口返回） */
    @SerialName("open_issues_count") val openIssuesCount: Int? = null,
) {
    /** owner/repo 里的 owner 部分 */
    val owner: String get() = fullName.substringBefore('/', "")
}

@Serializable
data class RepoSearchResponse(
    val items: List<GHRepo> = emptyList(),
)

@Serializable
data class GHWorkflowRun(
    val id: Long,
    val name: String? = null,
    @SerialName("display_title") val displayTitle: String? = null,
    @SerialName("run_number") val runNumber: Int = 0,
    val status: String? = null,
    val conclusion: String? = null,
    @SerialName("head_branch") val headBranch: String? = null,
    val event: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
) {
    val isRunning: Boolean get() = status != "completed"
}

@Serializable
data class RunsResponse(
    @SerialName("total_count") val totalCount: Int = 0,
    @SerialName("workflow_runs") val workflowRuns: List<GHWorkflowRun> = emptyList(),
)

@Serializable
data class GHArtifact(
    val id: Long,
    val name: String,
    @SerialName("size_in_bytes") val sizeInBytes: Long = 0,
    val expired: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
)

@Serializable
data class ArtifactsResponse(
    @SerialName("total_count") val totalCount: Int = 0,
    val artifacts: List<GHArtifact> = emptyList(),
)

/** 正式版（Release） */
@Serializable
data class GHRelease(
    val id: Long,
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @SerialName("published_at") val publishedAt: String? = null,
    val assets: List<GHReleaseAsset> = emptyList(),
) {
    /** 展示名：优先用 name，为空时退回 tag */
    val displayName: String get() = if (!name.isNullOrEmpty()) name else tagName
}

@Serializable
data class GHReleaseAsset(
    val id: Long,
    val name: String,
    val size: Long = 0,
    @SerialName("download_count") val downloadCount: Int = 0,
    @SerialName("content_type") val contentType: String? = null,
)

@Serializable
data class GHBranch(
    val name: String,
)

/**
 * README 原文。
 * 走 `Accept: application/vnd.github.raw` 直接拿 Markdown 文本，
 * 省掉一次 base64 解码。
 */
data class GHReadme(
    /** 仓库内的文件路径，例如 docs/README.md */
    val path: String,
    /** Markdown 正文 */
    val text: String,
)
