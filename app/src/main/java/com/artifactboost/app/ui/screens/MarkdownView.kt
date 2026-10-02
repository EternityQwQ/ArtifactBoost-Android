package com.artifactboost.app.ui.screens

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
 */

sealed interface MDBlock {
    data class Heading(val level: Int, val text: String) : MDBlock
    data class Paragraph(val text: String) : MDBlock
    data class Bullet(val text: String, val indent: Int) : MDBlock
    data class Ordered(val number: String, val text: String, val indent: Int) : MDBlock
    data class Quote(val text: String) : MDBlock
    data class Code(val language: String?, val content: String) : MDBlock
    data class Table(val header: List<String>, val rows: List<List<String>>) : MDBlock
    data object Rule : MDBlock
    data class Image(val url: String, val alt: String) : MDBlock
}

object MarkdownParser {
    fun parse(source: String): List<MDBlock> {
        val blocks = mutableListOf<MDBlock>()
        val lines = source.replace("\r\n", "\n").split("\n")
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

            // 分割线
            if (line == "---" || line == "***" || line == "___") {
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
                val header = splitTableRow(line)
                val rows = mutableListOf<List<String>>()
                index += 2
                while (index < lines.size && lines[index].contains("|")) {
                    rows.add(splitTableRow(lines[index]))
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
                blocks.add(MDBlock.Quote(buffer.joinToString(" ")))
                continue
            }

            // 独占一行的图片
            if (line.startsWith("![")) {
                val image = parseImage(line)
                if (image != null) {
                    blocks.add(MDBlock.Image(image.first, image.second))
                    index += 1
                    continue
                }
            }

            // 列表
            val marker = parseListMarker(raw)
            if (marker != null) {
                when (val kind = marker.kind) {
                    is ListKind.Bullet -> blocks.add(MDBlock.Bullet(marker.text, marker.indent))
                    is ListKind.Ordered -> blocks.add(MDBlock.Ordered(kind.number, marker.text, marker.indent))
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
                    trimmed.startsWith("#") ||
                    trimmed.startsWith("```") ||
                    trimmed.startsWith(">") ||
                    trimmed == "---" || trimmed == "***" || trimmed == "___" ||
                    parseListMarker(current) != null
                ) {
                    break
                }
                buffer.add(trimmed)
                index += 1
            }
            blocks.add(MDBlock.Paragraph(buffer.joinToString(" ")))
        }

        return blocks
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
        return trimmed.split("|").map { it.trim() }
    }

    /** 返回 (url, alt) */
    private fun parseImage(line: String): Pair<String, String>? {
        val openAlt = line.indexOf('[')
        val closeAlt = line.indexOf(']')
        val openUrl = line.indexOf('(')
        val closeUrl = line.lastIndexOf(')')
        if (openAlt < 0 || closeAlt < 0 || openUrl < 0 || closeUrl < 0) return null
        if (!(openAlt < closeAlt && closeAlt < openUrl && openUrl < closeUrl)) return null
        val alt = line.substring(openAlt + 1, closeAlt)
        val url = line.substring(openUrl + 1, closeUrl)
        return url to alt
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

            if (remainder.startsWith("[") || remainder.startsWith("![")) {
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
        var offset = 0
        when {
            work.startsWith("![") -> {
                work = work.drop(2)
                offset = 2
            }

            work.startsWith("[") -> {
                work = work.drop(1)
                offset = 1
            }

            else -> return null
        }
        val closeBracket = work.indexOf(']')
        if (closeBracket < 0) return null
        val label = work.substring(0, closeBracket)
        val afterBracket = closeBracket + 1
        if (afterBracket >= work.length || work[afterBracket] != '(') return null
        val urlStart = afterBracket + 1
        val closeParen = work.indexOf(')', urlStart)
        if (closeParen < 0) return null
        val url = work.substring(urlStart, closeParen)
        return Link(label, url, offset + closeParen + 1)
    }
}

@Composable
fun MarkdownContent(markdown: String, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val blocks = remember(markdown) { MarkdownParser.parse(markdown) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        blocks.forEach { block -> MarkdownBlockView(block) }
    }
}

@Composable
private fun MarkdownBlockView(block: MDBlock) {
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
                text = MarkdownInline.render(block.text, size, weight, colors.strongText, colors),
                modifier = Modifier.padding(top = if (block.level <= 2) 6.dp else 2.dp),
            )
        }

        is MDBlock.Paragraph -> Text(
            text = MarkdownInline.render(block.text, 14f, FontWeight.Normal, colors.muted, colors),
            lineHeight = 20.sp,
        )

        is MDBlock.Bullet -> MarkdownListRow(
            bullet = "•",
            text = block.text,
            indent = block.indent,
            monospaced = false,
        )

        is MDBlock.Ordered -> MarkdownListRow(
            bullet = "${block.number}.",
            text = block.text,
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
                    .background(colors.border),
            )
            Text(
                text = MarkdownInline.render(block.text, 14f, FontWeight.Normal, colors.subtle, colors),
                fontStyle = FontStyle.Italic,
                lineHeight = 20.sp,
            )
        }

        is MDBlock.Code -> Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(colors.canvas)
                .border(1.dp, colors.border, RoundedCornerShape(8.dp)),
        ) {
            if (block.language != null) {
                Text(
                    block.language,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.subtle,
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
                    fontSize = 12.5.sp,
                    fontFamily = FontFamily.Monospace,
                    color = colors.strongText,
                    softWrap = false,
                )
            }
        }

        is MDBlock.Table -> MarkdownTableView(block.header, block.rows)

        MDBlock.Rule -> Hairline()

        is MDBlock.Image -> MarkdownImageView(block.url, block.alt)
    }
}

