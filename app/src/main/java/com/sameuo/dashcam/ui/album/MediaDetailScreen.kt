package com.sameuo.dashcam.ui.album

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem as M3MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.sameuo.dashcam.data.ServiceLocator
import com.sameuo.dashcam.ui.components.AppTopBar
import com.sameuo.dashcam.ui.components.ConfirmDialog
import java.io.File

@Composable
fun MediaDetailScreen(source: String, onBack: () -> Unit) {
    val vm = libraryViewModel()
    val context = LocalContext.current
    val selectedKey by vm.selectedKey.collectAsStateWithLifecycle()
    val item = vm.selectedItem
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val phone by vm.phone.collectAsStateWithLifecycle()

    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }

    // Download task for the active device file (if any).
    val activeTask = item?.deviceFile?.let { df ->
        tasks.firstOrNull { it.file.path == df.path }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(
            title = item?.name?.take(28).orEmpty(),
            onBack = onBack,
            actions = {
                IconButton(onClick = {
                    shareActive(vm, context) { msg -> toast = msg }
                }) {
                    Icon(Icons.Filled.Share, contentDescription = "Share", tint = MaterialTheme.colorScheme.onBackground)
                }
                IconButton(onClick = {
                    if (item?.deviceFile != null) vm.downloadActive()
                    else toast = "Already on your phone"
                }) {
                    Icon(Icons.Filled.Download, contentDescription = "Download", tint = MaterialTheme.colorScheme.onBackground)
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = MaterialTheme.colorScheme.onBackground)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Sharing") },
                            onClick = {
                                menuOpen = false
                                shareActive(vm, context) { msg -> toast = msg }
                            },
                            leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                        )
                        DropdownMenuItem(
                            text = { Text("Download") },
                            onClick = {
                                menuOpen = false
                                if (item?.deviceFile != null) vm.downloadActive() else toast = "Already on your phone"
                            },
                            leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null) },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.primary) },
                            onClick = {
                                menuOpen = false
                                confirmDelete = true
                            },
                            leadingIcon = {
                                Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                        )
                    }
                }
            },
        )

        if (item == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nothing to show", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return
        }

        Box(modifier = Modifier.weight(1f)) {
            when {
                item.isVideo -> VideoPlayer(item)
                else -> ZoomablePhoto(item)
            }

            // Download overlay.
            if (activeTask != null) {
                DownloadOverlay(activeTask.progress)
            }

            // Prev / next navigation.
            IconButton(
                onClick = { vm.stepSelected(-1) },
                modifier = Modifier.align(Alignment.CenterStart),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Previous",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp),
                )
            }
            IconButton(
                onClick = { vm.stepSelected(1) },
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Next",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp),
                )
            }
        }

        // Route map panel (placeholder until a maps dependency is added).
        RouteMapPanel()

        // Timeline for video; info line for photo.
        if (item.isVideo) {
            VideoTimeline(selectedKey)
        } else {
            InfoLine(item)
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete file",
            message = "Delete ${item?.name}? For safety, do not delete files while driving.",
            confirmText = "DELETE",
            dismissText = "CANCEL",
            destructive = true,
            onConfirm = {
                confirmDelete = false
                vm.deleteActive(onDone = onBack)
            },
            onDismiss = { confirmDelete = false },
        )
    }

    toast?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(2200)
            toast = null
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            ) {
                Text(msg, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/* ---------------- Video ---------------- */

@Composable
private fun VideoPlayer(item: MediaUiItem) {
    val context = LocalContext.current
    val player = remember(item.key) {
        ExoPlayer.Builder(context).build().apply {
            val url = when {
                item.deviceFile != null -> {
                    val ep = ServiceLocator.connection.currentClient?.endpoint
                    ep?.fileDownloadUrl(item.deviceFile.path)
                }
                item.localFile != null -> item.localFile.localPath
                else -> null
            }
            if (url != null) setMediaItem(M3MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(item.key) {
        onDispose { player.release() }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = true
                this.player = player
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    )
}

@Composable
private fun VideoTimeline(key: String?) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            val bars = remember(key) {
                List(48) { i -> 8 + ((i * 37) % 26) }
            }
            bars.forEachIndexed { i, h ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(h.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (i < bars.size / 3) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Waveform timeline • use the on-video controls to seek",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/* ---------------- Photo ---------------- */

@Composable
private fun ZoomablePhoto(item: MediaUiItem) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    val model: Any? = when {
        item.deviceFile != null -> {
            val ep = ServiceLocator.connection.currentClient?.endpoint
            ep?.fileDownloadUrl(item.deviceFile.path)
        }
        item.localFile != null -> File(item.localFile.localPath)
        else -> null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(item.key) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    offsetX += pan.x
                    offsetY += pan.y
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = model,
            contentDescription = item.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offsetX,
                translationY = offsetY,
            ),
        )
    }
}

@Composable
private fun InfoLine(item: MediaUiItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(item.timeText.ifBlank { item.name }, style = MaterialTheme.typography.bodyMedium)
        Text(String.format(java.util.Locale.US, "%.1f MB", item.mb()), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RouteMapPanel() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Map, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(10.dp))
            Text(
                "Route map • GPS track",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DownloadOverlay(progress: Float) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(10.dp))
            Text("${(progress * 100).toInt()}%", color = Color.White, style = MaterialTheme.typography.titleLarge)
        }
    }
}

/* ---------------- Share ---------------- */

private fun shareActive(
    vm: LibraryViewModel,
    context: android.content.Context,
    onMessage: (String) -> Unit,
) {
    val item = vm.selectedItem ?: return
    // Phone file → share directly.
    item.localFile?.let { lf ->
        launchShare(context, File(lf.localPath))
        return
    }
    // Device file → share if a local copy exists, otherwise prompt download.
    item.deviceFile?.let { df ->
        val local = vm.phone.value.firstOrNull { it.devicePath == df.path }
        if (local != null) {
            launchShare(context, File(local.localPath))
        } else {
            vm.downloadActive()
            onMessage("Downloading first — share again when complete")
        }
    }
}

private fun launchShare(context: android.content.Context, file: File) {
    if (!file.exists()) return
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = if (file.extension.equals("jpg", true) || file.extension.equals("jpeg", true)) "image/*" else "video/*"
        putExtra(android.content.Intent.EXTRA_STREAM, uri)
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(android.content.Intent.createChooser(intent, "Share via").apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
