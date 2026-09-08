package com.sameuo.dashcam.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sameuo.dashcam.data.firmware.OtaState
import com.sameuo.dashcam.ui.components.SectionCard
import com.sameuo.dashcam.ui.sameuoVm

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirmwareScreen(onBack: () -> Unit) {
    val vm = sameuoVm { FirmwareViewModel() }
    val context = LocalContext.current
    val file by vm.fileName.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            vm.pick(context, uri)
        }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Firmware update") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        })
    }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionCard {
                Text("1 · Choose the firmware file (.bin) supplied by SAMEUO.")
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Text(file ?: "Select firmware file")
                }
                Spacer(Modifier.height(14.dp))
                Text("2 · Keep the phone close and powered while uploading. The camera reboots itself afterwards.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(14.dp))
                Button(onClick = vm::start, modifier = Modifier.fillMaxWidth().height(50.dp),
                    enabled = state !is OtaState.Uploading) { Text("Start update") }
            }
            when (val s = state) {
                is OtaState.Uploading -> {
                    LinearProgressIndicator(progress = { s.percent / 100f }, modifier = Modifier.fillMaxWidth())
                    Text("Uploading ${s.percent}%")
                }
                is OtaState.Checking -> Text("Current version: ${s.currentVersion}")
                is OtaState.Verifying -> Text("Verifying checksum…")
                is OtaState.Done -> Text("Update delivered. The camera is applying it and will reboot.",
                    color = MaterialTheme.colorScheme.primary)
                is OtaState.Failed -> Text("Failed: ${s.message}", color = MaterialTheme.colorScheme.error)
                else -> Unit
            }
        }
    }
}
