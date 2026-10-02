package com.local.artifactboost.download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min

data class DownloadProgress(
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val fraction: Float = 0f,
    val speedBytesPerSecond: Double = 0.0,
)

sealed class DownloadException(message: String) : Exception(message) {
    object BadResponse : DownloadException("下载失败：服务器响应异常")
    object Cancelled : DownloadException("下载已取消")
    object Incomplete : DownloadException("下载失败：数据校验不通过（可能断流），请重试")
}

data class DownloadResult(val file: File, val averageSpeed: Double)

private data class Chunk(val index: Int, val start: Long, val end: Long) {
    val length: Long get() = end - start + 1
}

/**
 * 多线程分段下载引擎（Kotlin/OkHttp 版，算法与 iOS 版一致）：
 *
 * 1. 用 `Range: bytes=0-0` 探测文件大小，同时确认服务器真的支持分段（返回 206）；
 * 2. 切成 N 段并发下载，分块数 = 连接数 × 4，让快连接自动接下一块，
 *    避免「最慢的那块」拖住整体；
 * 3. 多通道并行时按实测速度加权分配分块，谁快谁多分；
 * 4. 每块都要校验收到的字节数和 Range 长度完全一致 —— 服务器忽略 Range
 *    （返回 200 全量）或连接被截断时能立刻发现，否则会合并出一个损坏的包；
 * 5. 按序合并并校验最终体积，任何异常都删掉半成品。
 */
class DownloadEngine {

