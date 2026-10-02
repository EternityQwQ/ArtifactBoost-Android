package com.artifactboost.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 视觉规范：配色与控件都对齐 GitHub 移动端（Primer 调色板）。
 * 对应 iOS 版的 Theme.swift。
 */
object Theme {
    // MARK: - Primer 调色板（浅色 / 深色两套）

    val blueLight = Color(0xFF0969DA); val blueDark = Color(0xFF2F81F7)
    val greenLight = Color(0xFF1F883D); val greenDark = Color(0xFF3FB950)
    val redLight = Color(0xFFCF222E); val redDark = Color(0xFFF85149)
    val purpleLight = Color(0xFF8250DF); val purpleDark = Color(0xFFA371F7)
    val orangeLight = Color(0xFFBC4C00); val orangeDark = Color(0xFFDB6D28)
    val yellowLight = Color(0xFF9A6700); val yellowDark = Color(0xFFD29922)

    val mutedLight = Color(0xFF656D76); val mutedDark = Color(0xFF8B949E)
    val subtleLight = Color(0xFF8C959F); val subtleDark = Color(0xFF6E7681)
    val canvasLight = Color(0xFFF6F8FA); val canvasDark = Color(0xFF0D1117)
    val surfaceLight = Color(0xFFFFFFFF); val surfaceDark = Color(0xFF161B22)
    val borderLight = Color(0xFFD0D7DE); val borderDark = Color(0xFF30363D)
    val strongTextLight = Color(0xFF1F2328); val strongTextDark = Color(0xFFE6EDF3)
}

/** 主题色板，通过 CompositionLocal 下发，方便各组件取用原始 Primer 色值 */
data class AppColors(
    val blue: Color,
    val green: Color,
    val red: Color,
    val purple: Color,
    val orange: Color,
    val yellow: Color,
    val muted: Color,
    val subtle: Color,
    val canvas: Color,
    val surface: Color,
    val border: Color,
    val strongText: Color,
    val isDark: Boolean,
)

val LocalAppColors = staticCompositionLocalOf {
    AppColors(
        blue = Theme.blueLight,
        green = Theme.greenLight,
        red = Theme.redLight,
        purple = Theme.purpleLight,
        orange = Theme.orangeLight,
        yellow = Theme.yellowLight,
        muted = Theme.mutedLight,
        subtle = Theme.subtleLight,
        canvas = Theme.canvasLight,
        surface = Theme.surfaceLight,
        border = Theme.borderLight,
        strongText = Theme.strongTextLight,
        isDark = false,
    )
}

object AppTheme {
    val colors: AppColors
        @Composable @ReadOnlyComposable get() = LocalAppColors.current
}

@Composable
fun ArtifactBoostTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) {
        AppColors(
            blue = Theme.blueDark,
            green = Theme.greenDark,
            red = Theme.redDark,
            purple = Theme.purpleDark,
            orange = Theme.orangeDark,
            yellow = Theme.yellowDark,
            muted = Theme.mutedDark,
            subtle = Theme.subtleDark,
            canvas = Theme.canvasDark,
            surface = Theme.surfaceDark,
            border = Theme.borderDark,
            strongText = Theme.strongTextDark,
            isDark = true,
        )
    } else {
        AppColors(
            blue = Theme.blueLight,
            green = Theme.greenLight,
            red = Theme.redLight,
            purple = Theme.purpleLight,
            orange = Theme.orangeLight,
            yellow = Theme.yellowLight,
            muted = Theme.mutedLight,
            subtle = Theme.subtleLight,
            canvas = Theme.canvasLight,
            surface = Theme.surfaceLight,
            border = Theme.borderLight,
            strongText = Theme.strongTextLight,
            isDark = false,
        )
    }

    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.blue,
            onPrimary = Color.White,
            secondary = colors.green,
            background = colors.canvas,
            onBackground = colors.strongText,
            surface = colors.surface,
            onSurface = colors.strongText,
            outline = colors.border,
            error = colors.red,
        )
    } else {
        lightColorScheme(
            primary = colors.blue,
            onPrimary = Color.White,
            secondary = colors.green,
            background = colors.canvas,
            onBackground = colors.strongText,
            surface = colors.surface,
            onSurface = colors.strongText,
            outline = colors.border,
            error = colors.red,
        )
    }

    CompositionLocalProvider(LocalAppColors provides colors) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography(),
            content = content,
        )
    }
}

/** GitHub 语言色（linguist 配色，取常用的几十种） */
fun languageColor(language: String?, colors: AppColors): Color = when (language) {
    "Swift" -> Color(0xFFF05138)
    "Objective-C" -> Color(0xFF438EFF)
    "C" -> Color(0xFF555555)
    "C++" -> Color(0xFFF34B7D)
    "C#" -> Color(0xFF178600)
    "Java" -> Color(0xFFB07219)
    "Kotlin" -> Color(0xFFA97BFF)
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
    else -> colors.muted
}
