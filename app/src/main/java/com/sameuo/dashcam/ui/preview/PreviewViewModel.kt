package com.sameuo.dashcam.ui.preview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sameuo.dashcam.core.Outcome
import com.sameuo.dashcam.data.ServiceLocator
import com.sameuo.dashcam.data.protocol.WifiCmd
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PreviewUiState(
    val streamCandidates: List<String> = emptyList(),
    val streamIndex: Int = 0,
    val isRecording: Boolean = false,
    val pipStyle: Int = 0,
    val busy: Boolean = false,
    val toast: String? = null,
) {
    val currentStream: String? get() = streamCandidates.getOrNull(streamIndex)
}

class PreviewViewModel : ViewModel() {
    private val device = ServiceLocator.device
    private val connection = ServiceLocator.connection

    private val _ui = MutableStateFlow(PreviewUiState())
    val ui: StateFlow<PreviewUiState> = _ui.asStateFlow()

    init { startStream() }

    private fun startStream() {
        val client = connection.currentClient
        val candidates = client?.endpoint?.rtspCandidates ?: emptyList()
        viewModelScope.launch {
            device.setLiveView(true)
            _ui.value = _ui.value.copy(streamCandidates = candidates, streamIndex = 0)
        }
    }

    /** Try the next candidate RTSP path when the current one fails to connect. */
    fun tryNextStream() {
        val s = _ui.value
        if (s.streamIndex + 1 < s.streamCandidates.size) {
            _ui.value = s.copy(streamIndex = s.streamIndex + 1)
        } else {
            _ui.value = _ui.value.copy(toast = "No live stream found on this firmware.")
        }
    }

    fun toggleRecording() = act {
        val next = !_ui.value.isRecording
        device.setEnum(WifiCmd.RECORD, if (next) 1 else 0)
        _ui.value = _ui.value.copy(isRecording = next, toast = if (next) "Recording started" else "Recording stopped")
    }

    fun capture() = act {
        device.capture()
        _ui.value = _ui.value.copy(toast = "Captured")
    }

    fun cyclePip() = act {
        val next = (_ui.value.pipStyle + 1) % 4
        device.setEnum(WifiCmd.SET_PIP_STYLE, next)
        _ui.value = _ui.value.copy(pipStyle = next)
    }

    private inline fun act(crossinline block: suspend () -> Unit) {
        if (_ui.value.busy) return
        _ui.value = _ui.value.copy(busy = true)
        viewModelScope.launch {
            block()
            _ui.value = _ui.value.copy(busy = false)
        }
    }

    fun consumeToast() { _ui.value = _ui.value.copy(toast = null) }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch { device.setLiveView(false) }
    }
}
