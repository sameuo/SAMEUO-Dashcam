package com.sameuo.dashcam.data.repository

import com.sameuo.dashcam.core.Outcome
import com.sameuo.dashcam.data.protocol.DeviceClient
import com.sameuo.dashcam.data.protocol.model.DeviceMediaFile
import com.sameuo.dashcam.data.protocol.model.DeviceMenu
import com.sameuo.dashcam.data.protocol.model.DeviceStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Single façade the ViewModels use to talk to the connected camera. */
class DeviceRepository(private val connection: ConnectionRepository) {

    private val _remoteFiles = MutableStateFlow<List<DeviceMediaFile>>(emptyList())
    val remoteFiles: StateFlow<List<DeviceMediaFile>> = _remoteFiles.asStateFlow()

    private val _menu = MutableStateFlow<DeviceMenu?>(null)
    val menu: StateFlow<DeviceMenu?> = _menu.asStateFlow()

    private val _status = MutableStateFlow<DeviceStatus?>(null)
    val status: StateFlow<DeviceStatus?> = _status.asStateFlow()

    private fun client(): DeviceClient? = connection.currentClient

    suspend fun refreshFiles(): Outcome<List<DeviceMediaFile>> {
        val c = client() ?: return Outcome.Err(com.sameuo.dashcam.core.Cause.NotConnected())
        return when (val r = c.listFiles()) {
            is Outcome.Ok -> { _remoteFiles.value = r.value; r }
            is Outcome.Err -> r
        }
    }

    suspend fun refreshStatus(): Outcome<DeviceStatus> {
        val c = client() ?: return Outcome.Err(com.sameuo.dashcam.core.Cause.NotConnected())
        return when (val r = c.queryStatus()) {
            is Outcome.Ok -> { _status.value = r.value; r }
            is Outcome.Err -> r
        }
    }

    suspend fun refreshMenu(): Outcome<DeviceMenu> {
        val c = client() ?: return Outcome.Err(com.sameuo.dashcam.core.Cause.NotConnected())
        return when (val r = c.queryMenu()) {
            is Outcome.Ok -> { _menu.value = r.value; r }
            is Outcome.Err -> r
        }
    }

    suspend fun setEnum(cmd: Int, par: Int) = client()?.sendCommand(cmd, par)
        ?: Outcome.Err(com.sameuo.dashcam.core.Cause.NotConnected())

    /** Read a setting's current enum by issuing its command with no par (<Value>). */
    suspend fun queryCurrentIndex(cmd: Int): Int? =
        (client()?.sendCommand(cmd, null) as? Outcome.Ok)?.value?.value?.toIntOrNull()

    suspend fun setString(cmd: Int, str: String) = client()?.sendStringCommand(cmd, str)
        ?: Outcome.Err(com.sameuo.dashcam.core.Cause.NotConnected())

    suspend fun delete(file: DeviceMediaFile) = client()?.deleteFile(file)
        ?: Outcome.Err(com.sameuo.dashcam.core.Cause.NotConnected())

    suspend fun deleteAll() = client()?.deleteAll()
        ?: Outcome.Err(com.sameuo.dashcam.core.Cause.NotConnected())

    suspend fun formatCard() = client()?.formatCard()
        ?: Outcome.Err(com.sameuo.dashcam.core.Cause.NotConnected())

    suspend fun factoryReset() = client()?.factoryReset()
        ?: Outcome.Err(com.sameuo.dashcam.core.Cause.NotConnected())

    suspend fun thumbnail(file: DeviceMediaFile): ByteArray? = client()?.thumbnailBytes(file)

    suspend fun setRecording(start: Boolean) = client()?.setRecording(start)
    suspend fun capture() = client()?.capturePhoto()
    suspend fun setLiveView(start: Boolean) = client()?.setLiveView(start)
    suspend fun setPip(style: Int) = client()?.setPipStyle(style)
}
