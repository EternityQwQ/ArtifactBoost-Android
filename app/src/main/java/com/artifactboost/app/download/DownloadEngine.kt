package com.artifactboost.app.download

import com.artifactboost.app.data.GitHubClient
import com.artifactboost.app.data.ScoredRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit

/** 下载进度快照，节流后推给 UI */
data class DownloadProgress(
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val fraction: Float = 0f,
    val speedBytesPerSecond: Double = 0.0,
)

sealed class DownloadException(message: String) : IOException(message) {
    object BadResponse : DownloadException("下载失败：服务器响应异常")
    object Cancelled : DownloadException("下载已取消")
    object Incomplete : DownloadException("下载失败：数据校验不通过（可能断流），请重试")
}

data class DownloadResult(
    val file: File,
    val averageSpeed: Double,
)

/** 一个待下载的分段 */
internal data class Chunk(
    val index: Int,
    val start: Long,
    val end: Long,
) {
    val length: Long get() = end - start + 1
}

/**
 * 多线程分段下载引擎：
 * 先用 Range 探测文件大小并确认服务器支持分段，然后切成 N 段并发下载，最后按序合并。
 *
 * 产物实际托管在 Azure Blob Storage，支持 Range 请求；
 * 单连接被限速时，多并发能显著提升总速度。
 */
class DownloadEngine {

    @Volatile
    private var cancelled = false

    /** 便于取消时立刻中断所有阻塞中的请求 */
    private val clients = mutableListOf<OkHttpClient>()

    fun cancel() {
        cancelled = true
        synchronized(clients) {
            clients.forEach { it.dispatcher.cancelAll() }
        }
    }

