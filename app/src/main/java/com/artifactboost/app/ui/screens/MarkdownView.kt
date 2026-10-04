package com.artifactboost.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.artifactboost.app.ui.components.SkeletonBlock
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.artifactboost.app.ui.components.Hairline
import com.artifactboost.app.ui.theme.AppTheme

/**
 * 轻量 Markdown 渲染，够用于 README：标题 / 段落 / 列表 / 代码块 / 引用 / 表格 / 分割线 / 图片。
 * 对应 iOS 版的 MarkdownView.swift（MarkdownParser + MarkdownInline + MarkdownContentView）。
 *
 * 两个容易踩的坑，这里都专门处理了：
 *  - **badge / 行内图片**：形如 `[![alt](img)](link)`，或段落里夹着 `![](img)`，
 *    必须渲染成图片，而不是把 `![alt](img)` 当成普通链接文字吐出来；
 *  - **表格列对齐**：每行各画各的宽度，各列当然对不齐。这里先量出整列的内容宽度，
 *    再让同一列所有单元格用同一个宽度，上下才会对齐。
 */

sealed interface MDBlock {
    data class Heading(val level: Int, val text: String) : MDBlock
    data class Paragraph(val segments: List<MDInline>) : MDBlock
    data class Bullet(val segments: List<MDInline>, val indent: Int) : MDBlock
    data class Ordered(val number: String, val segments: List<MDInline>, val indent: Int) : MDBlock
    data class Quote(val segments: List<MDInline>) : MDBlock
    data class Code(val language: String?, val content: String) : MDBlock
    data class Table(val header: List<List<MDInline>>, val rows: List<List<List<MDInline>>>) : MDBlock
    data object Rule : MDBlock
    data class Image(val url: String, val alt: String) : MDBlock
}

/**
 * 一段行内内容：要么是文字（交给 [MarkdownInline] 上样式），要么是一张图片。
 *
 * 之所以把图片单独拎出来，是因为 badge（ shields.io 那种）在 README 里随处可见，
 * 它们必须能被真的画出来 —— 而 Compose 的 AnnotatedString 里塞不了异步加载的图片。
 */
sealed interface MDInline {
    data class Text(val value: String) : MDInline
    data class Picture(val url: String, val alt: String, val link: String?) : MDInline
}

object MarkdownParser {

    /**
     * README 里大量存在的**内嵌 HTML**（GitHub 允许 Markdown 里直接写 HTML）：
     * `<div align="center">`、`<h1>`、`<img>` badge、`<a>`、`<sub>`……
     * 我们的解析器只认 Markdown，HTML 标签会被当成纯文本吐出来，
     * 界面上就是一屏源码。
     *
     * 这里在解析前做一遍归一化：常见标签转成等效 Markdown（走既有渲染管线），
     * 不认识的标签剥壳保内容，HTML 实体解码（badge URL 里的 `&amp;` 全靠它）。
     * ``` 围栏代码块里的内容原样保留 —— 那是用户想展示的代码。
     */
    internal fun normalizeHtml(source: String): String {
        // 按 ``` 围栏切开，围栏内的段落原样保留。
        // 用 findAll 手动枚举，不能用 split+捕获组 —— Kotlin 的 Regex.split 不保留捕获组，
        // 围栏会被当成分隔符直接丢掉（围栏内容凭空消失）。
        val fence = Regex("```[\\s\\S]*?```|```[\\s\\S]*")
        val out = StringBuilder()
        var last = 0
        for (m in fence.findAll(source)) {
            val start = m.range.first
            if (start > last) out.append(transformHtml(source.substring(last, start)))
            out.append(m.value)   // 围栏内原样保留
            last = m.range.last + 1
        }
        if (last < source.length) out.append(transformHtml(source.substring(last)))
        return out.toString()
    }

