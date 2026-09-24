package com.sameuo.dashcam.ui.album

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sameuo.dashcam.data.ServiceLocator
import com.sameuo.dashcam.ui.components.AppTopBar
import com.sameuo.dashcam.ui.components.ConfirmDialog
import com.sameuo.dashcam.ui.components.EmptyState

@Composable
fun AlbumGridScreen(
    source: String,
    category: String,
    onBack: () -> Unit,
    openSearch: () -> Unit,
    openDetail: () -> Unit,
) {
    val vm = libraryViewModel()
    val src = MediaSource.of(source)
    val cat = MediaCategory.entries.firstOrNull { it.id == category } ?: MediaCategory.ALL
    val items = remember(src, category, vm.device.value.size, vm.phone.value.size) {
        vm.filtered(src, cat)
    }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDeleteAll by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(
            title = cat.label,
            onBack = onBack,
            actions = {
                IconButton(onClick = openSearch) {
                    Icon(Icons.Filled.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onBackground)
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = MaterialTheme.colorScheme.onBackground)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Download all") },
                            onClick = {
                                menuOpen = false
                                vm.downloadItems(items)
                            },
                            leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null) },
                        )
                        if (src == MediaSource.DEVICE) {
                            DropdownMenuItem(
                                text = { Text("Delete all", color = MaterialTheme.colorScheme.primary) },
                                onClick = {
                                    menuOpen = false
                                    confirmDeleteAll = true
                                },
                            )
                        }
                    }
                }
            },
        )

        if (items.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.PlayCircle,
                title = "No files",
                subtitle = "Files in this category will appear here.",
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
            ) {
                items(items = items, key = { it.key }) { item ->
                    MediaThumb(
                        item = item,
                        onClick = {
                            vm.openDetail(src, cat, item.key)
                            openDetail()
                        },
                    )
                }
            }
        }
    }

    if (confirmDeleteAll) {
        val totalMb = items.sumOf { it.sizeBytes }
        ConfirmDialog(
            title = "Delete All videos",
            message = "You are about to delete ${items.size} videos (${formatSize(totalMb)}). Continue? " +
                "For safety, do not delete videos while driving.",
            confirmText = "DELETE",
            dismissText = "CANCEL",
            destructive = true,
            onConfirm = {
                confirmDeleteAll = false
                vm.deleteAll()
            },
            onDismiss = { confirmDeleteAll = false },
        )
    }
}

@Composable
fun MediaThumb(item: MediaUiItem, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        val model: Any? = thumbnailModel(item)
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Filled.PlayCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(34.dp),
            )
        }
        if (item.isVideo) {
            Icon(
                Icons.Filled.PlayCircle,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(22.dp),
            )
        }
        if (item.isLocked) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            ) {
                Text("EMG", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
        // Whole-thumb tap layer.
        IconButton(
            onClick = onClick,
            modifier = Modifier.fillMaxSize(),
        ) {}
    }
}

private fun thumbnailModel(item: MediaUiItem): Any? {
    item.deviceFile?.let { df ->
        val endpoint = ServiceLocator.connection.currentClient?.endpoint ?: return null
        return endpoint.thumbnailUrl(df.path).toString()
    }
    item.localFile?.let { lf ->
        return if (lf.kind == com.sameuo.dashcam.data.protocol.model.MediaKind.PHOTO) java.io.File(lf.localPath) else null
    }
    return null
}

private fun formatSize(bytes: Long): String {
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    return if (gb >= 1) String.format(java.util.Locale.US, "%.1fGB", gb)
    else String.format(java.util.Locale.US, "%.0fMB", bytes / (1024.0 * 1024.0))
}