    /**
     * 多通道并行：把分块按实测速度分配给多条通道同时下载，带宽可以叠加。
     * routes 需按速度从快到慢排列，第一条同时作为其它通道失败时的兜底。
     */
    suspend fun download(
        signedUrl: String,
        routes: List<ScoredRoute>,
        fileName: String,
        connections: Int,
        outputDir: File,
        progress: (DownloadProgress) -> Unit,
    ): DownloadResult = withContext(Dispatchers.IO) {
        val startedAt = System.nanoTime()
        val plan = routes.ifEmpty { listOf(ScoredRoute(com.artifactboost.app.data.DownloadRoute.DIRECT, 1.0)) }
        val urls = plan.map { it.route.apply(signedUrl) }

        val tempDir = File(outputDir.parentFile ?: outputDir, "tmp-${System.currentTimeMillis()}")
        if (!tempDir.exists() && !tempDir.mkdirs()) throw DownloadException.BadResponse
        outputDir.mkdirs()

        var outFile = File(outputDir, fileName)
        if (outFile.exists()) {
            outFile = File(outputDir, "${System.currentTimeMillis().toString().takeLast(6)}-$fileName")
        }

        try {
            val total = probeSize(urls)

            // 服务器不支持分块（或探测不到体积）时，退化为单连接下载
            if (total == null || total <= 0) {
                val downloaded = downloadSingle(urls.first(), outFile, progress)
                val elapsed = maxOf((System.nanoTime() - startedAt) / 1_000_000_000.0, 0.05)
                return@withContext DownloadResult(outFile, downloaded / elapsed)
            }

            val accumulator = ProgressAccumulator(total, progress)

            // 关键：Cloudflare 这类 CDN 会协商 HTTP/2，所有请求被多路复用到同一条 TCP 连接上，
            // 长链路下单连接带宽就是天花板，开再多「连接」也没用。
            // 每个 OkHttpClient 有独立连接池，拆成多个客户端才能真正拿到多条并行连接。
            val sessionCount = minOf(4, maxOf(1, connections / 8))
            val perSessionLimit = maxOf(1, connections / sessionCount)

            val sessionClients = (0 until sessionCount).map {
                OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(120, TimeUnit.SECONDS)
                    .callTimeout(0, TimeUnit.MILLISECONDS) // 不设总时长上限：大文件要慢慢下
                    .followRedirects(true)
                    .connectionPool(okhttp3.ConnectionPool(perSessionLimit, 5, TimeUnit.MINUTES))
                    .dispatcher(okhttp3.Dispatcher().apply {
                        maxRequests = perSessionLimit
                        maxRequestsPerHost = perSessionLimit
                    })
                    .build()
            }
            synchronized(clients) { clients.addAll(sessionClients) }

            try {
                val chunks = makeChunks(total, connections)
                val assignment = assign(chunks, plan.map { it.speed })
                val fallbackUrl = urls.first()

                val parts = coroutineScope {
                    chunks.mapIndexed { index, chunk ->
                        async(Dispatchers.IO) {
                            val url = urls[assignment[index].coerceAtMost(urls.size - 1)]
                            val client = sessionClients[index % sessionClients.size]
                            downloadChunk(
                                client = client,
                                url = url,
                                fallbackUrl = if (url == fallbackUrl) null else fallbackUrl,
                                chunk = chunk,
                                tempDir = tempDir,
                                accumulator = accumulator,
                            )
                        }
                    }.awaitAll()
                }

                merge(parts, outFile, total)
                accumulator.finish(total)
                val elapsed = maxOf((System.nanoTime() - startedAt) / 1_000_000_000.0, 0.05)
                DownloadResult(outFile, total / elapsed)
            } finally {
                synchronized(clients) { clients.removeAll(sessionClients) }
                sessionClients.forEach { it.dispatcher.cancelAll(); it.connectionPool.evictAll() }
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    // MARK: - 分块下载

    private suspend fun downloadChunk(
        client: OkHttpClient,
        url: String,
        fallbackUrl: String?,
        chunk: Chunk,
        tempDir: File,
        accumulator: ProgressAccumulator,
    ): Pair<Int, File> {
        return try {
            attemptChunk(client, url, chunk, tempDir, accumulator)
        } catch (e: Exception) {
            if (isCancellation(e)) throw DownloadException.Cancelled
            // 该通道彻底失败（镜像挂了/被限流），换主通道再试一次
            if (fallbackUrl == null) throw e
            attemptChunk(client, fallbackUrl, chunk, tempDir, accumulator)
        }
    }

    private suspend fun attemptChunk(
        client: OkHttpClient,
        url: String,
        chunk: Chunk,
        tempDir: File,
        accumulator: ProgressAccumulator,
    ): Pair<Int, File> {
        var lastError: Exception = DownloadException.BadResponse
        for (attempt in 0 until MAX_ATTEMPTS) {
            if (cancelled) throw DownloadException.Cancelled
            try {
                val partFile = File(tempDir, "part-${chunk.index}")
                writeRange(client, url, chunk, partFile, accumulator)

                // 关键校验：服务器忽略 Range（返回 200 全量）或连接被截断时，
                // 分块体积会对不上，必须在这里拦下来，否则会合并出一个损坏的压缩包
                if (partFile.length() != chunk.length) {
                    partFile.delete()
                    throw DownloadException.Incomplete
                }
                return chunk.index to partFile
            } catch (e: Exception) {
                if (isCancellation(e)) throw DownloadException.Cancelled
                lastError = e
                if (attempt < MAX_ATTEMPTS - 1) {
                    delay(800L * (attempt + 1))
                }
            }
        }
        throw lastError
    }

    /** 真正发起 Range 请求并把响应流写到 part 文件 */
    private fun writeRange(
        client: OkHttpClient,
        url: String,
        chunk: Chunk,
        dest: File,
        accumulator: ProgressAccumulator,
    ) {
        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=${chunk.start}-${chunk.end}")
            .header("User-Agent", GitHubClient.USER_AGENT)
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code != 200 && response.code != 206) {
                throw DownloadException.BadResponse
            }
            val body = response.body ?: throw DownloadException.BadResponse

            dest.delete()
            dest.parentFile?.mkdirs()

            var written = 0L
            body.byteStream().use { input ->
                RandomAccessFile(dest, "rw").use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        if (cancelled) throw DownloadException.Cancelled
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        written += read
                        // 进度节流在累加器里控制（250ms），这里只管喂数字
                        accumulator.update(chunk.index, written)
                    }
                }
            }
        }
    }

    // MARK: - 合并与校验

    /** 按分块顺序合并，并校验最终体积，任何异常都会删掉半成品 */
    private fun merge(parts: List<Pair<Int, File>>, outFile: File, total: Long) {
        outFile.delete()
        try {
            outFile.outputStream().buffered(BUFFER_SIZE).use { output ->
                for ((_, partFile) in parts.sortedBy { it.first }) {
                    partFile.inputStream().buffered(BUFFER_SIZE).use { input ->
                        input.copyTo(output, BUFFER_SIZE)
                    }
                }
            }
        } catch (e: Exception) {
            outFile.delete()
            throw e
        }

        if (outFile.length() != total) {
            outFile.delete()
            throw DownloadException.Incomplete
        }
    }

    /** 不支持分段时的单连接下载 */
    private fun downloadSingle(url: String, outFile: File, progress: (DownloadProgress) -> Unit): Long {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
        synchronized(clients) { clients.add(client) }

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", GitHubClient.USER_AGENT)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw DownloadException.BadResponse
                val body = response.body ?: throw DownloadException.BadResponse
                val total = body.contentLength().takeIf { it > 0 } ?: 0L

                outFile.delete()
                var written = 0L
                val startedAt = System.nanoTime()
                var lastEmit = 0L

                body.byteStream().use { input ->
                    outFile.outputStream().buffered(BUFFER_SIZE).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            if (cancelled) throw DownloadException.Cancelled
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            written += read

                            val now = System.currentTimeMillis()
                            if (now - lastEmit >= PROGRESS_INTERVAL_MS) {
                                lastEmit = now
                                val elapsed = maxOf((System.nanoTime() - startedAt) / 1_000_000_000.0, 0.05)
                                progress(
                                    DownloadProgress(
                                        downloadedBytes = written,
                                        totalBytes = total,
                                        fraction = if (total > 0) (written.toFloat() / total) else 0f,
                                        speedBytesPerSecond = written / elapsed,
                                    ),
                                )
                            }
                        }
                    }
                }

                progress(
                    DownloadProgress(
                        downloadedBytes = written,
                        totalBytes = maxOf(total, written),
                        fraction = 1f,
                        speedBytesPerSecond = 0.0,
                    ),
                )
                written
            }
        } finally {
            synchronized(clients) { clients.remove(client) }
            client.dispatcher.cancelAll()
            client.connectionPool.evictAll()
        }
    }

    /**
     * 探测文件大小：优先用 `Range: bytes=0-0`（返回 206 + Content-Range 才确认服务器支持分段），
     * 失败再退回 HEAD。返回 null 表示不能分段，调用方会退化为单连接下载。
     * 逐条通道尝试，任何一条成功即可。
     */
    private fun probeSize(urls: List<String>): Long? {
        for (url in urls) {
            probeSize(url)?.let { if (it > 0) return it }
        }
        return null
    }

    private fun probeSize(url: String): Long? {
        val client = probeClient
        synchronized(clients) { clients.add(client) }

        try {
            // 1) Range 探测
            val rangeRequest = Request.Builder()
                .url(url)
                .header("Range", "bytes=0-0")
                .header("User-Agent", GitHubClient.USER_AGENT)
                .build()
            client.newCall(rangeRequest).execute().use { response ->
                if (response.code == 206) {
                    val contentRange = response.header("Content-Range")
                    val total = contentRange?.substringAfterLast('/')?.trim()?.toLongOrNull()
                    if (total != null && total > 0) return total
                }
                // 返回 200 说明服务器忽略了 Range，不能分段
                if (response.code == 200) return null
            }

            // 2) HEAD 兜底
            val headRequest = Request.Builder()
                .url(url)
                .head()
                .header("User-Agent", GitHubClient.USER_AGENT)
                .build()
            client.newCall(headRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val length = response.header("Content-Length")?.toLongOrNull()
                    if (length != null && length > 0) return length
                }
            }
        } catch (_: Exception) {
            return null
        } finally {
            synchronized(clients) { clients.remove(client) }
        }
        return null
    }

    // MARK: - 分块策略

    /**
     * 分块数取连接数的 4 倍：多出来的分块排队，
     * 哪条连接先空出来就接下一条，既避免「最慢的那块」拖住整体，也更容易吃满带宽。
     * 注意分块下限不能太大，否则小产物根本拆不出几段
     * （这正是当初「开了加速还是几百 KB」的原因）。
     */
    private fun makeChunks(total: Long, connections: Int): List<Chunk> {
        val minChunk = 256L * 1024
        val maxChunks = minOf(connections, 64).coerceAtLeast(1).toLong() * 4
        val count = minOf(maxChunks, maxOf(1L, total / minChunk))
        val size = (total + count - 1) / count

        val chunks = mutableListOf<Chunk>()
        var start = 0L
        while (start < total) {
            val end = minOf(start + size - 1, total - 1)
            chunks.add(Chunk(chunks.size, start, end))
            start = end + 1
        }
        return chunks
    }

    /**
     * 按实测速度分配分块：谁快谁多分，避免慢通道拖住整体进度。
     * 贪心地把每一块交给「当前负载 / 速度」最小的通道。
     */
    private fun assign(chunks: List<Chunk>, speeds: List<Double>): List<Int> {
        if (speeds.size <= 1) return List(chunks.size) { 0 }
        val load = DoubleArray(speeds.size)
        return chunks.map { chunk ->
            var target = 0
            var bestScore = Double.MAX_VALUE
            for (index in speeds.indices) {
                val score = load[index] / maxOf(speeds[index], 0.01)
                if (score < bestScore) {
                    bestScore = score
                    target = index
                }
            }
            load[target] += chunk.length.toDouble()
            target
        }
    }

    private fun isCancellation(error: Throwable): Boolean =
        cancelled || error is DownloadException.Cancelled ||
            error is kotlinx.coroutines.CancellationException ||
            (error is IOException && error.message?.contains("Canceled") == true)

    private companion object {
        const val BUFFER_SIZE = 1 shl 16      // 64 KB
        const val MAX_ATTEMPTS = 3
        const val PROGRESS_INTERVAL_MS = 250L

        val probeClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .callTimeout(20, TimeUnit.SECONDS)
                .followRedirects(true)
                .build()
        }
    }
}

