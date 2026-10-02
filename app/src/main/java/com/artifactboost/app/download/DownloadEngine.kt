package com.artifactboost.app.download

import com.artifactboost.app.data.GitHubClient
import com.artifactboost.app.data.ScoredRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** 分片索引就是它在文件里的起始偏移，切分时不需要重编号 */
private fun Chunk(at: Long, to: Long) = Chunk(0, at, to)

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

    /** 服务器明确要求我们慢一点（429 / 503 等），需要按 Retry-After 退避 */
    class Throttled(val code: Int, val retryAfterMillis: Long?) :
        DownloadException("下载失败：服务器限流（$code）")
}

data class DownloadResult(
    val file: File,
    val averageSpeed: Double,
    /** 本次实际吃满的并发数，用于界面说明 */
    val lanes: Int = 1,
)

/** 一个待下载的区间（左闭右闭） */
internal data class Chunk(
    val index: Int,
    val start: Long,
    val end: Long,
) {
    val length: Long get() = end - start + 1
}

/** 一个通道：一个独立的 OkHttpClient（独立连接池）+ 地址 + 实时吞吐 */
private class RouteChannel(
    val client: OkHttpClient,
    var endpoints: List<String>,
    val speedHint: Double,
) {
    @Volatile var measuredSpeed: Double = speedHint
}

/**
 * 多线程分段下载引擎（滑动窗口 + 分片续做）。
 *
 * 产物实际托管在 Azure Blob Storage，支持 Range 请求；单连接被限速时，
 * 多并发能显著提升总速度——这正是本引擎存在的意义。
 *
 * 与「一次性切块 + 固定分配给各连接」的老做法相比，这里的调度是自适应的：
 *
 *  1. **持久 worker 池**：并发跑满 `connections` 个任务，每个任务只取「一个小分片」；
 *     而不是开 N 个协程去啃又大又不均匀的一大块。
 *  2. **分片续做（steal）**：任何时刻若只剩少量区间在跑、而空闲 worker 还很多，
 *     就把末尾那段区间再砍一半。这样下载最后阶段不再是「一个慢连接收尾、
 *     其它连接全部闲着」，而是所有连接一起把剩下的数据吃完。
 *  3. **动态分片大小**：起步小、快的时候逐步放大（减少请求数），
 *     带宽掉下来时又迅速缩小（缩短每次重试的代价）。
 *  4. **索引即偏移**：分片编号就是它在文件里的字节偏移，所以续做/分片不需要重编号，
 *     写盘也可以乱序并发。
 *  5. **指数退避 + Retry-After**：命中 Azure 的 503 ServerBusy / 429 时限速时按官方
 *     建议退避，而不是火上浇油地硬重试，否则会被越限越死。
 *  6. **渐进建连 + 实时吞吐反馈**：避免「一上来几百个请求把服务端打限流」和
 *     「某个通道早就慢下来了却还一直按旧速度分活」。
 */
class DownloadEngine {

    @Volatile
    private var cancelled = false

    private val clients = mutableListOf<OkHttpClient>()
    private val allClients = ConcurrentLinkedDeque<OkHttpClient>()

    /** 便于取消时立刻中断所有阻塞中的请求 */
    fun cancel() {
        cancelled = true
        DownloadEngineFlag.cancelled = true
        allClients.forEach { it.dispatcher.cancelAll() }
    }

