package com.sameuo.dashcam.data.firmware

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.File

/** OkHttp request body that streams a file to disk and reports upload progress. */
class ProgressRequestBody(
    private val file: File,
    private val mediaType: MediaType?,
    private val onProgress: (sent: Long, total: Long) -> Unit,
) : RequestBody() {

    override fun contentType(): MediaType? = mediaType
    override fun contentLength(): Long = file.length()

    override fun writeTo(sink: BufferedSink) {
        val total = file.length()
        var sent = 0L
        var lastCb = 0L
        file.inputStream().use { input ->
            input.source().use { source ->
                val buffer = okio.Buffer()
                while (true) {
                    val read = source.read(buffer, SEGMENT)
                    if (read == -1L) break
                    sink.write(buffer, read)
                    sent += read
                    if (sent - lastCb > 256 * 1024) {
                        onProgress(sent, total)
                        lastCb = sent
                    }
                }
            }
        }
        onProgress(sent, total)
    }

    private companion object { const val SEGMENT = 64L * 1024 }
}
