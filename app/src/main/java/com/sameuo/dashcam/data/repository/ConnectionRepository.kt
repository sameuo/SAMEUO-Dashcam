package com.sameuo.dashcam.data.repository

import com.sameuo.dashcam.core.AppLog
import com.sameuo.dashcam.core.Outcome
import com.sameuo.dashcam.data.connectivity.DeviceWifiManager
import com.sameuo.dashcam.data.local.db.SameuoDatabase
import com.sameuo.dashcam.data.local.db.SavedDevice
import com.sameuo.dashcam.data.local.prefs.AppSettings
import com.sameuo.dashcam.data.protocol.DeviceClient
import com.sameuo.dashcam.data.protocol.chip.ChipPlatform
import com.sameuo.dashcam.data.protocol.chip.ProtocolFactory
import com.sameuo.dashcam.data.protocol.model.DeviceStatus
import com.sameuo.dashcam.data.protocol.notify.DeviceEvent
import com.sameuo.dashcam.data.protocol.notify.NotifySocketClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Connecting : ConnectionState
    data class Connected(val client: DeviceClient, val chip: ChipPlatform, val host: String) : ConnectionState {
        var status: DeviceStatus? = null
    }
    data class Failed(val message: String) : ConnectionState
}

class ConnectionRepository(
    private val factory: ProtocolFactory,
    private val wifi: DeviceWifiManager,
    private val db: SameuoDatabase,
    private val settings: AppSettings,
    private val appScope: CoroutineScope,
) {
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<DeviceEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<DeviceEvent> = _events.asSharedFlow()

    private var heartbeat: Job? = null
    private val notifier = NotifySocketClient(hostProvider = { currentHost() })

    val currentClient: DeviceClient? get() = (_state.value as? ConnectionState.Connected)?.client

    private fun currentHost(): String = (_state.value as? ConnectionState.Connected)?.host ?: "192.168.1.254"

    /** Connect over an already-established camera Wi-Fi link (binding handled separately). */
    fun connect(platform: ChipPlatform = ChipPlatform.NOVATEK, host: String = platform.defaultHost) {
        if (_state.value is ConnectionState.Connecting) return
        _state.value = ConnectionState.Connecting
        appScope.launch(Dispatchers.IO) {
            val client = factory.create(platform, host)
            // 1) reachability
            if (!client.ping()) {
                _state.value = ConnectionState.Failed("Cannot reach $host. Connect to the camera Wi-Fi first.")
                return@launch
            }
            // 2) open an APP session so the device enters remote-control mode
            client.enterWifiSession()
            // 3) sync clock once (watermark accuracy)
            runCatching {
                val now = java.text.SimpleDateFormat("yyyy-MM-dd,HH:mm:ss", java.util.Locale.US)
                    .format(java.util.Date()).split(",")
                client.syncTime(now[0], now[1])
            }
            settings.setLastDevice(platform.name, host)
            db.upsertDevice(SavedDevice(name = "SAMEUO $platform", chip = platform.name, host = host))
            _state.value = ConnectionState.Connected(client, platform, host)
            startHeartbeat(client)
            notifier.start(appScope)
        }
    }

    fun disconnect() {
        appScope.launch(Dispatchers.IO) {
            currentClient?.exitWifiSession()
            heartbeat?.cancel(); heartbeat = null
            notifier.stop()
            _state.value = ConnectionState.Disconnected
        }
    }

    private fun startHeartbeat(client: DeviceClient) {
        heartbeat?.cancel()
        heartbeat = appScope.launch(Dispatchers.IO) {
            notifier.events.collect { _events.tryEmit(it) }
        }
        appScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(HEARTBEAT_MS)
                val connected = _state.value as? ConnectionState.Connected ?: break
                val hb = client.heartbeat()
                if (hb is Outcome.Err) {
                    AppLog.w("heartbeat lost: ${hb.cause.message}")
                    if (!client.ping()) {
                        _state.value = ConnectionState.Disconnected
                        break
                    }
                } else when (val s = client.queryStatus()) {
                    is Outcome.Ok -> connected.status = s.value
                    else -> Unit
                }
            }
        }
    }

    suspend fun refreshStatus(): DeviceStatus? {
        val client = currentClient ?: return null
        val connected = _state.value as? ConnectionState.Connected
        return (client.queryStatus() as? Outcome.Ok)?.value?.also { connected?.status = it }
    }

    companion object { private const val HEARTBEAT_MS = 5_000L }
}
