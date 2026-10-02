package com.artifactboost.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.artifactboost.app.ui.screens.LoginScreen
import com.artifactboost.app.ui.screens.RootScreen
import com.artifactboost.app.ui.theme.AppTheme
import com.artifactboost.app.ui.theme.ArtifactBoostTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ArtifactBoostTheme {
                ArtifactBoostRoot()
            }
        }
    }
}

/**
 * 按登录状态切换界面：未登录显示登录页，已登录进主界面。
 * 对应 iOS 版 ArtifactBoostApp 的 body。
 */
@Composable
private fun ArtifactBoostRoot() {
    val session = ArtifactBoostApp.instance.session
    val client by session.client.collectAsStateWithLifecycle()
    val isRestoring by session.isRestoring.collectAsStateWithLifecycle()

    when {
        // 正在从本地加密存储读 Token：先显示启动占位，
        // 否则已登录用户二次启动会先闪一下登录页。
        isRestoring -> SplashScreen()

        client != null -> RootScreen()

        else -> {
            // 登录页是栈底：侧滑 / 返回键直接退出 App，不做任何拦截
            BackHandler(enabled = false) { }
            LoginScreen()
        }
    }
}

@Composable
private fun SplashScreen() {
    val colors = AppTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(28.dp), color = colors.blue, strokeWidth = 2.5.dp)
    }
}
