package com.sameuo.dashcam.ui.album

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sameuo.dashcam.core.AppLog
import com.sameuo.dashcam.core.Outcome
import com.sameuo.dashcam.data.ServiceLocator
import com.sameuo.dashcam.data.download.DownloadTask
import com.sameuo.dashcam.data.local.db.DownloadedMedia
import com.sameuo.dashcam.data.protocol.model.CameraChannel
import com.sameuo.dashcam.data.protocol.model.DeviceMediaFile
import com.sameuo.dashcam.data.protocol.model.MediaKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class MediaSource { DEVICE, PHONE;
    val id get() = if (this == DEVICE) "device" else "phone"
    companion object { fun of(s: String) = if (s == "phone") PHONE else DEVICE }
}

enum class MediaCategory(val id: String, val label: String) {
    ALL("all", "All Media"),
    PHOTO("photo", "Photo"),
    FRONT("front", "Front"),
    REAR("rear", "Rear"),
    EMERGENCY("emergency", "Emergency"),
}

/** UI-neutral media item that can come from the camera or the phone. */
data class MediaUiItem(
    val key: String,
    val name: String,
    val timeText: String,
    val timeMs: Long,
    val isVideo: Boolean,
    val kind: MediaKind,
    val channel: CameraChannel,
    val sizeBytes: Long,
    val deviceFile: DeviceMediaFile?,
    val localFile: DownloadedMedia?,
) {
    val isLocked: Boolean get() = deviceFile?.isLocked == true
    fun mb(): Double = sizeBytes / (1024.0 * 1024.0)
}

private fun DeviceMediaFile.toUi(): MediaUiItem = MediaUiItem(
    key = "d:" + path,
    name = name,
    timeText = timeText,
    timeMs = if (timecode > 0) timecode * 1000L else 0L,
    isVideo = isVideo,
    kind = kind,
    channel = channel,
    sizeBytes = sizeBytes,
    deviceFile = this,
    localFile = null,
)

private fun DownloadedMedia.toUi(): MediaUiItem = MediaUiItem(
    key = "l:" + devicePath + ":" + id,
    name = name,
    timeText = timeText,
    timeMs = downloadedAt,
    isVideo = kind == MediaKind.MOVIE || kind == MediaKind.EMERGENCY,
    kind = kind,
    channel = channel,
    sizeBytes = sizeBytes,
    deviceFile = null,
    localFile = this,
)

data class SearchFilters(
    val video: Boolean = true,
    val emergency: Boolean = true,
    val photo: Boolean = true,
    val fromMs: Long? = null,
    val toMs: Long? = null,
)

class LibraryViewModel : ViewModel() {

    private val _device = MutableStateFlow<List<DeviceMediaFile>>(emptyList())
    val device: StateFlow<List<DeviceMediaFile>> = _device.asStateFlow()

    private val _phone = MutableStateFlow<List<DownloadedMedia>>(emptyList())
    val phone: StateFlow<List<DownloadedMedia>> = _phone.asStateFlow()

    private val _loadingDevice = MutableStateFlow(false)
    val loadingDevice: StateFlow<Boolean> = _loadingDevice.asStateFlow()

    private val _loadingPhone = MutableStateFlow(false)
    val loadingPhone: StateFlow<Boolean> = _loadingPhone.asStateFlow()

    private val _filters = MutableStateFlow(SearchFilters())
    val filters: StateFlow<SearchFilters> = _filters.asStateFlow()

    private val _searchResults = MutableStateFlow<List<MediaUiItem>>(emptyList())
    val searchResults: StateFlow<List<MediaUiItem>> = _searchResults.asStateFlow()

    private val _hasSearched = MutableStateFlow(false)
    val hasSearched: StateFlow<Boolean> = _hasSearched.asStateFlow()

    // Detail screen state.
    private val _activeList = MutableStateFlow<List<MediaUiItem>>(emptyList())
    val activeList: StateFlow<List<MediaUiItem>> = _activeList.asStateFlow()

    private val _selectedKey = MutableStateFlow<String?>(null)
    val selectedKey: StateFlow<String?> = _selectedKey.asStateFlow()

    val tasks: StateFlow<List<DownloadTask>> = ServiceLocator.downloadEngine.tasks

    init {
        refresh(MediaSource.PHONE)
        refresh(MediaSource.DEVICE)
    }

    fun rawItems(source: MediaSource): List<MediaUiItem> = when (source) {
        MediaSource.DEVICE -> _device.value.map { it.toUi() }
        MediaSource.PHONE -> _phone.value.map { it.toUi() }
    }

