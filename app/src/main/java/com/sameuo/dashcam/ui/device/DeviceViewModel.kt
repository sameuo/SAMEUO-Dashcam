package com.sameuo.dashcam.ui.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sameuo.dashcam.data.ServiceLocator
import com.sameuo.dashcam.data.local.db.SavedDevice
import com.sameuo.dashcam.data.protocol.chip.ChipPlatform
import com.sameuo.dashcam.data.repository.ConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DeviceViewModel : ViewModel() {
    private val connection = ServiceLocator.connection
    private val db = ServiceLocator.database
    val wifi = ServiceLocator.wifi

    val connectionState: StateFlow<ConnectionState> = connection.state
    val chips: List<ChipPlatform> = ChipPlatform.entries

    private val _selectedChip = MutableStateFlow(ChipPlatform.NOVATEK)
    val selectedChip = _selectedChip.asStateFlow()

    private val _host = MutableStateFlow(ChipPlatform.NOVATEK.defaultHost)
    val host = _host.asStateFlow()

    private val _savedDevices = MutableStateFlow<List<SavedDevice>>(emptyList())
    val savedDevices: StateFlow<List<SavedDevice>> = _savedDevices.asStateFlow()

    init { refreshSaved() }

    fun selectChip(chip: ChipPlatform) {
        _selectedChip.value = chip
        _host.value = chip.defaultHost
    }

    fun setHost(h: String) { _host.value = h }

    fun refreshSaved() = viewModelScope.launch { _savedDevices.value = db.listDevices() }

    /** User already joined the camera SSID in system settings: bind then connect. */
    fun bindAndConnect() {
        wifi.bindCurrent()
        connection.connect(_selectedChip.value, _host.value.ifBlank { _selectedChip.value.defaultHost })
    }

    fun connectTo(chip: ChipPlatform, host: String) {
        _selectedChip.value = chip; _host.value = host
        wifi.bindCurrent()
        connection.connect(chip, host)
    }

    fun disconnect() = connection.disconnect()
}
