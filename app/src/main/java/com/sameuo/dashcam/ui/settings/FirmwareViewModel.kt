package com.sameuo.dashcam.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sameuo.dashcam.core.Outcome
import com.sameuo.dashcam.data.ServiceLocator
import com.sameuo.dashcam.data.firmware.OtaState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class FirmwareViewModel : ViewModel() {
    private val _fileName = MutableStateFlow<String?>(null)
    val fileName: StateFlow<String?> = _fileName.asStateFlow()

    private val _state = MutableStateFlow<OtaState>(OtaState.Idle)
    val state: StateFlow<OtaState> = _state.asStateFlow()

    private var localFile: File? = null

    fun pick(context: Context, uri: Uri) {
        viewModelScope.launch {
            _fileName.value = uri.lastPathSegment
            localFile = withContext(Dispatchers.IO) {
                val out = File(context.cacheDir, "ota_firmware.bin")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    out.outputStream().use { input.copyTo(it) }
                } ?: return@withContext null
                out
            }
        }
    }

    fun start() {
        val file = localFile ?: run { _state.value = OtaState.Failed("Choose a firmware file first"); return }
        val updater = ServiceLocator.firmwareUpdater()
            ?: run { _state.value = OtaState.Failed("Not connected"); return }
        viewModelScope.launch {
            updater.update(file) { _state.value = it }
        }
    }
}
