package com.sameuo.dashcam.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sameuo.dashcam.BuildConfig
import com.sameuo.dashcam.data.download.DownloadStatus
import com.sameuo.dashcam.data.local.prefs.AppLanguage
import com.sameuo.dashcam.data.local.prefs.ThemeMode
import com.sameuo.dashcam.ui.components.SectionCard
import com.sameuo.dashcam.ui.components.SectionLabel
import com.sameuo.dashcam.ui.sameuoVm

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen() {
    val vm = sameuoVm { ProfileViewModel() }
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val tasks by vm.downloadTasks.collectAsStateWithLifecycle()
    var langOpen by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Me", style = MaterialTheme.typography.headlineMedium)

        SectionCard {
            SectionLabel("Appearance")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { m ->
                    FilterChip(selected = prefs.theme == m, onClick = { vm.setTheme(m) },
                        label = { Text(m.name.lowercase().replaceFirstChar { it.uppercase() }) })
                }
            }
        }

        SectionCard {
            SectionLabel("Language")
            FilterChip(selected = true, onClick = { langOpen = !langOpen }, label = { Text(prefs.language.nativeName) })
            if (langOpen) {
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppLanguage.entries.forEach { l ->
                        FilterChip(selected = prefs.language == l, onClick = { vm.setLanguage(l); langOpen = false },
                            label = { Text(l.nativeName) })
                    }
                }
            }
        }

        val active = tasks.filter { it.status == DownloadStatus.RUNNING || it.status == DownloadStatus.QUEUED }
        if (active.isNotEmpty()) {
            SectionCard {
                SectionLabel("Downloads")
                active.take(4).forEach { t ->
                    Text(t.file.name, style = MaterialTheme.typography.labelMedium)
                    LinearProgressIndicator(progress = { t.progress }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                }
            }
        }

        SectionCard {
            SectionLabel("About")
            Text("SAMEUO Dashcam", style = MaterialTheme.typography.titleMedium)
            Text("Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE}) · ${BuildConfig.BUILD_TYPE}",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            Text("Default gateway ${BuildConfig.DEFAULT_DEVICE_HOST} · Novatek Gen3 protocol",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        }
    }
}
