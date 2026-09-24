package com.sameuo.dashcam.ui.album

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sameuo.dashcam.ui.components.AppTopBar
import com.sameuo.dashcam.ui.components.EmptyState
import com.sameuo.dashcam.ui.components.PrimaryRedButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Opt-in(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    source: String,
    onBack: () -> Unit,
    openDetail: () -> Unit,
) {
    val vm = libraryViewModel()
    val src = MediaSource.of(source)
    val filters by vm.filters.collectAsStateWithLifecycle()
    val results by vm.searchResults.collectAsStateWithLifecycle()
    val hasSearched by vm.hasSearched.collectAsStateWithLifecycle()

    var pickFrom by remember { mutableStateOf(false) }
    var pickTo by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(title = "Search", onBack = onBack)

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            CheckRow("Video", filters.video) { v -> vm.setFilters { it.copy(video = v) } }
            CheckRow("Emergency", filters.emergency) { v -> vm.setFilters { it.copy(emergency = v) } }
            CheckRow("Photo", filters.photo) { v -> vm.setFilters { it.copy(photo = v) } }

            Spacer(Modifier.height(8.dp))
            DateRow("From", filters.fromMs) { pickFrom = true }
            Spacer(Modifier.height(8.dp))
            DateRow("To", filters.toMs) { pickTo = true }

            Spacer(Modifier.height(18.dp))
            PrimaryRedButton("START", onClick = { vm.runSearch(src) })
            Spacer(Modifier.height(14.dp))
        }

        if (hasSearched) {
            Text(
                "SEARCH RESULTS (${results.size})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, bottom = 6.dp),
            )
            if (results.isEmpty()) {
                EmptyState(icon = Icons.Filled.CalendarMonth, title = "No matching files")
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(items = results, key = { it.key }) { item ->
                        MediaThumb(item = item, onClick = {
                            vm.openSearchDetail(item.key)
                            openDetail()
                        })
                    }
                }
            }
        }
    }

    if (pickFrom) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { pickFrom = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        // Start of selected day 00:00.
                        vm.setFilters { f -> f.copy(fromMs = ms) }
                    }
                    pickFrom = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickFrom = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }

    if (pickTo) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { pickTo = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        // End of selected day 23:59:59 → add just under 24h.
                        vm.setFilters { f -> f.copy(toMs = ms + 24L * 60 * 60 * 1000 - 1) }
                    }
                    pickTo = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickTo = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Checkbox(
            checked = checked,
            onCheckedChange = onChange,
            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
        )
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun DateRow(label: String, ms: Long?, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                ms?.let { formatDate(it) } ?: "Any",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatDate(ms: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(ms))
