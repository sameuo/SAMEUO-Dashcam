package com.sameuo.dashcam.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sameuo.dashcam.ui.album.AlbumScreen
import com.sameuo.dashcam.ui.create.CreateScreen
import com.sameuo.dashcam.ui.device.DeviceScreen
import com.sameuo.dashcam.ui.player.PlayerScreen
import com.sameuo.dashcam.ui.preview.PreviewScreen
import com.sameuo.dashcam.ui.profile.ProfileScreen
import com.sameuo.dashcam.ui.ride.RideScreen
import com.sameuo.dashcam.ui.settings.DeviceSettingsScreen
import com.sameuo.dashcam.ui.settings.FirmwareScreen

private val fullScreenRoutes = setOf(Routes.PREVIEW, Routes.DEVICE_SETTINGS, Routes.FIRMWARE, "player/")

@Composable
fun SameuoRoot() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBar = currentRoute != null && fullScreenRoutes.none { currentRoute.startsWith(it) }

    Scaffold(
        bottomBar = {
            if (showBar) NavigationBar {
                BottomTab.entries.forEach { tab ->
                    val selected = backStack?.destination?.hierarchy?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        }
    ) { pad ->
        NavHost(nav, startDestination = Routes.DEVICE, modifier = Modifier.padding(pad)) {
            composable(Routes.DEVICE) { DeviceScreen(onNavigate = nav::navigate) }
            composable(Routes.ALBUM) { AlbumScreen(onNavigate = nav::navigate) }
            composable(Routes.CREATE) { CreateScreen() }
            composable(Routes.RIDE) { RideScreen() }
            composable(Routes.PROFILE) { ProfileScreen() }

            composable(Routes.PREVIEW) { PreviewScreen(onBack = nav::popBackStack) }
            composable(Routes.DEVICE_SETTINGS) {
                DeviceSettingsScreen(onBack = nav::popBackStack, onFirmware = { nav.navigate(Routes.FIRMWARE) })
            }
            composable(Routes.FIRMWARE) { FirmwareScreen(onBack = nav::popBackStack) }
            composable(
                Routes.PLAYER,
                arguments = listOf(
                    navArgument("uri") { type = NavType.StringType },
                    navArgument("title") { type = NavType.StringType },
                ),
            ) { e ->
                PlayerScreen(
                    encodedUri = e.arguments?.getString("uri").orEmpty(),
                    title = java.net.URLDecoder.decode(e.arguments?.getString("title").orEmpty(), "UTF-8"),
                    onBack = nav::popBackStack,
                )
            }
        }
    }
}
