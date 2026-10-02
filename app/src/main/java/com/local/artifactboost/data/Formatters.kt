package com.local.artifactboost.data

import java.text.DecimalFormat
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 文件大小：1536 -> 1.5 KB */
fun formatBytes(bytes: Long): String {
    if (bytes < 0) return "0 B"
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index++
    }
    val pattern = if (value >= 100) "0" else "0.#"
    return DecimalFormat(pattern).format(value) + " " + units[index]
}

/** 速度：1048576 -> 1 MB/s */
fun formatSpeed(bytesPerSecond: Double): String =
    formatBytes(bytesPerSecond.toLong().coerceAtLeast(0)) + "/s"

/** 1200 -> 1.2k，34000 -> 34k（GitHub 的数字缩写风格） */
fun formatCount(count: Int): String = when {
    count < 1000 -> "$count"
    count < 10_000 -> String.format(java.util.Locale.US, "%.1fk", count / 1000.0)
    count < 1_000_000 -> "${count / 1000}k"
    else -> String.format(java.util.Locale.US, "%.1fm", count / 1_000_000.0)
}

/**
 * 相对时间：刚刚 / 5 分钟前 / 3 小时前 / 2 天前 / 4 个月前 / 1 年前。
 * 与 iOS 版 formatRelative 行为保持一致。
 */
fun formatRelative(iso: String?, now: Instant = Instant.now()): String? {
    val instant = parseInstant(iso) ?: return null
    val seconds = Duration.between(instant, now).seconds

    // 未来时间（时钟偏差）统一显示为「刚刚」，避免出现「-3 分钟前」
    if (seconds < 0) return "刚刚"
    if (seconds < 60) return "刚刚"

    val minutes = seconds / 60
    if (minutes < 60) return "$minutes 分钟前"

    val hours = minutes / 60
    if (hours < 24) return "$hours 小时前"

    val days = hours / 24
    if (days < 30) return "$days 天前"

    val months = days / 30
    if (months < 12) return "$months 个月前"

    return "${months / 12} 年前"
}

/** 2026-10-02 08:30 */
fun formatDateTime(iso: String?): String? {
    val instant = parseInstant(iso) ?: return null
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())
    return formatter.format(instant)
}

/** 2026-10-02 */
fun formatDate(iso: String?): String? {
    val instant = parseInstant(iso) ?: return null
    // 不用 LocalDate.ofInstant（那是 API 34 才有的），走 ZoneId 转换，24 也能用
    return instant.atZone(ZoneId.systemDefault()).toLocalDate().toString()
}

/**
 * GitHub 返回的是 ISO8601（形如 2026-10-02T00:43:21Z，也可能带小数秒或 +08:00 偏移）。
 * 依次尝试三种解析方式，都失败才返回 null，避免个别响应格式差异导致整页炸掉。
 */
private fun parseInstant(iso: String?): Instant? {
    if (iso.isNullOrBlank()) return null
    return runCatching { Instant.parse(iso) }
        .recoverCatching { OffsetDateTime.parse(iso).toInstant() }
        .recoverCatching {
            // 最后兜底：按本地时区解析不带时区的形式
            LocalDateTime.parse(iso).atZone(ZoneId.systemDefault()).toInstant()
        }
        .getOrNull()
}
