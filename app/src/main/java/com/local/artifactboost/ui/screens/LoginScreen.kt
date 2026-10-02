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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.artifactboost.runtime.AppViewModel
import com.local.artifactboost.ui.components.CardBox
import com.local.artifactboost.ui.components.ErrorBanner
import com.local.artifactboost.ui.components.Hairline
import com.local.artifactboost.ui.theme.accent
import com.local.artifactboost.ui.theme.border
import com.local.artifactboost.ui.theme.canvas
import com.local.artifactboost.ui.theme.green
import com.local.artifactboost.ui.theme.muted
import com.local.artifactboost.ui.theme.orange
import com.local.artifactboost.ui.theme.strongText
import com.local.artifactboost.ui.theme.subtle
import com.local.artifactboost.ui.theme.surface

/** 登录页：填一个 GitHub Token 就能用，Token 只存本机 */
@Composable
fun LoginScreen(vm: AppViewModel) {
    var token by remember { mutableStateOf("") }
    var revealed by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(canvas())
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(72.dp))

        Box(
            modifier = Modifier
                .size(74.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(accent()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Bolt,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(40.dp),
            )
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "ArtifactBoost",
            fontSize = 27.sp,
            fontWeight = FontWeight.Bold,
            color = strongText(),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "给 GitHub 的构建产物和源码包加速下载",
            fontSize = 14.sp,
            color = muted(),
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(34.dp))

        CardBox(padding = 18.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = null,
                        tint = accent(),
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        "Personal Access Token",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = strongText(),
                    )
                }

                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = {
                        Text("ghp_… 或 github_pat_…", fontSize = 13.sp, color = subtle())
                    },
                    visualTransformation = if (revealed) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Go,
                    ),
                    trailingIcon = {
                        IconButton(onClick = { revealed = !revealed }) {
                            Icon(
                                if (revealed) Icons.Filled.VisibilityOff
                                else Icons.Filled.Visibility,
                                contentDescription = if (revealed) "隐藏" else "显示",
                                tint = muted(),
                                modifier = Modifier.size(19.dp),
                            )
                        }
                    },
                )

                vm.loginError?.let { ErrorBanner(it, orange()) }

                Button(
                    onClick = { vm.login(token) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    enabled = token.isNotBlank() && !vm.isLoggingIn,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accent()),
                ) {
                    if (vm.isLoggingIn) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(9.dp))
                        Text("正在验证…", fontSize = 15.sp)
                    } else {
                        Text("登录", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        CardBox(padding = 16.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "怎么生成 Token",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = strongText(),
                )
                Hairline()
                GuideStep(1, "打开 GitHub → Settings → Developer settings")
                GuideStep(2, "Personal access tokens → Fine-grained tokens")
                GuideStep(3, "勾选 Public Repositories 只读（想下载私有仓库再加你的仓库权限）")
                GuideStep(4, "Generate token，复制粘贴到上面")
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(green()),
            )
            Text(
                "Token 只保存在本机，不经过任何第三方服务器",
                fontSize = 11.sp,
                color = subtle(),
            )
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun GuideStep(index: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Box(
            modifier = Modifier
                .size(19.dp)
                .clip(RoundedCornerShape(6.dp))
                .border(1.dp, border(), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("$index", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = muted())
        }
        Text(text, fontSize = 12.sp, color = muted(), modifier = Modifier.weight(1f))
    }
}
