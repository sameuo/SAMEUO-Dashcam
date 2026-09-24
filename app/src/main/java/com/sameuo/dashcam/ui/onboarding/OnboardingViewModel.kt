package com.sameuo.dashcam.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sameuo.dashcam.data.ServiceLocator
import com.sameuo.dashcam.data.protocol.chip.ChipPlatform
import com.sameuo.dashcam.data.repository.ConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class OnboardingStep { SPLASH, WARNING, CONNECT, SELECT_MODEL, CONNECTING }

/** Camera generation offered on the model picker. Both reuse the Novatek dialect today. */
enum class CameraModel(val title: String, val subtitle: String) {
    GEN4("GEN4", "2025 MODEL"),
    GEN3("GEN3", "2022 MODEL"),
}

class OnboardingViewModel : ViewModel() {

    private val _step = MutableStateFlow(OnboardingStep.SPLASH)
    val step: StateFlow<OnboardingStep> = _step.asStateFlow()

    private val _model = MutableStateFlow(CameraModel.GEN3)
    val model: StateFlow<CameraModel> = _model.asStateFlow()

    val connectionState: StateFlow<ConnectionState> = ServiceLocator.connection.state

    /** SSID the phone is currently attached to, refreshed when the user returns from Settings. */
    private val _ssid = MutableStateFlow("")
    val ssid: StateFlow<String> = _ssid.asStateFlow()

    private val _errorVisible = MutableStateFlow(false)
    val errorVisible: StateFlow<Boolean> = _errorVisible.asStateFlow()

    private val _errorMessage = MutableStateFlow("")
    val errorMessage: StateFlow<String> = _errorMessage.asStateFlow()

    init {
        // Observe connection outcomes while on the connecting step.
        viewModelScope.launch {
            connectionState.collect { st ->
                if (_step.value == OnboardingStep.CONNECTING) {
                    when (st) {
                        is ConnectionState.Connected -> _errorVisible.value = false
                        is ConnectionState.Failed -> {
                            _errorMessage.value = CONNECT_ERROR_TEXT
                            _errorVisible.value = true
                        }
                        else -> Unit
                    }
                }
            }
        }
    }

    fun splashFinished() {
        if (_step.value == OnboardingStep.SPLASH) _step.value = OnboardingStep.WARNING
    }

    fun acknowledgeWarning() {
        _step.value = OnboardingStep.CONNECT
        refreshSsid()
    }

    fun refreshSsid() {
        _ssid.value = ServiceLocator.wifi.currentSsid().orEmpty()
    }

    fun goSelectModel() {
        refreshSsid()
        _step.value = OnboardingStep.SELECT_MODEL
    }

    fun selectModel(model: CameraModel) {
        _model.value = model
    }

    /** Bind to the current Wi-Fi network (camera AP chosen in system Settings) and connect. */
    fun startConnect() {
        _errorVisible.value = false
        _step.value = OnboardingStep.CONNECTING
        // User already selected the camera network in system Wi-Fi settings; bind process to it.
        ServiceLocator.wifi.bindCurrent()
        ServiceLocator.connection.connect(ChipPlatform.NOVATEK, ChipPlatform.NOVATEK.defaultHost)
    }

    /** Connection Error dialog OK → back to the connect-camera page. */
    fun errorOk() {
        _errorVisible.value = false
        _step.value = OnboardingStep.CONNECT
    }

    /** Enter the app offline to browse already-downloaded (phone) files. */
    fun viewDownloadedFiles(onDone: () -> Unit) = onDone()
}

const val CONNECT_ERROR_TEXT =
    "Please check your camera. Make sure its power is On, and the Wifi mode is at the APP Mode. " +
        "Then go to Phone's Wifi Settings and try to connect to camera's Wifi network again."
