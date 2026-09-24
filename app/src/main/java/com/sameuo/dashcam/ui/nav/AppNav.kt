package com.sameuo.dashcam.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sameuo.dashcam.ui.album.AlbumGridScreen
import com.sameuo.dashcam.ui.album.AlbumTabs
import com.sameuo.dashcam.ui.album.MediaDetailScreen
import com.sameuo.dashcam.ui.album.SearchScreen
import com.sameuo.dashcam.ui.home.HomeTab
import com.sameuo.dashcam.ui.live.LiveViewScreen
import com.sameuo.dashcam.ui.onboarding.OnboardingFlow
import com.sameuo.dashcam.ui.settings.SettingsSubScreen
import com.sameuo.dashcam.ui.settings.SettingsTab

private enum class BottomTab(val route: String, val label: String, val icon: ImageVector) {
    HOME("tab_home", "Home", Icons.Filled.Home),
    MEMORY("tab_memory", "Memory Card", Icons.Filled.PhotoLibrary),
    PHONE("tab_phone", "Phone Memory", Icons.Filled.Smartphone),
    SETTING("tab_setting", "Setting", Icons.Filled.Settings),
}

@Composable
fun AppNav(root: NavHostController = rememberNavController()) {
    NavHost(navController = root, startDestination = "onboarding") {

        composable("onboarding") {
            OnboardingFlow(
                onConnected = {
                    root.navigate("main") {
                        popUpTo("onboarding") { inclusive = true }
                    }
                },
                onEnterOffline = {
                    root.navigate("main") {
                        popUpTo("onboarding") { inclusive = true }
                    }
                },
            )
        }

        composable("main") { MainScaffold(root) }

        composable("live") {
            LiveViewScreen(onBack = { root.popBackStack() })
        }

        composable("album/{source}/{category}") { backStackEntry ->
            val source = backStackEntry.arguments?.getString("source").orEmpty()
            val category = backStackEntry.arguments?.getString("category").orEmpty()
            AlbumGridScreen(
                source = source,
                category = category,
                onBack = { root.popBackStack() },
                onOpenSearch = { root.navigate("search/$source") },
                onOpenItem = { key -> root.navigate("detail/$source/$key") },
            )
        }

        composable("search/{source}") { backStackEntry ->
            val source = backStackEntry.arguments?.getString("source").orEmpty()
            SearchScreen(
                source = source,
                onBack = { root.popBackStack() },
                onOpenItem = { key -> root.navigate("detail/$source/$key") },
            )
        }

        composable("detail/{source}/{key}") { backStackEntry ->
            val source = backStackEntry.arguments?.getString("source").orEmpty()
            val key = backStackEntry.arguments?.getString("key").orEmpty()
            MediaDetailScreen(
                source = source,
                initialKey = key,
                onBack = { root.popBackStack() },
            )
        }

        composable("setting/{key}") { backStackEntry ->
            val key = backStackEntry.arguments?.getString("key").orEmpty()
            SettingsSubScreen(
                settingKey = key,
                onBack = { root.popBackStack() },
            )
        }
    }
}

@Composable
private fun MainScaffold(root: NavHostController) {
    val tabs = remember { BottomTab.entries }
    val tabsNav = rememberNavController()
    var currentTabRoute by remember { mutableStateOf(BottomTab.HOME.route) }

    androidx.compose.runtime.DisposableEffect(tabsNav) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            destination.route?.let { currentTabRoute = it }
        }
        tabsNav.addOnDestinationChangedListener(listener)
        onDispose { tabsNav.removeOnDestinationChangedListener(listener) }
    }

    Scaffold(
        bottomBar = {
            BottomBar(
                tabs = tabs,
                currentRoute = currentTabRoute,
                onSelect = { tab ->
                    tabsNav.navigate(tab.route) {
                        popUpTo(tabsNav.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            NavHost(navController = tabsNav, startDestination = BottomTab.HOME.route) {
                composable(BottomTab.HOME.route) {
                    HomeTab(
                        openLive = { root.navigate("live") },
                        onConnect = { root.navigate("onboarding") },
                    )
                }
                composable(BottomTab.MEMORY.route) {
                    AlbumTabs(
                        source = "DEVICE",
                        onOpenCategory = { category -> root.navigate("album/DEVICE/$category") },
                        onOpenSearch = { root.navigate("search/DEVICE") },
                    )
                }
                composable(BottomTab.PHONE.route) {
                    AlbumTabs(
                        source = "PHONE",
                        onOpenCategory = { category -> root.navigate("album/PHONE/$category") },
                        onOpenSearch = { root.navigate("search/PHONE") },
                    )
                }
                composable(BottomTab.SETTING.route) {
                    SettingsTab(onOpen = { key -> root.navigate("setting/$key") })
                }
            }
        }
    }
}

@Composable
private fun BottomBar(
    tabs: List<BottomTab>,
    currentRoute: String,
    onSelect: (BottomTab) -> Unit,
) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        tabs.forEach { tab ->
            NavigationBarItem(
                selected = currentRoute == tab.route,
                onClick = { onSelect(tab) },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}
