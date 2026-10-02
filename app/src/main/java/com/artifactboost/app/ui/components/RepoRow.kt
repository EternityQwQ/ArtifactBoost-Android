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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.artifactboost.app.data.GHRepo
import com.artifactboost.app.ui.theme.AppTheme
import com.artifactboost.app.util.formatCount
import com.artifactboost.app.util.formatRelative
import com.artifactboost.app.util.parseIso8601

/**
 * 仓库图标：优先加载仓库所属 owner 的真实头像
 * （`https://github.com/{owner}.png`，GitHub 官方免鉴权端点），
 * 加载中/失败时退回「锁 / 书籍」占位图标。
 *
 * 为什么不用 API 里的 `avatar_url`：那需要额外发一次 `GET /users/{owner}`
 * 请求，占配额也拖慢列表；而 `github.com/{owner}.png` 是纯 CDN 图片，
 * 既快又不消耗 API 配额，还能被 Coil 的磁盘缓存命中。
 */
@Composable
fun RepoAvatarView(
    isPrivate: Boolean,
    size: Dp = 44.dp,
    owner: String? = null,
) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(size * 0.28f)
    val avatarUrl = owner?.takeIf { it.isNotBlank() }?.let { "https://github.com/$it.png?size=200" }

    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(colors.border.copy(alpha = 0.4f))
            .border(0.5.dp, colors.border, shape),
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
                    .background(colors.surface),
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
                tint = colors.muted,
                modifier = Modifier.size(size * 0.45f),
            )
        }
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
        RepoAvatarView(isPrivate = repo.isPrivate, size = 34.dp, owner = repo.owner)

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
