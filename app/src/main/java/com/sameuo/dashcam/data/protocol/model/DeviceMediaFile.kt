package com.sameuo.dashcam.data.protocol.model

/** Type of media stored on the device. */
enum class MediaKind { MOVIE, PHOTO, EMERGENCY, UNKNOWN }

/** Front / rear channel for dual-channel dashcams. */
enum class CameraChannel { FRONT, REAR, UNKNOWN }

/**
 * One file entry returned by the device file-list command (Novatek 3015):
 * <LIST><ALLFile><File><NAME/><FPATH/><SIZE/><TIMECODE/><TIME/><ATTR/></File></ALLFile></LIST>
 */
data class DeviceMediaFile(
    val name: String,
    val path: String,                 // e.g. A:\NOVATEK\MOVIE\xxx.MOV
    val sizeBytes: Long,
    val timecode: Long = 0L,
    val timeText: String = "",
    val attr: Int = 0,
    val kind: MediaKind = MediaKind.UNKNOWN,
    val channel: CameraChannel = CameraChannel.UNKNOWN,
) {
    val isVideo: Boolean get() = kind == MediaKind.MOVIE || kind == MediaKind.EMERGENCY
    val isLocked: Boolean get() = attr and 0x01 != 0 // FAT read-only bit -> emergency-locked
    val displaySizeMb: Double get() = sizeBytes / (1024.0 * 1024.0)
}
