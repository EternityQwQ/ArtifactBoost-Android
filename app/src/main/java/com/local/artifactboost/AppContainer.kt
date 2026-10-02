package com.local.artifactboost

import android.app.Application
import com.local.artifactboost.download.AccelerationSettings
import com.local.artifactboost.download.DownloadManager
import com.local.artifactboost.runtime.AppViewModel
import java.io.File

/**
 * 应用入口。手动做依赖装配，不引入 Hilt/Koin——
 * 这个规模的项目用一个 Application 当容器就够了，少一层注解处理器和构建开销。
 */
class ArtifactBoostApp : Application() {

    lateinit var session: SessionManager
        private set
    lateinit var downloads: DownloadManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        session = SessionManager(this.applicationContext)
        // 产物落在应用外部私有目录：Android/data/<pkg>/files/Download
        // 卸载即清理，也不需要申请存储权限
        val dir = getExternalFilesDir("Download") ?: File(filesDir, "Download")
        if (!dir.exists()) dir.mkdirs()
        downloads = DownloadManager(this.applicationContext, session, dir)
    }

    companion object {
        lateinit var instance: ArtifactBoostApp
            private set
    }
}

/** 装配 AppViewModel 需要的东西，交给 Compose 侧调用 */
object AppContainer {
    suspend fun createViewModel(app: ArtifactBoostApp): AppViewModel {
        val settings = AccelerationSettings.load(app)
        return AppViewModel(
            session = app.session,
            downloads = app.downloads,
            settings = settings,
            appContext = app,
        )
    }
}
