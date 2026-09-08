package com.sameuo.dashcam.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.ui.graphics.vector.ImageVector

object Routes {
    const val DEVICE = "device"
    const val ALBUM = "album"
    const val CREATE = "create"
    const val RIDE = "ride"
    const val PROFILE = "profile"

    const val PREVIEW = "preview"
    const val DEVICE_SETTINGS = "device_settings"
    const val FIRMWARE = "firmware"
    const val PLAYER = "player/{uri}/{title}"
    fun player(uri: String, title: String): String =
        "player/${java.net.URLEncoder.encode(uri, "UTF-8")}/${java.net.URLEncoder.encode(title, "UTF-8")}"
}

enum class BottomTab(val route: String, val label: String, val icon: ImageVector) {
    DEVICE(Routes.DEVICE, "Device", Icons.Filled.Sensors),
    ALBUM(Routes.ALBUM, "Album", Icons.Filled.VideoLibrary),
    CREATE(Routes.CREATE, "Create", Icons.Outlined.AutoAwesome),
    RIDE(Routes.RIDE, "Ride", Icons.AutoMirrored.Filled.DirectionsBike),
    PROFILE(Routes.PROFILE, "Me", Icons.Filled.Person),
}