    private fun transformHtml(text: String): String {
        var s = text

        // HTML 注释
        s = Regex("<!--[\\s\\S]*?-->").replace(s, "")

        // <img src="X" alt="Y" ...> → ![Y](X)（属性任意顺序；width/height 忽略）
        s = Regex("<img\\s[^>]*>", RegexOption.IGNORE_CASE).replace(s) { m ->
            val src = htmlAttr(m.value, "src")
            if (src.isNullOrEmpty()) ""
            else "![${htmlAttr(m.value, "alt") ?: ""}]($src)"
        }

        // <a href="X">内容</a> → [内容](X)（内容可能已含上面转换出的图片 → badge）
        s = Regex("<a\\s[^>]*href\\s*=\\s*[\"']([^\"']*)[\"'][^>]*>([\\s\\S]*?)</a>", RegexOption.IGNORE_CASE)
            .replace(s) { m -> "[${m.groupValues[2].trim()}](${m.groupValues[1]})" }

        // <h1>~<h6> → 标题；内容里有图片就降级成段落（标题渲染不了图）
        s = Regex("<h([1-6])(\\s[^>]*)?>([\\s\\S]*?)</h\\1>", RegexOption.IGNORE_CASE).replace(s) { m ->
            val level = m.groupValues[1].toInt()
            val inner = m.groupValues[3].trim()
            if (inner.isEmpty()) ""
            else if (inner.contains("](")) "\n$inner\n"
            else "\n${"#".repeat(level)} $inner\n"
        }

        // <b>/<strong>/<i>/<em> → Markdown 强调
        s = Regex("</?(?:b|strong)>", RegexOption.IGNORE_CASE).replace(s, "**")
        s = Regex("</?(?:i|em)>", RegexOption.IGNORE_CASE).replace(s, "*")

        // <br> / <hr>
        s = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE).replace(s, "\n")
        s = Regex("<hr\\s*/?>", RegexOption.IGNORE_CASE).replace(s, "\n---\n")

        // <li> → 列表行（必须在通用剥壳之前）
        s = Regex("<li(\\s[^>]*)?>", RegexOption.IGNORE_CASE).replace(s, "\n- ")

        // 已知的纯排版标签：剥壳保内容（div/p/span/sub/sup/center/details…）
        s = Regex(
            "</?(?:div|p|span|sub|sup|center|details|summary|kbd|samp|small|big|font|picture|source|figure|figcaption|picture|ins|del|u|s|strike)(\\s[^>]*)?/?>",
            RegexOption.IGNORE_CASE,
        ).replace(s, "")

        // 兜底：其余任何未知标签也剥掉 —— 绝不让源码出现在界面上。
        // 注意这在解码 HTML 实体**之前**：正文里的 `&lt;div&gt;` 此时还不是真标签，不受影响。
        s = Regex("</?[a-zA-Z][a-zA-Z0-9-]*(\\s[^<>]*)?/?>").replace(s, "")

        // HTML 实体解码（&amp; 常见于 shields badge URL 的参数里，不解会 404）
        s = s.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")