/**
 * 汇总各分块进度，节流后回调给 UI。
 * 速度用滑动平均，避免瞬时抖动导致数字乱跳。
 */
class ProgressAccumulator(
    private val total: Long,
    private val handler: (DownloadProgress) -> Unit,
) {
    private val chunkBytes = java.util.concurrent.ConcurrentHashMap<Int, Long>()
    @Volatile private var lastEmit = 0L
    @Volatile private var lastSampleTime = System.nanoTime()
    @Volatile private var lastSampleBytes = 0L
    @Volatile private var smoothedSpeed = 0.0

    fun update(chunk: Int, bytes: Long) {
        chunkBytes[chunk] = bytes
        val now = System.currentTimeMillis()
        synchronized(this) {
            if (now - lastEmit < 250L) return
            lastEmit = now
        }
        handler(snapshot(chunkBytes.values.sum()))
    }

    fun finish(downloaded: Long) {
        val finalTotal = maxOf(total, downloaded)
        handler(
            DownloadProgress(
                downloadedBytes = finalTotal,
                totalBytes = finalTotal,
                fraction = 1f,
                speedBytesPerSecond = maxOf(smoothedSpeed, 0.0),
            ),
        )
    }

    private fun snapshot(downloaded: Long): DownloadProgress {
        val now = System.nanoTime()
        val dt = (now - lastSampleTime) / 1_000_000_000.0
        if (dt > 0.05) {
            val instant = (downloaded - lastSampleBytes) / dt
            smoothedSpeed = if (smoothedSpeed <= 0) instant else smoothedSpeed * 0.6 + instant * 0.4
            lastSampleTime = now
            lastSampleBytes = downloaded
        }
        return DownloadProgress(
            downloadedBytes = downloaded,
            totalBytes = total,
            fraction = if (total > 0) minOf(downloaded.toFloat() / total, 1f) else 0f,
            speedBytesPerSecond = maxOf(smoothedSpeed, 0.0),
        )
    }
}