    fun refresh(source: MediaSource) {
        when (source) {
            MediaSource.DEVICE -> {
                if (ServiceLocator.connection.currentClient == null) return
                _loadingDevice.value = true
                viewModelScope.launch {
                    when (val r = ServiceLocator.device.refreshFiles()) {
                        is Outcome.Ok -> _device.value = r.value
                        is Outcome.Err -> AppLog.w("refresh device files: ${r.cause.message}")
                    }
                    _loadingDevice.value = false
                }
            }
            MediaSource.PHONE -> {
                _loadingPhone.value = true
                viewModelScope.launch {
                    _phone.value = ServiceLocator.localMedia.localMedia()
                    _loadingPhone.value = false
                }
            }
        }
    }

    fun count(source: MediaSource, category: MediaCategory): Int =
        filtered(source, category).size

    private fun matchesCategory(item: MediaUiItem, category: MediaCategory): Boolean = when (category) {
        MediaCategory.ALL -> true
        MediaCategory.PHOTO -> item.kind == MediaKind.PHOTO
        MediaCategory.FRONT -> item.channel == CameraChannel.FRONT
        MediaCategory.REAR -> item.channel == CameraChannel.REAR
        MediaCategory.EMERGENCY -> item.kind == MediaKind.EMERGENCY
    }

    fun filtered(source: MediaSource, category: MediaCategory): List<MediaUiItem> =
        rawItems(source).filter { matchesCategory(it, category) }

    /* ---------- Search ---------- */

    fun setFilters(transform: (SearchFilters) -> SearchFilters) {
        _filters.value = transform(_filters.value)
    }

    fun runSearch(source: MediaSource) {
        val f = _filters.value
        val results = rawItems(source).filter { item ->
            val kindOk = when (item.kind) {
                MediaKind.PHOTO -> f.photo
                MediaKind.EMERGENCY -> f.emergency
                MediaKind.MOVIE -> f.video
                MediaKind.UNKNOWN -> f.video
            }
            val afterFrom = f.fromMs == null || item.timeMs == 0L || item.timeMs >= f.fromMs
            val beforeTo = f.toMs == null || item.timeMs == 0L || item.timeMs <= f.toMs
            kindOk && afterFrom && beforeTo
        }
        _searchResults.value = results
        _hasSearched.value = true
    }

    fun clearSearch() {
        _hasSearched.value = false
        _searchResults.value = emptyList()
    }

    /* ---------- Detail ---------- */

    fun openDetail(source: MediaSource, category: MediaCategory, key: String) {
        _activeList.value = filtered(source, category)
        _selectedKey.value = key
    }

    fun openSearchDetail(key: String) {
        _activeList.value = _searchResults.value
        _selectedKey.value = key
    }

    fun selectKey(key: String?) { _selectedKey.value = key }

    fun stepSelected(delta: Int) {
        val list = _activeList.value
        val current = list.indexOfFirst { it.key == _selectedKey.value }
        if (current < 0) return
        val target = (current + delta).coerceIn(0, list.lastIndex)
        _selectedKey.value = list[target].key
    }

    val selectedItem: MediaUiItem?
        get() = _activeList.value.firstOrNull { it.key == _selectedKey.value }

    /* ---------- Actions ---------- */

    fun downloadActive() {
        val item = selectedItem ?: return
        val file = item.deviceFile ?: return
        ServiceLocator.localMedia.download(listOf(file))
    }

    fun downloadItems(items: List<MediaUiItem>) {
        val files = items.mapNotNull { it.deviceFile }
        if (files.isNotEmpty()) ServiceLocator.localMedia.download(files)
    }

    fun deleteActive(onDone: () -> Unit) {
        val item = selectedItem ?: return
        viewModelScope.launch {
            if (item.deviceFile != null) {
                ServiceLocator.device.delete(item.deviceFile)
                refresh(MediaSource.DEVICE)
            } else if (item.localFile != null) {
                ServiceLocator.localMedia.deleteLocal(item.localFile)
                refresh(MediaSource.PHONE)
            }
            val list = _activeList.value.toMutableList()
            val idx = list.indexOfFirst { it.key == item.key }
            if (idx >= 0) list.removeAt(idx)
            _activeList.value = list
            _selectedKey.value = list.getOrNull(idx.coerceAtMost(list.lastIndex))?.key
                ?: list.getOrNull((idx - 1).coerceAtLeast(0))?.key
            if (_selectedKey.value == null) onDone()
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            ServiceLocator.device.deleteAll()
            refresh(MediaSource.DEVICE)
        }
    }
}
