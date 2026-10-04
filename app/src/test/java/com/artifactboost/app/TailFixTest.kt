package com.artifactboost.app

import com.artifactboost.app.download.MAX_SLICE_TARGET
import com.artifactboost.app.download.MIN_ACCEPTABLE_SLICE_SPEED
import com.artifactboost.app.download.MIN_SLICE_TARGET
import com.artifactboost.app.download.RouteChannel
import com.artifactboost.app.download.bdpFloorBytes
import com.artifactboost.app.download.isTailRemaining
import com.artifactboost.app.download.pickChannel
import com.artifactboost.app.download.pickRetryChannel
import com.artifactboost.app.download.sliceTimeoutMs
import com.artifactboost.app.download.sliceWant
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 尾段掉速修复的纯逻辑测试——不碰网络。
 *
 * 覆盖四件套：尾段判定 / 快通道隔离选路 / BDP 自适应分片 / 慢片超时。
 */
class TailFixTest {

    private fun channel(name: String, speed: Double, throttled: Boolean = false): RouteChannel {
        val client = OkHttpClient.Builder().build()
        return RouteChannel(client, listOf("https://example.com/$name"), speed, name).also {
            it.throttled = throttled
        }
    }

    @Test
    fun `尾段阈值随并发自适应`() {
        // lanes=64 时阈值 8MB：剩 1MB 已是尾段，剩 16MB 还不是
        assertTrue(isTailRemaining(1L * 1024 * 1024, 64))
        assertFalse(isTailRemaining(16L * 1024 * 1024, 64))
        // lanes=1 时阈值 128KB
        assertTrue(isTailRemaining(100L * 1024, 1))
        assertFalse(isTailRemaining(1L * 1024 * 1024, 1))
    }

    @Test
    fun `尾段选路排除被限流通道`() {
        val fast = channel("fast", 10_000_000.0)
        val slow = channel("slow", 100_000.0, throttled = true)
        val channels = listOf(fast, slow)
        repeat(100) {
            val idx = pickChannel(channels, it, excludeThrottled = true)
            assertEquals("尾段隔离后不应选中被限流通道", 0, idx)
        }
    }

    @Test
    fun `尾段全被限流时退回不断流`() {
        val channels = listOf(channel("a", 1_000.0, true), channel("b", 2_000.0, true))
        repeat(20) {
            val idx = pickChannel(channels, it, excludeThrottled = true)
            assertTrue(idx in 0..1)
        }
    }

    @Test
    fun `重试在尾段同时避开失败线和限流线`() {
        val ok = channel("ok", 8_000_000.0)
        val bad = channel("bad", 9_000_000.0)
        val limited = channel("limited", 7_000_000.0, throttled = true)
        val channels = listOf(ok, bad, limited)
        repeat(100) {
            val idx = pickRetryChannel(channels, excluding = "bad", excludeThrottled = true)
            assertEquals("应只剩 ok 可选", 0, idx)
        }
    }

    @Test
    fun `慢片超时随片长放大且有下限`() {
        assertEquals(20_000L, sliceTimeoutMs(64L * 1024))
        assertEquals(20_000L, sliceTimeoutMs(256L * 1024))
        val fourMb = sliceTimeoutMs(4L * 1024 * 1024)
        assertEquals(4L * 1024 * 1024 * 1000L / MIN_ACCEPTABLE_SLICE_SPEED, fourMb)
        assertTrue(fourMb > 20_000L)
        assertTrue(sliceTimeoutMs(4L * 1024 * 1024) > sliceTimeoutMs(1L * 1024 * 1024))
    }

    @Test
    fun `BDP 保底在未测速时近似无操作且有上限`() {
        assertEquals(0, bdpFloorBytes(1.0))
        assertEquals((5_000_000 * 0.25).toInt(), bdpFloorBytes(5_000_000.0))
        assertEquals(MAX_SLICE_TARGET, bdpFloorBytes(1_000_000_000.0))
    }

    @Test
    fun `自适应分片不越界且快通道取大片`() {
        val total = 500L * 1024 * 1024
        val remaining = total
        // 手头只剩 100 字节：再怎么保底也不能越界
        assertEquals(100, sliceWant(16, total, remaining, 100, 5_000_000.0))
        // 快通道：want 不小于基准且不大于手头长度
        val base = sliceWant(16, total, remaining, 4 * 1024 * 1024, 1.0)
        val fast = sliceWant(16, total, remaining, 4 * 1024 * 1024, 5_000_000.0)
        assertTrue(fast >= base)
        assertTrue(fast <= 4 * 1024 * 1024)
        // 尾段小剩余：仍不低于最小片
        assertTrue(sliceWant(64, total, 1L, 4 * 1024 * 1024, 1.0) >= MIN_SLICE_TARGET)
    }
}
