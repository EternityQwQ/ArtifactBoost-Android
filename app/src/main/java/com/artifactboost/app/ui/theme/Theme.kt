package com.artifactboost.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Material Design 3 主题。
 *
 * 分层说明（M3 规范做法）：
 * - 表面 / 背景 / 主色：走 [MaterialTheme.colorScheme]，支持动态取色（Monet），
 *   浅色/深色/动态三套都是完整的 M3 tonal 色板，不会再冒出默认紫。
 * - 语义色（构建状态红/绿、语言色、Primer 蓝/紫/橙）：走 [LocalAppColors]，
 *   承载信息、不随壁纸变色，保证“成功永远是绿、失败永远是红”。
 *
 * 对应 iOS 版的 Theme.swift（Primer 调色板）。
 */
object Theme {
    // Primer 调色板（浅色 / 深色两套，只做语义色）
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

/** 语义色板：只放“带信息”的颜色，表面色一律从 MaterialTheme.colorScheme 取 */
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

/** M3 shape scale：extraSmall → extraLarge，组件统一从 MaterialTheme.shapes 取 */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * M3 字阶：标题更紧、正文更松。
 * display/headline 沿用默认，title/label 加粗半档，labelSmall 带字距。
 */
private val AppTypography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(letterSpacing = (-0.25).sp),
        displayMedium = base.displayMedium.copy(letterSpacing = 0.sp),
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
        bodyLarge = base.bodyLarge.copy(letterSpacing = 0.15.sp),
        bodyMedium = base.bodyMedium.copy(letterSpacing = 0.15.sp),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
        labelMedium = base.labelMedium.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
        labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
    )
}

/**
 * 是否启用 Android 12+ 动态取色（Monet）。M3 标志性体验，默认开。
 * 开启后主色/表面跟随壁纸，语义色（红/绿/蓝…）仍钉死在 Primer 上。
 */
private const val USE_DYNAMIC_COLOR = true

@Composable
fun ArtifactBoostTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = USE_DYNAMIC_COLOR,
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

    val context = LocalContext.current
    val scheme: ColorScheme = when {
        // M3 动态取色：表面/主色跟随系统壁纸，只把语义槽钉死。
        // 之前实现把 background/surface 也钉回 Primer，等于关掉了动态取色的
        // tonal 表面——这里改成保留系统 tonal，只替换信息色。
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val dynamic = if (darkTheme) {
                dynamicDarkColorScheme(context)
            } else {
                dynamicLightColorScheme(context)
            }
            dynamic.copy(
                secondary = colors.green,
                onSecondary = Color.White,
                secondaryContainer = colors.green.copy(alpha = 0.16f),
                onSecondaryContainer = colors.green,
                tertiary = colors.purple,
                onTertiary = Color.White,
                tertiaryContainer = colors.purple.copy(alpha = 0.16f),
                onTertiaryContainer = colors.purple,
                error = colors.red,
                onError = Color.White,
                errorContainer = colors.red.copy(alpha = 0.14f),
                onErrorContainer = colors.red,
            )
        }
        darkTheme -> DarkScheme(colors)
        else -> LightScheme(colors)
    }

    CompositionLocalProvider(LocalAppColors provides colors) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

/** 浅色 M3 全色板：以 Primer 蓝为 primary，表面走 GitHub canvas/surface 层级 */
private fun LightScheme(colors: AppColors): ColorScheme = lightColorScheme(
    primary = colors.blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE1EDFB),
    onPrimaryContainer = Color(0xFF0550AE),
    inversePrimary = Color(0xFF8AB4F8),
    secondary = colors.green,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4F0E7),
    onSecondaryContainer = Color(0xFF0F5132),
    tertiary = colors.purple,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEDE7FB),
    onTertiaryContainer = Color(0xFF4C1D95),
    background = colors.canvas,
    onBackground = colors.strongText,
    surface = colors.surface,
    onSurface = colors.strongText,
    surfaceVariant = Color(0xFFE7EAEE),
    onSurfaceVariant = colors.muted,
    surfaceTint = colors.blue,
    inverseSurface = colors.strongText,
    inverseOnSurface = colors.surface,
    surfaceContainerLowest = colors.surface,
    surfaceContainerLow = colors.canvas,
    surfaceContainer = Color(0xFFEFF1F3),
    surfaceContainerHigh = Color(0xFFEAECEF),
    surfaceContainerHighest = Color(0xFFE3E6EA),
    surfaceBright = colors.surface,
    surfaceDim = Color(0xFFDDE0E3),
    error = colors.red,
    onError = Color.White,
    errorContainer = Color(0xFFFCE8E9),
    onErrorContainer = Color(0xFF7D1A1F),
    outline = colors.border,
    outlineVariant = colors.border.copy(alpha = 0.6f),
    scrim = Color.Black,
)

/** 深色 M3 全色板：表面层级上亮下暗，卡片比背景高一层 */
private fun DarkScheme(colors: AppColors): ColorScheme = darkColorScheme(
    primary = colors.blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1B2F4C),
    onPrimaryContainer = Color(0xFFBCD6FA),
    inversePrimary = colors.blue,
    secondary = colors.green,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF14331F),
    onSecondaryContainer = Color(0xFF9EE6B0),
    tertiary = colors.purple,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF2E2148),
    onTertiaryContainer = Color(0xFFD5BFFC),
    background = colors.canvas,
    onBackground = colors.strongText,
    surface = colors.surface,
    onSurface = colors.strongText,
    surfaceVariant = Color(0xFF2A3036),
    onSurfaceVariant = colors.muted,
    surfaceTint = colors.blue,
    inverseSurface = colors.strongText,
    inverseOnSurface = colors.surface,
    surfaceContainerLowest = Color(0xFF0B0E13),
    surfaceContainerLow = colors.canvas,
    surfaceContainer = colors.surface,
    surfaceContainerHigh = Color(0xFF1C2128),
    surfaceContainerHighest = Color(0xFF262C34),
    surfaceBright = Color(0xFF2A3038),
    surfaceDim = Color(0xFF0B0E13),
    error = colors.red,
    onError = Color.White,
    errorContainer = Color(0xFF4A1518),
    onErrorContainer = Color(0xFFFFB4AB),
    outline = colors.border,
    outlineVariant = colors.border.copy(alpha = 0.7f),
    scrim = Color.Black,
)

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
