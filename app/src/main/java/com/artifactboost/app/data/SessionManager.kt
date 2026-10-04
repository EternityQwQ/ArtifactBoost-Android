package com.artifactboost.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 登录态管理（Token 存取 + 当前用户）。
 * 对应 iOS 版的 SessionManager。
 */
class SessionManager(private val appContext: Context) {

    private val _user = MutableStateFlow<GHUser?>(null)
    val user: StateFlow<GHUser?> = _user.asStateFlow()

    /**
     * 当前客户端。
     * 用 StateFlow 承载，未登录为 null；恢复 Token 是异步的，所以启动瞬间可能为 null。
     */
    private val _client = MutableStateFlow<GitHubClient?>(null)
    val client: StateFlow<GitHubClient?> = _client.asStateFlow()

    /**
     * 是否正在从本地读取 Token。
     * 首屏据此显示启动占位，避免「已登录用户看到登录页闪一下」。
     */
    private val _isRestoring = MutableStateFlow(true)
    val isRestoring: StateFlow<Boolean> = _isRestoring.asStateFlow()

    val isLoggedIn: Boolean get() = _client.value != null

    private var didRestore = false

    /**
     * 从本地恢复 Token。
     *
     * 必须在 IO 线程执行：EncryptedSharedPreferences 的初始化要走 Keystore 解密，
     * 放在主线程（原来的做法是在 init 里同步读）会让二次启动首屏卡住数秒。
     */
    suspend fun restore() {
        if (didRestore) return
        didRestore = true

        val saved = withContext(Dispatchers.IO) {
            runCatching { TokenStore.read(appContext) }.getOrNull()
        }
        if (saved != null) {
            _client.value = GitHubClient(saved)
        }
        _isRestoring.value = false
    }

    /** 验证 Token 并登录 */
    suspend fun login(token: String) {
        val newClient = GitHubClient(token)
        val user = newClient.validateToken()
        withContext(Dispatchers.IO) {
            runCatching { TokenStore.save(appContext, token) }
        }
        _client.value = newClient
        _user.value = user
        _isRestoring.value = false
        didRestore = true
    }

    fun logout() {
        _client.value = null
        _user.value = null
        // 清 Token 同样走 IO，避免在 UI 线程动 Keystore
        ioScope.launch { runCatching { TokenStore.delete(appContext) } }
    }

    /** 启动后异步补拉用户信息；Token 已失效时自动登出 */
    suspend fun refreshUser() {
        val current = _client.value ?: return
        try {
            _user.value = current.validateToken()
        } catch (e: GitHubException.Http) {
            // Token 被撤销 / 过期：直接退出登录，避免停在「假登录」状态里
            if (e.code == 401) logout()
        } catch (_: Exception) {
            // 网络问题不登出
        }
    }

    /**
     * 统一把错误转成给用户看的文案：
     * Token 失效时自动登出，回到登录页。
     */
    fun message(error: Throwable): String {
        if (error is GitHubException.Http && error.code == 401) {
            logout()
            return "登录已失效（401），请重新输入 Token"
        }
        networkHint(error)?.let { return it }
        return error.message ?: "未知错误"
    }

    /**
     * 网络层错误的中文映射：超时/DNS/建连失败透出的都是英文原文，
     * 顺着 cause 链找，命中就给一句人话。解析阶段永远直连 api.github.com，
     * 所以这里点名，免得用户去折腾通道设置。
     */
    private fun networkHint(error: Throwable): String? {
        var cause: Throwable? = error
        while (cause != null) {
            when (cause) {
                is java.net.SocketTimeoutException ->
                    return "连接 GitHub 超时，请检查网络后重试（解析下载地址时永远直连 api.github.com，换通道也救不了这一段）"
                is java.net.UnknownHostException ->
                    return "无法解析 GitHub 域名，请检查网络 / DNS 后重试"
                is java.net.ConnectException ->
                    return "连不上 GitHub，请检查网络或代理后重试"
            }
            val next = cause.cause
            if (next == null || next === cause) break
            cause = next
        }
        return null
    }

    private companion object {
        /** 只用于后台清理 Token 这类「发了就不管」的动作 */
        val ioScope = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + Dispatchers.IO,
        )
    }
}
