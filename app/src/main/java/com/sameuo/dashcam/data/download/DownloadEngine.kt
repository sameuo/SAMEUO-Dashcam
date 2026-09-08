package com.sameuo.dashcam.data.download

import android.content.Context
import com.sameuo.dashcam.core.AppLog
import com.sameuo.dashcam.data.local.db.DownloadedMedia
import com.sameuo.dashcam.data.local.db.SameuoDatabase
import com.sameuo.dashcam.data.protocol.DeviceClient
import com.sameuo.dashcam.data.protocol.model.DeviceMediaFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicLong

enum class DownloadStatus { QUEUED, RUNNING, DONE, FAILED, CANCELED }

data class DownloadTask(
    val id: Long,
    val file: DeviceMediaFile,
    val downloaded: Long = 0,
    val total: Long = file.sizeBytes,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val localPath: String = "",
    val error: String? = null,
) {
    val progress: Float get() = if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f
}

/**
 * Sequential background download queue. Runs inside [com.sameuo.dashcam.data.download.DownloadService]
 * so users can keep browsing the app while 4K files transfer — a fix for Viidure's
 * "cannot navigate while downloading" complaint. Resumable via HTTP Range.
 */
class DownloadEngine(
    private val appContext: Context,
    private val db: SameuoDatabase,
    private val scope: CoroutineScope,
    private val clientProvider: () -> DeviceClient?,
    private val baseDirOverride: () -> String? = { null },
) {
    private val seq = AtomicLong(1)
    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()
    private val cancelled = mutableSetOf<Long>()
    private var worker: Job? = null

    fun enqueue(files: List<DeviceMediaFile>): List<Long> {
        val ids = files.map { seq.getAndIncrement() }
        _tasks.update { it + files.mapIndexed { i, f -> DownloadTask(ids[i], f) } }
        kick()
        return ids
    }

    fun cancel(id: Long) {
        cancelled += id
        _tasks.update { list -> list.map { if (it.id == id) it.copy(status = DownloadStatus.CANCELED) else it } }
    }

    fun retry(id: Long) {
        cancelled.remove(id)
        _tasks.update { list -> list.map { if (it.id == id) it.copy(status = DownloadStatus.QUEUED, error = null) else it } }
        kick()
    }

    fun clearFinished() = _tasks.update { list ->
        list.filter { it.status == DownloadStatus.RUNNING || it.status == DownloadStatus.QUEUED }
    }

    @Synchronized
    private fun kick() {
        if (worker?.isActive == true) return
        worker = scope.launch {
            while (true) {
                val next = _tasks.value.firstOrNull { it.status == DownloadStatus.QUEUED } ?: break
                if (next.id in cancelled) continue
                downloadOne(next)
            }
        }
    }

    private suspend fun downloadOne(task: DownloadTask) {
        val client = clientProvider()
        if (client == null) {
            _tasks.update { l -> l.map { if (it.id == task.id) it.copy(status = DownloadStatus.FAILED, error = "Not connected") else it } }
            return
        }
        val dir = baseDirOverride()?.takeIf { it.isNotBlank() }?.let { File(it) }
            ?: File(appContext.getExternalFilesDir(null), "SAMEUO/Media")
        dir.mkdirs()
        val dest = File(dir, task.file.name)
        update(task.id) { it.copy(status = DownloadStatus.RUNNING) }
        val outcome = client.download(task.file, dest) { got, total ->
            update(task.id) { it.copy(downloaded = got, total = if (total > 0) total else it.total) }
        }
        when (outcome) {
            is com.sameuo.dashcam.core.Outcome.Ok -> {
                db.upsertMedia(
                    DownloadedMedia(
                        devicePath = task.file.path,
                        localPath = dest.absolutePath,
                        name = task.file.name,
                        sizeBytes = dest.length(),
                        kind = task.file.kind,
                        channel = task.file.channel,
                        timeText = task.file.timeText,
                    )
                )
                update(task.id) {
                    it.copy(status = DownloadStatus.DONE, downloaded = dest.length(), localPath = dest.absolutePath)
                }
            }
            is com.sameuo.dashcam.core.Outcome.Err -> {
                AppLog.e("download failed ${task.file.name}", outcome.cause.throwable)
                update(task.id) { it.copy(status = DownloadStatus.FAILED, error = outcome.cause.message) }
            }
        }
    }

    private inline fun update(id: Long, block: (DownloadTask) -> DownloadTask) =
        _tasks.update { list -> list.map { if (it.id == id) block(it) else it } }
}