        return s
    }

    /** 从 HTML 标签里取属性值（任意属性顺序，单双引号都认；要求属性名前有空白，避免误命中 data-src 之类） */
    private fun htmlAttr(tag: String, name: String): String? {
        val m = Regex("(?:^|\\s)$name\\s*=\\s*[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE).find(tag) ?: return null
        return m.groupValues[1]
    }

    fun parse(source: String): List<MDBlock> {
        val blocks = mutableListOf<MDBlock>()
        val lines = normalizeHtml(source).replace("\r\n", "\n").split("\n")
        var index = 0

        while (index < lines.size) {
            val raw = lines[index]
            val line = raw.trim()

            if (line.isEmpty()) {
                index += 1
                continue
            }

            // ``` 代码块
            if (line.startsWith("```")) {
                val language = line.drop(3).trim()
                val buffer = mutableListOf<String>()
                index += 1
                while (index < lines.size && !lines[index].trim().startsWith("```")) {
                    buffer.add(lines[index])
                    index += 1
                }
                index += 1
                blocks.add(MDBlock.Code(language.ifEmpty { null }, buffer.joinToString("\n")))
                continue
            }

            // 分割线（至少 3 个 -, *, _）
            if (isRule(line)) {
                blocks.add(MDBlock.Rule)
                index += 1
                continue
            }

            // 标题
            if (line.startsWith("#")) {
                val hashes = line.takeWhile { it == '#' }.length
                if (hashes in 1..6) {
                    val text = line.drop(hashes).trim()
                    if (text.isNotEmpty()) {
                        blocks.add(MDBlock.Heading(minOf(hashes, 4), text))
                        index += 1
                        continue
                    }
                }
            }

            // 表格
            if (line.contains("|") && index + 1 < lines.size && isTableSeparator(lines[index + 1])) {
                val header = parseInline(splitTableRow(line))
                val rows = mutableListOf<List<List<MDInline>>>()
                index += 2
                while (index < lines.size && lines[index].contains("|")) {
                    rows.add(parseInline(splitTableRow(lines[index])))
                    index += 1
                }
                blocks.add(MDBlock.Table(header, rows))
                continue
            }

            // 引用
            if (line.startsWith(">")) {
                val buffer = mutableListOf<String>()
                while (index < lines.size) {
                    val current = lines[index].trim()
                    if (!current.startsWith(">")) break
                    buffer.add(current.drop(1).trim())
                    index += 1
                }
                blocks.add(MDBlock.Quote(parseInlineText(buffer.joinToString(" "))))
                continue
            }

            // 列表
            val marker = parseListMarker(raw)
            if (marker != null) {
                val segments = parseInlineText(marker.text)
                when (val kind = marker.kind) {
                    is ListKind.Bullet -> blocks.add(MDBlock.Bullet(segments, marker.indent))
                    is ListKind.Ordered -> blocks.add(MDBlock.Ordered(kind.number, segments, marker.indent))
                }
                index += 1
                continue
            }

            // 段落：连续的非空、非结构性行合并成一段
            val buffer = mutableListOf(line)
            index += 1
            while (index < lines.size) {
                val current = lines[index]
                val trimmed = current.trim()
                if (trimmed.isEmpty() ||
                    isRule(trimmed) ||
                    trimmed.startsWith("#") ||
                    trimmed.startsWith("```") ||
                    trimmed.startsWith(">") ||
                    (trimmed.contains("|") && index + 1 < lines.size && isTableSeparator(lines[index + 1])) ||
                    parseListMarker(current) != null
                ) {
                    break
                }
                buffer.add(trimmed)
                index += 1
            }
            // 段落拼回一行：图片之间的换行不能变成空格，否则 badge 会连成一条缝
            blocks.add(MDBlock.Paragraph(parseInlineParagraph(buffer)))
        }

        return blocks
    }

    private fun isRule(line: String): Boolean {
        if (line.length < 3) return false
        val ch = line[0]
        if (ch != '-' && ch != '*' && ch != '_') return false
        return line.length >= 3 && line.all { it == ch || it == ' ' } && line.count { it == ch } >= 3
    }

    private fun isTableSeparator(line: String): Boolean {
        val trimmed = line.trim()
        if (!trimmed.contains("-") || !trimmed.contains("|")) return false
        return trimmed.all { it == '-' || it == '|' || it == ':' || it == ' ' }
    }

    private fun splitTableRow(line: String): List<String> {
        var trimmed = line.trim()
        if (trimmed.startsWith("|")) trimmed = trimmed.substring(1)
        if (trimmed.endsWith("|")) trimmed = trimmed.dropLast(1)
        return splitOutsideCode(trimmed, '|').map { it.trim() }
    }

    /** 按分隔符切分，但跳过 `` ` `` 代码段里的分隔符（列名里常有 `a|b`） */
    private fun splitOutsideCode(source: String, separator: Char): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inCode = false
        for (ch in source) {
            when {
                ch == '`' -> {
                    inCode = !inCode
                    current.append(ch)
                }
                ch == separator && !inCode -> {
                    result.add(current.toString())
                    current.setLength(0)
                }
                else -> current.append(ch)
            }
        }
        result.add(current.toString())
        return result
    }

    /** 表格单元格：逐个转成行内序列 */
    private fun parseInline(cells: List<String>): List<List<MDInline>> = cells.map { parseInlineText(it) }

    private fun parseInlineText(text: String): List<MDInline> = InlineScanner(text).scan()

    /**
     * 段落专用：把多行合并时，只要其中一行是「独占一行的图片」，就单独当作一个图片段，
     * 不参与后续的文字拼接 —— 否则 badge 之间会被塞进空格，看起来像乱码。
     */
    private fun parseInlineParagraph(lines: List<String>): List<MDInline> {
        val out = mutableListOf<MDInline>()
        for (line in lines) {
            out.addAll(parseInlineText(line))
        }
        return out
    }

    private sealed interface ListKind {
        data object Bullet : ListKind
        data class Ordered(val number: String) : ListKind
    }

    private class ListMarker(val kind: ListKind, val text: String, val indent: Int)

    private fun parseListMarker(raw: String): ListMarker? {
        val leading = raw.takeWhile { it == ' ' || it == '\t' }
        var width = 0
        for (ch in leading) width += if (ch == '\t') 4 else 1
        val indent = width / 2
        val line = raw.trim()
        if (line.isEmpty()) return null

        for (prefix in listOf("- ", "* ", "+ ")) {
            if (line.startsWith(prefix)) {
                val text = line.drop(prefix.length).trim()
                return ListMarker(ListKind.Bullet, text, indent)
            }
        }

        val digits = line.takeWhile { it.isDigit() }
        if (digits.isNotEmpty()) {
            val rest = line.drop(digits.length)
            if (rest.startsWith(". ") || rest.startsWith(") ")) {
                val text = rest.drop(2).trim()
                return ListMarker(ListKind.Ordered(digits), text, indent)
            }
        }
        return null
    }
}

