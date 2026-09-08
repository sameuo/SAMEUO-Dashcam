package com.sameuo.dashcam.ui.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sameuo.dashcam.data.repository.ConnectionState
import com.sameuo.dashcam.ui.components.SectionCard
import com.sameuo.dashcam.ui.components.SectionLabel
import com.sameuo.dashcam.ui.components.StatPill
import com.sameuo.dashcam.ui.navigation.Routes
import com.sameuo.dashcam.ui.sameuoVm

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeviceScreen(onNavigate: (String) -> Unit) {
    val vm = sameuoVm { DeviceViewModel() }
    val state by vm.connectionState.collectAsStateWithLifecycle()
    val chip by vm.selectedChip.collectAsStateWithLifecycle()
    val host by vm.host.collectAsStateWithLifecycle()
    val saved by vm.savedDevices.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("SAMEUO Dashcam", style = MaterialTheme.typography.headlineMedium)
        when (val s = state) {
            is ConnectionState.Connected -> ConnectedCard(s, onPreview = { onNavigate(Routes.PREVIEW) },
                onSettings = { onNavigate(Routes.DEVICE_SETTINGS) }, onDisconnect = vm::disconnect)
            ConnectionState.Connecting -> SectionCard {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.height(24.dp))
                    Spacer(Modifier.height(0.dp))
                    Text("  Connecting to ${chip.displayName.substringBefore(' ')} …")
                }
            }
            is ConnectionState.Failed -> SectionCard {
                Text("Connection failed", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                Text(s.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Button(onClick = vm::bindAndConnect, modifier = Modifier.fillMaxWidth()) { Text("Retry") }
            }
            ConnectionState.Disconnected -> DisconnectedCard(
                chip = chip.name,
                host = host,
                savedNames = saved,
                onChip = vm::selectChip,
                onHost = vm::setHost,
                onConnect = vm::bindAndConnect,
                onSaved = { d -> vm.connectTo(com.sameuo.dashcam.data.protocol.chip.ChipPlatform.fromName(d.chip), d.host) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DisconnectedCard(
    chip: String,
    host: String,
    savedNames: List<com.sameuo.dashcam.data.local.db.SavedDevice>,
    onChip: (com.sameuo.dashcam.data.protocol.chip.ChipPlatform) -> Unit,
    onHost: (String) -> Unit,
    onConnect: () -> Unit,
    onSaved: (com.sameuo.dashcam.data.local.db.SavedDevice) -> Unit,
) {
    SectionCard {
        SectionLabel("1 · Choose chipset")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            com.sameuo.dashcam.data.protocol.chip.ChipPlatform.supported().forEach { p ->
                FilterChip(
                    selected = p.name == chip,
                    onClick = { onChip(p) },
                    label = { Text(p.name) },
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        SectionLabel("2 · Join the camera Wi-Fi, then connect")
        Text(
            "Open system Wi-Fi settings and join the SAMEUO hotspot (password on the mount). Return here and tap Connect — pairing takes about 3 seconds.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(value = host, onValueChange = onHost, label = { Text("Device gateway") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(14.dp))
        Button(onClick = onConnect, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Connect", fontWeight = FontWeight.SemiBold) }
    }

    if (savedNames.isNotEmpty()) {
        SectionCard {
            SectionLabel("Recent devices")
            savedNames.forEach { d ->
                TextButton(onClick = { onSaved(d) }, modifier = Modifier.fillMaxWidth()) {
                    Text("${d.name}  ·  ${d.host}")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConnectedCard(
    s: ConnectionState.Connected,
    onPreview: () -> Unit,
    onSettings: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val st = s.status
    SectionCard {
        Text("Connected", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        Text("${s.chip.name} · ${s.host}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatPill("Battery", st?.battery?.name ?: "—")
            StatPill("Free space", st?.freeSpaceBytes?.takeIf { it >= 0 }?.let { humanSize(it) } ?: "—")
            StatPill("Mode", st?.operationMode?.name ?: "—")
            StatPill("SD card", st?.card?.name ?: "—")
        }
    }
    SectionCard {
        Button(onClick = onPreview, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Icon(Icons.Filled.Videocam, null); Text("   Live preview & control")
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onSettings, modifier = Modifier.fillMaxWidth().height(50.dp)) {
            Icon(Icons.Filled.Settings, null); Text("   Device settings")
        }
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) { Text("Disconnect") }
    }
}

internal fun humanSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var v = bytes.toDouble(); var i = 0
    while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
    return "%.1f %s".format(v, units[i])
}
