package com.artifactboost.app

import com.artifactboost.app.download.Chunk
import com.artifactboost.app.download.MAX_SLICE_TARGET
import com.artifactboost.app.download.MIN_SLICE_TARGET
import com.artifactboost.app.download.ResumeTracker
import com.artifactboost.app.download.SlicePool
import com.artifactboost.app.download.maxSliceFor
import com.artifactboost.app.download.normalizeRanges
import com.artifactboost.app.download.sliceTarget
import com.artifactboost.app.download.sliceWant
import com.artifactboost.app.download.subtractRanges
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile

/**
 * 大文件增强的纯逻辑测试 —— 不碰网络。
 *
 * 覆盖：自适应分片上限 / 区间归一化与差集 / 账本编解码 /
 * 续传准入 / SlicePool 挖除。
 */
class LargeFileTest {

    private val mb = 1024 * 1024L
    private val gb = 1024 * mb

    // ---------- 自适应分片上限 ----------

    @Test
    fun `分片上限随体积放量`() {
        assertEquals(MAX_SLICE_TARGET, maxSliceFor(500 * mb))
        assertEquals(8 * 1024 * 1024, maxSliceFor(1 * gb))
        assertEquals(16 * 1024 * 1024, maxSliceFor(2 * gb))
        assertEquals(16 * 1024 * 1024, maxSliceFor(10 * gb))
    }

    @Test
    fun `大文件分片目标吃到新上限`() {
        // 2GB、64 并发：share 远超旧 4MB 上限，应顶到 16MB
        assertEquals(16 * 1024 * 1024, sliceTarget(64, 2 * gb, 2 * gb))
        // 500MB 行为不变（旧上限）
        assertTrue(sliceTarget(16, 500 * mb, 500 * mb) <= MAX_SLICE_TARGET)
        // 下限不受影响
        assertEquals(MIN_SLICE_TARGET, sliceTarget(64, 500 * mb, 1L))
    }

    @Test
    fun `快通道在大文件上取大片`() {
        val total = 2 * gb
        val want = sliceWant(64, total, total, 16 * 1024 * 1024, 5_000_000.0)
        assertEquals(16 * 1024 * 1024, want)
    }

    // ---------- 区间归一化与差集 ----------

    @Test
    fun `归一化合并重叠相邻并裁剪越界`() {
        val norm = normalizeRanges(
            listOf(
                Chunk(0, 200, 299),
                Chunk(0, 0, 99),
                Chunk(0, 50, 250), // 桥接前两段
                Chunk(0, 900, 2000), // 越界裁剪
                Chunk(0, 500, 400), // 非法丢弃
            ),
            total = 1000,
        )
        assertEquals(listOf(Chunk(0, 0, 299), Chunk(0, 900, 999)), norm)
    }

    @Test
    fun `归一化空输入与零体积`() {
        assertTrue(normalizeRanges(emptyList(), 1000).isEmpty())
        assertTrue(normalizeRanges(listOf(Chunk(0, 0, 10)), 0).isEmpty())
    }

    @Test
    fun `差集挖洞保持顺序`() {
        val rest = subtractRanges(
            Chunk(0, 0, 999),
            listOf(Chunk(0, 100, 199), Chunk(0, 500, 599)),
        )
        assertEquals(
            listOf(Chunk(0, 0, 99), Chunk(0, 200, 499), Chunk(0, 600, 999)),
            rest,
        )
    }

    @Test
    fun `差集全覆盖与无交集`() {
        assertTrue(subtractRanges(Chunk(0, 0, 99), listOf(Chunk(0, 0, 99))).isEmpty())
        assertEquals(
            listOf(Chunk(0, 0, 99)),
            subtractRanges(Chunk(0, 0, 99), listOf(Chunk(0, 200, 299))),
        )
    }

    // ---------- 账本 ----------