/**
 * 行内扫描器：把文字切成「文本 / 图片」两态。
 *
 * 支持的图片写法：
 *  - `![alt](url)`                     → 直接图片
 *  - `[![alt](img)](link)`             → 可点击的 badge（README 里最常见的形态）
 *  - `<img src="..." alt="...">`       → 部分 README 会直接写 HTML（含 width/height）
 *
 * 普通链接 `[文字](url)` 仍然走文字路径，不会误判成图片。
 */
internal class InlineScanner(private val source: String) {

    fun scan(): List<MDInline> {
        val out = mutableListOf<MDInline>()
        val text = StringBuilder()
        var i = 0

        fun flush() {
            if (text.isNotEmpty()) {
                out.add(MDInline.Text(text.toString()))
                text.setLength(0)
            }
        }

        while (i < source.length) {
            // HTML <img ...>
            if (source[i] == '<') {
                val close = source.indexOf('>', i)
                if (close > i) {
                    val tag = source.substring(i, close + 1)
                    val img = parseHtmlImage(tag)
                    if (img != null) {
                        flush()
                        out.add(img)
                        i = close + 1
                        continue
                    }
                }
            }

            // [![alt](img)](link) —— badge
            if (source.startsWith("[![", i)) {
                val badge = parseBadge(source, i)
                if (badge != null) {
                    flush()
                    out.add(badge.first)
                    i = badge.second
                    continue
                }
            }

            // ![alt](img)
            if (source[i] == '!' && i + 1 < source.length && source[i + 1] == '[') {
                val img = parseImageAt(source, i)
                if (img != null) {
                    flush()
                    out.add(img.first)
                    i = img.second
                    continue
                }
            }

            text.append(source[i])
            i += 1
        }
        flush()
        return out
    }

    /** `[![alt](img)](link)` → 可点击图片 + 消费长度 */
    private fun parseBadge(src: String, start: Int): Pair<MDInline, Int>? {
        // 期望结构：[![ ... ]( ... )]( ... )
        if (!src.startsWith("[![", start)) return null
        val altEnd = src.indexOf(']', start + 3)
        if (altEnd < 0) return null
        val alt = src.substring(start + 3, altEnd)
        if (altEnd + 1 >= src.length || src[altEnd + 1] != '(') return null
        val imgUrlEnd = src.indexOf(')', altEnd + 2)
        if (imgUrlEnd < 0) return null
        val imgUrl = src.substring(altEnd + 2, imgUrlEnd)
        // 紧随其后应当是 ](link)
        if (imgUrlEnd + 1 >= src.length || src[imgUrlEnd + 1] != ']') return null
        val consumedWithLink: Int
        val link: String?
        if (imgUrlEnd + 2 < src.length && src[imgUrlEnd + 2] == '(') {
            val linkEnd = src.indexOf(')', imgUrlEnd + 3)
            if (linkEnd < 0) return null
            link = src.substring(imgUrlEnd + 3, linkEnd)
            consumedWithLink = linkEnd + 1
        } else {
            link = null
            consumedWithLink = imgUrlEnd + 2
        }
        return MDInline.Picture(imgUrl.trim(), alt, link) to consumedWithLink
    }

