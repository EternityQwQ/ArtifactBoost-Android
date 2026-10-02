package com.artifactboost.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.net.URLEncoder

/**
 * 下载通道：直连 Azure 签名地址，或经由镜像 / 自建反代中转（前缀 + 原始地址）。
 */
data class DownloadRoute(
    val name: String,
    val prefix: String,
) {
    val isDirect: Boolean get() = prefix.isEmpty()

    /** 把通道前缀套到已签名的产物地址上 */
    fun apply(signedUrl: String): String =
        if (prefix.isEmpty()) signedUrl else prefix + signedUrl

    companion object {
        val DIRECT = DownloadRoute("直连", "")

        /**
         * 内置公共镜像。它们只是中转「已签名的产物地址」，不接触 Token；
         * 但私有仓库的产物不应经过第三方，所以只在公开仓库且开启智能加速时使用。
         * 不同节点往往落在不同机房/线路，多通道并行时带宽可以叠加。
         */
        val BUILT_IN_MIRRORS = listOf(
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
data class ScoredRoute(
    val route: DownloadRoute,
    val speed: Double,
)

enum class RouteMode(val title: String, val detail: String) {
    DIRECT("直连", "直接连 GitHub 存储，最安全，但国内通常很慢"),
    SMART("智能加速", "自动在直连与公共镜像之间测速，选最快的通道"),
    CUSTOM("自定义", "使用你自己搭建的中转（Cloudflare Worker / 反向代理）"),
}

/**
 * 持久化的加速设置：在「设置」页调好并保存，下载时直接套用。
 * 对应 iOS 版的 `AccelerationSettings`（UserDefaults → SharedPreferences）。
 */
data class AccelerationSettings(
    val connections: Int = 16,
    val mode: RouteMode = RouteMode.SMART,
    val customPrefix: String = "",
    /** 「设置」页测速得到的最快通道 */
    val testedRoute: DownloadRoute? = null,
    val testedSpeed: Double = 0.0,
    val testedAtMillis: Long? = null,
) {
    val clampedConnections: Int get() = connections.coerceIn(1, 64)

    /**
     * 当前设置下的候选通道（直连永远保留兜底）。
     * 私有仓库一律只走直连，避免产物数据经过第三方。
     */
    fun candidateRoutes(isPrivateRepo: Boolean): List<DownloadRoute> = when (mode) {
        RouteMode.DIRECT -> listOf(DownloadRoute.DIRECT)

        RouteMode.CUSTOM -> {
            val prefix = DownloadRoute.normalizedPrefix(customPrefix)
            if (prefix.isEmpty()) listOf(DownloadRoute.DIRECT)
            else listOf(DownloadRoute("自定义加速", prefix), DownloadRoute.DIRECT)
        }

        RouteMode.SMART ->
            if (isPrivateRepo) listOf(DownloadRoute.DIRECT)
            else listOf(DownloadRoute.DIRECT) + DownloadRoute.BUILT_IN_MIRRORS
    }

    /** 可以直接沿用的测速结果（24 小时内有效、且不在私有仓库里用镜像） */
    fun savedPlan(isPrivateRepo: Boolean, nowMillis: Long = System.currentTimeMillis()): List<ScoredRoute>? {
        val route = testedRoute ?: return null
        val testedAt = testedAtMillis ?: return null
        if (nowMillis - testedAt >= 24 * 3600 * 1000L) return null
        if (isPrivateRepo && !route.isDirect) return null
        if (route !in candidateRoutes(isPrivateRepo)) return null
        return listOf(ScoredRoute(route, maxOf(testedSpeed, 0.01)))
    }

    fun record(route: DownloadRoute, speed: Double): AccelerationSettings =
        copy(testedRoute = route, testedSpeed = speed, testedAtMillis = System.currentTimeMillis())

    // MARK: - 持久化

    fun save(context: Context) {
        val store = prefs(context)
        store.edit()
            .putInt(KEY_CONNECTIONS, clampedConnections)
            .putString(KEY_MODE, mode.name)
            .putString(KEY_CUSTOM_PREFIX, customPrefix)
            .apply {
                val route = testedRoute
                if (route != null) {
                    putString(KEY_TESTED_NAME, route.name)
                    putString(KEY_TESTED_PREFIX, route.prefix)
                    putFloat(KEY_TESTED_SPEED, testedSpeed.toFloat())
                    putLong(KEY_TESTED_AT, testedAtMillis ?: System.currentTimeMillis())
                } else {
                    remove(KEY_TESTED_NAME)
                    remove(KEY_TESTED_PREFIX)
                    remove(KEY_TESTED_SPEED)
                    remove(KEY_TESTED_AT)
                }
            }
            .apply()
    }

    companion object {
        val CONNECTION_OPTIONS = listOf(8, 16, 32, 64)

        private const val PREFS_NAME = "artifactboost_settings"
        private const val KEY_CONNECTIONS = "ab.connections"
        private const val KEY_MODE = "ab.routeMode"
        private const val KEY_CUSTOM_PREFIX = "ab.customPrefix"
        private const val KEY_TESTED_NAME = "ab.testedRouteName"
        private const val KEY_TESTED_PREFIX = "ab.testedRoutePrefix"
        private const val KEY_TESTED_SPEED = "ab.testedRouteSpeed"
        private const val KEY_TESTED_AT = "ab.testedRouteDate"

        private fun prefs(context: Context): SharedPreferences =
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        fun load(context: Context): AccelerationSettings {
            val store = prefs(context)
            val storedConnections = store.getInt(KEY_CONNECTIONS, 0)
            val mode = runCatching {
                RouteMode.valueOf(store.getString(KEY_MODE, null) ?: RouteMode.SMART.name)
            }.getOrDefault(RouteMode.SMART)

            val testedName = store.getString(KEY_TESTED_NAME, null)
            val testedRoute = testedName?.let {
                DownloadRoute(it, store.getString(KEY_TESTED_PREFIX, "").orEmpty())
            }

            return AccelerationSettings(
                connections = if (storedConnections > 0) storedConnections else 16,
                mode = mode,
                customPrefix = store.getString(KEY_CUSTOM_PREFIX, "").orEmpty(),
                testedRoute = testedRoute,
                testedSpeed = store.getFloat(KEY_TESTED_SPEED, 0f).toDouble(),
                testedAtMillis = if (testedRoute != null) store.getLong(KEY_TESTED_AT, 0L) else null,
            )
        }
    }
}

/**
 * 通道测速：每个通道各拉一小段数据，取最快的那些。
 * 采样 256KB——快通道 0.1~0.5s 出结果，慢通道也不会拖太久。
 */
object RouteProbe {
    const val SAMPLE_BYTES: Long = 256 * 1024
    private const val TIMEOUT_MS = 6_000L

    /**
     * 探测专用客户端：限制总时长，
     * 防止某个通道不认 Range 时把整个文件都拉进内存。
     */
    private val probeClient: okhttp3.OkHttpClient by lazy {
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
            .readTimeout(TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
            .callTimeout(TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
            .followRedirects(true)
            .build()
    }

    /**
     * 逐条通道测速，返回按速度从快到慢排序的结果（失败的通道会被丢掉）。
     */
    suspend fun measureAll(
        routes: List<DownloadRoute>,
        signedUrl: String,
        onResult: (ScoredRoute) -> Unit = {},
    ): List<ScoredRoute> = coroutineScope {
        val results = routes.map { route ->
            async(Dispatchers.IO) {
                measure(route, signedUrl)?.also(onResult)
            }
        }.mapNotNull { it.await() }
        results.sortedByDescending { it.speed }
    }

    /** 单通道测速，失败返回 null */
    fun measure(route: DownloadRoute, signedUrl: String, limit: Long = SAMPLE_BYTES): ScoredRoute? {
        val request = okhttp3.Request.Builder()
            .url(route.apply(signedUrl))
            .header("Range", "bytes=0-${limit - 1}")
            .header("User-Agent", GitHubClient.USER_AGENT)
            .build()

        val startedAt = System.nanoTime()
        return try {
            probeClient.newCall(request).execute().use { response ->
                // 206 = 支持分段；200 说明目标本身小于采样长度（比如日志包），按实际收到字节算速度
                if (response.code != 206 && response.code != 200) return null
                val body = response.body ?: return null
                var received = 0L
                body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (received < limit) {
                        val read = input.read(buffer, 0, minOf(buffer.size.toLong(), limit - received).toInt())
                        if (read <= 0) break
                        received += read
                    }
                }
                if (received <= 0) return null
                val elapsed = maxOf((System.nanoTime() - startedAt) / 1_000_000_000.0, 0.05)
                ScoredRoute(route, received / elapsed)
            }
        } catch (_: Exception) {
            null
        }
    }
}

/** 估算给定字符串的 URL 编码长度（保留 `/`），用于拼接 codeload 路径 */
internal fun urlEncodeSegment(raw: String): String =
    raw.split('/').joinToString("/") {
        URLEncoder.encode(it, "UTF-8").replace("+", "%20")
    }