    @Test
    fun `账本合并与编解码往返`() {
        val tracker = ResumeTracker(File("/tmp/nonexistent-part"), 1000)
        tracker.markDone(0, 99)
        tracker.markDone(200, 299)
        tracker.markDone(50, 250) // 桥接
        assertEquals(listOf(Chunk(0, 0, 299)), tracker.snapshot())
        assertEquals(300L, tracker.bytesDone())

        val parsed = ResumeTracker.parse(tracker.encode())
        assertEquals(1000L, parsed!!.first)
        assertEquals(listOf(Chunk(0, 0, 299)), parsed.second)
    }

    @Test
    fun `账本解析容错`() {
        assertNull(ResumeTracker.parse(""))
        assertNull(ResumeTracker.parse("total=abc\nranges=0-1"))
        assertNull(ResumeTracker.parse("total=100\nranges=5-3"))
        assertNull(ResumeTracker.parse("total=0\nranges="))
        assertNull(ResumeTracker.parse("hello"))
        // 越界区间被裁剪而非整单作废
        val parsed = ResumeTracker.parse("total=1000\nranges=0-99,900-5000")
        assertEquals(listOf(Chunk(0, 0, 99), Chunk(0, 900, 999)), parsed!!.second)
    }

    @Test
    fun `账本空done往返`() {
        val tracker = ResumeTracker(File("/tmp/nonexistent-part"), 1000)
        val parsed = ResumeTracker.parse(tracker.encode())
        assertEquals(1000L, parsed!!.first)
        assertTrue(parsed.second.isEmpty())
    }

    // ---------- 续传准入（走真实临时文件，纯 JDK） ----------

    private fun tempDir(): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "ab-resume-${System.nanoTime()}")
        dir.mkdirs()
        return dir
    }

    @Test
    fun `续传准入total一致才认`() {
        val dir = tempDir()
        try {
            val part = File(dir, "f.part")
            RandomAccessFile(part, "rw").use { it.setLength(1000) }
            val sidecar = File(dir, "f.part.ranges")
            sidecar.writeText("total=1000\nranges=0-99,200-299")

            val got = ResumeTracker.loadResumeRanges(sidecar, part, 1000)
            assertEquals(listOf(Chunk(0, 0, 99), Chunk(0, 200, 299)), got)

            // total 变了：part+账本双删，从头下
            val stale = ResumeTracker.loadResumeRanges(sidecar, part, 2000)
            assertTrue(stale.isEmpty())
            assertTrue(!part.exists() && !sidecar.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `缺账本或长度不对都从头下`() {
        val dir = tempDir()
        try {
            // 有数据没账本：删 part
            val part = File(dir, "g.part")
            RandomAccessFile(part, "rw").use { it.setLength(1000) }
            assertTrue(ResumeTracker.loadResumeRanges(File(dir, "g.part.ranges"), part, 1000).isEmpty())
            assertTrue(!part.exists())

            // 长度不对：双删
            val part2 = File(dir, "h.part")
            RandomAccessFile(part2, "rw").use { it.setLength(500) }
            val sidecar2 = File(dir, "h.part.ranges")
            sidecar2.writeText("total=1000\nranges=0-99")
            assertTrue(ResumeTracker.loadResumeRanges(sidecar2, part2, 1000).isEmpty())
            assertTrue(!part2.exists() && !sidecar2.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    // ---------- SlicePool 挖除 ----------

    @Test
    fun `挖除后派发总量恰为剩余量`() {
        val total = 10 * mb
        val pool = SlicePool(total, 4)
        val excluded = pool.excludeDone(listOf(Chunk(0, 0, 2 * mb - 1), Chunk(0, 5 * mb, 6 * mb - 1)))
        assertEquals(3 * mb, excluded)
        assertEquals(3 * mb, pool.downloaded())

        var drained = 0L
        while (true) {
            val c = pool.take() ?: break
            drained += c.length
            pool.recordDone(c.length)
        }
        assertEquals(total - 3 * mb, drained)
        assertEquals(total, pool.downloaded())
    }

    @Test
    fun `空挖除无影响`() {
        val pool = SlicePool(1024, 4)
        assertEquals(0L, pool.excludeDone(emptyList()))
        assertEquals(0L, pool.downloaded())
    }
}