    /** `![alt](url)` → 图片 + 消费长度 */
    private fun parseImageAt(src: String, start: Int): Pair<MDInline, Int>? {
        val altStart = start + 2
        val altEnd = src.indexOf(']', altStart)
        if (altEnd < 0) return null
        if (altEnd + 1 >= src.length || src[altEnd + 1] != '(') return null
        val urlStart = altEnd + 2
        val urlEnd = findClosingParen(src, urlStart)
        if (urlEnd < 0) return null
        val alt = src.substring(altStart, altEnd)
        val url = parseUrlAndTitle(src.substring(urlStart, urlEnd))
            ?: return null
        return MDInline.Picture(url, alt, null) to urlEnd + 1
    }

    /** 链接 URL 可能带标题：`url "title"`，也可能带空格（GitHub 的 shields 地址偶尔如此） */
    private fun parseUrlAndTitle(body: String): String? {
        var value = body.trim()
        if (value.isEmpty()) return null
        // 去掉 markdown 的可选标题部分
        val quoted = value.indexOf('"')
        if (quoted > 0) value = value.substring(0, quoted).trim()
        if (value.startsWith("<") && value.endsWith(">")) value = value.drop(1).dropLast(1)
        return value.ifEmpty { null }
    }

    /** URL 里可能含括号（少数 shields 地址），按配对计数找真正的右括号 */
    private fun findClosingParen(src: String, from: Int): Int {
        var depth = 1
        var i = from
        while (i < src.length) {
            when (src[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
            i += 1
        }
        return -1
    }

    /** `<img src="..." alt="..." width="...">` */
    private fun parseHtmlImage(tag: String): MDInline.Picture? {
        if (!tag.startsWith("<img", ignoreCase = true)) return null
        val src = attr(tag, "src") ?: return null
        val alt = attr(tag, "alt").orEmpty()
        return MDInline.Picture(src, alt, null)
    }

    private fun attr(tag: String, name: String): String? {
        val pattern = Regex("""$name\s*=\s*(["'])(.*?)\1""", RegexOption.IGNORE_CASE)
        return pattern.find(tag)?.groupValues?.get(2)
    }
}

/** 把 `**粗**`、`` `代码` ``、`[文字](链接)` 解析为 AnnotatedString */
object MarkdownInline {

    fun render(
        text: String,
        baseFontSize: Float,
        baseWeight: FontWeight,
        baseColor: Color,
        colors: com.artifactboost.app.ui.theme.AppColors,
    ): AnnotatedString = buildAnnotatedString {
        var index = 0
        val n = text.length

        while (index < n) {
            val remainder = text.substring(index)

            // ***粗斜*** / **粗**
            if (remainder.startsWith("***")) {
                val end = remainder.substring(3).indexOf("***")
                if (end >= 0) {
                    val inner = remainder.substring(3, 3 + end)
                    withStyle(
                        SpanStyle(
                            fontSize = baseFontSize.sp,
                            fontWeight = FontWeight.Bold,
                            fontStyle = FontStyle.Italic,
                            color = baseColor,
                        ),
                    ) { append(inner) }
                    index += 3 + end + 3
                    continue
                }
            }

            if (remainder.startsWith("**")) {
                val end = remainder.substring(2).indexOf("**")
                if (end >= 0) {
                    val inner = remainder.substring(2, 2 + end)
                    withStyle(SpanStyle(fontSize = baseFontSize.sp, fontWeight = FontWeight.Bold, color = baseColor)) {
                        append(inner)
                    }
                    // 关掉的是 2 个字符，必须整体跳过，否则会漏出半个 `**`
                    index += 2 + end + 2
                    continue
                }
            }

            if (remainder.startsWith("`")) {
                val end = remainder.substring(1).indexOf("`")
                if (end >= 0) {
                    val inner = remainder.substring(1, 1 + end)
                    withStyle(
                        SpanStyle(
                            fontSize = (baseFontSize - 2f).sp,
                            fontFamily = FontFamily.Monospace,
                            color = colors.red,
                            background = colors.border.copy(alpha = 0.35f),
                        ),
                    ) { append(inner) }
                    index += 1 + end + 1
                    continue
                }
            }

            if (remainder.startsWith("[")) {
                val link = parseLink(remainder)
                if (link != null) {
                    withStyle(
                        SpanStyle(
                            fontSize = baseFontSize.sp,
                            fontWeight = baseWeight,
                            color = colors.blue,
                            textDecoration = TextDecoration.Underline,
                        ),
                    ) { append(link.label) }
                    index += link.consumed
                    continue
                }
            }

            withStyle(SpanStyle(fontSize = baseFontSize.sp, fontWeight = baseWeight, color = baseColor)) {
                append(remainder.substring(0, 1))
            }
            index += 1
        }
    }

    private class Link(val label: String, val url: String, val consumed: Int)

    private fun parseLink(source: String): Link? {
        var work = source
        if (!work.startsWith("[")) return null
        work = work.drop(1)
        val closeBracket = work.indexOf(']')
        if (closeBracket < 0) return null
        val label = work.substring(0, closeBracket)
        val afterBracket = closeBracket + 1
        if (afterBracket >= work.length || work[afterBracket] != '(') return null
        val urlStart = afterBracket + 1
        val closeParen = work.indexOf(')', urlStart)
        if (closeParen < 0) return null
        val url = work.substring(urlStart, closeParen)
        return Link(label, url, 1 + closeParen + 1)
    }
}

@Composable
fun MarkdownContent(markdown: String, modifier: Modifier = Modifier) {
    // 解析必须离开主线程。
    //
    // 老实现是 remember(markdown) { MarkdownParser.parse(...) } —— 那是在
    // composition 阶段同步跑解析。超长 README（动辄几百 KB、几千行）会把主线程顶住，
    // 表现就是「进了仓库详情页后，点返回没反应、要等一两秒才跳走」。
    // 改成 produceState + withContext(Dispatchers.Default)，解析期间先画骨架屏，
    // 主线程始终空闲，返回手势随时可响应。
    val blocks by produceState<List<MDBlock>>(initialValue = emptyList(), markdown) {
        value = withContext(Dispatchers.Default) { MarkdownParser.parse(markdown) }
    }

    if (blocks.isEmpty()) {
        // 解析期间占位：保证导航随时可返回，不会出现一片空白
        Box(modifier = modifier) { SkeletonBlock(6) }
        return
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        blocks.forEach { block -> MarkdownBlockView(block) }
    }
}

@Composable
private fun MarkdownBlockView(block: MDBlock) {
    val scheme = MaterialTheme.colorScheme
    val colors = AppTheme.colors

    when (block) {
        is MDBlock.Heading -> {
            val (size, weight) = when (block.level) {
                1 -> 20f to FontWeight.Bold
                2 -> 17f to FontWeight.Bold
                3 -> 15f to FontWeight.Bold
                else -> 13f to FontWeight.Bold
            }
            Text(
                text = MarkdownInline.render(block.text, size, weight, scheme.onSurface, colors),
                modifier = Modifier.padding(top = if (block.level <= 2) 6.dp else 2.dp),
            )
        }

        is MDBlock.Paragraph -> MarkdownSegmentFlow(
            segments = block.segments,
            fontSize = 14f,
            weight = FontWeight.Normal,
            color = scheme.onSurfaceVariant,
        )

        is MDBlock.Bullet -> MarkdownListRow(
            bullet = "•",
            segments = block.segments,
            indent = block.indent,
            monospaced = false,
        )

        is MDBlock.Ordered -> MarkdownListRow(
            bullet = "${block.number}.",
            segments = block.segments,
            indent = block.indent,
            monospaced = true,
        )

        is MDBlock.Quote -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .width(3.dp)
                    .heightIn(min = 20.dp)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.shapes.extraSmall,
                    ),
            )
            Box(Modifier.weight(1f)) {
                MarkdownSegmentFlow(
                    segments = block.segments,
                    fontSize = 14f,
                    weight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontStyle = FontStyle.Italic,
                )
            }
        }

