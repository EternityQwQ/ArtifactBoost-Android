package com.artifactboost.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artifactboost.app.data.GHRepo
import com.artifactboost.app.ui.theme.AppTheme
import com.artifactboost.app.util.formatCount
import com.artifactboost.app.util.formatRelative
import com.artifactboost.app.util.parseIso8601

/** 仓库图标：私有仓库用锁，公开仓库用书籍（GitHub 移动端风格） */
@Composable
fun RepoAvatarView(
    isPrivate: Boolean,
    size: Dp = 44.dp,
) {
    val colors = AppTheme.colors
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(colors.border.copy(alpha = 0.4f))
            .border(0.5.dp, colors.border, RoundedCornerShape(size * 0.28f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isPrivate) Icons.Filled.Lock else Icons.AutoMirrored.Filled.MenuBook,
            contentDescription = null,
            tint = if (isPrivate) colors.yellow else colors.muted,
            modifier = Modifier.size(size * 0.45f),
        )
    }
}

/**
 * 仓库行：GitHub 移动端风格（owner/repo 双色标题 + 描述 + 语言/星标）。
 * 列表页和搜索页共用。
 */
@Composable
fun RepoCardRow(
    repo: GHRepo,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RepoAvatarView(isPrivate = repo.isPrivate, size = 34.dp)

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = colors.muted, fontSize = 14.sp)) {
                        append("${repo.owner}/")
                    }
                    withStyle(
                        SpanStyle(
                            color = colors.blue,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    ) {
                        append(repo.name)
                    }
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (!repo.description.isNullOrEmpty()) {
                Text(
                    text = repo.description,
                    fontSize = 12.sp,
                    color = colors.muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (repo.language != null) {
                    LanguageLabel(repo.language)
                }
                if ((repo.stargazersCount ?: 0) > 0) {
                    StatLabel(Icons.Filled.Star, formatCount(repo.stargazersCount!!))
                }
                if ((repo.forksCount ?: 0) > 0) {
                    StatLabel(Icons.AutoMirrored.Filled.CallSplit, formatCount(repo.forksCount!!))
                }
            }

            parseIso8601(repo.updatedAt)?.let { millis ->
                Text("更新于 ${formatRelative(millis)}", fontSize = 11.sp, color = colors.subtle)
            }
        }

        Spacer(Modifier.weight(1f))
    }
}
