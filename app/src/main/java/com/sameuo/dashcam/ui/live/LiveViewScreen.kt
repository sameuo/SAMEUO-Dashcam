package com.sameuo.dashcam.ui.live

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.sameuo.dashcam.data.ServiceLocator
import kotlinx.coroutines.launch

@Composable
fun LiveViewScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val endpoint = ServiceLocator.connection.currentClient?.endpoint
    val candidates = remember { endpoint?.rtspCandidates ?: listOf("rtsp://192.168.1.254/live_rtsp") }
    var candidateIndex by remember { mutableIntStateOf(0) }
    var recording by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }

    // Make sure the device is in movie mode and streaming.
    LaunchedEffect(Unit) {
        ServiceLocator.connection.currentClient?.switchToMovieMode()
        ServiceLocator.device.setLiveView(true)
    }

    val player = remember(candidateIndex) {
        ExoPlayer.Builder(context).build().apply {
            val url = candidates[candidateIndex.coerceIn(0, candidates.lastIndex)]
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    // Try the next RTSP path candidate.
                    if (candidateIndex < candidates.lastIndex) candidateIndex++
                }
            })
        }
    }

    DisposableEffect(candidateIndex) {
        onDispose {
            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { ServiceLocator.device.setLiveView(false) }
            }
            player.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    this.player = player
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

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
