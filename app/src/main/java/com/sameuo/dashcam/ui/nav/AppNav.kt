package com.sameuo.dashcam.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PermMedia
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sameuo.dashcam.data.ServiceLocator
import com.sameuo.dashcam.data.repository.ConnectionState
import com.sameuo.dashcam.ui.album.AlbumGridScreen
import com.sameuo.dashcam.ui.album.MediaDetailScreen
import com.sameuo.dashcam.ui.album.MemoryTab
import com.sameuo.dashcam.ui.album.PhoneTab
import com.sameuo.dashcam.ui.album.SearchScreen
import com.sameuo.dashcam.ui.home.HomeTab
import com.sameuo.dashcam.ui.live.LiveViewScreen
import com.sameuo.dashcam.ui.onboarding.OnboardingFlow
import com.sameuo.dashcam.ui.settings.SettingsSubScreen
import com.sameuo.dashcam.ui.settings.SettingsTab

object Routes {
    const val ONBOARDING = "onboarding"
    const val MAIN = "main"
    const val LIVE = "live"
    const val ALBUM = "album/{source}/{category}"
    const val SEARCH = "search/{source}"
    const val DETAIL = "detail/{source}"
    const val SETTING_SUB = "setting/{key}"

    fun album(source: String, category: String) = "album/$source/$category"
    fun search(source: String) = "search/$source"
    fun detail(source: String) = "detail/$source"
    fun settingSub(key: String) = "setting/$key"
}

@Composable
fun AppNav() {
    val outer = rememberNavController()
    val start = remember {
        if (ServiceLocator.connection.state.value is ConnectionState.Connected) Routes.MAIN else Routes.ONBOARDING
    }

    NavHost(navController = outer, startDestination = start) {
        composable(Routes.ONBOARDING) {
            OnboardingFlow(
                onConnected = {
                    outer.navigate(Routes.MAIN) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
                onEnterOffline = {
                    outer.navigate(Routes.MAIN) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.MAIN) {
            MainScaffold(
                openLive = { outer.navigate(Routes.LIVE) },
                openAlbum = { source, category -> outer.navigate(Routes.album(source, category)) },
                openSearch = { source -> outer.navigate(Routes.search(source)) },
                openSetting = { key -> outer.navigate(Routes.settingSub(key)) },
                onReconnect = { outer.navigate(Routes.ONBOARDING) },
            )
        }
        composable(Routes.LIVE) { LiveViewScreen(onBack = { outer.popBackStack() }) }
        composable(Routes.ALBUM) { entry ->
            val source = entry.arguments?.getString("source") ?: "device"
            val category = entry.arguments?.getString("category") ?: "all"
            AlbumGridScreen(
                source = source,
                category = category,
                onBack = { outer.popBackStack() },
                openSearch = { outer.navigate(Routes.search(source)) },
                openDetail = { outer.navigate(Routes.detail(source)) },
            )
        }
        composable(Routes.SEARCH) { entry ->
            val source = entry.arguments?.getString("source") ?: "device"
            SearchScreen(
                source = source,
                onBack = { outer.popBackStack() },
                openDetail = { outer.navigate(Routes.detail(source)) },
            )
        }
        composable(Routes.DETAIL) { entry ->
            val source = entry.arguments?.getString("source") ?: "device"
            MediaDetailScreen(source = source, onBack = { outer.popBackStack() })
        }
        composable(Routes.SETTING_SUB) { entry ->
            val key = entry.arguments?.getString("key") ?: ""
            SettingsSubScreen(key = key, onBack = { outer.popBackStack() })
        }
    }
}

private enum class TabItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
) {
    HOME("tab_home", "Home", Icons.Filled.Home, Icons.Outlined.Home),
    MEMORY("tab_memory", "Memory Card", Icons.Filled.SdStorage, Icons.Outlined.SdStorage),
    PHONE("tab_phone", "Phone Memory", Icons.Filled.PermMedia, Icons.Outlined.PermMedia),
    SETTING("tab_setting", "Setting", Icons.Filled.Settings, Icons.Outlined.Settings),
}

@Composable
private fun MainScaffold(
    openLive: () -> Unit,
    openAlbum: (String, String) -> Unit,
    openSearch: (String) -> Unit,
    openSetting: (String) -> Unit,
    onReconnect: () -> Unit,
) {
    val tabs = rememberNavController()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { BottomBar(tabs) },
    ) { innerPadding ->
        NavHost(
            navController = tabs,
            startDestination = TabItem.HOME.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(TabItem.HOME.route) {
                HomeTab(openLive = openLive, onConnect = onReconnect)
            }
            composable(TabItem.MEMORY.route) {
                MemoryTab(
                    openAlbum = { category -> openAlbum("device", category) },
                    openSearch = { openSearch("device") },
                )
            }
            composable(TabItem.PHONE.route) {
                PhoneTab(
                    openAlbum = { category -> openAlbum("phone", category) },
                    openSearch = { openSearch("phone") },
                )
            }
            composable(TabItem.SETTING.route) {
                SettingsTab(openSetting = openSetting)
            }
        }
    }
}

@Composable
private fun BottomBar(tabs: NavHostController) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        val current = currentTabRoute(tabs)
        TabItem.entries.forEach { item ->
            val selected = current == item.route
            NavigationBarItem(
                selected = selected,
                onClick = {
                    tabs.navigate(item.route) {
                        popUpTo(tabs.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    Icon(
                        if (selected) item.selectedIcon else item.icon,
                        contentDescription = item.label,
                    )
                },
                label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.surface,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

@Composable
private fun currentTabRoute(nav: NavHostController): String? {
    val current = remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    androidx.compose.runtime.DisposableEffect(nav) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            current.value = destination.route
        }
        nav.addOnDestinationChangedListener(listener)
        onDispose { nav.removeOnDestinationChangedListener(listener) }
    }
    return current.value
}
