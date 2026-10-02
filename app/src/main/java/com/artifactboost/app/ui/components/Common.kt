package com.artifactboost.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artifactboost.app.ui.theme.AppTheme
import com.artifactboost.app.ui.theme.languageColor

/** 圆角图标（GitHub 移动端的仓库 / 文件图标风格） */
@Composable
fun IconBadge(
    icon: ImageVector,
    color: Color,
    size: androidx.compose.ui.unit.Dp = 34.dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(color.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(size * 0.52f),
        )
    }
}

/** 灰底小胶囊（GitHub 的 label / badge 风格） */
@Composable
fun StatusPill(
    text: String,
    color: Color,
    icon: ImageVector? = null,
) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(11.dp))
        }
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}

/** 语言色点 + 名称 */
@Composable
fun LanguageLabel(language: String) {
    val colors = AppTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(languageColor(language, colors)),
        )
        Text(language, fontSize = 11.sp, color = colors.muted)
    }
}

/** 星标 / fork 之类的小统计 */
@Composable
fun StatLabel(icon: ImageVector, text: String) {
    val colors = AppTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(icon, contentDescription = null, tint = colors.muted, modifier = Modifier.size(12.dp))
        Text(text, fontSize = 11.sp, color = colors.muted)
    }
}

/** 空状态 */
@Composable
fun EmptyStateView(
    icon: ImageVector,
    title: String,
    message: String? = null,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = colors.subtle, modifier = Modifier.size(34.dp))
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.muted)
        if (message != null) {
            Text(
                text = message,
                fontSize = 12.sp,
                color = colors.subtle,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 卡片容器：浅色/深色下都有清晰边界（GitHub 的 box 风格） */
@Composable
fun CardSurface(
    modifier: Modifier = Modifier,
    padding: androidx.compose.ui.unit.Dp = 14.dp,
    content: @Composable () -> Unit,
) {
    val colors = AppTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
            .padding(padding),
    ) {
        content()
    }
}

/** 错误 / 提示横幅 */
@Composable
fun InlineBanner(
    text: String,
    color: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.1f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(text, fontSize = 13.sp, color = colors.muted)
    }
}

/** 方形描边图标按钮（对齐 iOS 的 bordered 小按钮） */
@Composable
fun IconBadgeButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(10.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
    ) {
        Icon(icon, contentDescription = null, tint = colors.strongText, modifier = Modifier.size(18.dp))
    }
}

/** 0.5dp 分割线（GitHub 的 border 色） */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(AppTheme.colors.border),
    )
}

/** 加载中的骨架条 */
@Composable
fun SkeletonBar(height: androidx.compose.ui.unit.Dp = 12.dp, widthFraction: Float = 1f) {
    val colors = AppTheme.colors
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.7f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(750),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeletonAlpha",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .alpha(alpha)
            .background(colors.border),
    )
}

/** 骨架卡片：给 README / 列表加载时占位 */
@Composable
fun SkeletonBlock(lines: Int = 4) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        repeat(lines) { index ->
            Row(modifier = Modifier.fillMaxWidth()) {
                SkeletonBar(
                    height = if (index == 0) 16.dp else 11.dp,
                    widthFraction = if (index == lines - 1) 0.5f else 1f,
                )
                Spacer(Modifier.width(0.dp))
            }
        }
    }
}
