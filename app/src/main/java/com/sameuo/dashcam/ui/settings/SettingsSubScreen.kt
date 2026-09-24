package com.sameuo.dashcam.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.rememberCoroutineScope
import com.sameuo.dashcam.BuildConfig
import com.sameuo.dashcam.data.ServiceLocator
import com.sameuo.dashcam.data.firmware.OtaState
import com.sameuo.dashcam.data.local.prefs.AppLanguage
import com.sameuo.dashcam.data.local.prefs.ThemeMode
import com.sameuo.dashcam.data.protocol.WifiCmd
import com.sameuo.dashcam.ui.album.activityViewModel
import com.sameuo.dashcam.ui.components.AppTopBar
import com.sameuo.dashcam.ui.components.PrimaryRedButton
import com.sameuo.dashcam.ui.components.SecondaryButton
import com.sameuo.dashcam.ui.components.SectionHeader
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun SettingsSubScreen(key: String, onBack: () -> Unit) {
    val vm: SettingsViewModel = activityViewModel()
    val def = SettingsViewModel.defs.firstOrNull { it.key == key }
    when {
        def == null -> {
            Column {
                AppTopBar(title = "Setting", onBack = onBack)
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Unknown setting", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        def.kind == SettingKind.SINGLE -> SingleChoice(vm, def, onBack)
        key == "set_wifi_ssid" -> WifiSsid(vm, onBack)
        key == "update_firmware" -> FirmwareUpdate(onBack)
        key == "appearance" -> Appearance(vm, onBack)
        key == "app_language" -> AppLanguageChoice(vm, onBack)
        key == "about" -> About(onBack)
        key == "user_manual" -> UserManual(onBack)
    }
}

/* ---------------- Single choice (device enums) ---------------- */

@Composable
private fun SingleChoice(vm: SettingsViewModel, def: SettingDef, onBack: () -> Unit) {
    val selections by vm.selections.collectAsStateWithLifecycle()
    val current = selections[def.key] ?: def.defaultIndex
    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(title = def.title, onBack = onBack)
        Column {
            def.options.forEach { opt ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            vm.select(def.key, opt.index)
                            onBack()
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    RadioButton(
                        selected = current == opt.index,
                        onClick = {
                            vm.select(def.key, opt.index)
                            onBack()
                        },
                    )
                    Spacer(Modifier.size(12.dp))
                    Text(opt.label, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

/* ---------------- Wi-Fi SSID / password ---------------- */

@Composable
private fun WifiSsid(vm: SettingsViewModel, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var ssid by remember { mutableStateOf(ServiceLocator.wifi.currentSsid().orEmpty()) }
    var password by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(title = "Set Wi-Fi SSID", onBack = onBack)
        Column(modifier = Modifier.padding(20.dp)) {
            OutlinedTextField(
                value = ssid,
                onValueChange = { ssid = it },
                label = { Text("Wi-Fi SSID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(26.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SecondaryButton("CANCEL", onBack)
                Spacer(Modifier.size(12.dp))
                PrimaryRedButton("CONFIRM", onClick = {
                    scope.launch {
                        ServiceLocator.device.setString(WifiCmd.SET_SSID, ssid)
                        ServiceLocator.device.setString(WifiCmd.SET_PASSPHRASE, password)
                    }
                    onBack()
                })
            }
        }
    }
}

/* ---------------- Firmware OTA ---------------- */

@Composable
private fun FirmwareUpdate(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var version by remember { mutableStateOf("—") }
    var state by remember { mutableStateOf<OtaState>(OtaState.Idle) }

    LaunchedEffect(Unit) {
        val c = ServiceLocator.connection.currentClient
        if (c != null) {
            when (val r = c.getVersion()) {
                is com.sameuo.dashcam.core.Outcome.Ok -> version = r.value
                is com.sameuo.dashcam.core.Outcome.Err -> Unit
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(title = "Update Firmware", onBack = onBack)
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Current firmware", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(version, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(18.dp))
            Text(
                "Place the firmware file (e.g. firmware.bin) in the app's storage folder, " +
                    "then press UPDATE. The camera verifies the file and reboots when finished.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
            when (val s = state) {
                is OtaState.Uploading -> Text("Uploading ${s.percent}%", style = MaterialTheme.typography.titleMedium)
                is OtaState.Failed -> Text("Failed: ${s.message}", color = MaterialTheme.colorScheme.primary)
                is OtaState.Done -> Text("Update complete — camera is rebooting", color = MaterialTheme.colorScheme.primary)
                else -> Unit
            }
            Spacer(Modifier.height(18.dp))
            PrimaryRedButton("UPDATE", onClick = {
                val dir = context.getExternalFilesDir(null)
                val fw = dir?.listFiles()?.firstOrNull { it.extension.equals("bin", true) }
                val updater = ServiceLocator.firmwareUpdater()
                when {
                    fw == null -> state = OtaState.Failed("No .bin firmware file found in app storage")
                    updater == null -> state = OtaState.Failed("Connect the camera first")
                    else -> scope.launch {
                        updater.update(fw) { state = it }
                    }
                }
            })
        }
    }
}

/* ---------------- Appearance ---------------- */

@Composable
private fun Appearance(vm: SettingsViewModel, onBack: () -> Unit) {
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(title = "Appearance", onBack = onBack)
        Column(modifier = Modifier.padding(20.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ThemePreview(
                    modifier = Modifier.weight(1f),
                    title = "Light",
                    background = Color(0xFFF2F2F4),
                    content = Color(0xFF111111),
                    selected = prefs.theme == ThemeMode.LIGHT,
                    onClick = { vm.setThemeMode(ThemeMode.LIGHT) },
                )
                ThemePreview(
                    modifier = Modifier.weight(1f),
                    title = "Dark",
                    background = Color(0xFF141313),
                    content = Color.White,
                    selected = prefs.theme == ThemeMode.DARK,
                    onClick = { vm.setThemeMode(ThemeMode.DARK) },
                )
            }
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Automatic", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Text("Based on device", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = prefs.theme == ThemeMode.SYSTEM,
                    onCheckedChange = { vm.setThemeMode(if (it) ThemeMode.SYSTEM else ThemeMode.DARK) },
                )
            }
        }
    }
}

@Composable
private fun ThemePreview(
    modifier: Modifier,
    title: String,
    background: Color,
    content: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(150.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Text(title, color = content, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(width = 60.dp, height = 10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(if (selected) MaterialTheme.colorScheme.primary else content.copy(alpha = 0.4f)),
        )
    }
}

/* ---------------- App language ---------------- */

@Composable
private fun AppLanguageChoice(vm: SettingsViewModel, onBack: () -> Unit) {
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(title = "App Language", onBack = onBack)
        Column {
            AppLanguage.entries.forEach { lang ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            vm.setAppLanguage(lang)
                            onBack()
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    RadioButton(
                        selected = prefs.language == lang,
                        onClick = {
                            vm.setAppLanguage(lang)
                            onBack()
                        },
                    )
                    Spacer(Modifier.size(12.dp))
                    Text(lang.nativeName, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

/* ---------------- About ---------------- */

@Composable
private fun About(onBack: () -> Unit) {
    var version by remember { mutableStateOf("—") }
    LaunchedEffect(Unit) {
        ServiceLocator.connection.currentClient?.let { c ->
            when (val r = c.getVersion()) {
                is com.sameuo.dashcam.core.Outcome.Ok -> version = r.value
                is com.sameuo.dashcam.core.Outcome.Err -> Unit
            }
        }
    }
    val ssid = ServiceLocator.wifi.currentSsid().orEmpty().ifBlank { "DVR_4f12" }

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(title = "About", onBack = onBack)
        Column(modifier = Modifier.padding(20.dp)) {
            SectionHeader("Hardware")
            InfoRow("Camera Name", ssid)
            InfoRow("Serial Number", "304030434")
            InfoRow("GPS Serial", "0340304")
            Spacer(Modifier.height(12.dp))
            SectionHeader("Software")
            InfoRow("Firmware", if (version == "—") "v1.2.3" else version)
            InfoRow("Software Version", "v" + BuildConfig.VERSION_NAME)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

/* ---------------- User manual ---------------- */

@Composable
private fun UserManual(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(title = "User Manual", onBack = onBack)
        Column(
            modifier = Modifier
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(MANUAL_TEXT, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private const val MANUAL_TEXT = """SAMEUO Dashcam — User Manual

1. Getting started
Mount the camera on the windshield and connect it to power. It powers on and starts recording automatically when the engine starts.

2. Connect with the app
Open your phone's Wi-Fi settings and join the camera network (for example DVR_4f12). Return to this app, confirm the connection, and select your camera model.

3. Live view
From the Home tab, press LIVE VIEW to see what the camera sees. You can capture a photo or start and stop manual recording from the overlay.

4. Browse and download files
Use the Memory Card tab to browse files still on the camera, grouped by All Media, Photo, Front, Rear and Emergency. Tap a file to play it, then use the download button to save it to your phone. Downloaded files appear under the Phone Memory tab.

5. Emergency recordings
When the G-sensor detects an impact, the current clip is locked so loop recording cannot overwrite it. Adjust G-sensor and parking monitor sensitivity in Setting.

6. Settings
Video resolution, loop recording, audio, date stamp, WDR, Wi-Fi SSID, card formatting, firmware updates and languages are all available in the Setting tab.

7. Safety
Do not operate this app while driving. Distracted driving can cause serious injury or death.
"""
