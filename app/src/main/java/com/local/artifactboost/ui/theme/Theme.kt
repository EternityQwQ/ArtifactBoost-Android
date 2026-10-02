package com.local.artifactboost.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 配色对齐 GitHub Primer，与 iOS 版 Theme.swift 使用同一组色值，
 * 保证两端看起来是同一个产品。
 */
object Theme {
    // Primer 调色板（浅色 / 深色）
    val Blue = Color(0xFF0969DA)
    val BlueDark = Color(0xFF2F81F7)
    val Green = Color(0xFF1F883D)
    val GreenDark = Color(0xFF3FB950)
    val Red = Color(0xFFCF222E)
    val RedDark = Color(0xFFF85149)
    val Purple = Color(0xFF8250DF)
    val PurpleDark = Color(0xFFA371F7)
    val Orange = Color(0xFFBC4C00)
    val OrangeDark = Color(0xFFDB6D28)
    val Yellow = Color(0xFF9A6700)
    val YellowDark = Color(0xFFD29922)

    val Muted = Color(0xFF656D76)
    val MutedDark = Color(0xFF8B949E)
    val Subtle = Color(0xFF8C959F)
    val SubtleDark = Color(0xFF6E7681)
    val Canvas = Color(0xFFF6F8FA)
    val CanvasDark = Color(0xFF0D1117)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceDark = Color(0xFF161B22)
    val Border = Color(0xFFD0D7DE)
    val BorderDark = Color(0xFF30363D)
    val StrongText = Color(0xFF1F2328)
    val StrongTextDark = Color(0xFFE6EDF3)
}

val isDark: Boolean
    @Composable get() = isSystemInDarkTheme()

@Composable fun accent(): Color = if (isDark) Theme.BlueDark else Theme.Blue
@Composable fun success(): Color = if (isDark) Theme.GreenDark else Theme.Green

/** success() 的别名，跟 iOS 版 Theme.swift 里的 green 命名对齐 */
@Composable fun green(): Color = success()
@Composable fun danger(): Color = if (isDark) Theme.RedDark else Theme.Red
@Composable fun red(): Color = danger()
@Composable fun purple(): Color = if (isDark) Theme.PurpleDark else Theme.Purple
@Composable fun warning(): Color = if (isDark) Theme.YellowDark else Theme.Yellow
@Composable fun orange(): Color = if (isDark) Theme.OrangeDark else Theme.Orange
@Composable fun muted(): Color = if (isDark) Theme.MutedDark else Theme.Muted
@Composable fun subtle(): Color = if (isDark) Theme.SubtleDark else Theme.Subtle
@Composable fun canvas(): Color = if (isDark) Theme.CanvasDark else Theme.Canvas
@Composable fun surface(): Color = if (isDark) Theme.SurfaceDark else Theme.Surface
@Composable fun border(): Color = if (isDark) Theme.BorderDark else Theme.Border
@Composable fun strongText(): Color = if (isDark) Theme.StrongTextDark else Theme.StrongText

/** GitHub 语言色（linguist 配色，取常用的几十种） */
fun languageColor(language: String?): Color = when (language) {
    "Swift" -> Color(0xFFF05138)
    "Kotlin" -> Color(0xFFA97BFF)
    "Java" -> Color(0xFFB07219)
    "Objective-C" -> Color(0xFF438EFF)
    "C" -> Color(0xFF555555)
    "C++" -> Color(0xFFF34B7D)
    "C#" -> Color(0xFF178600)
    "JavaScript" -> Color(0xFFF1E05A)
    "TypeScript" -> Color(0xFF3178C6)
    "Python" -> Color(0xFF3572A5)
    "Go" -> Color(0xFF00ADD8)
    "Rust" -> Color(0xFFDEA584)
    "Ruby" -> Color(0xFF701516)
    "PHP" -> Color(0xFF4F5D95)
    "Shell" -> Color(0xFF89E051)
    "HTML" -> Color(0xFFE34C26)
    "CSS" -> Color(0xFF563D7C)
    "Vue" -> Color(0xFF41B883)
    "Dart" -> Color(0xFF00B4AB)
    "Jupyter Notebook" -> Color(0xFFDA5B0B)
    "Markdown" -> Color(0xFF083FA1)
    else -> Color(0xFF8C959F)
}

private val LightColors = lightColorScheme(
    primary = Theme.Blue,
    onPrimary = Color.White,
    secondary = Theme.Purple,
    background = Theme.Canvas,
    onBackground = Theme.StrongText,
    surface = Theme.Surface,
    onSurface = Theme.StrongText,
    outline = Theme.Border,
    error = Theme.Red,
)

private val DarkColors = darkColorScheme(
    primary = Theme.BlueDark,
    onPrimary = Color.White,
    secondary = Theme.PurpleDark,
    background = Theme.CanvasDark,
    onBackground = Theme.StrongTextDark,
    surface = Theme.SurfaceDark,
    onSurface = Theme.StrongTextDark,
    outline = Theme.BorderDark,
    error = Theme.RedDark,
)

private val AppTypography = Typography().let { base ->
    base.copy(
        titleLarge = base.titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = base.labelSmall.copy(fontSize = 11.sp),
    )
}

@Composable
fun ArtifactBoostTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
