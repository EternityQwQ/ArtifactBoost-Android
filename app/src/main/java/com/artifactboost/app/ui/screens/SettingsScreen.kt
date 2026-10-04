package com.artifactboost.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.artifactboost.app.ArtifactBoostApp
import com.artifactboost.app.data.AccelerationSettings
import com.artifactboost.app.data.DownloadRoute
import com.artifactboost.app.data.RouteMode
import com.artifactboost.app.ui.components.CardSurface
import com.artifactboost.app.ui.components.Hairline
import com.artifactboost.app.ui.components.IconBadge
import com.artifactboost.app.ui.theme.AppTheme

/**
 * 设置：账户 + 加速设置（并发 / 通道）+ 关于。
 * M3：分组标题 + OutlinedCard 列表 + ListItem 行 + FilterChip/分段按钮。
 * 对应 iOS 版的 SettingsView。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen() {
    val colors = AppTheme.colors
    val app = ArtifactBoostApp.instance
    val session = app.session
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val haptics = LocalHapticFeedback.current

    val user by session.user.collectAsStateWithLifecycle()

    var settings by remember { mutableStateOf(AccelerationSettings.load(context)) }
    // 原神彩蛋兜底入口：长按顶部「设置」标题同样触发（底部 Tab 手势若被系统消费时仍可发现）
    var showGenshinEgg by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        settings = AccelerationSettings.load(context)
    }

    fun persist(updated: AccelerationSettings) {
        settings = updated
        updated.save(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "设置",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.combinedClickable(
                            onClick = {},
                            onLongClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                showGenshinEgg = true
                            },
                            onLongClickLabel = "发现彩蛋",
                        ),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // MARK: - 账户
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SettingsHeader("账户")
                    CardSurface {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            ListItem(
                                leadingContent = {
                                    val avatar = user?.avatarUrl
                                    if (avatar != null) {
                                        AsyncImage(
                                            model = avatar,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(46.dp)
                                                .clip(CircleShape),
                                        )
                                    } else {
                                        IconBadge(Icons.Filled.Person, colors.blue, size = 46.dp)
                                    }
                                },
                                headlineContent = {
                                    Text(
                                        user?.login ?: "已登录",
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        "Token 保存在本机加密存储",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                },
                                colors = ListItemDefaults.colors(
                                    containerColor = Color.Transparent,
                                ),
                            )

                            Hairline()

                            ListItem(
                                modifier = Modifier.clickable { session.logout() },
                                leadingContent = {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Logout,
                                        contentDescription = null,
                                        tint = colors.red,
                                    )
                                },
                                headlineContent = {
                                    Text(
                                        "退出登录",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = colors.red,
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                        }
                    }
                }
            }

            // MARK: - 加速设置
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SettingsHeader("加速设置")
                    CardSurface {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("并发连接数", style = MaterialTheme.typography.titleSmall)
                            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                AccelerationSettings.CONNECTION_OPTIONS.forEachIndexed { index, count ->
                                    SegmentedButton(
                                        selected = settings.connections == count,
                                        onClick = { persist(settings.copy(connections = count)) },
                                        shape = SegmentedButtonDefaults.itemShape(
                                            index = index,
                                            count = AccelerationSettings.CONNECTION_OPTIONS.size,
                                        ),
                                    ) { Text("$count") }
                                }
                            }

                            Hairline()

                            Text(
                                "并发数越大越能跑满带宽；绿色网络环境建议 32~64，一般 16 即可，千兆内网/高速 Wi-Fi 可试 128。被限流时引擎会自动退让并把活儿转给健康通道，不会失败。设置会自动保存，下载时直接生效。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }
            }

            // MARK: - 下载源：M3 FilterChip 大按钮
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SettingsHeader("下载源")
                    CardSurface {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                SourceButton(
                                    title = "官方源",
                                    subtitle = "直连 GitHub",
                                    icon = Icons.Filled.Cloud,
                                    selected = settings.mode == RouteMode.DIRECT,
                                    accent = colors.blue,
                                    onClick = { persist(settings.copy(mode = RouteMode.DIRECT)) },
                                )
                                SourceButton(
                                    title = "镜像加速",
                                    subtitle = "多通道并行 · 推荐",
                                    icon = Icons.Filled.Bolt,
                                    selected = settings.mode == RouteMode.SMART,
                                    accent = colors.green,
                                    onClick = { persist(settings.copy(mode = RouteMode.SMART)) },
                                )
                            }

                            Hairline()

                            ListItem(
                                headlineContent = {
                                    Text("自建中转", style = MaterialTheme.typography.titleSmall)
                                },
                                supportingContent = {
                                    Text(
                                        "用自己的反代地址下载",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                },
                                trailingContent = {
                                    Switch(
                                        checked = settings.mode == RouteMode.CUSTOM,
                                        onCheckedChange = { enabled ->
                                            persist(
                                                settings.copy(
                                                    mode = if (enabled) RouteMode.CUSTOM else RouteMode.SMART,
                                                ),
                                            )
                                        },
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )

                            if (settings.mode == RouteMode.CUSTOM) {
                                OutlinedTextField(
                                    value = settings.customPrefix,
                                    onValueChange = { persist(settings.copy(customPrefix = it)) },
                                    placeholder = { Text("https://你的中转地址/") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.small,
                                )
                                if (DownloadRoute.normalizedPrefix(settings.customPrefix).isEmpty()) {
                                    Text(
                                        "前缀为空时将回退直连",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.orange,
                                    )
                                }
                            }

                            Hairline()

                            Text(
                                settings.mode.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }
            }

            // MARK: - 关于
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SettingsHeader("关于")
                    CardSurface {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            ListItem(
                                headlineContent = { Text("版本", style = MaterialTheme.typography.bodyLarge) },
                                trailingContent = {
                                    Text("1.2", style = MaterialTheme.typography.bodyMedium)
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                            Hairline()
                            ListItem(
                                modifier = Modifier.clickable {
                                    uriHandler.openUri("https://github.com/yitenchen123/ArtifactBoost")
                                },
                                leadingContent = {
                                    Icon(
                                        Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                },
                                headlineContent = {
                                    Text(
                                        "项目主页 / 自建中转教程",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                            Hairline()
                            Text(
                                "智能加速与自定义通道可能让产物数据经过第三方中转，私有仓库会自动强制走直连。Token 全程只在本机使用。\n\n" +
                                    "智能加速会额外尝试 ghfast.top —— 它只认 github.com 原始地址，因此仅对「发行版」附件生效；构建产物与日志仍走其它镜像。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showGenshinEgg) {
        GenshinEasterEggDialog(
            onDismiss = { showGenshinEgg = false },
            onOpenOfficialSite = { uriHandler.openUri(GENSHIN_CN_URL) },
        )
    }
}

@Composable
private fun SettingsHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp),
    )
}

/**
 * M3 下载源大按钮：FilterChip 风格的可选中卡片。
 * 选中时 tonal 高亮 + 语义色描边，未选中时 surface 描边。
 */
@Composable
private fun RowScope.SourceButton(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.weight(1f),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (selected) {
                accent.copy(alpha = 0.12f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.5.dp,
            if (selected) accent else MaterialTheme.colorScheme.outlineVariant,
        ),
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}
