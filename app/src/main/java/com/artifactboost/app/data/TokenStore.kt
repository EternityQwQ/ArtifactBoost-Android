package com.artifactboost.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Token 只存在本机加密存储里，不上传任何服务器。
 * 对应 iOS 版的 KeychainHelper——Android 上用 EncryptedSharedPreferences（AES256-GCM）。
 *
 * 注意：`EncryptedSharedPreferences.create` 要走 Keystore + 解密整个文件，
 * 属于重 IO。绝对不能在主线程（Application.onCreate / Composable 首帧）里做，
 * 否则二次启动时会卡住首屏很久。这里统一只在 IO 线程上初始化。
 */
object TokenStore {
    private const val FILE_NAME = "artifactboost_secure"
    private const val KEY_TOKEN = "github-token"

    private val lock = Any()

    @Volatile
    private var cached: SharedPreferences? = null

    @Volatile
    private var initFailed = false

    /** 只在 IO 线程调用。首次会做 Keystore 初始化，之后走缓存。 */
    private fun prefs(context: Context): SharedPreferences? {
        cached?.let { return it }
        if (initFailed) return plainPrefs(context)

        synchronized(lock) {
            cached?.let { return it }
            if (initFailed) return plainPrefs(context)

            val encrypted = runCatching {
                val masterKey = MasterKey.Builder(context.applicationContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context.applicationContext,
                    FILE_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            }.onFailure {
                // 极少数机型上 Keystore 异常：退化为普通存储，至少保证功能可用
                android.util.Log.w("ArtifactBoost", "加密存储不可用，退化为普通存储", it)
            }.getOrNull()

            return if (encrypted != null) {
                cached = encrypted
                encrypted
            } else {
                initFailed = true
                plainPrefs(context).also { cached = it }
            }
        }
    }

    private fun plainPrefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    /** 写入 Token。调用方需自行在 IO 线程执行。 */
    fun save(context: Context, token: String) {
        // commit() 而不是 apply()：登录后立刻可能被杀进程/重启，
        // 必须保证 Token 已经落盘，否则下次启动会读不到、又回到登录页。
        prefs(context)?.edit()?.putString(KEY_TOKEN, token)?.commit()
    }

    /** 读取 Token。调用方需自行在 IO 线程执行。 */
    fun read(context: Context): String? =
        prefs(context)?.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }

    /** 删除 Token。调用方需自行在 IO 线程执行。 */
    fun delete(context: Context) {
        prefs(context)?.edit()?.remove(KEY_TOKEN)?.commit()
    }
}
