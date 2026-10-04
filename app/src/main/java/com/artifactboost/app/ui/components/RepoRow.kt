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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.artifactboost.app.data.GHRepo
import com.artifactboost.app.ui.theme.AppTheme
import com.artifactboost.app.util.formatCount
import com.artifactboost.app.util.formatRelative
import com.artifactboost.app.util.parseIso8601

/**
 * M3 仓库图标：tonal 占位 + 真实头像（`https://github.com/{owner}.png` 纯 CDN，不耗 API 配额），
 * 形状走 M3 small，描边用 outlineVariant。
 */
@Composable
fun RepoAvatarView(
    isPrivate: Boolean,
    size: Dp = 44.dp,
    owner: String? = null,
) {
    val colors = AppTheme.colors
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.small
    val avatarUrl = owner?.takeIf { it.isNotBlank() }?.let { "https://github.com/$it.png?size=200" }

    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(scheme.surfaceContainerHighest)
            .border(0.5.dp, scheme.outlineVariant, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (avatarUrl != null) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size)
                    .clip(shape),
            )
        }
        // 私有仓库额外盖一个小锁角标：头像本身看不出可见性
        if (isPrivate) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(size * 0.44f)
                    .clip(CircleShape)
                    .background(scheme.surfaceContainerLowest)
                    .border(0.5.dp, scheme.outlineVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = colors.yellow,
                    modifier = Modifier.size(size * 0.28f),
                )
            }
        } else if (avatarUrl == null) {
            // 拿不到 owner（老数据）：退回原来的占位图标
            Icon(
                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(size * 0.45f),
            )
        }
    }
}

/**
 * M3 仓库行：owner/repo 双色标题 + 描述 + 语言/星标，排版走 M3 type scale。
 * 列表页和搜索页共用，外层由 CardSurface 提供 M3 卡片容器。
 */
@Composable
fun RepoCardRow(
    repo: GHRepo,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RepoAvatarView(isPrivate = repo.isPrivate, size = 40.dp, owner = repo.owner)

        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = scheme.onSurfaceVariant)) {
                        append("${repo.owner}/")
                    }
                    withStyle(
                        SpanStyle(
                            color = scheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    ) {
                        append(repo.name)
                    }
                },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (!repo.description.isNullOrEmpty()) {
                Text(
                    text = repo.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
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
                Text(
                    "更新于 ${formatRelative(millis)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.outline,
                )
            }
        }
    }
}
