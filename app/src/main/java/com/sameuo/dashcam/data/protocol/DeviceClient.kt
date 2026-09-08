package com.sameuo.dashcam.data.protocol

import com.sameuo.dashcam.core.Outcome
import com.sameuo.dashcam.data.protocol.model.DeviceMediaFile
import com.sameuo.dashcam.data.protocol.model.DeviceMenu
import com.sameuo.dashcam.data.protocol.model.DeviceStatus
import com.sameuo.dashcam.data.protocol.model.FirmwareInfo
import com.sameuo.dashcam.data.protocol.model.FunctionResult
import java.io.File

/**
 * Chip-agnostic high-level contract every dashcam protocol adapter implements.
 * Novatek (SAMEUO Gen3) is implemented; other chips reuse the Novatek-like HTTP
 * dialect or provide their own adapter via [com.sameuo.dashcam.data.protocol.chip].
 */
interface DeviceClient {
    val endpoint: DeviceEndpoint

    /** Reachability probe used by auto-reconnect. */
    suspend fun ping(): Boolean

    /** Generic set/enum command. */
    suspend fun sendCommand(cmd: Int, par: Int? = null): Outcome<FunctionResult>
    suspend fun sendStringCommand(cmd: Int, str: String): Outcome<FunctionResult>

    // --- Session / mode ---
    suspend fun enterWifiSession(): Outcome<FunctionResult>
    suspend fun exitWifiSession(): Outcome<FunctionResult>
    suspend fun heartbeat(): Outcome<FunctionResult>
    suspend fun switchToPlaybackMode(): Outcome<FunctionResult>
    suspend fun switchToMovieMode(): Outcome<FunctionResult>

    // --- Recording / preview ---
    suspend fun setRecording(start: Boolean): Outcome<FunctionResult>
    suspend fun setLiveView(start: Boolean): Outcome<FunctionResult>
    suspend fun capturePhoto(): Outcome<FunctionResult>
    suspend fun setPipStyle(style: Int): Outcome<FunctionResult>

    // --- Status / info ---
    suspend fun queryStatus(): Outcome<DeviceStatus>
    suspend fun getVersion(): Outcome<String>
    suspend fun getFreeSpaceBytes(): Outcome<Long>
    suspend fun queryMenu(): Outcome<DeviceMenu>

    // --- Files ---
    suspend fun listFiles(): Outcome<List<DeviceMediaFile>>
    suspend fun deleteFile(file: DeviceMediaFile): Outcome<FunctionResult>
    suspend fun deleteAll(): Outcome<FunctionResult>
    suspend fun thumbnailBytes(file: DeviceMediaFile): ByteArray?
    suspend fun download(file: DeviceMediaFile, dest: File, onProgress: (Long, Long) -> Unit): Outcome<File>

    // --- Settings helpers ---
    suspend fun syncTime(yyyyMmDd: String, hhMmSs: String): Outcome<FunctionResult>
    suspend fun formatCard(): Outcome<FunctionResult>
    suspend fun factoryReset(): Outcome<FunctionResult>

    // --- OTA ---
    suspend fun checkFirmware(): Outcome<FirmwareInfo>
}
