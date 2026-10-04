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

    // ---------- 内嵌 HTML 归一化（normalizeHtml） ----------

    @Test
    fun `div centered img becomes picture paragraph`() {
        val md = "<div align=\"center\">\n<img src=\"https://example.com/icon.png\" alt=\"Air\" width=\"80\">\n</div>"
        val blocks = MarkdownParser.parse(md)
        val para = blocks.filterIsInstance<MDBlock.Paragraph>().single()
        val pic = para.segments.filterIsInstance<MDInline.Picture>().single()
        assertEquals("https://example.com/icon.png", pic.url)
        assertEquals("Air", pic.alt)
        // div 标签本身不能泄漏到任何块里
        assertTrue(blocks.none { b -> b.toString().contains("<div") })
    }

    @Test
    fun `html heading becomes markdown heading`() {
        val md = "<h1 align=\"center\">Air</h1>"
        val blocks = MarkdownParser.parse(md)
        val heading = blocks.filterIsInstance<MDBlock.Heading>().single()
        assertEquals(1, heading.level)
        assertEquals("Air", heading.text)
    }

    @Test
    fun `a wrapping img becomes linked picture`() {
        val md = "<a href=\"https://github.com/x/y/releases\">\n<img src=\"https://img.shields.io/badge/dl-1.2.3-blue\" alt=\"Downloads\">\n</a>"
        val blocks = MarkdownParser.parse(md)
        val para = blocks.filterIsInstance<MDBlock.Paragraph>().single()
        val pic = para.segments.filterIsInstance<MDInline.Picture>().single()
        assertEquals("https://img.shields.io/badge/dl-1.2.3-blue", pic.url)
        assertEquals("Downloads", pic.alt)
        assertEquals("https://github.com/x/y/releases", pic.link)
    }

    @Test
    fun `html entity in badge url is decoded`() {
        val md = "<img src=\"https://img.shields.io/badge/style-flat&amp;logo=android\" alt=\"style\">"
        val blocks = MarkdownParser.parse(md)
        val para = blocks.filterIsInstance<MDBlock.Paragraph>().single()
        val pic = para.segments.filterIsInstance<MDInline.Picture>().single()
        assertEquals("https://img.shields.io/badge/style-flat&logo=android", pic.url)
    }

    @Test
    fun `html inside code fence stays untouched`() {
        val md = "```html\n<h1 align=\"center\">Air</h1>\n<img src=\"x.png\">\n```"
        val blocks = MarkdownParser.parse(md)
        val code = blocks.filterIsInstance<MDBlock.Code>().single()
        assertTrue(code.content.contains("<h1 align=\"center\">Air</h1>"))
        assertTrue(code.content.contains("<img src=\"x.png\">"))
    }

    @Test
    fun `typography tags are stripped keeping content`() {
        val md = "<p>\n<sub>Powerful note &amp; text</sub>\n</p>"
        val blocks = MarkdownParser.parse(md)
        val para = blocks.filterIsInstance<MDBlock.Paragraph>().single()
        val text = para.segments.filterIsInstance<MDInline.Text>().joinToString("") { it.value }
        assertEquals("Powerful note & text", text.trim())
    }

    @Test
    fun `unknown tags are stripped as fallback`() {
        val md = "<video controls>\n<source src=\"a.mp4\">\n</video>\n正文"
        val blocks = MarkdownParser.parse(md)
        val para = blocks.filterIsInstance<MDBlock.Paragraph>().single()
        val text = para.segments.filterIsInstance<MDInline.Text>().joinToString("") { it.value }
        assertTrue(text.contains("正文"))
        assertTrue(!text.contains("<video") && !text.contains("<source"))
    }

    @Test
    fun `escaped entity does not become a real tag`() {
        val md = "示例 &lt;div align=&quot;center&quot;&gt; 保持原样"
        val blocks = MarkdownParser.parse(md)
        val para = blocks.filterIsInstance<MDBlock.Paragraph>().single()
        val text = para.segments.filterIsInstance<MDInline.Text>().joinToString("") { it.value }
        assertTrue(text.contains("<div align=\"center\">"))
    }
}
