package com.sameuo.dashcam.data.firmware

import com.sameuo.dashcam.core.AppLog
import com.sameuo.dashcam.core.Outcome
import com.sameuo.dashcam.data.protocol.DeviceClient
import com.sameuo.dashcam.data.protocol.DeviceHttpClient
import com.sameuo.dashcam.data.protocol.WifiCmd
import com.sameuo.dashcam.data.protocol.model.FirmwareInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/** Result of an OTA attempt. */
sealed class OtaState {
    data object Idle : OtaState()
    data class Checking(val currentVersion: String) : OtaState()
    data class Uploading(val percent: Int) : OtaState()
    data class Verifying(val info: FirmwareInfo) : OtaState()
    data class Done(val info: FirmwareInfo) : OtaState()
    data class Failed(val message: String) : OtaState()
}

/**
 * Firmware over-the-air flow per Novatek Gen3:
 * cmd 3025 descriptor → cmd 3026 set upload path → cmd 5001 multipart upload →
 * checksum verify → device applies & reboots.
 */
class FirmwareUpdater(
    private val client: DeviceClient,
    private val http: DeviceHttpClient,
) {
    suspend fun update(firmwareFile: File, onState: (OtaState) -> Unit): Outcome<FirmwareInfo> {
        return try {
            val desc = when (val r = client.checkFirmware()) {
                is Outcome.Ok -> r.value
                is Outcome.Err -> return r
            }
            onState(OtaState.Checking(desc.version))

            // Tell the device where the incoming firmware should land.
            val target = desc.deviceUploadPath ?: DEFAULT_UPLOAD_PATH
            client.sendStringCommand(WifiCmd.GET_UPDATE_FW_PATH, target)

            onState(OtaState.Uploading(0))
            upload(firmwareFile) { sent, total ->
                onState(OtaState.Uploading(if (total > 0) (sent * 100 / total).toInt() else 0))
            }

            onState(OtaState.Verifying(desc))
            if (desc.checkMethod.equals("md5", ignoreCase = true) && desc.checkValue.isNotBlank()) {
                val actual = md5(firmwareFile).lowercase()
                if (!actual.equals(desc.checkValue.lowercase().trim(), ignoreCase = true)) {
                    return Outcome.Err(com.sameuo.dashcam.core.Cause.Protocol(
                        "Checksum mismatch: expected ${desc.checkValue}, got $actual"))
                }
            }
            onState(OtaState.Done(desc))
            Outcome.Ok(desc)
        } catch (t: Throwable) {
            AppLog.e("OTA failed", t)
            onState(OtaState.Failed(t.message ?: "OTA failed"))
            Outcome.Err(com.sameuo.dashcam.core.Cause.from(t))
        }
    }

    private suspend fun upload(file: File, onProgress: (Long, Long) -> Unit) = withContext(Dispatchers.IO) {
        val total = file.length()
        val url = client.endpoint.command(WifiCmd.UPLOAD).toString()
        // Streaming multipart so multi-hundred-MB firmware doesn't load into RAM.
        val fileBody = ProgressRequestBody(file, "application/octet-stream".toMediaType(), onProgress)
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, fileBody)
            .build()
        val req = Request.Builder().url(url).post(multipart).build()
        http.client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful || !body.contains("<Status>0</Status>")) {
                throw java.io.IOException("Firmware upload rejected: HTTP ${resp.code} $body")
            }
        }
        onProgress(total, total)
    }

    private suspend fun md5(file: File): String = withContext(Dispatchers.IO) {
        val md = MessageDigest.getInstance("MD5")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                coroutineContext.ensureActive()
                md.update(buf, 0, n)
            }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object { const val DEFAULT_UPLOAD_PATH = "A:\\FW" }
}