    /**
     * 多通道并行下载。
     *
     * @param routes 已按实测速度排序的通道，第一条同时作为其它通道失败时的兜底。
     * @param allowChunking 目标是否可能支持分段；为 false 时先走单连接，但在读到 206
     *        之后依旧会自动升级为分段下载（源码包也有 206 的时候）。
     */
    suspend fun download(
        signedUrl: String,
        routes: List<ScoredRoute>,
        fileName: String,
        connections: Int,
        outputDir: File,
        allowChunking: Boolean = true,
        progress: (DownloadProgress) -> Unit,
    ): DownloadResult = withContext(Dispatchers.IO) {
        cancelled = false
        DownloadEngineFlag.cancelled = false
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
            // 先探测体积 + 确认服务器是否真的支持 Range
            val probe = if (allowChunking) probeSize(urls) else null
            val total = probe?.total
            val lanes = connections.coerceIn(1, 64)

            if (total == null || total <= 0) {
                // 探测不到体积（不少接口不回 Content-Length）：
                // 先单连接跑，只要响应是 206 就现场升级成多线程分段
                val single = downloadSingle(urls.first(), outFile, lanes, progress)
                val elapsed = maxOf((System.nanoTime() - startedAt) / 1_000_000_000.0, 0.05)
                return@withContext DownloadResult(single.file, single.bytes / elapsed, single.lanes)
            }

            if (!probe.chunked || total < MIN_CHUNKED_TOTAL) {
                // 服务器忽略了 Range（返回 200 全量），或者文件太小不值得分段
                val single = downloadSingle(urls.first(), outFile, lanes, progress)
                val elapsed = maxOf((System.nanoTime() - startedAt) / 1_000_000_000.0, 0.05)
                return@withContext DownloadResult(single.file, single.bytes / elapsed, single.lanes)
            }

            val result = segmentDownload(urls, total, outFile, tempDir, lanes, plan, progress)
            val elapsed = maxOf((System.nanoTime() - startedAt) / 1_000_000_000.0, 0.05)
            DownloadResult(result, total / elapsed, lanes)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    // MARK: - 分段下载主循环

    /**
     * 滑动窗口 + 分片续做（work stealing）的调度器。
     *
     * `lanes` 是目标并发数，同时也是「同时在跑的区间数」上限。
     * 每个区间按 [sliceTarget] 的粒度取数据，写完一片就接着取下一片；
     * 一旦池子里没活儿而还有连接闲着，就从末尾区间切一刀 —— 空闲连接立刻有活干。
     *
     * 这样就不会再出现「刚开始很快、到后面掉到几十 KB」：
     * 那正是老实现里「一条慢连接独自收尾，其余连接全部空转」造成的。
     */
    private suspend fun segmentDownload(
        urls: List<String>,
        total: Long,
        outFile: File,
        tempDir: File,
        lanes: Int,
        plan: List<ScoredRoute>,
        progress: (DownloadProgress) -> Unit,
    ): File {
        val accumulator = ProgressAccumulator(total, progress)

        // 关键：Cloudflare 这类 CDN 会协商 HTTP/2，所有请求被多路复用到同一条 TCP 连接上，
        // 长链路下单连接带宽就是天花板，开再多「连接」也没用。
        // 每个 OkHttpClient 有独立连接池，拆成多个客户端才能真正拿到多条并行连接。
        val sessionCount = minOf(4, maxOf(1, lanes / 8))
        val perSessionLimit = maxOf(1, lanes / sessionCount)

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
        allClients.addAll(sessionClients)

        try {
            val direct = urls.first()
            val channels = plan.mapIndexed { index, scored ->
                val primary = urls[minOf(index, urls.size - 1)]
                // 镜像挂掉/被限流时自动退回直连
                val endpoints = if (primary == direct) listOf(primary) else listOf(primary, direct)
                RouteChannel(
                    client = sessionClients[index % sessionClients.size],
                    endpoints = endpoints,
                    speedHint = maxOf(scored.speed, 1.0),
                )
            }

            val output = RandomAccessFile(outFile, "rw")
            output.setLength(total)

            val pool = SlicePool(total)
            val written = AtomicLong(0)
            val startedAt = System.nanoTime()

            try {
                coroutineScope {
                    val jobs = mutableListOf<Job>()
                    var roundRobin = 0

                    while (true) {
                        jobs.removeAll { it.isCompleted }

                        // 1) 把并发顶到 lanes
                        var assigned = false
                        while (jobs.size < lanes) {
                            val work = nextWork(pool, jobs.size, lanes, total) ?: break
                            val channel = channels[pickChannel(channels, roundRobin)]
                            roundRobin = (roundRobin + 1) % channels.size
                            jobs += launch(Dispatchers.IO) {
                                runSlice(
                                    channel = channel,
                                    initial = work,
                                    pool = pool,
                                    lanes = lanes,
                                    total = total,
                                    output = output,
                                    written = written,
                                    accumulator = accumulator,
                                )
                            }
                            assigned = true
                        }

                        // 2) 全干完了
                        if (jobs.isEmpty()) break

                        // 3) 没活儿可派：等一小会儿再评估，别忙等烧 CPU
                        if (!assigned) delay(delayFor(pool, jobs.size, lanes, startedAt))
                    }

                    jobs.forEach { it.cancel() }
                }

                if (written.get() != total) throw DownloadException.Incomplete
                runCatching { output.fd.sync() }
            } finally {
                runCatching { output.close() }
            }

            accumulator.finish(total)
            return outFile
        } finally {
            synchronized(clients) { clients.removeAll(sessionClients) }
            sessionClients.forEach {
                allClients.remove(it)
                it.dispatcher.cancelAll()
                it.connectionPool.evictAll()
            }
        }
    }

    // MARK: - 单连接下载（不支持分段 / 探测不到体积时）

    /**
     * 单连接下载。会顺手看响应头：只要拿到 206，就说明服务端支持 Range，
     * 立刻放弃单连接、改用多线程分段引擎重下 ——
     * 很多「日志包只能单线程」其实是误判。
     */
    private suspend fun downloadSingle(
        url: String,
        outFile: File,
        lanes: Int,
        progress: (DownloadProgress) -> Unit,
    ): SingleOutcome {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .followRedirects(true)
            .build()
        synchronized(clients) { clients.add(client) }
        allClients.add(client)

        try {
            // 先带 Range 试一小段：能拿到 206 就说明可以分段
            val rangeProbe = Request.Builder()
                .url(url)
                .header("Range", "bytes=0-${SINGLE_PROBE_BYTES - 1}")
                .header("User-Agent", GitHubClient.USER_AGENT)
                .build()

            val ranged = runCatching {
                client.newCall(rangeProbe).execute().use { it.code == 206 }
            }.getOrDefault(false)

            if (ranged) {
                val total = probeSize(listOf(url))?.total
                if (total != null && total >= MIN_CHUNKED_TOTAL) {
                    // 升级：交给分段引擎跑，用独立临时目录避免和外层冲突
                    val upgradeDir = File(outFile.parentFile ?: outFile.absoluteFile.parentFile, "u-${System.nanoTime()}")
                    upgradeDir.mkdirs()
                    try {
                        val file = segmentDownload(
                            urls = listOf(url),
                            total = total,
                            outFile = outFile,
                            tempDir = upgradeDir,
                            lanes = lanes,
                            plan = listOf(ScoredRoute(com.artifactboost.app.data.DownloadRoute.DIRECT, 1.0)),
                            progress = progress,
                        )
                        return SingleOutcome(file, total, lanes)
                    } finally {
                        upgradeDir.deleteRecursively()
                    }
                }
            }

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", GitHubClient.USER_AGENT)
                .build()

            val written = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw DownloadException.BadResponse
                val body = response.body ?: throw DownloadException.BadResponse
                val total = body.contentLength().takeIf { it > 0 } ?: 0L

                outFile.delete()
                var done = 0L
                val startedAt = System.nanoTime()
                var lastEmit = 0L

                body.byteStream().use { input ->
                    outFile.outputStream().buffered(BUFFER_SIZE).use { sink ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            if (cancelled) throw DownloadException.Cancelled
                            val read = input.read(buffer)
                            if (read <= 0) break
                            sink.write(buffer, 0, read)
                            done += read

                            val now = System.currentTimeMillis()
                            if (now - lastEmit >= PROGRESS_INTERVAL_MS) {
                                lastEmit = now
                                val elapsed = maxOf((System.nanoTime() - startedAt) / 1_000_000_000.0, 0.05)
                                progress(
                                    DownloadProgress(
                                        downloadedBytes = done,
                                        totalBytes = total,
                                        fraction = if (total > 0) (done.toFloat() / total) else 0f,
                                        speedBytesPerSecond = done / elapsed,
                                    ),
                                )
                            }
                        }
                    }
                }

                progress(
                    DownloadProgress(
                        downloadedBytes = done,
                        totalBytes = maxOf(total, done),
                        fraction = 1f,
                        speedBytesPerSecond = 0.0,
                    ),
                )
                done
            }
            return SingleOutcome(outFile, written, 1)
        } finally {
            synchronized(clients) { clients.remove(client) }
            allClients.remove(client)
            client.dispatcher.cancelAll()
            client.connectionPool.evictAll()
        }
    }

    private data class SingleOutcome(val file: File, val bytes: Long, val lanes: Int)

    // MARK: - 探测

    private data class Probe(val total: Long, val chunked: Boolean)

    /**
     * 探测文件大小：优先用 `Range: bytes=0-0`（返回 206 + Content-Range 才确认服务器支持分段），
     * 失败再退回 HEAD。逐条通道尝试，任何一条成功即可。
     */
    private fun probeSize(urls: List<String>): Probe? {
        var headFallback: Long? = null
        for (url in urls) {
            val probe = probeSize(url) ?: continue
            if (probe.chunked && probe.total > 0) return probe
            if (headFallback == null && probe.total > 0) headFallback = probe.total
        }
        return headFallback?.let { Probe(it, false) }
    }

    private fun probeSize(url: String): Probe? {
        val client = probeClient
        synchronized(clients) { clients.add(client) }
        allClients.add(client)

        try {
            val rangeRequest = Request.Builder()
                .url(url)
                .header("Range", "bytes=0-0")
                .header("User-Agent", GitHubClient.USER_AGENT)
                .build()
            client.newCall(rangeRequest).execute().use { response ->
                if (response.code == 206) {
                    val contentRange = response.header("Content-Range")
                    val total = contentRange?.substringAfterLast('/')?.trim()?.toLongOrNull()
                    if (total != null && total > 0) return Probe(total, true)
                }
                // 返回 200 说明服务器忽略了 Range，不能分段
                if (response.code == 200) {
                    val length = response.header("Content-Length")?.toLongOrNull() ?: 0L
                    return Probe(length, false)
                }
            }

            val headRequest = Request.Builder()
                .url(url)
                .head()
                .header("User-Agent", GitHubClient.USER_AGENT)
                .build()
            client.newCall(headRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val length = response.header("Content-Length")?.toLongOrNull()
                    if (length != null && length > 0) return Probe(length, false)
                }
            }
        } catch (_: Exception) {
            return null
        } finally {
            synchronized(clients) { clients.remove(client) }
            allClients.remove(client)
        }
        return null
    }

    private fun isCancellation(error: Throwable): Boolean =
        cancelled || error is DownloadException.Cancelled ||
            error is CancellationException ||
            (error is IOException && error.message?.contains("Canceled") == true)

    internal companion object {
        const val BUFFER_SIZE = 1 shl 16          // 64 KB
        const val MAX_ATTEMPTS = 3
        const val PROGRESS_INTERVAL_MS = 250L

        /** 单连接模式判断「能否升级为分段」时试探的字节数 */
        const val SINGLE_PROBE_BYTES = 64 * 1024

        /** 小于这个体积不做分段：切来切去不如一条连接拉完 */
        const val MIN_CHUNKED_TOTAL = 4L * 1024 * 1024

        /** 探测专用客户端：限制总时长，防止某个通道不认 Range 时把整个文件都拉进内存 */
        val probeClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .callTimeout(20, TimeUnit.SECONDS)
                .followRedirects(true)
                .build()
        }

        fun retryAfter(headers: Headers): Long? {
            val raw = headers.get("Retry-After")?.trim() ?: return null
            val seconds = raw.toLongOrNull() ?: return null
            return if (seconds in 0..600) seconds * 1000 else null
        }

        /** 指数退避 + 抖动；限流时优先听服务端的 Retry-After */
        fun backoffMillis(attempt: Int, error: Exception): Long {
            if (error is DownloadException.Throttled) {
                val suggested = error.retryAfterMillis
                // 防雪崩：多个 worker 同时被限流时把退避时间错开
                val base = suggested ?: (1000L shl (attempt - 1).coerceIn(0, 4))
                val jitter = (base * 0.25 * Math.random()).toLong()
                return (base + jitter).coerceIn(250L, 30_000L)
            }
            val base = 250L shl (attempt - 1).coerceIn(0, 5)
            val jitter = (base * 0.3 * Math.random()).toLong()
            return (base + jitter).coerceAtMost(15_000L)
        }
    }
}

