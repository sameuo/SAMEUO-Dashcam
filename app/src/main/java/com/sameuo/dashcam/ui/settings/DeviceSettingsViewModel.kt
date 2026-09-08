package com.sameuo.dashcam.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sameuo.dashcam.core.Outcome
import com.sameuo.dashcam.data.ServiceLocator
import com.sameuo.dashcam.data.protocol.model.DeviceMenu
import com.sameuo.dashcam.data.protocol.model.MenuItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUi(
    val loading: Boolean = true,
    val menu: DeviceMenu? = null,
    val error: String? = null,
    val busy: String? = null,
)

class DeviceSettingsViewModel : ViewModel() {
    private val device = ServiceLocator.device
    private val _ui = MutableStateFlow(SettingsUi())
    val ui: StateFlow<SettingsUi> = _ui.asStateFlow()

    init { load() }

    fun load() {
        _ui.value = SettingsUi(loading = true)
        viewModelScope.launch {
            when (val r = device.refreshMenu()) {
                is Outcome.Ok -> _ui.value = SettingsUi(loading = false, menu = r.value)
                is Outcome.Err -> _ui.value = SettingsUi(loading = false, error = r.cause.message)
            }
        }
    }

    fun choose(item: MenuItem, optionIndex: Int) {
        viewModelScope.launch {
            _ui.update { it.copy(busy = item.name) }
            val r = device.setEnum(item.cmd, optionIndex)
            _ui.update { cur ->
                val updated = cur.menu?.items?.map { m ->
                    if (m.cmd == item.cmd) m.copy(currentIndex = optionIndex) else m
                }?.let { DeviceMenu(it) }
                cur.copy(menu = updated, busy = null,
                    error = (r as? Outcome.Err)?.cause?.message)
            }
        }
    }

    fun syncTime() = viewModelScope.launch {
        _ui.update { it.copy(busy = "Syncing time") }
        ServiceLocator.connection.refreshStatus()
        _ui.update { it.copy(busy = null) }
    }

    fun format() = viewModelScope.launch {
        _ui.update { it.copy(busy = "Formatting SD card") }
        device.formatCard(); _ui.update { it.copy(busy = null) }
    }

    fun factoryReset() = viewModelScope.launch {
        _ui.update { it.copy(busy = "Resetting") }
        device.factoryReset(); _ui.update { it.copy(busy = null) }
    }
}
