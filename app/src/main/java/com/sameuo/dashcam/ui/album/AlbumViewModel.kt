package com.sameuo.dashcam.ui.album

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sameuo.dashcam.core.Outcome
import com.sameuo.dashcam.data.ServiceLocator
import com.sameuo.dashcam.data.local.db.DownloadedMedia
import com.sameuo.dashcam.data.protocol.model.DeviceMediaFile
import com.sameuo.dashcam.data.protocol.model.MediaKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AlbumTab { REMOTE, LOCAL }

data class AlbumUi(
    val loading: Boolean = false,
    val error: String? = null,
    val remote: List<DeviceMediaFile> = emptyList(),
    val local: List<DownloadedMedia> = emptyList(),
    val filter: MediaKind? = null,
    val selected: Set<String> = emptySet(),   // keyed by device path
    val selectionMode: Boolean = false,
    val thumbs: Map<String, ByteArray> = emptyMap(),
    val busyMessage: String? = null,
)

class AlbumViewModel : ViewModel() {
    private val device = ServiceLocator.device
    private val local = ServiceLocator.localMedia

    private val _ui = MutableStateFlow(AlbumUi())
    val ui: StateFlow<AlbumUi> = _ui.asStateFlow()

    val downloads get() = local.tasks

    init { refreshRemote(); refreshLocal() }

    fun refreshRemote() {
        _ui.value = _ui.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val files = when (val r = device.refreshFiles()) {
                is Outcome.Ok -> { _ui.value = _ui.value.copy(loading = false, remote = r.value); r.value }
                is Outcome.Err -> { _ui.value = _ui.value.copy(loading = false, error = r.cause.message); return@launch }
            }
            files.filter { it.kind == MediaKind.MOVIE || it.kind == MediaKind.EMERGENCY }.take(24).forEach { loadThumb(it) }
        }
    }

    fun refreshLocal() = viewModelScope.launch {
        _ui.value = _ui.value.copy(local = local.localMedia())
    }

    fun filter(kind: MediaKind?) { _ui.value = _ui.value.copy(filter = kind) }

    fun toggleSelect(path: String) = _ui.update { s ->
        val next = s.selected.toMutableSet().apply { if (!add(path)) remove(path) }
        s.copy(selected = next, selectionMode = next.isNotEmpty())
    }

    fun selectAll() = _ui.update { it.copy(selected = it.remote.map { f -> f.path }.toSet(), selectionMode = true) }
    fun clearSelection() = _ui.update { it.copy(selected = emptySet(), selectionMode = false) }

    fun downloadSelected() {
        val chosen = _ui.value.remote.filter { it.path in _ui.value.selected }
        local.download(chosen)
        _ui.value = _ui.value.copy(busyMessage = "Downloading ${chosen.size} file(s) in background")
        clearSelection()
    }

    fun deleteSelectedRemote() = viewModelScope.launch {
        _ui.value.remote.filter { it.path in _ui.value.selected }.forEach { device.delete(it) }
        clearSelection(); refreshRemote()
    }

    fun deleteLocal(item: DownloadedMedia) = viewModelScope.launch {
        local.deleteLocal(item); refreshLocal()
    }

    fun loadThumb(file: DeviceMediaFile) {
        if (_ui.value.thumbs.containsKey(file.path)) return
        viewModelScope.launch {
            val bytes = device.thumbnail(file) ?: return@launch
            _ui.update { it.copy(thumbs = it.thumbs + (file.path to bytes)) }
        }
    }

    fun consumeBusy() { _ui.value = _ui.value.copy(busyMessage = null) }
}
