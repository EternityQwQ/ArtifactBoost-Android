package com.artifactboost.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artifactboost.app.ArtifactBoostApp
import com.artifactboost.app.ui.components.IconBadge
import com.artifactboost.app.ui.components.InlineBanner
import com.artifactboost.app.ui.theme.AppTheme
import kotlinx.coroutines.launch

@Composable
fun LoginScreen() {
    val colors = AppTheme.colors
    val session = ArtifactBoostApp.instance.session
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    var tokenInput by remember { mutableStateOf("") }
    var isWorking by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val canSubmit = tokenInput.isNotBlank() && !isWorking

    fun login() {
        val token = tokenInput.trim()
        isWorking = true
        errorMessage = null
        scope.launch {
            try {
                session.login(token)
            } catch (e: Exception) {
                errorMessage = e.message ?: "登录失败"
            }
            isWorking = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // Hero
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(top = 22.dp, bottom = 2.dp),
        ) {
            IconBadge(Icons.Filled.Bolt, colors.blue, 64.dp)
            Text("ArtifactBoost", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colors.strongText)
            Text(
                "GitHub 产物 · 正式版 · 源码 · 构建日志\n多通道并发加速下载",
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                color = colors.muted,
            )
        }

        // Token 卡片
        CardBlock {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Filled.Key, contentDescription = null, tint = colors.muted, modifier = Modifier.size(16.dp))
                    Text("Personal Access Token", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.muted)
                }

                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = { tokenInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("粘贴 Token（ghp_… 或 github_pat_…）", fontSize = 13.sp) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    shape = RoundedCornerShape(10.dp),
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TokenLink(
                        text = "创建 classic Token（勾选 repo）",
                        onClick = {
                            uriHandler.openUri(
                                "https://github.com/settings/tokens/new?scopes=repo&description=ArtifactBoost",
                            )
                        },
                    )
                    TokenLink(
                        text = "创建 fine-grained Token（Actions + Contents 只读）",
                        onClick = {
                            uriHandler.openUri("https://github.com/settings/personal-access-tokens/new")
                        },
                    )
                }

                Text(
                    "想下载别人的公开仓库：classic Token 勾 repo 即可；" +
                        "fine-grained Token 需要在 Account permissions 里允许读取公开仓库。",
                    fontSize = 11.sp,
                    color = colors.subtle,
                )
            }
        }

        if (errorMessage != null) {
            CardBlock {
                InlineBanner(
                    text = errorMessage!!,
                    color = colors.orange,
                    icon = Icons.Filled.Warning,
                )
            }
        }

        // 登录按钮
        Button(
            onClick = { login() },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            enabled = canSubmit,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.green,
                contentColor = androidx.compose.ui.graphics.Color.White,
            ),
            shape = RoundedCornerShape(10.dp),
        ) {
            if (isWorking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = androidx.compose.ui.graphics.Color.White,
                    strokeWidth = 2.dp,
                )
            } else {
                Text("验证并登录", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Text(
            "Token 只保存在本机加密存储中，不会上传到任何第三方服务器。",
            fontSize = 12.sp,
            color = colors.subtle,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun CardBlock(content: @Composable () -> Unit) {
    val colors = AppTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .padding(14.dp),
    ) { content() }
}

@Composable
private fun TokenLink(text: String, onClick: () -> Unit) {
    val colors = AppTheme.colors
    TextButton(onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = colors.blue, modifier = Modifier.size(15.dp))
        Spacer(Modifier.size(6.dp))
        Text(text, fontSize = 13.sp, color = colors.blue)
    }
}
