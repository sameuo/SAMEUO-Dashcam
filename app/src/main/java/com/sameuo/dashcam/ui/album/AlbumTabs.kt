package com.sameuo.dashcam.ui.album

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraFront
import androidx.compose.material.icons.filled.CameraRear
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.sameuo.dashcam.data.ServiceLocator
import com.sameuo.dashcam.ui.components.AppTopBar
import com.sameuo.dashcam.ui.components.CategoryTile
import com.sameuo.dashcam.ui.components.EmptyState

@Composable
fun MemoryTab(
    openAlbum: (String) -> Unit,
    openSearch: () -> Unit,
) {
    val vm = libraryViewModel()
    val connected = ServiceLocator.connection.currentClient != null
    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(
            title = "Memory Card",
            actions = {
                IconButton(onClick = openSearch) {
                    Icon(Icons.Filled.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onBackground)
                }
            },
        )
        if (!connected) {
            EmptyState(
                icon = Icons.Filled.PhotoLibrary,
                title = "Camera not connected",
                subtitle = "Connect your camera from the Home tab to browse the memory card.",
            )
            return
        }
        CategoryGrid(vm, MediaSource.DEVICE, openAlbum)
    }
}

@Composable
fun PhoneTab(
    openAlbum: (String) -> Unit,
    openSearch: () -> Unit,
) {
    val vm = libraryViewModel()
    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(
            title = "Phone Memory",
            actions = {
                IconButton(onClick = openSearch) {
                    Icon(Icons.Filled.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onBackground)
                }
            },
        )
        CategoryGrid(vm, MediaSource.PHONE, openAlbum)
    }
}

@Composable
private fun CategoryGrid(
    vm: LibraryViewModel,
    source: MediaSource,
    openAlbum: (String) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        MediaCategory.entries.forEach { category ->
            item(key = category.id) {
                CategoryTile(
                    title = category.label,
                    count = vm.count(source, category),
                    icon = categoryIcon(category),
                    onClick = { openAlbum(category.id) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun categoryIcon(category: MediaCategory): ImageVector = when (category) {
    MediaCategory.ALL -> Icons.Filled.PhotoLibrary
    MediaCategory.PHOTO -> Icons.Filled.Photo
    MediaCategory.FRONT -> Icons.Filled.CameraFront
    MediaCategory.REAR -> Icons.Filled.CameraRear
    MediaCategory.EMERGENCY -> Icons.Filled.Emergency
}