// MARK: - 调度原语（引擎之外，便于独立推理）

/** 一次分片请求的目标字节数下限 */
internal const val MIN_SLICE_TARGET = 128 * 1024

/** 一次分片请求的目标字节数上限：太大就退化成「一条连接啃大块」了 */
internal const val MAX_SLICE_TARGET = 4 * 1024 * 1024

/**
 * 分配下一段活儿：优先拿现成的；拿不到而连接还闲着，就从末尾切一刀。
 * 这一步是「分片续做」的入口，也是收尾阶段还能保持满速的原因。
 */
internal fun nextWork(pool: SlicePool, live: Int, lanes: Int, total: Long): Chunk? {
    pool.take()?.let { return it }
    if (live >= lanes) return null
    val remaining = (total - pool.downloaded()).coerceAtLeast(0)
    return pool.splitTail(live, sliceTarget(lanes, total, remaining))
}

/**
 * 一个区间一次取多少：剩余数据越多取越大（少发请求），
 * 越接近尾声取越小（让所有连接都能分到收尾的活儿）。
 */
internal fun sliceTarget(lanes: Int, total: Long, remaining: Long): Int {
    val share = (remaining / (lanes.toLong() * 4L)).coerceAtLeast(0L) * 2L
    return share.coerceIn(MIN_SLICE_TARGET.toLong(), MAX_SLICE_TARGET.toLong()).toInt()
}

