package com.sameuo.dashcam.ui.preview

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.sameuo.dashcam.ui.sameuoVm

@Composable
fun PreviewScreen(onBack: () -> Unit) {
    val vm = sameuoVm { PreviewViewModel() }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    val player = remember { ExoPlayer.Builder(context).build() }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) { vm.tryNextStream() }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }

    LaunchedEffect(ui.currentStream) {
        val uri = ui.currentStream ?: return@LaunchedEffect
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        player.playWhenReady = true
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    this.player = player
                    useController = true
                    setShowNextButton(false); setShowPreviousButton(false)
                }
            },
        )

        // REC indicator
        if (ui.isRecording) {
            Surface(color = MaterialTheme.colorScheme.error, shape = RoundedCornerShape(6.dp),
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp)) {
                Text("  REC  ", color = Color.White, modifier = Modifier.padding(6.dp))
            }
        }
        Text("Stream ${ui.streamIndex + 1}/${ui.streamCandidates.size.coerceAtLeast(1)}",
            color = Color.White.copy(alpha = 0.7f), modifier = Modifier.align(Alignment.TopEnd).padding(16.dp))

        // Control dock
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleCtrl(Icons.Filled.CameraAlt, "Capture") { vm.capture() }
            SmallFloatingActionButton(onClick = { vm.toggleRecording() },
                containerColor = if (ui.isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp)) {
                Icon(if (ui.isRecording) Icons.Filled.Stop else Icons.Filled.PlayArrow, "Record", modifier = Modifier.size(36.dp))
            }
            CircleCtrl(Icons.Filled.PictureInPicture, "PIP") { vm.cyclePip() }
        }

        ui.toast?.let {
            Surface(color = Color.Black.copy(alpha = 0.7f), shape = RoundedCornerShape(8.dp),
                modifier = Modifier.align(Alignment.Center)) {
                Text(it, color = Color.White, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
            }
            LaunchedEffect(it) { vm.consumeToast() }
        }
    }
}

@Composable
private fun CircleCtrl(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.15f)) {
        IconButton(onClick = onClick, modifier = Modifier.size(56.dp)) {
            Icon(icon, desc, tint = Color.White, modifier = Modifier.size(28.dp))
        }
    }
}
