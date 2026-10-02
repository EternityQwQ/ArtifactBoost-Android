package com.local.artifactboost.data

/** 能加速下载的东西：构建产物 / 构建日志 / 正式版附件 / 源码包 */
sealed interface DownloadSource {
    val repo: String

    data class Artifact(override val repo: String, val id: Long) : DownloadSource
    data class RunLogs(override val repo: String, val runId: Long) : DownloadSource
    data class ReleaseAsset(override val repo: String, val assetId: Long) : DownloadSource
    data class SourceArchive(
        override val repo: String,
        val ref: String,
        val format: ArchiveFormat,
    ) : DownloadSource

    /** 源码包由 GitHub 现场打包，不支持 Range 分段，只能单连接下载 */
    val supportsChunkedDownload: Boolean
        get() = this !is SourceArchive

    val kindName: String
        get() = when (this) {
            is Artifact -> "构建产物"
            is RunLogs -> "构建日志"
            is ReleaseAsset -> "正式版附件"
            is SourceArchive -> "源码包"
        }
}

enum class ArchiveFormat(val path: String, val fileExtension: String, val title: String) {
    ZIP("zipball", "zip", "ZIP"),
    TARBALL("tarball", "tar.gz", "TAR.GZ"),
}

/** 界面上一行「可下载项」 */
data class DownloadItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val size: Long?,
    val isPrivate: Boolean,
    val source: DownloadSource,
) {
    /** 落盘文件名 */
    val fileName: String
        get() = when (val s = source) {
            is DownloadSource.Artifact -> sanitize(title) + ".zip"
            is DownloadSource.RunLogs -> sanitize(title) + "-logs.zip"
            is DownloadSource.ReleaseAsset -> sanitize(title)
            is DownloadSource.SourceArchive -> {
                val base = if (s.ref.isEmpty()) "source" else sanitize(s.ref)
                "${sanitize(title)}-$base.${s.format.fileExtension}"
            }
        }

    private fun sanitize(raw: String): String =
        raw.replace("/", "_").replace(":", "_").trim()

    companion object {
        fun artifact(artifact: GHArtifact, repo: GHRepo) = DownloadItem(
            id = "artifact-${artifact.id}",
            title = artifact.name,
            subtitle = "构建产物 · ${formatDateTime(artifact.createdAt) ?: "时间未知"}",
            size = artifact.sizeInBytes,
            isPrivate = repo.isPrivate,
            source = DownloadSource.Artifact(repo.fullName, artifact.id),
        )

        fun runLogs(run: GHWorkflowRun, repo: GHRepo) = DownloadItem(
            id = "logs-${run.id}",
            title = "${run.name ?: "Workflow"} #${run.runNumber} 日志",
            subtitle = "构建日志 · ${run.headBranch ?: "-"}",
            size = null,
            isPrivate = repo.isPrivate,
            source = DownloadSource.RunLogs(repo.fullName, run.id),
        )

        fun releaseAsset(asset: GHReleaseAsset, release: GHRelease, repo: GHRepo) = DownloadItem(
            id = "asset-${asset.id}",
            title = asset.name,
            subtitle = "${release.displayName} · 下载 ${asset.downloadCount} 次",
            size = asset.size,
            isPrivate = repo.isPrivate,
            source = DownloadSource.ReleaseAsset(repo.fullName, asset.id),
        )

        fun sourceArchive(repo: GHRepo, ref: String, format: ArchiveFormat): DownloadItem {
            val label = ref.ifEmpty { repo.defaultBranch ?: "默认分支" }
            return DownloadItem(
                id = "source-${repo.fullName}-$label-${format.name}",
                title = "${repo.name}-$label",
                subtitle = "源码包 · ${format.title}",
                size = null,
                isPrivate = repo.isPrivate,
                source = DownloadSource.SourceArchive(repo.fullName, ref, format),
            )
        }
    }
}
