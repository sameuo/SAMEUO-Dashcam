package com.sameuo.dashcam.ui.live

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.ui.PlayerView
import com.sameuo.dashcam.data.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.util.concurrent.TimeUnit

private enum class LiveMode { RTSP, MJPG }

@Composable
fun LiveViewScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val endpoint = ServiceLocator.connection.currentClient?.endpoint
    val rtspCandidates = remember {
        endpoint?.rtspCandidates ?: listOf("rtsp://192.168.1.254/live_rtsp")
    }
    val mjpgCandidates = remember { endpoint?.mjpgCandidates ?: emptyList() }

    var mode by remember { mutableStateOf(LiveMode.RTSP) }
    var candidateIndex by remember { mutableIntStateOf(0) }
    var showVideo by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }

    // Put the camera in movie/preview mode and route the preview to the APP (Gen3 3028).
    LaunchedEffect(Unit) {
        ServiceLocator.connection.currentClient?.switchToMovieMode()
        ServiceLocator.device.setLiveView(true)
    }

    // RTSP is forced over TCP (RTP-interleaved): dashcam APs commonly drop UDP,
    // which otherwise presents as a connected-but-black screen.
    val rtspFactory = remember { RtspMediaSource.Factory().setForceUseRtpTcp(true) }

    val player = remember(candidateIndex) {
        showVideo = false
        ExoPlayer.Builder(context).setMediaSourceFactory(rtspFactory).build().apply {
            val url = rtspCandidates[candidateIndex.coerceIn(0, rtspCandidates.lastIndex)]
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) showVideo = true
                }

                override fun onPlayerError(error: PlaybackException) {
                    if (candidateIndex < rtspCandidates.lastIndex) {
                        candidateIndex++
                    } else if (mjpgCandidates.isNotEmpty()) {
                        mode = LiveMode.MJPG
                    } else {
                        showVideo = false
                    }
                }
            })
        }
    }

    DisposableEffect(candidateIndex) {
        onDispose { player.release() }
    }
    DisposableEffect(Unit) {
        onDispose {
            kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                runCatching { ServiceLocator.device.setLiveView(false) }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        when (mode) {
            LiveMode.RTSP -> AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        this.player = player
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            LiveMode.MJPG -> MjpegPreview(urls = mjpgCandidates, modifier = Modifier.fillMaxSize())
        }

        if (!showVideo && mode == LiveMode.RTSP) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.Center).size(46.dp),
            )
        }

        // Top bar overlay.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.35f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                onClick = onBack,
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.18f),
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("✕", color = Color.White, style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.size(12.dp))
            Text("LIVE", color = Color.White, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            Surface(
                onClick = {
                    toast = "Photo captured"
                    scope.launch { ServiceLocator.device.capture() }
                },
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.18f),
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Camera, contentDescription = "Capture", tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
        }

        // Bottom record control.
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.35f))
                .padding(vertical = 22.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Surface(
                onClick = {
                    recording = !recording
                    scope.launch { ServiceLocator.device.setRecording(recording) }
                },
                shape = CircleShape,
                color = if (recording) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.2f),
                modifier = Modifier.size(68.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.FiberManualRecord,
                        contentDescription = "Record",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp),
                    )
                }
            }
        }

        toast?.let { msg ->
            LaunchedEffect(msg) {
                kotlinx.coroutines.delay(1800)
                toast = null
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) { Text(msg, color = Color.White) }
            }
        }
    }
}

/**
 * Best-effort Motion-JPEG preview. Scans the raw byte stream for JPEG frames
 * (SOI FFD8 ... EOI FFD9), which works for both multipart/x-mixed-replace and
 * raw JPEG streams without parsing the multipart boundary.
 */
@Composable
private fun MjpegPreview(urls: List<String>, modifier: Modifier = Modifier) {
    var frame by remember { mutableStateOf<Bitmap?>(null) }

    DisposableEffect(Unit) {
        val client = OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
        val job = kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            for (u in urls) {
                try {
                    client.newCall(Request.Builder().url(u).build()).execute().use { resp ->
                        if (!resp.isSuccessful) return@use
                        val body = resp.body ?: return@use
                        scanJpegFrames(body.byteStream()) { bmp -> frame = bmp }
                    }
                } catch (t: Throwable) {
                    // Try the next candidate URL.
                }
                currentCoroutineContext().ensureActive()
            }
        }
        onDispose { job.cancel() }
    }

    Box(modifier = modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        val cur = frame
        if (cur != null) {
            Image(bitmap = cur.asImageBitmap(), contentDescription = "Live preview", modifier = Modifier.fillMaxSize())
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp))
                Spacer(Modifier.size(12.dp))
                Text("Connecting preview…", color = Color.White, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Stream-scanner emitting one callback per complete JPEG (SOI..EOI). */
private suspend fun scanJpegFrames(stream: InputStream, onFrame: (Bitmap) -> Unit) =
    withContext(Dispatchers.IO) {
        val acc = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(4096)
        var inFrame = false
        var prev = 0
        while (true) {
            val n = stream.read(chunk)
            if (n < 0) break
            var i = 0
            while (i < n) {
                val b = chunk[i].toInt() and 0xFF
                if (!inFrame && prev == 0xFF && b == 0xD8) {
                    inFrame = true
                    acc.reset()
                    acc.write(0xFF)
                    acc.write(0xD8)
                } else if (inFrame) {
                    acc.write(b)
                    if (prev == 0xFF && b == 0xD9) {
                        val bytes = acc.toByteArray()
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let(onFrame)
                        inFrame = false
                    }
                }
                prev = b
                i++
            }
            currentCoroutineContext().ensureActive()
        }
    }
