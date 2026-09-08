package com.sameuo.dashcam.data.local.db

import com.sameuo.dashcam.data.protocol.model.CameraChannel
import com.sameuo.dashcam.data.protocol.model.MediaKind

/** A paired / recently-used dashcam. */
data class SavedDevice(
    val id: Long = 0,
    val name: String,
    val chip: String,            // ChipPlatform name
    val host: String,
    val ssid: String = "",
    val lastConnected: Long = 0L,
)

/** A media file downloaded from a device, kept in the local album. */
data class DownloadedMedia(
    val id: Long = 0,
    val devicePath: String,
    val localPath: String,
    val name: String,
    val sizeBytes: Long,
    val kind: MediaKind,
    val channel: CameraChannel,
    val timeText: String = "",
    val downloadedAt: Long = System.currentTimeMillis(),
)
