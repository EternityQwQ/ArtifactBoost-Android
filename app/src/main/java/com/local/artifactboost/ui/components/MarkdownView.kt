package com.local.artifactboost.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.artifactboost.ui.theme.accent
import com.local.artifactboost.ui.theme.border
import com.local.artifactboost.ui.theme.canvas
import com.local.artifactboost.ui.theme.muted
import com.local.artifactboost.ui.theme.strongText
import com.local.artifactboost.ui.theme.subtle

/**
 * 轻量 Markdown 渲染，够用于 README。
 * 解析逻辑与 iOS 版 MarkdownParser 完全一致（同一套分支判定顺序），
 * 包括「关闭标记必须整体跳过」这个曾经踩过的坑。
 */

sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Bullet(val text: String, val indent: Int) : MdBlock
    data class Ordered(val number: String, val text: String, val indent: Int) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Code(val language: String?, val content: String) : MdBlock
    data class Table(val header: List<String>, val rows: List<List<String>>) : MdBlock
    data object Rule : MdBlock
    data class Image(val url: String, val alt: String) : MdBlock
}

object MarkdownParser {

    fun parse(source: String): List<MdBlock> {
        val blocks = mutableListOf<MdBlock>()
        val lines = source.replace("\r\n", "\n").split("\n")
        var index = 0

        while (index < lines.size) {
            val raw = lines[index]
            val line = raw.trim()

            if (line.isEmpty()) {
                index++
                continue
            }

            // ``` 代码块
            if (line.startsWith("```")) {
                val language = line.removePrefix("```").trim()
                val buffer = mutableListOf<String>()
                index++
                while (index < lines.size && !lines[index].trim().startsWith("```")) {
                    buffer.add(lines[index])
                    index++
                }
                index++ // 跳过结尾 ```
                blocks.add(MdBlock.Code(language.ifEmpty { null }, buffer.joinToString("\n")))
                continue
            }

            // 分割线
            if (line == "---" || line == "***" || line == "___") {
                blocks.add(MdBlock.Rule)
                index++
                continue
            }

            // 标题
            if (line.startsWith("#")) {
                val hashes = line.takeWhile { it == '#' }.length
                if (hashes in 1..6) {
                    val text = line.drop(hashes).trim()
                    if (text.isNotEmpty()) {
                        blocks.add(MdBlock.Heading(minOf(hashes, 4), text))
                        index++
                        continue
                    }
                }
            }

            // 表格
            if (line.contains("|") && index + 1 < lines.size && isTableSeparator(lines[index + 1])) {
                val header = splitTableRow(line)
                val rows = mutableListOf<List<String>>()
                index += 2
                while (index < lines.size && lines[index].contains("|")) {
                    rows.add(splitTableRow(lines[index]))
                    index++
                }
                blocks.add(MdBlock.Table(header, rows))
                continue
            }

            // 引用
            if (line.startsWith(">")) {
                val buffer = mutableListOf<String>()
                while (index < lines.size) {
                    val current = lines[index].trim()
                    if (!current.startsWith(">")) break
                    buffer.add(current.removePrefix(">").trim())
                    index++
                }
                blocks.add(MdBlock.Quote(buffer.joinToString(" ")))
                continue
            }

            // 独占一行的图片
            if (line.startsWith("![")) {
                val image = parseImage(line)
                if (image != null) {
                    blocks.add(MdBlock.Image(image.first, image.second))
                    index++
                    continue
                }
            }

            // 列表
            val marker = parseListMarker(raw)
            if (marker != null) {
                val (kind, text, indent) = marker
                when (kind) {
                    is ListKind.Bullet -> blocks.add(MdBlock.Bullet(text, indent))
                    is ListKind.Ordered -> blocks.add(MdBlock.Ordered(kind.number, text, indent))
                }
                index++
                continue
            }

            // 段落：连续的非空、非结构性行合并成一段
            val buffer = mutableListOf(line)
            index++
            while (index < lines.size) {
                val current = lines[index]
                val trimmed = current.trim()
                if (trimmed.isEmpty() ||
                    trimmed.startsWith("#") ||
                    trimmed.startsWith("```") ||
                    trimmed.startsWith(">") ||
                    trimmed == "---" || trimmed == "***" || trimmed == "___" ||
                    parseListMarker(current) != null
                ) break
                buffer.add(trimmed)
                index++
            }
            blocks.add(MdBlock.Paragraph(buffer.joinToString(" ")))
        }

        return blocks
    }

