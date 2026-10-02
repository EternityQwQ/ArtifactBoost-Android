package com.local.artifactboost

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.local.artifactboost.net.GitHubClient
import com.local.artifactboost.net.GitHubException
import com.local.artifactboost.data.GHUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

private val Context.tokenStore by preferencesDataStore(name = "artifactboost_token")

/**
 * 会话管理：持有 Token 与当前用户，401 时自动登出。
 * Token 只存在本机的 DataStore 里（应用私有目录），不上传、不写日志。
 */
class SessionManager(private val appContext: Context) {

    private val tokenKey = stringPreferencesKey("ab.token")

    private val _user = MutableStateFlow<GHUser?>(null)
    val user: StateFlow<GHUser?> = _user.asStateFlow()

    private val _client = MutableStateFlow<GitHubClient?>(null)
    val client: StateFlow<GitHubClient?> = _client.asStateFlow()

    private val _token = MutableStateFlow<String?>(null)
    val token: StateFlow<String?> = _token.asStateFlow()

    /** 登录：验证 Token 并落盘 */
    suspend fun login(rawToken: String): Result<GHUser> {
        val token = rawToken.trim()
        if (token.isEmpty()) {
            return Result.failure(GitHubException.BadResponse)
        }
        return try {
            val client = GitHubClient(token)
            val user = client.validateToken()
            appContext.tokenStore.edit { it[tokenKey] = token }
            _token.value = token
            _client.value = client
            _user.value = user
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 启动时恢复已保存的 Token。返回是否恢复成功。 */
    suspend fun restore(): Boolean {
        val saved = runCatching { appContext.tokenStore.data.first()[tokenKey] }.getOrNull()
        if (saved.isNullOrEmpty()) return false
        return runCatching {
            val client = GitHubClient(saved)
            val user = client.validateToken()
            _token.value = saved
            _client.value = client
            _user.value = user
            true
        }.getOrElse {
            // Token 已失效，清掉避免每次启动都失败
            appContext.tokenStore.edit { it.remove(tokenKey) }
            false
        }
    }

    suspend fun logout() {
        appContext.tokenStore.edit { it.remove(tokenKey) }
        _token.value = null
        _client.value = null
        _user.value = null
    }

    companion object {
        /** 把异常翻译成给用户看的文案（登录页在拿到 session 之前也要用，所以放 companion） */
        fun messageFor(error: Throwable): String = error.message ?: "未知错误"
    }
}

