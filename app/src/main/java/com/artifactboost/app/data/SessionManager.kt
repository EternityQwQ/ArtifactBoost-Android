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
        return error.message ?: "未知错误"
    }

    private companion object {
        /** 只用于后台清理 Token 这类「发了就不管」的动作 */
        val ioScope = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + Dispatchers.IO,
        )
    }
}
