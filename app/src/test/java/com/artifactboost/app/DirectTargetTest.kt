package com.artifactboost.app

import com.artifactboost.app.ui.screens.DirectTarget
import com.artifactboost.app.ui.screens.parseDirectTarget
import com.artifactboost.app.ui.screens.parseFullName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「直接打开仓库 / Actions」输入解析必须守住：
 * 普通仓库链接进仓库主页，actions/runs 链接直达构建详情。
 */
class DirectTargetTest {

    @Test
    fun `裸 owner repo 进仓库`() {
        assertEquals(
            DirectTarget.Repo("cli/cli"),
            parseDirectTarget("cli/cli"),
        )
    }

    @Test
    fun `完整仓库链接进仓库`() {
        val target = parseDirectTarget("https://github.com/cli/cli")
        assertEquals(DirectTarget.Repo("cli/cli"), target)
        // 兼容旧入口：退化为仓库名
        assertEquals("cli/cli", parseFullName("https://github.com/cli/cli"))
    }

    @Test
    fun `actions runs 链接直达构建`() {
        val target = parseDirectTarget(
            "https://github.com/EternityQwQ/Amethyst-iOS-MyRemastered/actions/runs/37171664473",
        )
        assertEquals(
            DirectTarget.Run("EternityQwQ/Amethyst-iOS-MyRemastered", 37171664473L),
            target,
        )
    }

    @Test
    fun `actions 链接带 jobs 后缀仍取 runId`() {
        val target = parseDirectTarget(
            "https://github.com/owner/repo/actions/runs/123456/jobs/789?check_suite_focus=true#x",
        )
        assertTrue(target is DirectTarget.Run)
        assertEquals(123456L, (target as DirectTarget.Run).runId)
        assertEquals("owner/repo", target.fullName)
    }

    @Test
    fun `裸 actions 简写同样识别`() {
        assertEquals(
            DirectTarget.Run("owner/repo", 42L),
            parseDirectTarget("owner/repo/actions/runs/42"),
        )
    }

    @Test
    fun `markdown 尖括号包裹可解析`() {
        assertEquals(
            DirectTarget.Run("o/r", 7L),
            parseDirectTarget("<https://github.com/o/r/actions/runs/7>"),
        )
    }

    @Test
    fun `非法输入返回 null`() {
        assertNull(parseDirectTarget(""))
        assertNull(parseDirectTarget("just-one-word"))
        assertNull(parseDirectTarget("https://example.com/o/r"))
    }
}
