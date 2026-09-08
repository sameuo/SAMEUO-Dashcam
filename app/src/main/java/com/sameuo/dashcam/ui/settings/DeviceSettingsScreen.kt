package com.sameuo.dashcam.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sameuo.dashcam.ui.components.LoadingView
import com.sameuo.dashcam.ui.components.SectionCard
import com.sameuo.dashcam.ui.components.SectionLabel
import com.sameuo.dashcam.ui.sameuoVm

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DeviceSettingsScreen(onBack: () -> Unit, onFirmware: () -> Unit) {
    val vm = sameuoVm { DeviceSettingsViewModel() }
    val ui by vm.ui.collectAsStateWithLifecycle()

    Scaffold(topBar = {
        TopAppBar(title = { Text("Device settings") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        })
    }) { pad ->
        when {
            ui.loading -> LoadingView("Loading device menu…", Modifier.padding(pad))
            ui.menu == null -> Column(Modifier.padding(pad).padding(16.dp)) {
                Text("Menu unavailable: ${ui.error ?: "not connected"}")
                Spacer(Modifier.height(12.dp)); Button(onClick = vm::load) { Text("Retry") }
            }
            else -> Column(
                Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ui.menu!!.items.filter { it.options.isNotEmpty() }.forEach { item ->
                    SectionCard {
                        Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item.options.forEach { opt ->
                                FilterChip(
                                    selected = opt.index == item.currentIndex,
                                    onClick = { vm.choose(item, opt.index) },
                                    label = { Text(opt.id.ifBlank { "Option ${opt.index}" }) },
                                )
                            }
                        }
                    }
                }
                SectionLabel("Maintenance")
                SectionCard {
                    OutlinedButton(onClick = vm::syncTime, modifier = Modifier.fillMaxWidth()) { Text("Sync date & time") }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onFirmware, modifier = Modifier.fillMaxWidth()) { Text("Firmware update (OTA)") }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = vm::format, modifier = Modifier.fillMaxWidth()) { Text("Format SD card") }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = vm::factoryReset, modifier = Modifier.fillMaxWidth()) { Text("Factory reset") }
                }
                ui.busy?.let { Text("Working: $it …", color = MaterialTheme.colorScheme.primary) }
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}