    private val callTag = Any()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS) // 大文件不设总时长上限
        .retryOnConnectionFailure(true)
        .build()

    /** 探测用的短超时客户端 */
    private val probeClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    fun cancel() {
        // 取消当前引擎发起的全部请求
        client.dispatcher.runningCalls().forEach { call ->
            if (call.request().tag(Any::class.java) === callTag) call.cancel()
        }
        client.dispatcher.queuedCalls().forEach { call ->
            if (call.request().tag(Any::class.java) === callTag) call.cancel()
        }
        client.dispatcher.cancelAll()
        probeClient.dispatcher.cancelAll()
    }

    private fun tagged(url: String, rangeHeader: String? = null): Request {
        val builder = Request.Builder().url(url).tag(Any::class.java, callTag)
        if (rangeHeader != null) builder.header("Range", rangeHeader)
        return builder.build()
    }

    suspend fun download(
        signedUrl: String,
        routes: List<ScoredRoute>,
        fileName: String,
        connections: Int,
        outputDir: File,
        onProgress: (DownloadProgress) -> Unit,
    ): DownloadResult = withContext(Dispatchers.IO) {
        val startedAt = System.nanoTime()
        val plan = routes.ifEmpty { listOf(ScoredRoute(DownloadRoute.Direct, 1.0)) }
        val urls = plan.map { it.route.apply(signedUrl) }

        outputDir.mkdirs()
        var outFile = File(outputDir, fileName)
        if (outFile.exists()) {
            outFile = File(outputDir, "${System.currentTimeMillis().toString().takeLast(6)}-$fileName")
        }

        val tempDir = File(outputDir, "tmp-${System.nanoTime()}").apply { mkdirs() }
        try {
            val total = probeSize(urls)
            val counter = ProgressCounter(total ?: 0L, onProgress)

            // 服务器不支持分块（或探测不到体积）时，退化为单连接下载
            if (total == null || total <= 0L) {
                val size = singleConnectionDownload(urls[0], outFile, counter)
                counter.finish(size)
                return@withContext DownloadResult(
                    outFile,
                    speed(size, startedAt),
                )
            }

            val chunks = makeChunks(total, connections)
            val assignment = assign(chunks, plan.map { it.speed })
            val fallbackUrl = urls[0]

            val parts = coroutineScope {
                chunks.mapIndexed { index, chunk ->
                    async(Dispatchers.IO) {
                        val url = urls[min(assignment[index], urls.size - 1)]
                        // 该通道不是主通道时，失败后回退到主通道再试
                        val fallback = if (url == fallbackUrl) null else fallbackUrl
                        downloadChunk(url, fallback, chunk, tempDir, counter)
                    }
                }.awaitAll()
            }

            merge(parts, outFile, total)
            counter.finish(total)
            DownloadResult(outFile, speed(total, startedAt))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    // MARK: - 进度

    /** 汇总各分块进度，节流后回调（速度用滑动平均，避免数字乱跳） */
    private class ProgressCounter(
        private val total: Long,
        private val onProgress: (DownloadProgress) -> Unit,
    ) {
        private val chunkBytes = HashMap<Int, Long>()
        private val sum = AtomicLong(0)
        private var lastEmitAt = 0L
        private var lastSampleAt = System.nanoTime()
        private var lastSampleBytes = 0L
        private var smoothedSpeed = 0.0
        private val lock = Any()

        fun update(chunk: Int, bytes: Long) {
            synchronized(lock) {
                val previous = chunkBytes[chunk] ?: 0L
                chunkBytes[chunk] = bytes
                sum.addAndGet(bytes - previous)
            }
            val now = System.nanoTime()
            if (now - lastEmitAt < 250_000_000L) return
            lastEmitAt = now
            onProgress(snapshot(sum.get()))
        }

        fun finish(downloaded: Long) {
            val finalTotal = max(total, downloaded)
            onProgress(
                DownloadProgress(
                    downloadedBytes = finalTotal,
                    totalBytes = finalTotal,
                    fraction = 1f,
                    speedBytesPerSecond = smoothedSpeed,
                ),
            )
        }

        private fun snapshot(downloaded: Long): DownloadProgress {
            val now = System.nanoTime()
            val dt = (now - lastSampleAt) / 1e9
            if (dt > 0.05) {
                val instant = (downloaded - lastSampleBytes) / dt
                smoothedSpeed =
                    if (smoothedSpeed <= 0) instant else smoothedSpeed * 0.6 + instant * 0.4
                lastSampleAt = now
                lastSampleBytes = downloaded
            }
            return DownloadProgress(
                downloadedBytes = downloaded,
                totalBytes = total,
                fraction = if (total > 0) min(downloaded.toFloat() / total, 1f) else 0f,
                speedBytesPerSecond = max(smoothedSpeed, 0.0),
            )
        }
    }

    // MARK: - 单块下载

    private suspend fun downloadChunk(
        url: String,
        fallbackUrl: String?,
        chunk: Chunk,
        tempDir: File,
        counter: ProgressCounter,
    ): Pair<Int, File> {
        return try {
            attemptChunk(url, chunk, tempDir, counter)
        } catch (e: DownloadException) {
            if (e === DownloadException.Cancelled) throw e
            // 该通道彻底失败（镜像挂了/被限流），换主通道再试一次
            if (fallbackUrl == null) throw e
            attemptChunk(fallbackUrl, chunk, tempDir, counter)
        }
    }

    private suspend fun attemptChunk(
        url: String,
        chunk: Chunk,
        tempDir: File,
        counter: ProgressCounter,
    ): Pair<Int, File> {
        var lastError: Exception = DownloadException.BadResponse
        for (attempt in 0..2) {
            try {
                coroutineContext.ensureActive()
                val part = File(tempDir, "part-${chunk.index}")
                val request = tagged(url, "bytes=${chunk.start}-${chunk.end}")

                client.newCall(request).execute().use { response ->
                    if (response.code != 206 && response.code != 200) {
                        throw DownloadException.BadResponse
                    }
                    val body = response.body ?: throw DownloadException.BadResponse

                    // 关键校验：服务器忽略 Range（返回 200 全量）时会和预期长度不符
                    if (response.code == 200) {
                        val contentLength = body.contentLength()
                        if (contentLength > 0 && contentLength != chunk.length) {
                            throw DownloadException.Incomplete
                        }
                    }

                    var received = 0L
                    part.outputStream().use { out ->
                        val buffer = ByteArray(256 * 1024)
                        body.byteStream().use { input ->
                            while (true) {
                                coroutineContext.ensureActive()
                                val read = input.read(buffer)
                                if (read <= 0) break
                                out.write(buffer, 0, read)
                                received += read
                                counter.update(chunk.index, received)
                            }
                        }
                        out.flush()
                    }

                    // 关键校验：字节数必须和 Range 长度完全一致，否则说明连接被截断
                    if (received != chunk.length) {
                        part.delete()
                        throw DownloadException.Incomplete
                    }
                    return chunk.index to part
                }
            } catch (e: DownloadException) {
                throw e
            } catch (e: Exception) {
                if (isCancellation(e)) throw DownloadException.Cancelled
                lastError = e
                if (attempt < 2) delay(800L * (attempt + 1))
            }
        }
        throw lastError
    }

    private fun singleConnectionDownload(
        url: String,
        outFile: File,
        counter: ProgressCounter,
    ): Long {
        client.newCall(tagged(url)).execute().use { response ->
            if (!response.isSuccessful) throw DownloadException.BadResponse
            val body = response.body ?: throw DownloadException.BadResponse
            val expected = body.contentLength()
            var received = 0L

            outFile.outputStream().use { out ->
                val buffer = ByteArray(256 * 1024)
                body.byteStream().use { input ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        out.write(buffer, 0, read)
                        received += read
                        counter.update(0, received)
                    }
                }
                out.flush()
            }

            if (expected > 0 && received != expected) {
                outFile.delete()
                throw DownloadException.Incomplete
            }
            return received
        }
    }

    // MARK: - 合并与工具

    /** 按分块顺序合并，并校验最终体积，任何异常都会删掉半成品 */
    private fun merge(parts: List<Pair<Int, File>>, outFile: File, total: Long) {
        outFile.delete()
        try {
            RandomAccessFile(outFile, "rw").use { out ->
                for ((_, part) in parts.sortedBy { it.first }) {
                    if (!part.exists()) throw DownloadException.Incomplete
                    part.inputStream().use { input ->
                        val buffer = ByteArray(1 shl 20)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            out.write(buffer, 0, read)
                        }
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

    /**
     * 分块数取连接数的 4 倍：多出来的分块排队，哪条连接先空出来就接下一条，
     * 既避免"最慢的那块"拖住整体，也更容易吃满带宽。
     * 分块下限 256KB，否则小产物根本拆不出几段。
     */
    private fun makeChunks(total: Long, connections: Int): List<Chunk> {
        val minChunk = 256L * 1024
        val maxChunks = max(1, min(connections, 64).toLong()) * 4
        val count = min(maxChunks, max(1, total / minChunk))
        val size = (total + count - 1) / count

        val chunks = ArrayList<Chunk>()
        var start = 0L
        while (start < total) {
            val end = min(start + size - 1, total - 1)
            chunks.add(Chunk(chunks.size, start, end))
            start = end + 1
        }
        return chunks
    }

    /** 按实测速度分配分块：谁快谁多分，避免慢通道拖住整体进度 */
    private fun assign(chunks: List<Chunk>, speeds: List<Double>): List<Int> {
        if (speeds.size <= 1) return List(chunks.size) { 0 }
        val load = DoubleArray(speeds.size)
        return chunks.map { chunk ->
            var target = 0
            var bestScore = Double.MAX_VALUE
            for (i in speeds.indices) {
                val score = load[i] / max(speeds[i], 0.01)
                if (score < bestScore) {
                    bestScore = score
                    target = i
                }
            }
            load[target] += chunk.length.toDouble()
            target
        }
    }

    /**
     * 探测文件大小：优先用 `Range: bytes=0-0`（返回 206 才能确认服务器支持分段下载），
     * 失败再退回 HEAD。返回 null 表示不能分段，调用方会退化为单连接下载。
     */
    private fun probeSize(urls: List<String>): Long? {
        for (url in urls) {
            probeSize(url)?.let { if (it > 0) return it }
        }
        return null
    }

    private fun probeSize(url: String): Long? {
        runCatching {
            probeClient.newCall(tagged(url, "bytes=0-0")).execute().use { response ->
                if (response.code == 206) {
                    // Content-Range: bytes 0-0/12345
                    val contentRange = response.header("Content-Range") ?: return@use null
                    val totalPart = contentRange.substringAfterLast("/", "").trim()
                    val total = totalPart.toLongOrNull()
                    if (total != null && total > 0) return total
                }
                // 返回 200 说明服务器忽略了 Range，不能分段
                if (response.code == 200) {
                    return@use null
                }
                null
            }
        }

        runCatching {
            val request = Request.Builder()
                .url(url)
                .method("HEAD", null)
                .tag(Any::class.java, callTag)
                .build()
            probeClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val len = response.header("Content-Length")?.toLongOrNull()
                    if (len != null && len > 0) return len
                }
            }
        }
        return null
    }

    private fun speed(bytes: Long, startedAtNanos: Long): Double {
        val elapsed = (System.nanoTime() - startedAtNanos) / 1e9
        return bytes / elapsed.coerceAtLeast(0.05)
    }

    private fun isCancellation(e: Exception): Boolean =
        e is kotlinx.coroutines.CancellationException ||
            e is java.io.InterruptedIOException ||
            e is java.io.IOException && e.message?.contains("Canceled") == true
}
