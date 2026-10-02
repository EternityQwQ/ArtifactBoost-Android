package com.local.artifactboost.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.artifactboost.data.formatDateTime
import com.local.artifactboost.data.formatSpeed
import com.local.artifactboost.download.AccelerationSettings
import com.local.artifactboost.download.DownloadRoute
import com.local.artifactboost.download.RouteMode
import com.local.artifactboost.download.RouteProbe
import com.local.artifactboost.runtime.AppViewModel
import com.local.artifactboost.ui.components.CardBox
import com.local.artifactboost.ui.components.ErrorBanner
import com.local.artifactboost.ui.components.Hairline
import com.local.artifactboost.ui.components.SectionLabel
import com.local.artifactboost.ui.theme.accent
import com.local.artifactboost.ui.theme.border
import com.local.artifactboost.ui.theme.canvas
import com.local.artifactboost.ui.theme.green
import com.local.artifactboost.ui.theme.muted
import com.local.artifactboost.ui.theme.orange
import com.local.artifactboost.ui.theme.strongText
import com.local.artifactboost.ui.theme.subtle
import com.local.artifactboost.ui.theme.success
import com.local.artifactboost.ui.theme.surface
import kotlinx.coroutines.launch

/** 设置页：连接数 / 通道模式 / 自定义前缀 / 一键测速 */
@Composable
fun SettingsScreen(vm: AppViewModel, onLogout: suspend () -> Unit) {
    val scope = rememberCoroutineScope()
    var settings by remember {
        mutableStateOf(AccelerationSettings())
    }
    var loaded by remember { mutableStateOf(false) }
    var testResults by remember {
        mutableStateOf<List<com.local.artifactboost.download.ScoredRoute>?>(null)
    }
    var isTesting by remember { mutableStateOf(false) }
    var testError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        settings = AccelerationSettings.load(requireNotNull(vm.context()))
        loaded = true
    }

    fun persist(next: AccelerationSettings) {
        settings = next
        scope.launch { AccelerationSettings.save(requireNotNull(vm.context()), next) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(canvas()),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 12.dp,
            bottom = 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "设置",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = strongText(),
                )
                vm.user?.let { Text("@${it.login}", fontSize = 12.sp, color = subtle()) }
            }
        }

        // ---- 测速 ----
        item {
            CardBox(padding = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionLabel(Icons.Filled.Speed, "通道测速", accent())
                    Text(
                        "用你自己仓库里的一个真实产物做样本，实测每条线路的速度。" +
                            "测完后 24 小时内下载都会直接用最快的通道。",
                        fontSize = 12.sp,
                        color = subtle(),
                    )

                    val tested = settings.testedRoute
                    if (tested != null && settings.testedSpeed > 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(9.dp))
                                .background(success().copy(alpha = 0.1f))
                                .padding(11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Filled.Check, null, Modifier.size(15.dp), tint = success())
                            Column {
                                Text(
                                    "当前最快：${tested.name}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = strongText(),
                                )
                                Text(
                                    "${formatSpeed(settings.testedSpeed)} · ${formatDateTime(epochToIso(settings.testedAt))}",
                                    fontSize = 11.sp,
                                    color = subtle(),
                                )
                            }
                        }
                    }

                    testError?.let { ErrorBanner(it, orange()) }

                    testResults?.let { results ->
                        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Hairline()
                            val fastest = results.firstOrNull()?.speed ?: 0.0
                            results.forEach { result ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Text(
                                        result.route.name,
                                        fontSize = 13.sp,
                                        color = strongText(),
                                        modifier = Modifier.width(110.dp),
                                    )
                                    Text(
                                        formatSpeed(result.speed),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (result.speed >= fastest * 0.7) success() else muted(),
                                    )
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        if (result.speed <= 0.0) "不可用" else "样本 256 KB",
                                        fontSize = 10.sp,
                                        color = subtle(),
                                    )
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            val client = vm.session.client.value ?: return@Button
                            scope.launch {
                                isTesting = true
                                testError = null
                                try {
                                    val target = vm.downloads.findTestTarget(client)
                                    if (target == null) {
                                        testError = "没找到可测速的产物：需要至少一个仓库里有未过期的构建产物或构建日志。"
                                    } else {
                                        val routes = settings.candidateRoutes(target.isPrivate)
                                        val results = RouteProbe.measureAll(routes, target.url)
                                        testResults = results
                                        results.firstOrNull()?.let { best ->
                                            persist(
                                                settings.copy(
                                                    testedRoute = best.route,
                                                    testedSpeed = best.speed,
                                                    testedAt = System.currentTimeMillis(),
                                                ),
                                            )
                                        }
                                    }
                                } catch (e: Throwable) {
                                    testError = e.message ?: "测速失败"
                                } finally {
                                    isTesting = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        enabled = !isTesting && loaded,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accent()),
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(
                                Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("正在测速…", fontSize = 14.sp)
                        } else {
                            Icon(Icons.Filled.Speed, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("开始测速", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // ---- 通道模式 ----
        item {
            CardBox(padding = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionLabel(Icons.Filled.Cloud, "下载通道", accent())
                    RouteMode.entries.forEach { mode ->
                        ModeRow(mode, settings.mode == mode) {
                            persist(settings.copy(mode = mode, testedRoute = null, testedSpeed = 0.0))
                        }
                    }

                    if (settings.mode == RouteMode.CUSTOM) {
                        Hairline()
                        OutlinedTextField(
                            value = settings.customPrefix,
                            onValueChange = { persist(settings.copy(customPrefix = it)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(9.dp),
                            placeholder = {
                                Text(
                                    "https://your-worker.workers.dev/",
                                    fontSize = 13.sp,
                                    color = subtle(),
                                )
                            },
                            label = { Text("中转前缀", fontSize = 12.sp) },
                        )
                        val valid = DownloadRoute.normalizedPrefix(settings.customPrefix).isNotEmpty()
                        Text(
                            if (settings.customPrefix.isBlank()) "例如 Cloudflare Worker 地址，必须带 https:// 且以 / 结尾"
                            else if (valid) "✓ 前缀合法，下载时会拼成「前缀 + 签名地址」"
                            else "✗ 前缀必须以 http:// 或 https:// 开头",
                            fontSize = 11.sp,
                            color = if (settings.customPrefix.isBlank() || valid) subtle() else orange(),
                        )
                    }
                }
            }
        }

        // ---- 连接数 ----
        item {
            CardBox(padding = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionLabel(Icons.Filled.Bolt, "并发连接数", accent())
                    Text(
                        "同时开几条连接抢带宽。并不是越大越快——" +
                            "很多服务器单连接限速，需要多条连接才能真正叠加；" +
                            "但开太多会被限流。16 是通用最优解。",
                        fontSize = 12.sp,
                        color = subtle(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        AccelerationSettings.connectionOptions.forEach { option ->
                            val selected = settings.clampedConnections == option
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(if (selected) accent() else surface())
                                    .border(
                                        1.dp,
                                        if (selected) accent() else border(),
                                        RoundedCornerShape(9.dp),
                                    )
                                    .clickable { persist(settings.copy(connections = option)) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "$option",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (selected) Color.White else strongText(),
                                )
                            }
                        }
                    }
                }
            }
        }

        // ---- 关于 / 退出 ----
        item {
            CardBox(padding = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionLabel(Icons.Filled.Settings, "关于", accent())
                    InfoLine("版本", "1.1 (Android)")
                    Hairline()
                    InfoLine("服务对象", "GitHub REST API v3")
                    Hairline()
                    InfoLine("数据存储", "Token 与设置只在本机 DataStore")
                    Hairline()
                    InfoLine("加速原理", "镜像测速 + 分段并发 + 失败重试")
                }
            }
        }

        item {
            Button(
                onClick = { scope.launch { onLogout() } },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = orange().copy(alpha = 0.12f),
                    contentColor = orange(),
                ),
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, null, Modifier.size(16.dp))
                Spacer(Modifier.width(7.dp))
                Text("退出登录", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ModeRow(mode: RouteMode, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) accent().copy(alpha = 0.08f) else Color.Transparent)
            .border(
                1.dp,
                if (selected) accent().copy(alpha = 0.45f) else border(),
                RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onSelect)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(9.dp))
                .border(1.5.dp, if (selected) accent() else border(), RoundedCornerShape(9.dp))
                .background(if (selected) accent() else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(Icons.Filled.Check, null, Modifier.size(12.dp), tint = Color.White)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                mode.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) accent() else strongText(),
            )
            Text(mode.detail, fontSize = 11.sp, color = subtle())
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, fontSize = 13.sp, color = muted(), modifier = Modifier.width(78.dp))
        Text(value, fontSize = 12.sp, color = subtle(), modifier = Modifier.weight(1f))
    }
}

/** 把 epoch 毫秒转成 formatDateTime 能吃的 ISO 字符串 */
private fun epochToIso(millis: Long): String? {
    if (millis <= 0L) return null
    return java.time.Instant.ofEpochMilli(millis).toString()
}
