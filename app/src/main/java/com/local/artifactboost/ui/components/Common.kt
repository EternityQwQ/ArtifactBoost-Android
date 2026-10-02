package com.local.artifactboost.ui.components

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.artifactboost.ui.theme.accent
import com.local.artifactboost.ui.theme.border
import com.local.artifactboost.ui.theme.languageColor
import com.local.artifactboost.ui.theme.muted
import com.local.artifactboost.ui.theme.strongText
import com.local.artifactboost.ui.theme.subtle
import com.local.artifactboost.ui.theme.surface
import com.local.artifactboost.ui.theme.warning

/** 圆角图标（GitHub 移动端的仓库 / 文件图标风格），对应 iOS 版 IconBadge */
@Composable
fun IconBadge(
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(color.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

/** 灰底小胶囊（GitHub 的 label / badge 风格），对应 iOS 版 StatusPill */
@Composable
fun StatusPill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
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
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(languageColor(language)),
        )
        Spacer(Modifier.width(4.dp))
        Text(language, fontSize = 11.sp, color = muted())
    }
}

/** 星标 / fork 之类的小统计 */
@Composable
fun StatLabel(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = muted(), modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(3.dp))
        Text(text, fontSize = 11.sp, color = muted())
    }
}

/** 仓库图标：私有仓库用锁，公开仓库用书籍 */
@Composable
fun RepoAvatar(isPrivate: Boolean, size: Dp = 44.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(border().copy(alpha = 0.4f))
            .border(0.5.dp, border(), RoundedCornerShape(size * 0.28f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isPrivate) Icons.Filled.Lock else Icons.AutoMirrored.Outlined.MenuBook,
            contentDescription = null,
            tint = if (isPrivate) warning() else muted(),
            modifier = Modifier.size(size * 0.42f),
        )
    }
}

/** 0.5dp 分割线（GitHub 的 border 色） */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(border()),
    )
}

/** 卡片容器：浅色/深色下都有清晰边界（GitHub 的 box 风格） */
@Composable
fun CardBox(
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(surface())
            .border(1.dp, border(), RoundedCornerShape(12.dp))
            .padding(padding),
    ) { content() }
}

/** 空状态 / 无数据提示 */
@Composable
fun EmptyState(icon: ImageVector, title: String, message: String? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = subtle(), modifier = Modifier.size(34.dp))
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = muted())
        if (message != null) {
            Text(
                message,
                fontSize = 12.sp,
                color = subtle(),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 错误提示条 */
@Composable
fun ErrorBanner(text: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.1f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text(text, fontSize = 13.sp, color = muted())
    }
}

/** 骨架条：加载占位 */
@Composable
fun SkeletonBar(height: Dp = 12.dp, widthFraction: Float = 1f) {
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(border().copy(alpha = 0.6f)),
    )
}

/** 骨架块 */
@Composable
fun SkeletonBlock(lines: Int = 4) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(lines) { index ->
            SkeletonBar(
                height = if (index == 0) 16.dp else 11.dp,
                widthFraction = if (index == lines - 1) 0.6f else 1f,
            )
        }
    }
}

/** 分节标题 */
@Composable
fun SectionLabel(icon: ImageVector, text: String, color: Color = accent()) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = strongText())
    }
}

@Composable
fun MonoText(text: String, color: Color = strongText(), fontSize: Int = 13) {
    Text(
        text = text,
        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        fontSize = fontSize.sp,
        color = color,
        style = MaterialTheme.typography.bodySmall,
    )
}