/** 没活儿可派时的等待时长：刚起步就快查快切，收尾时慢一点 */
private fun delayFor(pool: SlicePool, live: Int, lanes: Int, startedAt: Long): Long {
    val warmingUp = (System.nanoTime() - startedAt) / 1_000_000 < 2_000
    return when {
        warmingUp -> 40L
        pool.backlog > 0 -> 30L
        live >= lanes -> 80L
        live > 1 -> 150L
        else -> 250L
    }
}

/**
 * 一个 worker 的生命周期：攥着一段区间，一小片一小片地取数据。
 *
 * 每片只取 [sliceTarget] 那么多字节，剩下的立刻还回池子 ——
 * 这样任何一个连接都不会长时间独占一大块，空闲连接永远有活儿可干。
 */
private suspend fun runSlice(
    channel: RouteChannel,
    initial: Chunk,
    pool: SlicePool,
    lanes: Int,
    total: Long,
    output: RandomAccessFile,
    written: AtomicLong,
    accumulator: ProgressAccumulator,
) {
    var current = initial

    while (true) {
        val remaining = (total - written.get()).coerceAtLeast(0)
        val want = sliceTarget(lanes, total, remaining).coerceAtMost(current.length.toInt())
        val from = current.start
        val to = from + want - 1

        if (want < current.length) {
            // 手里这段太长：只取前一小片，剩下的还回去让别的连接分
            pool.putBack(Chunk(0, to + 1, current.end))
        }

        val outcome = fetchSlice(channel, Chunk(0, from, to), pool)
        if (outcome.received > 0) {
            writeAt(output, from, outcome.data, outcome.received)
            written.addAndGet(outcome.received.toLong())
            pool.recordDone(outcome.received.toLong())
            accumulator.advance(outcome.received.toLong())
            channel.observe(outcome.elapsedNanos, outcome.received.toLong())
        }
        if (outcome.received < want) {
            // 没取满（连接中途断了）：把缺的那一段还回池子重取，绝不丢数据
            val missing = Chunk(0, from + outcome.received, to)
            if (missing.length > 0) pool.putBack(missing)
        }

        if (to >= current.end) {
            // 手里这段干完了，再要一段；要不到就收工
            current = nextWork(pool, 0, lanes, total) ?: return
        } else {
            // 手里还剩一截，接着啃
            current = Chunk(0, to + 1, current.end)
        }
    }
}