        is MDBlock.Code -> Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant,
            ),
        ) {
            Column {
                if (block.language != null) {
                    Text(
                        block.language,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp, top = 8.dp),
                    )
                }
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp),
                ) {
                    Text(
                        block.content,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                        softWrap = false,
                    )
                }
            }
        }

        is MDBlock.Table -> MarkdownTableView(block.header, block.rows)

        MDBlock.Rule -> Hairline()

        is MDBlock.Image -> MarkdownImageView(block.url, block.alt)
    }
}

/**
 * 行内序列的排版容器。
 *
 * 纯文字时就是一个普通 [Text]，省掉一层布局；
 * 一旦混进图片（badge），就换成 [androidx.compose.foundation.layout.FlowRow] 风格的手工换行 ——
 * 用 Row + 自动换行把 badge 一个挨一个排好，而不是让它们排成一条长线被裁掉。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MarkdownSegmentFlow(
    segments: List<MDInline>,
    fontSize: Float,
    weight: FontWeight,
    color: Color,
    fontStyle: FontStyle = FontStyle.Normal,
) {
    val colors = AppTheme.colors
    val hasPicture = segments.any { it is MDInline.Picture }

    if (!hasPicture) {
        val text = segments.filterIsInstance<MDInline.Text>().joinToString("") { it.value }
        Text(
            text = MarkdownInline.render(text, fontSize, weight, color, colors),
            fontSize = fontSize.sp,
            fontStyle = fontStyle,
            lineHeight = (fontSize + 6f).sp,
        )
        return
    }

    // 混排：按「文字块 / 图片块」顺序依次摆开，图片之间自动换行
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        segments.forEach { segment ->
            when (segment) {
                is MDInline.Text -> {
                    if (segment.value.isBlank()) return@forEach
                    Text(
                        text = MarkdownInline.render(segment.value, fontSize, weight, color, colors),
                        fontSize = fontSize.sp,
                        fontStyle = fontStyle,
                        lineHeight = (fontSize + 6f).sp,
                    )
                }

                is MDInline.Picture -> MarkdownBadge(
                    url = segment.url,
                    alt = segment.alt,
                )
            }
        }
    }
}

/** 内联 badge / 图片：限高以免撑破版面，加载失败就退回 alt 文字 */
@Composable
private fun MarkdownBadge(url: String, alt: String) {
    val target = remember(url) { resolveMarkdownUrl(url) }

    if (target == null) {
        MarkdownRelativeImageHint(alt)
        return
    }

    SubcomposeAsyncImage(
        model = target,
        contentDescription = alt,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .heightIn(max = 28.dp)
            .widthIn(max = 220.dp)
            .clip(MaterialTheme.shapes.extraSmall),
        loading = {
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .height(20.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHighest,
                        MaterialTheme.shapes.extraSmall,
                    ),
            )
        },
        error = {
            if (alt.isNotEmpty()) {
                Text(alt, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
        },
    )
}

@Composable
private fun MarkdownListRow(
    bullet: String,
    segments: List<MDInline>,
    indent: Int,
    monospaced: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (minOf(indent, 4) * 14).dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            bullet,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            fontFamily = if (monospaced) FontFamily.Monospace else FontFamily.Default,
        )
        Box(Modifier.weight(1f)) {
            MarkdownSegmentFlow(
                segments = segments,
                fontSize = 14f,
                weight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// MARK: - 表格

/**
 * 表格：先量宽，再统一绘制。
 *
 * 旧实现每行各自 `widthIn(min = 80.dp, max = 220.dp)`，内容长短不同列宽就不同，
 * 于是「上下格没对齐」。正确做法是 ——
 * 先按整列里最宽的那个单元格定出列宽，再让该列所有行都用这个宽度。
 */
@Composable
private fun MarkdownTableView(header: List<List<MDInline>>, rows: List<List<List<MDInline>>>) {
    val colors = AppTheme.colors

    val columnCount = maxOf(
        header.size,
        rows.maxOfOrNull { it.size } ?: 0,
    )
    if (columnCount == 0) return

    // 量宽：按字符数估算每列需要的宽度（中文字符算 2 个宽度）
    val columnWidths = remember(header, rows) {
        val widths = IntArray(columnCount) { MIN_COLUMN_CHARS }
        fun measure(cells: List<List<MDInline>>) {
            cells.forEachIndexed { index, cell ->
                if (index >= columnCount) return@forEachIndexed
                val chars = visualLength(plainText(cell))
                if (chars > widths[index]) widths[index] = chars
            }
        }
        measure(header)
        rows.forEach { measure(it) }
        widths.map { chars ->
            // 每字符约 7dp，再留出左右 padding
            (chars * 7 + 24).coerceIn(72, 260)
        }
    }

    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                TableRowView(
                    cells = header,
                    columnCount = columnCount,
                    columnWidths = columnWidths,
                    isHeader = true,
                    isLastRow = false,
                )
            }
            rows.forEachIndexed { rowIndex, cells ->
                Hairline()
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    TableRowView(
                        cells = cells,
                        columnCount = columnCount,
                        columnWidths = columnWidths,
                        isHeader = false,
                        isLastRow = rowIndex == rows.lastIndex,
                    )
                }
            }
        }
    }
}

