package com.artifactboost.app

import android.app.Application
import com.artifactboost.app.data.AccelerationSettings
import com.artifactboost.app.data.SessionManager
import com.artifactboost.app.download.DownloadManager
import com.artifactboost.app.download.DownloadService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 应用级容器：持有 SessionManager / DownloadManager 单例。
 * 对应 iOS 版在 ArtifactBoostApp 里用 @StateObject 创建的两个对象。
 */
class ArtifactBoostApp : Application() {

    lateinit var session: SessionManager
        private set
    lateinit var downloads: DownloadManager
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        session = SessionManager(this)
        downloads = DownloadManager(this, session)

        // Token 读取要走 Keystore，必须放在 IO 线程；
        // 之前是在 SessionManager.init 里同步读，是二次启动首屏卡住的元凶。
        appScope.launch {
            session.restore()
            // 恢复出登录态后再补拉一次用户信息
            session.refreshUser()
            // 后台续下：上次进程被杀时没下完的任务自动恢复，并挂起前台服务保活
            val resumed = downloads.restorePending()
            if (resumed > 0) DownloadService.start(this@ArtifactBoostApp)
        }

        // 预热一次设置（同样是磁盘读，放 IO）
        appScope.launch { AccelerationSettings.load(this@ArtifactBoostApp) }
    }

    companion object {
        lateinit var instance: ArtifactBoostApp
            private set
    }
}
