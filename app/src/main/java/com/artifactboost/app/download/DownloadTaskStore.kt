package com.artifactboost.app.download

import android.content.Context
import com.artifactboost.app.data.AccelerationSettings
import com.artifactboost.app.data.DownloadItem
import com.artifactboost.app.data.RouteMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * 后台下载的任务落盘：未完成的任务（下载项 + 加速设置快照）。
 *
 * 进程被系统杀掉后内存队列全丢；有了这份记录，
 * 下次启动时 [DownloadManager.restorePending] 能把没下完的任务自动续上。
 * 只有「死时还没终结」的任务会留在这里：
 * 完成 / 失败 / 用户取消 / 移除都会删记录，不会复活。
 */
@Serializable
data class TaskRecord(
    val item: DownloadItem,
    val connections: Int = 16,
    val mode: RouteMode = RouteMode.SMART,
    val customPrefix: String = "",
) {
    fun toSettings(): AccelerationSettings =
        AccelerationSettings(
            connections = connections,
            mode = mode,
            customPrefix = customPrefix,
        )

    companion object {
        fun from(item: DownloadItem, settings: AccelerationSettings): TaskRecord =
            TaskRecord(
                item = item,
                connections = settings.clampedConnections,
                mode = settings.mode,
                customPrefix = settings.customPrefix,
            )
    }
}

/** 未完成任务的持久化：SharedPreferences 存一份 JSON 数组。 */
class DownloadTaskStore(appContext: Context) {
    private val prefs = appContext.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 入队 / 重试时落盘（同 id 覆盖） */
    fun save(record: TaskRecord) {
        val current = loadAll().associateBy { it.item.id }.toMutableMap()
        current[record.item.id] = record
        persist(current.values.toList())
    }

    /** 终结（完成 / 失败 / 取消 / 移除）时清记录，避免下次启动复活 */
    fun remove(id: String) {
        val remaining = loadAll().filter { it.item.id != id }
        persist(remaining)
    }

    /** 读出全部未完成任务；数据损坏时清空并返回空表，不让坏数据卡死恢复 */
    fun loadAll(): List<TaskRecord> {
        val raw = prefs.getString(KEY_PENDING, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(TaskRecord.serializer()), raw)
        }.getOrElse {
            prefs.edit().remove(KEY_PENDING).apply()
            emptyList()
        }
    }

    private fun persist(records: List<TaskRecord>) {
        prefs.edit()
            .putString(KEY_PENDING, json.encodeToString(ListSerializer(TaskRecord.serializer()), records))
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "artifactboost_tasks"
        private const val KEY_PENDING = "ab.pendingTasks.v1"

        internal val json = Json { ignoreUnknownKeys = true }
    }
}
