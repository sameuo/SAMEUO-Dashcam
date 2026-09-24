package com.sameuo.dashcam.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sameuo.dashcam.data.ServiceLocator
import com.sameuo.dashcam.ui.components.AppTopBar
import com.sameuo.dashcam.ui.components.ChevronRow
import com.sameuo.dashcam.ui.components.ConfirmDialog
import com.sameuo.dashcam.ui.components.SectionHeader
import com.sameuo.dashcam.ui.components.ToggleRow

@Composable
fun SettingsTab(openSetting: (String) -> Unit) {
    val vm: SettingsViewModel = com.sameuo.dashcam.ui.album.activityViewModel()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    var formatOpen by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(title = "Setting")
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            SettingGroup.entries.forEach { group ->
                item(key = "header_" + group.name) { SectionHeader(group.title) }
                SettingsViewModel.defs.filter { it.group == group }.forEach { def ->
                    item(key = def.key) {
                        when (def.kind) {
                            SettingKind.TOGGLE ->
                                ToggleRow(
                                    title = def.title,
                                    checked = vm.isToggleOn(def.key),
                                    onChange = { vm.toggle(def.key, it) },
                                )
                            SettingKind.SINGLE ->
                                ChevronRow(
                                    title = def.title,
                                    value = vm.displayValue(def.key),
                                    onClick = { openSetting(def.key) },
                                )
                            SettingKind.SPECIAL -> {
                                when (def.key) {
                                    "format_memory" ->
                                        ChevronRow(
                                            title = def.title,
                                            value = null,
                                            onClick = { formatOpen = true },
                                        )
                                    "update_firmware" ->
                                        ChevronRow(
                                            title = def.title,
                                            value = null,
                                            badge = 1,
                                            onClick = { openSetting(def.key) },
                                        )
                                    else ->
                                        ChevronRow(
                                            title = def.title,
                                            value = specialValue(def.key, prefs),
                                            onClick = { openSetting(def.key) },
                                        )
                                }
                            }
                        }
                    }
                }
            }
            item(key = "bottom_space") { Box(Modifier.padding(24.dp)) }
        }
    }

    if (formatOpen) {
        ConfirmDialog(
            title = "Format / Erase Memory Card",
            message = "All files on the memory card will be permanently erased. Continue? " +
                "For safety, do not format the card while driving.",
            confirmText = "FORMAT",
            dismissText = "CANCEL",
            destructive = true,
            onConfirm = {
                formatOpen = false
                vm.formatCard { toast = it }
            },
            onDismiss = { formatOpen = false },
        )
    }

    toast?.let { msg ->
        androidx.compose.runtime.LaunchedEffect(msg) {
            kotlinx.coroutines.delay(2200)
            toast = null
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface, androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            ) { Text(msg, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

private fun specialValue(key: String, prefs: com.sameuo.dashcam.data.local.prefs.AppPrefs): String? = when (key) {
    "set_wifi_ssid" -> ServiceLocator.wifi.currentSsid()
    "appearance" -> prefs.theme.name.lowercase().replaceFirstChar { it.uppercase() }
    "app_language" -> prefs.language.nativeName
    else -> null
}
