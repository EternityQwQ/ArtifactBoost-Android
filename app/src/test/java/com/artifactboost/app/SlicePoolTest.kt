package com.artifactboost.app

import com.artifactboost.app.download.MIN_SLICE_TARGET
import com.artifactboost.app.download.SlicePool
import com.artifactboost.app.download.nextWork
import com.artifactboost.app.download.sliceTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 调度原语的纯逻辑测试 —— 不碰网络，只验证「切分 / 抢活」这套滑动窗口的核心不变量。
 *
 * 这套逻辑是「全程顶着网络顶峰」的关键，一旦分片有重叠或漏空，
 * 表现就是文件校验不过或速度掉底，所以必须单测守住。
 */
class SlicePoolTest {

    @Test
    fun `sliceTarget 落在上下限之间`() {
        // 极端情况：剩余量巨大也不该超过上限
        assertTrue(sliceTarget(16, 1_000_000_000L, 1_000_000_000L) <= 4 * 1024 * 1024)
        // 收尾阶段剩余量很小，不应该低于下限
        assertTrue(sliceTarget(16, 1_000_000_000L, 1L) >= MIN_SLICE_TARGET)
    }

    @Test
    fun `切分出来的两半首尾相接且不重叠`() {
        val pool = SlicePool(total = 10_000_000L)
        // 先拿走整段，制造「池子空、有连接闲着」的局面
        val whole = pool.take()
        assertNotNull(whole)
        pool.putBack(whole!!)

        val live = 2
        val target = 128 * 1024
        val left = pool.splitTail(live, target)
        assertNotNull(left)

        val right = pool.take()
        assertNotNull(right)
        // 左半段结束的下一刻，正好是右半段的开始 —— 不重叠、不漏字节
        assertEquals(left!!.end + 1, right!!.start)
        assertEquals(whole.start, left.start)
        assertEquals(whole.end, right.end)
    }

    @Test
    fun `切到目标粒度后不再继续碎片化`() {
        val pool = SlicePool(total = 1000L)
        val whole = pool.take()!!
        pool.putBack(whole)

        // 目标粒度比整段还大：这一刀不该切下去
        assertNull(pool.splitTail(live = 4, target = 4096))
        // 整段原样还在池子里
        assertEquals(whole, pool.take())
    }

    @Test
    fun `nextWork 在池子空时从末尾切一刀给空闲连接`() {
        val total = 8L * 1024 * 1024
        val pool = SlicePool(total)
        // 第一个 worker 直接拿走整段（此刻池子是空的，无法再切）
        val first = nextWork(pool, live = 0, lanes = 4, total = total)
        assertNotNull(first)

        // 模拟「这个 worker 只啃一小片，剩下的还回池子」——
        // 这正是 runSlice 里的做法，也是空闲连接能立刻拿到活儿的来源。
        val want = sliceTarget(4, total, total).coerceAtMost(first!!.length.toInt())
        pool.putBack(com.artifactboost.app.download.Chunk(0, first.start + want, first.end))

        // 现在池子里有活：空闲连接应该拿到它
        val second = nextWork(pool, live = 1, lanes = 4, total = total)
        assertNotNull(second)
        assertTrue(second!!.length > 0)
    }

    @Test
    fun `nextWork 拆解 hold 区间时首尾相接`() {
        val total = 8L * 1024 * 1024
        val pool = SlicePool(total)
        val first = nextWork(pool, live = 0, lanes = 4, total = total)!!
        val want = sliceTarget(4, total, total).coerceAtMost(first.length.toInt())
        val rest = com.artifactboost.app.download.Chunk(0, first.start + want, first.end)
        pool.putBack(rest)

        val second = nextWork(pool, live = 1, lanes = 4, total = total)!!
        // 还回去的那段就是第二个 worker 拿到的，字节区间必须严丝合缝
        assertEquals(rest, second)
        assertEquals(first.start + want, second.start)
    }

    @Test
    fun `nextWork 在并发已满时不再派活`() {
        val pool = SlicePool(total = 8L * 1024 * 1024)
        pool.take() // 清空池子
        // live 已经等于 lanes：即便池子空也不该再切，避免碎片化
        assertNull(nextWork(pool, live = 4, lanes = 4, total = 8L * 1024 * 1024))
    }

    @Test
    fun `并发抢活不会拿到同一段区间`() {
        val total = 16L * 1024 * 1024
        val pool = SlicePool(total)
        val claimed = mutableListOf<Pair<Long, Long>>()

        // 模拟 8 个连接并发抢活，每抢到一段就记录，再模拟「又空了」
        repeat(200) {
            val work = nextWork(pool, live = claimed.size.coerceAtMost(7), lanes = 8, total = total)
            if (work != null) {
                // 任何两段都不允许重叠
                claimed.forEach { (s, e) ->
                    val overlap = work.start <= e && s <= work.end
                    assertTrue("区间 [${work.start},${work.end}] 与 [$s,$e] 重叠", !overlap)
                }
                claimed += work.start to work.end
            }
        }
        assertTrue(claimed.isNotEmpty())
    }
}
