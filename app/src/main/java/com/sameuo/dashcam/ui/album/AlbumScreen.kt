package com.sameuo.dashcam.ui.album

import android.graphics.BitmapFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sameuo.dashcam.data.ServiceLocator
import com.sameuo.dashcam.data.protocol.model.DeviceMediaFile
import com.sameuo.dashcam.data.protocol.model.MediaKind
import com.sameuo.dashcam.ui.components.EmptyView
import com.sameuo.dashcam.ui.components.LoadingView
import com.sameuo.dashcam.ui.device.humanSize
import com.sameuo.dashcam.ui.navigation.Routes
import com.sameuo.dashcam.ui.sameuoVm

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalComposeUiApi::class)
@Composable
fun AlbumScreen(onNavigate: (String) -> Unit) {
    val vm = sameuoVm { AlbumViewModel() }
    val ui by vm.ui.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("On device") })
            Tab(selected = tab == 1, onClick = { tab = 1; vm.refreshLocal() }, text = { Text("On phone") })
        }
        FilterRow(ui.filter, vm::filter)

        if (ui.selectionMode && tab == 0) SelectionBar(
            count = ui.selected.size,
            onClose = vm::clearSelection, onAll = vm::selectAll,
            onDownload = vm::downloadSelected, onDelete = vm::deleteSelectedRemote,
        )

        when (tab) {
            0 -> RemoteGrid(ui, vm, onPlay = { f ->
                val ep = ServiceLocator.connection.currentClient?.endpoint
                val uri = ep?.fileDownloadUrl(f.path)
                if (uri != null) onNavigate(Routes.player(uri, f.name))
            })
            1 -> LocalGrid(ui, onPlay = { m ->
                onNavigate(Routes.player("file://${m.localPath}", m.name))
            }, onDelete = vm::deleteLocal)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterRow(current: MediaKind?, onChange: (MediaKind?) -> Unit) {
    val opts = listOf(null to "All", MediaKind.MOVIE to "Video", MediaKind.EMERGENCY to "Emergency", MediaKind.PHOTO to "Photo")
    FlowRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        opts.forEach { (kind, label) ->
            FilterChip(selected = current == kind, onClick = { onChange(kind) }, label = { Text(label) })
        }
    }
}

@Composable
private fun SelectionBar(count: Int, onClose: () -> Unit, onAll: () -> Unit, onDownload: () -> Unit, onDelete: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$count selected", Modifier.padding(start = 8.dp))
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onAll) { Text("All") }
            IconButton(onClick = onDownload) { Icon(Icons.Filled.Download, "Download") }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Delete") }
            IconButton(onClick = onClose) { Text("✕") }
        }
    }
}

@Composable
private fun RemoteGrid(ui: AlbumUi, vm: AlbumViewModel, onPlay: (DeviceMediaFile) -> Unit) {
    when {
        ui.loading && ui.remote.isEmpty() -> LoadingView("Reading camera files…")
        ui.error != null && ui.remote.isEmpty() -> EmptyView(
            "Not connected", ui.error, actionText = "Retry", onAction = vm::refreshRemote)
        filtered(ui.remote, ui.filter).isEmpty() -> EmptyView("No files on device",
            "Record something, or pull to refresh.", "Refresh", vm::refreshRemote)
        else -> LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(filtered(ui.remote, ui.filter), key = { it.path }) { f ->
                MediaTile(
                    title = f.name, subtitle = humanSize(f.sizeBytes) + " · " + f.timeText,
                    selected = f.path in ui.selected, locked = f.isLocked,
                    thumb = ui.thumbs[f.path],
                    onTap = { if (ui.selectionMode) vm.toggleSelect(f.path) else if (f.isVideo) onPlay(f) },
                    onLong = { vm.toggleSelect(f.path) },
                )
            }
        }
    }
}

@Composable
private fun LocalGrid(ui: AlbumUi, onPlay: (com.sameuo.dashcam.data.local.db.DownloadedMedia) -> Unit,
                      onDelete: (com.sameuo.dashcam.data.local.db.DownloadedMedia) -> Unit) {
    val list = ui.local.filter { ui.filter == null || it.kind == ui.filter }
    if (list.isEmpty()) EmptyView("Nothing downloaded yet",
        "Files you download from the camera appear here and are available offline.")
    else LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(list, key = { it.id }) { m ->
            MediaTile(title = m.name, subtitle = humanSize(m.sizeBytes), selected = false, locked = false, thumb = null,
                onTap = { onPlay(m) }, onLong = { onDelete(m) }, local = true)
        }
    }
}

private fun filtered(items: List<DeviceMediaFile>, kind: MediaKind?) =
    if (kind == null) items else items.filter { it.kind == kind }

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
private fun MediaTile(
    title: String, subtitle: String, selected: Boolean, locked: Boolean,
    thumb: ByteArray?, onTap: () -> Unit, onLong: () -> Unit, local: Boolean = false,
) {
    Surface(Modifier.clip(RoundedCornerShape(12.dp)).combinedClickable(onClick = onTap, onLongClick = onLong),
        color = MaterialTheme.colorScheme.surfaceVariant) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(1f).background(MaterialTheme.colorScheme.surface)) {
                val bmp = remember(thumb) { thumb?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }?.asImageBitmap() }
                if (bmp != null) Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Icon(Icons.Filled.PlayCircle, null, Modifier.align(Alignment.Center).size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                if (locked) Icon(Icons.Filled.Lock, "Emergency", Modifier.align(Alignment.TopStart).padding(6.dp).size(16.dp),
                    tint = MaterialTheme.colorScheme.error)
                Icon(if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, null,
                    Modifier.align(Alignment.TopEnd).padding(6.dp).size(20.dp),
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.padding(6.dp)) {
                Text(title, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}