private data class SliceOutcome(
    val data: ByteArray,
    val received: Int,
    val elapsedNanos: Long,
)

/**
 * 真正发起 Range 请求，把这一小片读进内存（不落临时文件）。
 *
 * 失败时按指数退避重试；命中 429/503 时读 `Retry-After` 退避 ——
 * Azure 单 Blob 有「约 60 MiB/s 或 500 请求/秒」的目标，超了就是 503 ServerBusy，
 * 官方建议用指数退避而不是硬顶，否则会被越限越死。
 */
private fun fetchSlice(channel: RouteChannel, chunk: Chunk, pool: SlicePool): SliceOutcome {
    var lastError: Exception = DownloadException.BadResponse
    var attempt = 0

    while (attempt < DownloadEngine.MAX_ATTEMPTS) {
        if (DownloadEngineFlag.cancelled) throw DownloadException.Cancelled

        val url = channel.endpoints.first()
        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=${chunk.start}-${chunk.end}")
            .header("User-Agent", GitHubClient.USER_AGENT)
            .build()

        val buffer = ByteArray(chunk.length.toInt())
        val startedAt = System.nanoTime()
        try {
            channel.client.newCall(request).execute().use { response ->
                when (response.code) {
                    206, 200 -> Unit
                    429, 503 -> {
                        pool.throttles.incrementAndGet()
                        throw DownloadException.Throttled(response.code, DownloadEngine.retryAfter(response.headers))
                    }
                    else -> throw DownloadException.BadResponse
                }
                val body = response.body ?: throw DownloadException.BadResponse

                var received = 0
                body.byteStream().use { input ->
                    while (received < buffer.size) {
                        val read = input.read(buffer, received, buffer.size - received)
                        if (read <= 0) break
                        received += read
                    }
                }
                if (received <= 0) throw DownloadException.Incomplete
                return SliceOutcome(buffer, received, System.nanoTime() - startedAt)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e is DownloadException.Cancelled || DownloadEngineFlag.cancelled) {
                throw DownloadException.Cancelled
            }
            lastError = e
            attempt++
            if (attempt >= DownloadEngine.MAX_ATTEMPTS) break

            if (e is DownloadException.Throttled) {
                // 被限流：把这条通道的权重降下来，让活儿分给别人
                channel.measuredSpeed = maxOf(channel.measuredSpeed * 0.5, 1.0)
            }
            if (attempt >= 2 && channel.endpoints.size > 1) {
                // 通道彻底不通了就轮到备用地址
                channel.endpoints = channel.endpoints.drop(1) + channel.endpoints.first()
            }
            Thread.sleep(DownloadEngine.backoffMillis(attempt, e))
        }
    }

    pool.failures.incrementAndGet()
    throw lastError
}

