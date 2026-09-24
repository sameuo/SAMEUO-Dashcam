package com.sameuo.dashcam.data.protocol

import com.sameuo.dashcam.core.AppLog
import com.sameuo.dashcam.core.Outcome
import com.sameuo.dashcam.data.protocol.model.BatteryStatus
import com.sameuo.dashcam.data.protocol.model.CardStatus
import com.sameuo.dashcam.data.protocol.model.DeviceMediaFile
import com.sameuo.dashcam.data.protocol.model.DeviceMenu
import com.sameuo.dashcam.data.protocol.model.DeviceStatus
import com.sameuo.dashcam.data.protocol.model.FirmwareInfo
import com.sameuo.dashcam.data.protocol.model.FunctionResult
import com.sameuo.dashcam.data.protocol.model.OperationMode
import com.sameuo.dashcam.data.protocol.parser.NovatekXmlParser
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.File

/**
 * Novatek-based device client, the reference implementation for SAMEUO Gen3
 * (NT96580/NT98529/NT9666x family). Command numbers and XML shapes follow
 * "Gen3-Set menu list-CMD" and "NT9666x Wi-Fi Command User Guide".
 */
class NovatekDeviceClient(
    override val endpoint: DeviceEndpoint,
    private val http: DeviceHttpClient,
    private val parser: NovatekXmlParser = NovatekXmlParser(),
) : DeviceClient {

    // 3001 mode-set enum (distinct from the 3037 operation-mode query).
    private companion object {
        const val SET_MODE_PHOTO = 0
        const val SET_MODE_MOVIE = 1
        const val SET_MODE_PLAYBACK = 2
    }

    private suspend fun getString(url: String): Outcome<String> =
        Outcome.catching { http.getString(url) }

    override suspend fun sendCommand(cmd: Int, par: Int?): Outcome<FunctionResult> {
        val res = getString(endpoint.command(cmd, par).toString())
        return res.map { parser.parseFunction(it) }
    }

    override suspend fun sendStringCommand(cmd: Int, str: String): Outcome<FunctionResult> {
        val res = getString(endpoint.commandStr(cmd, str).toString())
        return res.map { parser.parseFunction(it) }
    }

    override suspend fun ping(): Boolean = when (val r = sendCommand(WifiCmd.HEARTBEAT)) {
        is Outcome.Ok -> r.value.isSuccess
        is Outcome.Err -> false
    }

    override suspend fun enterWifiSession() = sendCommand(WifiCmd.APP_STARTUP)
    override suspend fun exitWifiSession() = sendCommand(WifiCmd.APP_SESSION_CLOSE)
    override suspend fun heartbeat() = sendCommand(WifiCmd.HEARTBEAT)

    override suspend fun switchToPlaybackMode() = sendCommand(WifiCmd.MODE_CHANGE, SET_MODE_PLAYBACK)
    override suspend fun switchToMovieMode() = sendCommand(WifiCmd.MODE_CHANGE, SET_MODE_MOVIE)

    override suspend fun setRecording(start: Boolean) =
        sendCommand(WifiCmd.RECORD, if (start) 1 else 0)

    override suspend fun setLiveView(start: Boolean): Outcome<FunctionResult> {
        if (start) switchToMovieMode()
        // Gen3 routes the preview to the APP screen via cmd 3028.
        val r = sendCommand(WifiCmd.APP_PREVIEW_SCREEN, if (start) 1 else 0)
        // Best-effort legacy stream enable (no-op on Gen3).
        runCatching { sendCommand(WifiCmd.LIVEVIEW_START, if (start) 1 else 0) }
        return r
    }

    override suspend fun capturePhoto(): Outcome<FunctionResult> =
        // Gen3 uses 2017 for snapshot in live-preview mode; 1001 in photo mode.
        sendCommand(WifiCmd.CAPTURE_FROM_PREVIEW)

    override suspend fun setPipStyle(style: Int) = sendCommand(WifiCmd.SET_PIP_STYLE, style)

    override suspend fun getVersion(): Outcome<String> =
        sendCommand(WifiCmd.VERSION).map { it.string ?: it.value ?: "" }

    override suspend fun getFreeSpaceBytes(): Outcome<Long> =
        sendCommand(WifiCmd.DISK_FREE_SPACE).map { it.value?.toLongOrNull() ?: -1L }

    override suspend fun queryMenu(): Outcome<DeviceMenu> = coroutineScope {
        // 3031 = full menu schema.
        val schema = when (val s = getString(endpoint.command(WifiCmd.QUERY_MENUITEM).toString())) {
            is Outcome.Ok -> s.value
            is Outcome.Err -> return@coroutineScope Outcome.Err(s.cause)
        }
        val base = parser.parseMenu(schema)
        // 3014 = current status (per Gen3 note, pairs with 3031).
        val current3014: Map<Int, Int> = when (
            val r = getString(endpoint.command(WifiCmd.QUERY_CUR_STATUS).toString())
        ) {
            is Outcome.Ok -> parser.parseCurrentStatus(r.value)
            is Outcome.Err -> emptyMap()
        }
        // Per-item current value: issue the set command with no par and read <Value>.
        val resolved = base.items.map { item ->
            async {
                val direct = (sendCommand(item.cmd, null) as? Outcome.Ok)?.value?.value?.toIntOrNull()
                val cur = direct ?: current3014[item.cmd]
                if (cur != null) item.copy(currentIndex = cur) else item
            }
        }.awaitAll()
        Outcome.Ok(DeviceMenu(resolved))
    }

    override suspend fun queryStatus(): Outcome<DeviceStatus> = coroutineScope {
        // Best-effort aggregation: a single failing sub-query must not break the panel.
        val mode = async { sendCommand(WifiCmd.GET_OPERATION_MODE) }
        val battery = async { sendCommand(WifiCmd.GET_BATTERY) }
        val card = async { sendCommand(WifiCmd.GET_CARD_STATUS) }
        val space = async { sendCommand(WifiCmd.DISK_FREE_SPACE) }
        val version = async { getVersion() }

        val opMode = (mode.await() as? Outcome.Ok)?.value?.value?.toIntOrNull().let { OperationMode.fromCode(it) }
        val bat = (battery.await() as? Outcome.Ok)?.value.let { BatteryStatus.fromCode(it?.value?.toIntOrNull()) }
        val cd = (card.await() as? Outcome.Ok)?.value.let { CardStatus.fromCode(it?.value?.toIntOrNull()) }
        val free = (space.await() as? Outcome.Ok)?.value?.value?.toLongOrNull() ?: -1L
        val ver = (version.await() as? Outcome.Ok)?.value

        Outcome.Ok(
            DeviceStatus(
                operationMode = opMode,
                isRecording = opMode == OperationMode.RECORDING,
                battery = bat,
                card = cd,
                freeSpaceBytes = free,
                version = ver,
            )
        )
    }

    override suspend fun listFiles(): Outcome<List<DeviceMediaFile>> {
        // File listing is only valid in playback mode on Novatek firmware.
        switchToPlaybackMode()
        // Standard Novatek file list is 3015; the Gen3 note also references 4001 in playback.
        val primary = getString(endpoint.command(WifiCmd.FILE_LIST).toString())
        val primaryFiles = (primary as? Outcome.Ok)?.value?.let { parser.parseFileList(it) }
        if (!primaryFiles.isNullOrEmpty()) {
            return Outcome.Ok(primaryFiles.sortedByDescending { it.timecode })
        }
        val alt = getString(endpoint.command(WifiCmd.THUMB).toString())
        return when (alt) {
            is Outcome.Ok -> Outcome.Ok(parser.parseFileList(alt.value).sortedByDescending { it.timecode })
            is Outcome.Err -> if (primary is Outcome.Err) Outcome.Err(primary.cause) else Outcome.Ok(emptyList())
        }
    }

    override suspend fun deleteFile(file: DeviceMediaFile): Outcome<FunctionResult> =
        sendStringCommand(WifiCmd.DELETE_ONE, file.path)

    override suspend fun deleteAll() = sendCommand(WifiCmd.DELETE_ALL)

    override suspend fun thumbnailBytes(file: DeviceMediaFile): ByteArray? = try {
        http.getBytes(endpoint.thumbnailUrl(file.path).toString())
    } catch (t: Throwable) {
        AppLog.w("thumbnail failed for ${file.name}", t); null
    }

    override suspend fun download(
        file: DeviceMediaFile,
        dest: File,
        onProgress: (Long, Long) -> Unit,
    ): Outcome<File> = Outcome.catching {
        http.download(endpoint.fileDownloadUrl(file.path), dest, file.sizeBytes, onProgress)
    }

    override suspend fun syncTime(yyyyMmDd: String, hhMmSs: String): Outcome<FunctionResult> {
        val d = sendStringCommand(WifiCmd.SET_DATE, yyyyMmDd)
        if (d is Outcome.Err) return d
        return sendStringCommand(WifiCmd.SET_TIME, hhMmSs)
    }

    override suspend fun formatCard() = sendCommand(WifiCmd.FORMAT, 1)
    override suspend fun factoryReset() = sendCommand(WifiCmd.SYS_RESET, 1)

    override suspend fun checkFirmware(): Outcome<FirmwareInfo> {
        val res = getString(endpoint.command(WifiCmd.GET_DOWNLOAD_URL).toString())
        return when (res) {
            is Outcome.Ok -> parser.parseFirmware(res.value)?.let { Outcome.Ok(it) }
                ?: Outcome.Err(com.sameuo.dashcam.core.Cause.Protocol("Empty firmware descriptor"))
            is Outcome.Err -> Outcome.Err(res.cause)
        }
    }
}
