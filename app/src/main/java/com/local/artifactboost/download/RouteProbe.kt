package com.local.artifactboost.download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 通道测速：每个通道各拉一小段数据，取最快的那条。
 * 与 iOS 版 RouteProbe 一致：采样 256KB，快通道 0.1~0.5s 出结果。
 */
object RouteProbe {

    const val SAMPLE_BYTES = 256L * 1024
    private const val TIMEOUT_SECONDS = 6L

    /** 探测专用客户端：限制总时长，防止某个通道不认 Range 时把整个文件都拉进内存 */
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(TIMEOUT_SECONDS * 2, TimeUnit.SECONDS)
        .build()

    /** 并发测量所有通道，返回按速度从快到慢排序的结果（失败的通道会被丢掉） */
    suspend fun measureAll(
        routes: List<DownloadRoute>,
        signedUrl: String,
        sampleLimit: Long = SAMPLE_BYTES,
    ): List<ScoredRoute> = withContext(Dispatchers.IO) {
        val limit = sampleLimit.coerceIn(64 * 1024, SAMPLE_BYTES)
        coroutineScope {
            routes.map { route ->
                async { measure(route, signedUrl, limit) }
            }.awaitAll().filterNotNull().sortedByDescending { it.speed }
        }
    }

    suspend fun measure(
        route: DownloadRoute,
        signedUrl: String,
        limit: Long = SAMPLE_BYTES,
    ): ScoredRoute? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(route.apply(signedUrl))
                .header("Range", "bytes=0-${limit - 1}")
                .build()

            val start = System.nanoTime()
            client.newCall(request).execute().use { response ->
                // 206 = 支持分段；200 说明目标本身小于采样长度（比如日志包），按实际字节算速度
                if (!response.isSuccessful) return@use null
                val code = response.code
                if (code != 206 && code != 200) return@use null

                val bytes = response.body?.bytes() ?: return@use null
                if (bytes.isEmpty()) return@use null

                val elapsed = (System.nanoTime() - start) / 1e9
                ScoredRoute(route, bytes.size / elapsed.coerceAtLeast(0.05))
            }
        }.getOrNull()
    }
}