private fun writeAt(output: RandomAccessFile, offset: Long, data: ByteArray, length: Int) {
    synchronized(output) {
        output.seek(offset)
        output.write(data, 0, length)
    }
}

/** 按实时吞吐挑通道：快的多干活 */
private fun pickChannel(channels: List<RouteChannel>, hint: Int): Int {
    if (channels.size == 1) return 0
    var best = hint % channels.size
    var bestSpeed = -1.0
    for (offset in channels.indices) {
        val index = (hint + offset) % channels.size
        val speed = channels[index].measuredSpeed
        if (speed > bestSpeed) {
            bestSpeed = speed
            best = index
        }
    }
    return best
}

/** 用一小片实测吞吐更新通道速度（滑动平均，避免抖动） */
private fun RouteChannel.observe(elapsedNanos: Long, bytes: Long) {
    val seconds = elapsedNanos / 1_000_000_000.0
    if (seconds < 0.02 || bytes <= 0) return
    val instant = bytes / seconds
    measuredSpeed = if (measuredSpeed <= 0) instant else measuredSpeed * 0.7 + instant * 0.3
}

/** 取消标志的载体：分片函数是顶层函数，拿不到引擎实例 */
internal object DownloadEngineFlag {
    @Volatile var cancelled = false
}

/**
 * 待下载区间的池子（滑动窗口）。
 *
 * 关键设计：区间只记 (start, end)，不带全局编号 ——
 * 于是「把一段砍成两半」不需要给任何 worker 重新编号，
 * 写盘也能按偏移随意定位。这正是「随时切分、随时抢活」的前提。
 */