    private fun isTableSeparator(line: String): Boolean {
        val trimmed = line.trim()
        if (!trimmed.contains("-") || !trimmed.contains("|")) return false
        return trimmed.all { it == '-' || it == '|' || it == ':' || it == ' ' }
    }

    private fun splitTableRow(line: String): List<String> =
        line.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() }

    /** 返回 (url, alt) */
    private fun parseImage(line: String): Pair<String, String>? {
        val openAlt = line.indexOf('[')
        val closeAlt = line.indexOf(']')
        val openUrl = line.indexOf('(')
        val closeUrl = line.lastIndexOf(')')
        if (openAlt < 0 || closeAlt < 0 || openUrl < 0 || closeUrl < 0) return null
        if (!(openAlt < closeAlt && closeAlt < openUrl && openUrl < closeUrl)) return null
        return line.substring(openUrl + 1, closeUrl) to line.substring(openAlt + 1, closeAlt)
    }

    private sealed interface ListKind {
        data object Bullet : ListKind
        data class Ordered(val number: String) : ListKind
    }

    /** 返回 (类型, 文本, 缩进) */
    private fun parseListMarker(raw: String): Triple<ListKind, String, Int>? {
        val indent = raw.takeWhile { it == ' ' || it == '\t' }
            .fold(0) { acc, ch -> acc + (if (ch == '\t') 4 else 1) } / 2
        val line = raw.trim()
        if (line.isEmpty()) return null

        for (prefix in listOf("- ", "* ", "+ ")) {
            if (line.startsWith(prefix)) {
                return Triple(ListKind.Bullet, line.removePrefix(prefix).trim(), indent)
            }
        }

        val digits = line.takeWhile { it.isDigit() }
        if (digits.isNotEmpty()) {
            val rest = line.drop(digits.length)
            if (rest.startsWith(". ") || rest.startsWith(") ")) {
                return Triple(ListKind.Ordered(digits), rest.drop(2).trim(), indent)
            }
        }
        return null
    }
}

/** 把 `**粗**`、`` `代码` ``、`[文字](链接)` 解析为 AnnotatedString */
object MarkdownInline {

    fun render(
        text: String,
        color: androidx.compose.ui.graphics.Color,
        codeColor: androidx.compose.ui.graphics.Color,
        linkColor: androidx.compose.ui.graphics.Color,
        bold: Boolean = false,
    ): AnnotatedString = buildAnnotatedString {
        var index = 0
        val base = SpanStyle(
            color = color,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        )

        fun appendPlain(s: String) = withStyle(base) { append(s) }

        // 用 if/else 链而不是 continue：buildAnnotatedString 的 lambda 是 inline 的，
        // 在里面用 continue 需要额外的实验性 opt-in，链式判断更省事也更好读。
        while (index < text.length) {
            val remainder = text.substring(index)
            var consumed = 0

            if (remainder.startsWith("**")) {
                val end = remainder.indexOf("**", startIndex = 2)
                if (end >= 0) {
                    withStyle(base.copy(fontWeight = FontWeight.Bold)) {
                        append(remainder.substring(2, end))
                    }
                    // 关掉的是 2 个字符，必须整体跳过，否则会漏出半个 `**`
                    consumed = end + 2
                }
            }

            if (consumed == 0 && remainder.startsWith("`")) {
                val end = remainder.indexOf('`', startIndex = 1)
                if (end >= 0) {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            color = codeColor,
                            fontSize = 13.sp,
                        ),
                    ) { append(remainder.substring(1, end)) }
                    consumed = end + 1
                }
            }

            if (consumed == 0 && (remainder.startsWith("[") || remainder.startsWith("!["))) {
                val link = parseLink(remainder)
                if (link != null) {
                    withStyle(base.copy(color = linkColor, fontWeight = FontWeight.Medium)) {
                        append(link.first)
                    }
                    consumed = link.second
                }
            }

            if (consumed == 0) {
                appendPlain(remainder.substring(0, 1))
                consumed = 1
            }

            index += consumed
        }
    }

    /** 返回 (label, 消耗长度)。消耗长度是相对原始 slice 的，包含被剥掉的前缀。 */
    private fun parseLink(slice: String): Pair<String, Int>? {
        val prefix = when {
            slice.startsWith("![") -> 2
            slice.startsWith("[") -> 1
            else -> return null
        }
        val work = slice.substring(prefix)

        val closeBracket = work.indexOf(']')
        if (closeBracket < 0) return null
        val label = work.substring(0, closeBracket)

        val afterBracket = closeBracket + 1
        if (afterBracket >= work.length || work[afterBracket] != '(') return null

        val closeParen = work.indexOf(')', startIndex = afterBracket + 1)
        if (closeParen < 0) return null

        return label to (prefix + closeParen + 1)
    }
}

