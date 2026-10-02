package com.artifactboost.app

import com.artifactboost.app.ui.screens.InlineScanner
import com.artifactboost.app.ui.screens.MDBlock
import com.artifactboost.app.ui.screens.MDInline
import com.artifactboost.app.ui.screens.MarkdownParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {

    private fun dump(segs: List<MDInline>): String = segs.joinToString(" | ") { s ->
        when (s) {
            is MDInline.Text -> "T(\"${s.value}\")"
            is MDInline.Picture -> "IMG(url=${s.url}, alt=${s.alt}, link=${s.link ?: "-"})"
        }
    }

    @Test
    fun `badge renders as picture with link`() {
        val out = dump(InlineScanner("[![CI](https://img.shields.io/badge/ci-pass-green)](https://github.com/x/y/actions)").scan())
        assertEquals("IMG(url=https://img.shields.io/badge/ci-pass-green, alt=CI, link=https://github.com/x/y/actions)", out)
    }

    @Test
    fun `badge mixed with text keeps order`() {
        val out = dump(InlineScanner("构建状态 [![build](https://img.shields.io/b.svg)](https://ci) 以及说明文字").scan())
        assertEquals("T(\"构建状态 \") | IMG(url=https://img.shields.io/b.svg, alt=build, link=https://ci) | T(\" 以及说明文字\")", out)
    }

    @Test
    fun `html img tag is parsed`() {
        val out = dump(InlineScanner("<img src=\"https://example.com/a.png\" alt=\"A\" width=\"100\">").scan())
        assertEquals("IMG(url=https://example.com/a.png, alt=A, link=-)", out)
    }

    @Test
    fun `url with parentheses`() {
        val out = dump(InlineScanner("![x](https://example.com/a_(b).png)").scan())
        assertEquals("IMG(url=https://example.com/a_(b).png, alt=x, link=-)", out)
    }

    @Test
    fun `normal link stays text`() {
        val out = dump(InlineScanner("请看 [文档](https://example.com/doc) 了解").scan())
        assertEquals("T(\"请看 [文档](https://example.com/doc) 了解\")", out)
    }

    @Test
    fun `table columns keep own cell counts`() {
        val md = """
            | 名称 | 说明 | 支持 |
            | --- | --- | --- |
            | alpha | 第一个 | 是 |
            | beta-long-name | 第二个更长的说明 | 否 |
        """.trimIndent()
        val table = MarkdownParser.parse(md).first()
        assertTrue(table is MDBlock.Table)
        table as MDBlock.Table
        assertEquals(3, table.header.size)
        assertEquals(2, table.rows.size)
        assertTrue(table.rows.all { it.size == 3 })
        assertEquals("T(\"名称\")", dump(table.header[0]))
        assertEquals("T(\"beta-long-name\")", dump(table.rows[1][0]))
    }

    @Test
    fun `badges in their own paragraph become pictures`() {
        val md = """
            ## Badges

            [![CI](https://img.shields.io/badge/ci-pass-green)](https://ci) [![License](https://img.shields.io/badge/license-MIT-blue)](https://lic)
        """.trimIndent()
        val blocks = MarkdownParser.parse(md)
        val para = blocks.filterIsInstance<MDBlock.Paragraph>().single()
        val pictures = para.segments.filterIsInstance<MDInline.Picture>()
        assertEquals(2, pictures.size)
        assertEquals("https://ci", pictures[0].link)
        assertEquals("https://lic", pictures[1].link)
    }
}