internal class SlicePool(val total: Long) {

    private val queue = ConcurrentLinkedDeque<Chunk>()

    /** 每次「把末尾区间砍一刀」记一笔 */
    val splits = AtomicInteger(0)

    /** 池子里还剩几段区间 */
    val backlog: Int get() = queue.size

    val failures = AtomicInteger(0)
    val throttles = AtomicInteger(0)

    /** 已完成的字节数：用于估算剩余量、决定分片粒度 */
    private val completed = AtomicLong(0)

    fun downloaded(): Long = completed.get()

    fun recordDone(bytes: Long) {
        completed.addAndGet(bytes)
    }

    init {
        queue.add(Chunk(0, 0L, total - 1))
    }

    /** 取一段活儿；没有就返回 null，由调度循环决定要不要切分 */
    fun take(): Chunk? = queue.pollFirst()

    /** 把没下完的区间还回队列最前面 */
    fun putBack(chunk: Chunk) {
        queue.addFirst(chunk)
    }

    /**
     * 池子空了、但还有连接闲着时调用：
     * 从队列末尾挑一段最大的砍成两半 ——
     * 右半段留在池子里，左半段直接返回给这个空闲连接。
     */
    fun splitTail(live: Int, target: Int): Chunk? {
        if (live <= 0) return null

        val victim = queue.pollLast() ?: return null
        if (victim.length <= target.toLong()) {
            // 已经切到目标粒度了，别再无谓地碎片化
            queue.addLast(victim)
            return null
        }

        val half = victim.length / 2
        queue.addLast(Chunk(0, victim.start + half, victim.end))
        splits.incrementAndGet()
        return Chunk(0, victim.start, victim.start + half - 1)
    }
}

/**
 * 汇总各分块进度，节流后回调给 UI。
 * 速度用滑动平均避免数字乱跳；但连续几拍零增长时必须往下压，
 * 否则界面会一直挂着峰值速度、而实际已经掉下去了。
 */
class ProgressAccumulator(
    private val total: Long,
    private val handler: (DownloadProgress) -> Unit,
) {
    @Volatile private var downloaded = 0L
    @Volatile private var lastEmit = 0L
    @Volatile private var lastSampleTime = System.nanoTime()
    @Volatile private var lastSampleBytes = 0L
    @Volatile private var smoothedSpeed = 0.0
    @Volatile private var zeroStreak = 0

    fun advance(bytes: Long) {
        val now = System.currentTimeMillis()
        val current = downloaded + bytes
        downloaded = current
        val shouldEmit = synchronized(this) {
            if (now - lastEmit < 250L) return@synchronized false
            lastEmit = now
            true
        }
        if (shouldEmit) handler(snapshot(current))
    }

    fun finish(downloaded: Long) {
        this.downloaded = downloaded
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

    private fun snapshot(current: Long): DownloadProgress {
        val now = System.nanoTime()
        val dt = (now - lastSampleTime) / 1_000_000_000.0
        if (dt > 0.05) {
            val delta = current - lastSampleBytes
            if (delta <= 0) {
                zeroStreak++
                // 连续 3 拍没涨（约 0.75s 零吞吐）：平滑值必须往下压
                if (zeroStreak >= 3) smoothedSpeed *= 0.4
            } else {
                zeroStreak = 0
                val instant = delta / dt
                smoothedSpeed = if (smoothedSpeed <= 0) instant else smoothedSpeed * 0.6 + instant * 0.4
            }
            lastSampleTime = now
            lastSampleBytes = current
        }
        return DownloadProgress(
            downloadedBytes = current,
            totalBytes = total,
            fraction = if (total > 0) minOf(current.toFloat() / total, 1f) else 0f,
            speedBytesPerSecond = maxOf(smoothedSpeed, 0.0),
        )
    }
}
