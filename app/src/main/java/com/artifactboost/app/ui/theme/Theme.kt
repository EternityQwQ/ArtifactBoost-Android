package com.artifactboost.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
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

/**
 * MD3 圆角规范。之前的圆角都是各组件里写死的 `RoundedCornerShape(8.dp)`，
 * 这里统一到 Material 3 的 shape scale（extraSmall → extraLarge），
 * 组件改成从 `MaterialTheme.shapes` 取，视觉一致性自然就有了。
 */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * MD3 字阶：只微调最常用的几档，其余沿用默认，
 * 保证「标题更紧、正文更松」的 MD3 阅读节奏。
 */
private val AppTypography = Typography().let { base ->
    base.copy(
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        // 数值/统计类文本用等宽，数字跳动时不会左右晃
        labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
    )
}

/**
 * 是否启用 Android 12+ 的动态取色（Monet）。
 *
 * 默认开启：用户的系统主题色会渗透到 App 的主色上，这是 MD3 的标志性体验。
 * 但 App 的**语义色**（语言色、运行状态红/绿）仍走 Primer 固定值 ——
 * 那些颜色承载信息，不能被主题色改掉。
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

    // 动态取色（仅 Android 12+）。开启时优先用系统色，
    // 但把 App 自己的语义色（红/绿/蓝…）与背景、表面色继续钉在 Primer 上，
    // 保证运行状态、语言色这些「带信息」的颜色不会被主题化。
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val dynamic = if (darkTheme) {
                dynamicDarkColorScheme(context)
            } else {
                dynamicLightColorScheme(context)
            }
            dynamic.copy(
                // 语义色钉死，不随系统主题漂移
                secondary = colors.green,
                error = colors.red,
                tertiary = colors.purple,
                // 画布/表面给回 App 自己的层次，避免动态色把卡片和背景调成同色
                background = colors.canvas,
                onBackground = colors.strongText,
                surface = colors.surface,
                onSurface = colors.strongText,
                surfaceVariant = colors.border,
                onSurfaceVariant = colors.muted,
                outline = colors.border,
                outlineVariant = colors.border,
                surfaceContainer = colors.surface,
                surfaceContainerLow = colors.surface,
                surfaceContainerHighest = colors.canvas,
            )
        }
        darkTheme -> fullScheme(
            darkColorScheme(),
            colors,
            onPrimary = Color.White,
        )
        else -> fullScheme(
            lightColorScheme(),
            colors,
            onPrimary = Color.White,
        )
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

/**
 * 把一个基础 ColorScheme 补齐成「MD3 全槽位」。
 *
 * 老实现只填了 9 个槽（primary/onPrimary/secondary/background/onBackground/
 * surface/onSurface/outline/error），其余全落到 MD3 的**默认紫**上 ——
 * 于是任何用到 `surfaceVariant`、`surfaceContainerHigh`、`inverseSurface` 的
 * MD3 组件（NavigationBar、BottomSheet、Menu、Snackbar…）都会冒出紫色，
 * 跟 App 的配色打架。这里一次性补全，MD3 化才算真的完成。
 */
private fun fullScheme(
    base: androidx.compose.material3.ColorScheme,
    colors: AppColors,
    onPrimary: Color,
): androidx.compose.material3.ColorScheme = base.copy(
    primary = colors.blue,
    onPrimary = onPrimary,
    primaryContainer = colors.blue.copy(alpha = 0.12f),
    onPrimaryContainer = colors.blue,
    inversePrimary = colors.blue,

    secondary = colors.green,
    onSecondary = onPrimary,
    secondaryContainer = colors.green.copy(alpha = 0.12f),
    onSecondaryContainer = colors.green,

    tertiary = colors.purple,
    onTertiary = onPrimary,
    tertiaryContainer = colors.purple.copy(alpha = 0.12f),
    onTertiaryContainer = colors.purple,

    background = colors.canvas,
    onBackground = colors.strongText,

    surface = colors.surface,
    onSurface = colors.strongText,
    surfaceVariant = colors.border,
    onSurfaceVariant = colors.muted,
    surfaceTint = colors.blue,
    inverseSurface = colors.strongText,
    inverseOnSurface = colors.surface,

    // MD3 的「表面容器」层级：从最低到最高，给卡片/底栏/弹窗做层次
    surfaceContainerLowest = colors.surface,
    surfaceContainerLow = colors.surface,
    surfaceContainer = colors.surface,
    surfaceContainerHigh = colors.surface,
    surfaceContainerHighest = colors.canvas,

    surfaceBright = colors.surface,
    surfaceDim = colors.canvas,

    error = colors.red,
    onError = onPrimary,
    errorContainer = colors.red.copy(alpha = 0.12f),
    onErrorContainer = colors.red,

    outline = colors.border,
    outlineVariant = colors.border,
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
