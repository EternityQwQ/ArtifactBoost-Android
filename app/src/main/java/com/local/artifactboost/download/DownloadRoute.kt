package com.local.artifactboost.download

/**
 * 下载通道：直连 Azure 签名地址，或经由镜像 / 自建反代中转（前缀 + 原始地址）。
 * 与 iOS 版 DownloadRoute 一致。
 */
data class DownloadRoute(val name: String, val prefix: String) {
    val isDirect: Boolean get() = prefix.isEmpty()

    fun apply(url: String): String =
        if (prefix.isEmpty()) url else prefix + url

    companion object {
        val Direct = DownloadRoute("直连", "")

        /**
         * 内置公共镜像。它们只是中转「已签名的产物地址」，不接触 Token；
         * 但私有仓库的产物不应经过第三方，所以只在公开仓库且用户开启智能加速时使用。
         * 不同节点往往落在不同机房/线路上，多通道并行时带宽可以叠加。
         */
        val BuiltInMirrors = listOf(
            DownloadRoute("gh-proxy.com", "https://gh-proxy.com/"),
            DownloadRoute("slink.ltd", "https://slink.ltd/"),
            DownloadRoute("hk.gh-proxy.com", "https://hk.gh-proxy.com/"),
            DownloadRoute("moeyy.xyz", "https://github.moeyy.xyz/"),
        )

        /** 补全并校验用户填的前缀，非法时返回空串 */
        fun normalizedPrefix(raw: String): String {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return ""
            if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) return ""
            return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
        }
    }
}

/** 带实测速度的通道，用于按速度分配分块 */
data class ScoredRoute(val route: DownloadRoute, val speed: Double)

enum class RouteMode(val title: String, val detail: String) {
    DIRECT("直连", "直接连 GitHub 存储，最安全，但国内通常很慢"),
    SMART("智能加速", "自动在直连与公共镜像之间测速，选最快的通道"),
    CUSTOM("自定义", "使用你自己搭建的中转（Cloudflare Worker / 反向代理）"),
}
