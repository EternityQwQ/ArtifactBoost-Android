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

    // ------------------------------------------------------------------
    // 以下是针对「派发总字节超过文件体积」那个线上 bug 的回归测试。
    //
    // 老实现里 runSlice 把这半句写错了：把「剩下的」putBack 回池子之后，
    // worker 的 current 仍然指向原来那一大段 —— 同一段字节同时存在于
    // 池子和 worker 手上，于是两个 worker 会下到重叠区间、重复写盘。
    // 单跑一次 200MB 就多派了 127KB，文件因此损坏。
    // ------------------------------------------------------------------

    /**
     * 复刻修好之后的 runSlice 循环：每次只攥一小片，干完回池子重取。
     * 返回「派发出去的总字节数」与「发生过重叠的次数」。
     */
    private fun simulateRunSlice(
        total: Long,
        lanes: Int,
        maxSteps: Int = 5_000_000,
    ): Pair<Long, Int> {
        val pool = SlicePool(total)
        var dispatched = 0L
        var overlaps = 0

        // 每个 worker 手上的区间；null 表示空闲
        val hands = ArrayDeque<Pair<Long, Long>>()

        fun liveCount() = hands.size

        // 先按并发上限铺满
        while (liveCount() < lanes) {
            val work = nextWork(pool, liveCount(), lanes, total) ?: break
            hands.addLast(work.start to work.end)
        }

        var steps = 0
        while (hands.isNotEmpty() && steps++ < maxSteps) {
            val (start, end) = hands.removeFirst()
            val remaining = (total - dispatched).coerceAtLeast(0)
            val want = sliceTarget(lanes, total, remaining)
                .coerceIn(1, (end - start + 1).toInt())
            val chunkEnd = start + want - 1

            // 核心不变量：这一小片不能和任何其他 worker 手上的区间重叠
            hands.forEach { (s, e) ->
                if (start <= e && s <= chunkEnd) overlaps++
            }

            dispatched += want
            pool.recordDone(want.toLong())

            // 修好之后的写法：把「剩下的」还回池子，且不再持有它
            if (chunkEnd < end) {
                pool.putBack(com.artifactboost.app.download.Chunk(0, chunkEnd + 1, end))
            }

            // 回池子重新要活儿
            nextWork(pool, 1, lanes, total)?.let { hands.addLast(it.start to it.end) }
            // 再按并发上限补位
            while (liveCount() < lanes) {
                val work = nextWork(pool, liveCount(), lanes, total) ?: break
                hands.addLast(work.start to work.end)
            }
        }
        return dispatched to overlaps
    }

    @Test
    fun `runSlice 全流程派发的总字节必须恰好等于文件体积`() {
        val total = 200L * 1024 * 1024   // 200MB，就是线上出问题的量级
        for (lanes in listOf(1, 4, 16, 64)) {
            val (dispatched, _) = simulateRunSlice(total = total, lanes = lanes)
            assertEquals(
                "lanes=$lanes 时派发字节与文件体积不符（说明有区间被重复派发或漏派）",
                total,
                dispatched,
            )
        }
    }

    @Test
    fun `runSlice 全流程不允许出现任何重叠区间`() {
        val total = 100L * 1024 * 1024
        for (lanes in listOf(2, 8, 32, 64)) {
            val (_, overlaps) = simulateRunSlice(total = total, lanes = lanes)
            assertEquals("lanes=$lanes 时出现了 $overlaps 次区间重叠", 0, overlaps)
        }
    }

    @Test
    fun `worker 自己续做时也能从池子切分尾部区间`() {
        val total = 64L * 1024 * 1024
        val pool = SlicePool(total)
        // 池子里只留一段大区间，没有现成的小片可取
        // （初始就一整段，nextWork 会 take 走它；这里手工构造「池子有货」的局面）
        val whole = pool.take()!!
        pool.putBack(whole)

        // live = 1 代表「我自己还在跑」。老实现传 0 会直接 return null，
        // 于是收尾阶段永远切不动、所有 worker 干等 —— 这就是「只跑 1 条连接」的成因。
        val chunk = nextWork(pool, live = 1, lanes = 64, total = total)
        assertNotNull("worker 续做时应当能从池子取到区间", chunk)

        // 而 live 一旦达到 lanes，就不该再切了
        val exhausted = SlicePool(total = 4096L)
        exhausted.take()
        assertNull(nextWork(exhausted, live = 64, lanes = 64, total = 4096L))
    }
}
