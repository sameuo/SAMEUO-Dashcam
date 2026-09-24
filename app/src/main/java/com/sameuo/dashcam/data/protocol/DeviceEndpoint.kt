package com.sameuo.dashcam.data.protocol

import com.sameuo.dashcam.BuildConfig
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.net.URLEncoder

/**
 * Knows how to build every URL the app talks to on a dashcam.
 *
 * @param host device AP gateway, e.g. 192.168.1.254 (SAMEUO Gen3 / Novatek)
 * @param httpPort command/HFS port (default 80)
 * @param mjpgPort motion-JPEG preview port (Novatek photo mode, default 8192)
 */
data class DeviceEndpoint(
    val host: String = BuildConfig.DEFAULT_DEVICE_HOST,
    val httpPort: Int = BuildConfig.DEFAULT_HTTP_PORT,
    val mjpgPort: Int = 8192,
) {
    private val base: HttpUrl
        get() = HttpUrl.Builder().scheme("http").host(host).port(httpPort).build()

    /** Build a control-command URL: /?custom=1&cmd=xxx[&par= / &str=]. */
    fun command(cmd: Int, par: Int? = null): HttpUrl {
        val b = base.newBuilder().addQueryParameter("custom", "1").addQueryParameter("cmd", cmd.toString())
        if (par != null) b.addQueryParameter("par", par.toString())
        return b.build()
    }

    fun commandStr(cmd: Int, str: String): HttpUrl =
        base.newBuilder()
            .addQueryParameter("custom", "1")
            .addQueryParameter("cmd", cmd.toString())
            // Device firmware expects raw values; encode spaces/backslashes safely.
            .addQueryParameter("str", str)
            .build()

    /** RTSP live-view URL. Novatek serves `rtsp://host/live_rtsp` / `<name>.mov`. */
    fun rtsp(streamName: String = "live_rtsp"): String = "rtsp://$host/$streamName"

    /** Alternate common RTSP paths used by Novatek / Allwinner firmwares. */
    val rtspCandidates: List<String>
        get() = listOf(
            rtsp("live_rtsp"),
            rtsp("live"),
            rtsp("preview"),
            "rtsp://$host/CH001.sdp",
            rtsp("$host/stream1"),
            rtsp("$host/live.mp4"),
            "rtsp://$host:8554/live",
            "rtsp://$host:8554/live_rtsp",
        )

    /** Motion-JPEG stream (photo mode), e.g. http://host:8192. */
    fun mjpg(): String = "http://$host:$mjpgPort"

    /** Candidate single-server / multipart MJPEG preview URLs tried after RTSP fails. */
    val mjpgCandidates: List<String>
        get() = listOf(
            mjpg(),
            "http://$host:$mjpgPort/live.jpg",
            "http://$host/live.jpg",
            "http://$host/live",
        )

    /**
     * Convert a device file path (A:\NOVATEK\MOVIE\xxx.MOV) to an HFS download URL.
     * The on-device HTTP file server serves the same path with forward slashes.
     */
    fun fileDownloadUrl(devicePath: String): String {
        val rel = devicePath.replace('\\', '/').trimStart('/')
        return base.newBuilder().addPathSegments(rel).build().toString()
    }

    /** Thumbnail / screen-nail are commands whose parameter is the file path. */
    fun thumbnailUrl(devicePath: String): HttpUrl = commandStr(WifiCmd.THUMB, devicePath)
    fun screenNailUrl(devicePath: String): HttpUrl = commandStr(WifiCmd.SCREEN, devicePath)

    /** Movie file info is served off the HFS path with a cmd query. */
    fun movieInfoUrl(devicePath: String): String {
        val rel = devicePath.replace('\\', '/').trimStart('/')
        return base.newBuilder()
            .addPathSegments(rel)
            .addQueryParameter("custom", "1")
            .addQueryParameter("cmd", WifiCmd.MOVIE_FILE_INFO.toString())
            .build().toString()
    }

    fun raw(path: String): String = base.newBuilder().addPathSegments(path.trimStart('/')).build().toString()

    @Suppress("unused")
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    companion object {
        /** Well-known device gateways across chip platforms, used for reachability probe. */
        val COMMON_HOSTS = listOf("192.168.1.254", "192.168.1.1", "192.168.0.1")
    }
}
