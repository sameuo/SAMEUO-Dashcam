package com.sameuo.dashcam.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sameuo.dashcam.core.Outcome
import com.sameuo.dashcam.data.ServiceLocator
import com.sameuo.dashcam.data.local.prefs.AppLanguage
import com.sameuo.dashcam.data.local.prefs.AppPrefs
import com.sameuo.dashcam.data.local.prefs.ThemeMode
import com.sameuo.dashcam.data.protocol.WifiCmd
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SettingKind { SINGLE, TOGGLE, SPECIAL }

enum class SettingGroup(val title: String) {
    VIDEO("Video and Photo Settings"),
    EMERGENCY("Emergency Recordings"),
    DEVICE("Device Setting"),
    APP("Apps Setting"),
    SUPPORT("Support"),
}

data class SettingOption(val index: Int, val label: String)

data class SettingDef(
    val key: String,
    val title: String,
    val group: SettingGroup,
    val kind: SettingKind,
    val cmd: Int? = null,
    val options: List<SettingOption> = emptyList(),
    val defaultIndex: Int = 0,
)

class SettingsViewModel : ViewModel() {

    val connected: Boolean get() = ServiceLocator.connection.currentClient != null

    private val _selections = MutableStateFlow(
        defs.associate { it.key to it.defaultIndex }
    )
    val selections: StateFlow<Map<String, Int>> = _selections.asStateFlow()

    val prefs: StateFlow<AppPrefs> = ServiceLocator.settings.flow
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, AppPrefs())

    init {
        syncFromDevice()
    }

    private fun syncFromDevice() {
        if (!connected) return
        viewModelScope.launch {
            // Best-effort schema refresh (3031 + 3014 inside the client).
            runCatching { ServiceLocator.device.refreshMenu() }
            // Authoritative current values: query each setting command directly.
            val updates = mutableMapOf<String, Int>()
            defs.forEach { d ->
                val cmd = d.cmd ?: return@forEach
                val cur = ServiceLocator.device.queryCurrentIndex(cmd)
                if (cur != null && cur >= 0) updates[d.key] = cur
            }
            if (updates.isNotEmpty()) {
                _selections.value = _selections.value + updates
            }
        }
    }

    fun indexOf(key: String): Int = _selections.value[key] ?: 0

    fun displayValue(key: String): String? {
        val def = defs.firstOrNull { it.key == key } ?: return null
        if (def.kind == SettingKind.TOGGLE) return null
        val idx = indexOf(key)
        return def.options.firstOrNull { it.index == idx }?.label
    }

    fun isToggleOn(key: String): Boolean = indexOf(key) == 1

    fun select(key: String, index: Int) {
        _selections.value = _selections.value.toMutableMap().apply { put(key, index) }
        val def = defs.first { it.key == key }
        val cmd = def.cmd ?: return
        if (!connected) return
        viewModelScope.launch { ServiceLocator.device.setEnum(cmd, index) }
    }

    fun toggle(key: String, on: Boolean) = select(key, if (on) 1 else 0)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { ServiceLocator.settings.setTheme(mode) }
    }

    fun setAppLanguage(language: AppLanguage) {
        viewModelScope.launch { ServiceLocator.settings.setLanguage(language) }
    }

    fun formatCard(onResult: (String) -> Unit) {
        if (!connected) {
            onResult("Connect the camera first")
            return
        }
        viewModelScope.launch {
            when (val r = ServiceLocator.device.formatCard()) {
                is Outcome.Ok -> onResult("Memory card formatted")
                is Outcome.Err -> onResult("Format failed: ${r.cause.message}")
            }
        }
    }

    companion object {
        fun def(key: String, title: String, group: SettingGroup, cmd: Int?, options: List<SettingOption>, defaultIndex: Int) =
            SettingDef(key, title, group, SettingKind.SINGLE, cmd, options, defaultIndex)

        fun toggle(key: String, title: String, group: SettingGroup, cmd: Int?, defaultOn: Boolean) =
            SettingDef(key, title, group, SettingKind.TOGGLE, cmd, emptyList(), if (defaultOn) 1 else 0)

        fun special(key: String, title: String, group: SettingGroup) =
            SettingDef(key, title, group, SettingKind.SPECIAL)

        val defs: List<SettingDef> = listOf(
            // ---- Video and Photo ----
            def(
                "video_resolution", "Video Resolution", SettingGroup.VIDEO, WifiCmd.MOVIE_REC_SIZE,
                listOf(
                    SettingOption(0, "P30 2560×1440"),
                    SettingOption(1, "FHD 1920×1080"),
                ),
                defaultIndex = 0,
            ),
            def(
                "video_record_loop", "Video Record Loop", SettingGroup.VIDEO, WifiCmd.CYCLIC_REC,
                listOf(
                    SettingOption(0, "Off"),
                    SettingOption(1, "1 min"),
                    SettingOption(2, "2 min"),
                    SettingOption(3, "3 min"),
                    SettingOption(4, "5 min"),
                    SettingOption(5, "10 min"),
                ),
                defaultIndex = 0,
            ),
            toggle("audio_recording", "Audio Recording", SettingGroup.VIDEO, WifiCmd.MOVIE_AUDIO, true),
            toggle("date_stamp", "Date Stamp", SettingGroup.VIDEO, WifiCmd.DATE_IMPRINT, true),
            toggle("wdr", "WDR Wide Dynamic Range", SettingGroup.VIDEO, WifiCmd.MOVIE_HDR_WDR, true),

            // ---- Emergency ----
            def(
                "g_sensor", "G Sensor", SettingGroup.EMERGENCY, WifiCmd.G_SENSOR,
                listOf(
                    SettingOption(0, "Off (No Recording)"),
                    SettingOption(1, "Low"),
                    SettingOption(3, "High"),
                ),
                defaultIndex = 3,
            ),
            def(
                "parking_monitor", "Parking Monitor", SettingGroup.EMERGENCY, WifiCmd.PARKING_MONITOR,
                listOf(
                    SettingOption(0, "Off"),
                    SettingOption(1, "Low"),
                    SettingOption(2, "Medium"),
                    SettingOption(3, "High"),
                ),
                defaultIndex = 3,
            ),

            // ---- Device ----
            special("set_wifi_ssid", "Set Wi-Fi SSID", SettingGroup.DEVICE),
            special("format_memory", "Format Memory Card", SettingGroup.DEVICE),
            special("update_firmware", "Update Firmware", SettingGroup.DEVICE),
            def(
                "device_language", "Device Language", SettingGroup.DEVICE, WifiCmd.LANGUAGE,
                listOf(
                    SettingOption(0, "English"),
                    SettingOption(1, "French"),
                    SettingOption(2, "Spanish"),
                    SettingOption(3, "Portuguese"),
                    SettingOption(4, "German"),
                    SettingOption(5, "Italian"),
                    SettingOption(6, "Russian"),
                    SettingOption(7, "Chinese Simplified"),
                    SettingOption(8, "Chinese Traditional"),
                    SettingOption(9, "Thai"),
                    SettingOption(10, "Japanese"),
                    SettingOption(11, "Language 11"),
                    SettingOption(12, "Vietnamese"),
                ),
                defaultIndex = 0,
            ),

            // ---- App ----
            special("appearance", "Appearance", SettingGroup.APP),
            special("app_language", "App Language", SettingGroup.APP),

            // ---- Support ----
            special("about", "About", SettingGroup.SUPPORT),
            special("user_manual", "User Manual", SettingGroup.SUPPORT),
        )
    }
}
