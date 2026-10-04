package com.artifactboost.app

import android.app.Activity
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.artifactboost.app.ui.screens.LoginScreen
import com.artifactboost.app.ui.screens.RootScreen
import com.artifactboost.app.ui.theme.AppTheme
import com.artifactboost.app.ui.theme.ArtifactBoostTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 边到边显示：状态栏/导航栏透明，内容延伸到系统栏下面。
        // 图标明暗交给 Compose 按当前主题动态设置（见 SystemBarAppearance），
        // 不再依赖 themes.xml 里写死的 windowLightStatusBar —— 那是深色模式
        // 「状态栏图标看不见」的根因。
        enableEdgeToEdge()
        setContent {
            ArtifactBoostTheme {
                SystemBarAppearance()
                ArtifactBoostRoot()
            }
        }
    }
}

/**
 * 按当前主题把系统栏图标刷成深色/浅色。
 *
 * `enableEdgeToEdge()` 只负责「透明」，图标明暗得自己跟着主题走；
 * 否则切到深色模式时图标仍是深色，贴在深色状态栏上等于消失。
 */
@Composable
private fun SystemBarAppearance() {
    val view = LocalView.current
    val isDark = AppTheme.colors.isDark
    // 状态栏要不要「深色图标」= 背景是浅色 = 当前不是深色主题
    val useDarkIcons = !isDark

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = useDarkIcons
                isAppearanceLightNavigationBars = useDarkIcons
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
    }
}