@Composable
fun MarkdownView(markdown: String) {
    val blocks = MarkdownParser.parse(markdown)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        blocks.forEach { block -> BlockView(block) }
    }
}

@Composable
private fun BlockView(block: MdBlock) {
    when (block) {
        is MdBlock.Heading -> {
            val (size, weight) = when (block.level) {
                1 -> 20.sp to FontWeight.Bold
                2 -> 17.sp to FontWeight.Bold
                3 -> 15.sp to FontWeight.Bold
                else -> 14.sp to FontWeight.Bold
            }
            Text(
                text = MarkdownInline.render(block.text, strongText(), codeColor(), accent()),
                fontSize = size,
                fontWeight = weight,
                color = strongText(),
            )
        }

        is MdBlock.Paragraph -> Text(
            text = MarkdownInline.render(block.text, muted(), codeColor(), accent()),
            fontSize = 14.sp,
            lineHeight = 21.sp,
            color = muted(),
        )

        is MdBlock.Bullet -> ListRow("•", block.text, block.indent, false)

        is MdBlock.Ordered -> ListRow("${block.number}.", block.text, block.indent, true)

        is MdBlock.Quote -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(41.dp)
                    .background(border()),
            )
            Text(
                text = MarkdownInline.render(block.text, subtle(), codeColor(), accent()),
                fontSize = 14.sp,
                fontStyle = FontStyle.Italic,
                color = subtle(),
            )
        }

        is MdBlock.Code -> Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(canvas())
                .border(1.dp, border(), RoundedCornerShape(8.dp)),
        ) {
            if (block.language != null) {
                Text(
                    block.language,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = subtle(),
                    modifier = Modifier.padding(start = 12.dp, top = 8.dp),
                )
            }
            Text(
                text = block.content,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.5.sp,
                color = strongText(),
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp),
            )
        }

        is MdBlock.Table -> MarkdownTable(block.header, block.rows)

        MdBlock.Rule -> Hairline()

        is MdBlock.Image -> {
            // README 里相对路径的图片没法直接加载，提示一下而不是留个空白
            val isAbsolute = block.url.startsWith("http://") || block.url.startsWith("https://")
            if (isAbsolute) {
                Text(
                    text = "🖼 ${block.alt.ifEmpty { "图片" }}",
                    fontSize = 12.sp,
                    color = accent(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(canvas())
                        .padding(10.dp),
                )
            } else {
                Text(
                    text = "🖼 ${block.alt.ifEmpty { "README 内的相对路径图片" }}（相对路径）",
                    fontSize = 12.sp,
                    color = subtle(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(canvas())
                        .padding(10.dp),
                )
            }
        }
    }
}

@Composable
private fun ListRow(bullet: String, text: String, indent: Int, monoDigit: Boolean) {
    Row(
        modifier = Modifier.padding(start = (minOf(indent, 4) * 14).dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            bullet,
            fontSize = 14.sp,
            color = subtle(),
            fontFamily = if (monoDigit) FontFamily.Monospace else FontFamily.Default,
        )
        Text(
            text = MarkdownInline.render(text, muted(), codeColor(), accent()),
            fontSize = 14.sp,
            lineHeight = 21.sp,
            color = muted(),
        )
    }
}

@Composable
private fun MarkdownTable(header: List<String>, rows: List<List<String>>) {
    Column(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, border(), RoundedCornerShape(8.dp)),
    ) {
        TableRow(header, isHeader = true)
        rows.forEach { cells ->
            Hairline()
            TableRow(cells, isHeader = false)
        }
    }
}

@Composable
private fun TableRow(cells: List<String>, isHeader: Boolean) {
    Row {
        cells.forEachIndexed { index, cell ->
            Text(
                text = MarkdownInline.render(
                    cell,
                    if (isHeader) strongText() else muted(),
                    codeColor(),
                    accent(),
                    bold = isHeader,
                ),
                fontSize = 13.sp,
                color = if (isHeader) strongText() else muted(),
                modifier = Modifier
                    .width(120.dp)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            )
            if (index < cells.size - 1) {
                Box(
                    Modifier
                        .width(0.5.dp)
                        .height(30.dp)
                        .background(border()),
                )
            }
        }
    }
}

@Composable
private fun codeColor() = androidx.compose.ui.graphics.Color(0xFFCF222E)