@Composable
private fun MarkdownListRow(
    bullet: String,
    text: String,
    indent: Int,
    monospaced: Boolean,
) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (minOf(indent, 4) * 14).dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            bullet,
            fontSize = 14.sp,
            color = colors.subtle,
            fontFamily = if (monospaced) FontFamily.Monospace else FontFamily.Default,
        )
        Text(
            text = MarkdownInline.render(text, 14f, FontWeight.Normal, colors.muted, colors),
            lineHeight = 20.sp,
        )
    }
}

@Composable
private fun MarkdownTableView(header: List<String>, rows: List<List<String>>) {
    val colors = AppTheme.colors
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, colors.border, RoundedCornerShape(8.dp))
            .background(colors.surface),
    ) {
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            TableRowView(header, isHeader = true)
        }
        rows.forEach { cells ->
            Hairline()
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                TableRowView(cells, isHeader = false)
            }
        }
    }
}

@Composable
private fun TableRowView(cells: List<String>, isHeader: Boolean) {
    val colors = AppTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        cells.forEachIndexed { index, cell ->
            Text(
                text = MarkdownInline.render(
                    cell,
                    if (isHeader) 12f else 12f,
                    if (isHeader) FontWeight.Bold else FontWeight.Normal,
                    if (isHeader) colors.strongText else colors.muted,
                    colors,
                ),
                modifier = Modifier
                    .widthIn(min = 80.dp, max = 220.dp)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            )
            if (index < cells.size - 1) {
                Box(
                    Modifier
                        .width(0.5.dp)
                        .height(28.dp)
                        .background(colors.border),
                )
            }
        }
    }
}

@Composable
private fun MarkdownImageView(url: String, alt: String) {
    val colors = AppTheme.colors
    val isAbsolute = url.startsWith("http://") || url.startsWith("https://")

    if (isAbsolute) {
        SubcomposeAsyncImage(
            model = url,
            contentDescription = alt,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp)),
            loading = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .background(colors.border.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(modifier = Modifier.width(22.dp), strokeWidth = 2.dp) }
            },
            error = {
                Text(
                    if (alt.isEmpty()) "图片加载失败" else alt,
                    fontSize = 12.sp,
                    color = colors.subtle,
                )
            },
        )
    } else {
        // README 里相对路径的图片没法直接加载，提示一下而不是留个空白
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(colors.canvas)
                .padding(10.dp),
        ) {
            Text(
                if (alt.isEmpty()) "README 内的相对路径图片" else "$alt（相对路径）",
                fontSize = 12.sp,
                color = colors.subtle,
            )
        }
    }
}
