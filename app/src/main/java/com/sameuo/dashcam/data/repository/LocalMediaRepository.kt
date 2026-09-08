package com.sameuo.dashcam.data.repository

import android.content.Context
import com.sameuo.dashcam.data.download.DownloadEngine
import com.sameuo.dashcam.data.download.DownloadService
import com.sameuo.dashcam.data.local.db.DownloadedMedia
import com.sameuo.dashcam.data.local.db.SameuoDatabase
import com.sameuo.dashcam.data.protocol.model.DeviceMediaFile
import com.sameuo.dashcam.data.protocol.model.MediaKind
import java.io.File

class LocalMediaRepository(
    private val context: Context,
    private val db: SameuoDatabase,
    val downloadEngine: DownloadEngine,
) {
    suspend fun localMedia(kind: MediaKind? = null): List<DownloadedMedia> = db.listMedia(kind)

    suspend fun deleteLocal(item: DownloadedMedia): Boolean {
        runCatching { File(item.localPath).takeIf { it.exists() }?.delete() }
        db.deleteMedia(item.id)
        return true
    }

    suspend fun alreadyDownloaded(path: String): DownloadedMedia? = db.mediaByDevicePath(path)

    /** Queue remote files for download and make sure the foreground service is alive. */
    fun download(files: List<DeviceMediaFile>) {
        if (files.isEmpty()) return
        downloadEngine.enqueue(files)
        DownloadService.start(context)
    }

    fun download(vararg files: DeviceMediaFile) = download(files.toList())

    val tasks get() = downloadEngine.tasks
}
