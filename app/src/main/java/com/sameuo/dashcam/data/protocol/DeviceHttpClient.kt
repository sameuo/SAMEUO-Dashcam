package com.sameuo.dashcam.data.protocol

import com.sameuo.dashcam.core.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * Thin OkHttp transport for talking to a dashcam over its Wi-Fi AP.
 * Commands are quick; downloads are streamed with a progress callback.
 */
class DeviceHttpClient(
    val client: OkHttpClient = defaultClient(),
) {
    suspend fun getString(url: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw java.io.IOException("HTTP ${resp.code} for $url")
            resp.body?.string().orEmpty()
        }
    }

    suspend fun getBytes(url: String): ByteArray = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw java.io.IOException("HTTP ${resp.code}")
            resp.body?.bytes() ?: ByteArray(0)
        }
    }

    /**
     * Stream a remote file to [sink]/[dest], honouring an existing partial file
     * (HTTP Range) so interrupted 4K downloads can resume — a key fix vs Viidure.
     * Reports 0..1 progress on [onProgress].
     */
    suspend fun download(
        url: String,
        dest: File,
        expectedTotal: Long = -1L,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        dest.parentFile?.mkdirs()
        val existing = if (dest.exists()) dest.length() else 0L
        val builder = Request.Builder().url(url)
        if (existing > 0) builder.header("Range", "bytes=$existing-")
        client.newCall(builder.build()).execute().use { resp ->
            if (resp.code !in 200..299) throw java.io.IOException("HTTP ${resp.code}")
            val body = resp.body ?: throw java.io.IOException("Empty body")
            val resumed = resp.code == 206
            val start = if (resumed) existing else 0L
            val contentLength = body.contentLength()
            val total = if (contentLength > 0) contentLength + start else expectedTotal
            val append = resumed && existing > 0
            dest.outputStream().use { os ->
                if (append) (os as? java.io.FileOutputStream)?.channel?.position(existing)
                body.byteStream().copyToSuspend(os, start, total, onProgress)
            }
        }
        dest
    }

    private suspend fun java.io.InputStream.copyToSuspend(
        out: OutputStream,
        start: Long,
        total: Long,
        onProgress: (Long, Long) -> Unit,
        bufferSize: Int = 64 * 1024,
    ): Long {
        val buf = ByteArray(bufferSize)
        var read: Int
        var copied = start
        var lastCb = 0L
        while (true) {
            read = this.read(buf)
            if (read < 0) break
            coroutineContext.ensureActive()
            out.write(buf, 0, read)
            copied += read
            if (copied - lastCb > 256 * 1024) { // throttle callbacks to ~4/s
                onProgress(copied, total)
                lastCb = copied
            }
        }
        onProgress(copied, total)
        return copied - start
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        /** A client with long timeouts for big file downloads. */
        fun downloadClient(): OkHttpClient = defaultClient().newBuilder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
