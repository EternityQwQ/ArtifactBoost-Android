package com.artifactboost.app

import com.artifactboost.app.data.AccelerationSettings
import com.artifactboost.app.data.ArchiveFormat
import com.artifactboost.app.data.DownloadItem
import com.artifactboost.app.data.DownloadSource
import com.artifactboost.app.data.RouteMode
import com.artifactboost.app.download.DownloadTaskStore
import com.artifactboost.app.download.TaskRecord
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 后台续下的地基：任务记录必须完整写进 JSON 再读回来。
 *
 * 四种来源类型都要覆盖 —— 恢复时靠 source 重新解析签名地址，
 * 丢任何一个字段都会导致「重启后任务恢复失败」。
 */
class TaskRecordTest {

    private val json = DownloadTaskStore.json

    private fun roundTrip(records: List<TaskRecord>): List<TaskRecord> {
        val raw = json.encodeToString(ListSerializer(TaskRecord.serializer()), records)
        return json.decodeFromString(ListSerializer(TaskRecord.serializer()), raw)
    }

    private fun item(id: String, source: DownloadSource) = DownloadItem(
        id = id,
        title = "name-$id",
        subtitle = "sub",
        size = 12345L,
        isPrivate = false,
        source = source,
    )

    @Test
    fun `四种来源往返无损`() {
        val records = listOf(
            TaskRecord(item("a1", DownloadSource.Artifact("o/r", 11L))),
            TaskRecord(item("l1", DownloadSource.RunLogs("o/r", 22L))),
            TaskRecord(
                item(
                    "r1",
                    DownloadSource.ReleaseAsset("o/r", 33L, "https://github.com/o/r/releases/download/v1/x.zip"),
                ),
            ),
            TaskRecord(
                item("s1", DownloadSource.SourceArchive("o/r", "main", ArchiveFormat.TARBALL)),
            ),
        )
        assertEquals(records, roundTrip(records))
    }

    @Test
    fun `加速设置快照往返无损`() {
        val settings = AccelerationSettings(connections = 64, mode = RouteMode.CUSTOM, customPrefix = "https://m.io/")
        val record = TaskRecord.from(
            item("c1", DownloadSource.Artifact("o/r", 1L)),
            settings,
        )
        val restored = roundTrip(listOf(record)).single()
        assertEquals(record, restored)
        assertEquals(settings.clampedConnections, restored.toSettings().connections)
        assertEquals(RouteMode.CUSTOM, restored.toSettings().mode)
        assertEquals("https://m.io/", restored.toSettings().customPrefix)
    }

    @Test
    fun `未知字段向前兼容`() {
        // 未来加字段后，旧版本读新数据不能炸（ignoreUnknownKeys）；
        // 用字符串注入未知字段，不硬编码多态 discriminator 的具体写法。
        val record = TaskRecord(item("a1", DownloadSource.Artifact("o/r", 11L)))
        val raw = json.encodeToString(ListSerializer(TaskRecord.serializer()), listOf(record))
        val withFuture = raw.replaceFirst("{", "{\"futureField\":1,")
        val decoded = json.decodeFromString(ListSerializer(TaskRecord.serializer()), withFuture)
        assertEquals(listOf(record), decoded)
    }
}