@Composable
private fun TableRowView(
    cells: List<List<MDInline>>,
    columnCount: Int,
    columnWidths: List<Int>,
    isHeader: Boolean,
    isLastRow: Boolean,
) {
    // 用 IntrinsicSize.Min 让同一行里所有单元格等高、内容垂直居中
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(
                if (isHeader) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
            )
            .height(IntrinsicSize.Min),
    ) {
        for (index in 0 until columnCount) {
            val cell = cells.getOrNull(index).orEmpty()
            Box(
                modifier = Modifier
                    .width(columnWidths[index].dp)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                MarkdownSegmentFlow(
                    segments = cell,
                    fontSize = 12f,
                    weight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                    color = if (isHeader) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            if (index < columnCount - 1) {
                // 竖线撑满整行高度，行与行之间才不会出现断口
                Box(
                    Modifier
                        .width(0.5.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }
        }
    }
}

private const val MIN_COLUMN_CHARS = 6

/** 估算显示宽度：CJK 字符按 2 个宽度算，其余按 1 */
private fun visualLength(text: String): Int {
    var width = 0
    for (ch in text) {
        width += if (ch.code > 0x2E80) 2 else 1
    }
    return width
}

private fun plainText(cells: List<MDInline>): String =
    cells.joinToString("") { segment ->
        when (segment) {
            is MDInline.Text -> segment.value
            is MDInline.Picture -> segment.alt
        }
    }

// MARK: - 图片

@Composable
private fun MarkdownImageView(url: String, alt: String) {
    val target = remember(url) { resolveMarkdownUrl(url) }

    if (target == null) {
        MarkdownRelativeImageHint(alt)
        return
    }

    SubcomposeAsyncImage(
        model = target,
        contentDescription = alt,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small),
        loading = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(modifier = Modifier.width(22.dp), strokeWidth = 2.dp) }
        },
        error = {
            Text(
                if (alt.isEmpty()) "图片加载失败" else alt,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        },
    )
}

@Composable
private fun MarkdownRelativeImageHint(alt: String) {
    // README 里相对路径的图片没法直接加载，提示一下而不是留个空白
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Text(
            if (alt.isEmpty()) "README 内的相对路径图片" else "$alt（相对路径）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(10.dp),
        )
    }
}

/**
 * README 里的图片地址常见几种形态，统一归一化：
 *  - `https://...` / `http://...` 直接用；
 *  - `//host/path` 补成 https；
 *  - `data:` 这类内嵌资源没法给 Coil，返回 null 让它走占位提示。
 */
private fun resolveMarkdownUrl(url: String): String? {
    val trimmed = url.trim()
    if (trimmed.isEmpty()) return null
    return when {
        trimmed.startsWith("https://") || trimmed.startsWith("http://") -> trimmed
        trimmed.startsWith("//") -> "https:$trimmed"
        trimmed.startsWith("data:") -> null
        else -> null
    }
}
