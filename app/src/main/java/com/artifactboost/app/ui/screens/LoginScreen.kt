package com.artifactboost.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.artifactboost.app.ArtifactBoostApp
import com.artifactboost.app.R
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
            // 用真实的应用图标（双闪电品牌图形），而不是通用闪电字形
            Image(
                painter = painterResource(R.drawable.ab_brand_mark),
                contentDescription = null,
                modifier = Modifier
                    .height(72.dp)
                    .width(48.dp),
            )
            Text("ArtifactBoost", style = MaterialTheme.typography.headlineSmall)
            Text(
                "GitHub 产物 · 发行版 · 源码 · 构建日志\n多通道并发加速下载",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Token 卡片：M3 ElevatedCard
        CardBlock {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Filled.Key,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Text("Personal Access Token", style = MaterialTheme.typography.titleSmall)
                }

                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = { tokenInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("粘贴 Token（ghp_… 或 github_pat_…）") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    // 注意：不要用 KeyboardType.Password，它在部分国产 ROM 上会强制弹出
                    // 系统安全键盘（禁粘贴/禁第三方输入法），Token 需要粘贴所以用普通文本键盘。
                    // 显示遮挡由上面的 PasswordVisualTransformation 负责，与键盘类型无关。
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        autoCorrect = false,
                        imeAction = ImeAction.Done,
                    ),
                    shape = MaterialTheme.shapes.small,
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
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
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

        // 登录按钮：M3 FilledButton，走主题 primary（跟随动态取色）
        Button(
            onClick = { login() },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            enabled = canSubmit,
            shape = MaterialTheme.shapes.small,
        ) {
            if (isWorking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Text("验证并登录", style = MaterialTheme.typography.titleSmall)
            }
        }

        Text(
            "Token 只保存在本机加密存储中，不会上传到任何第三方服务器。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun CardBlock(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun TokenLink(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
        Icon(
            Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.size(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
