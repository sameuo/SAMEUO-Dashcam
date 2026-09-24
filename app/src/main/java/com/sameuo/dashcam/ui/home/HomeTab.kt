package com.sameuo.dashcam.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sameuo.dashcam.data.ServiceLocator
import com.sameuo.dashcam.data.repository.ConnectionState
import com.sameuo.dashcam.ui.components.EmptyState
import com.sameuo.dashcam.ui.components.PrimaryRedButton
import com.sameuo.dashcam.ui.components.SecondaryButton

@Composable
fun HomeTab(openLive: () -> Unit, onConnect: () -> Unit) {
    val conn by ServiceLocator.connection.state.collectAsStateWithLifecycle()
    val connected = conn as? ConnectionState.Connected

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
    ) {
        if (connected == null) {
            DisconnectedHome(onConnect)
            return
        }
        Spacer(Modifier.height(24.dp))
        DeviceCard(connected)
        Spacer(Modifier.height(18.dp))
        StatusGrid(connected)
        Spacer(Modifier.weight(1f))
        PrimaryRedButton(
            text = "LIVE VIEW",
            onClick = openLive,
            leadingIcon = Icons.Filled.PlayArrow,
        )
        Spacer(Modifier.height(12.dp))
        SecondaryButton("DISCONNECT", onClick = { ServiceLocator.connection.disconnect() })
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun DisconnectedHome(onConnect: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(Modifier.height(40.dp))
        EmptyState(
            icon = Icons.Filled.Videocam,
            title = "No camera connected",
            subtitle = "Connect your SAMEUO dashcam over Wi-Fi to start live view and browse files.",
        )
        Spacer(Modifier.weight(1f))
        PrimaryRedButton("CONNECT CAMERA", onConnect)
    }
}

@Composable
private fun DeviceCard(connected: ConnectionState.Connected) {
    val ssid = ServiceLocator.wifi.currentSsid().orEmpty()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(androidx.compose.material3.MaterialTheme.colorScheme.surface)
            .padding(22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(androidx.compose.material3.MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Videocam, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.size(18.dp))
            Column {
                Text(
                    ssid.ifBlank { "SAMEUO Dashcam" },
                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(androidx.compose.material3.MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        "Connected",
                        color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusGrid(connected: ConnectionState.Connected) {
    val status = connected.status
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatusPill(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.BatteryFull,
            label = "Battery",
            value = status?.battery?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "—",
        )
        StatusPill(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.SdStorage,
            label = "SD Card",
            value = status?.card?.name?.let { "Inserted" } ?: "—",
        )
    }
    Spacer(Modifier.height(12.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatusPill(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.SdStorage,
            label = "Free Space",
            value = status?.freeSpaceBytes?.takeIf { it >= 0 }?.let { formatGb(it) } ?: "—",
        )
        StatusPill(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.Videocam,
            label = "Recording",
            value = if (status?.isRecording == true) "Yes" else "No",
        )
    }
}

@Composable
private fun StatusPill(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(androidx.compose.material3.MaterialTheme.colorScheme.surface)
            .padding(16.dp),
    ) {
        Column {
            Icon(
                icon, contentDescription = null,
                tint = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(value, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                label,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatGb(bytes: Long): String {
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    return String.format(java.util.Locale.US, "%.1f GB", gb)
}
