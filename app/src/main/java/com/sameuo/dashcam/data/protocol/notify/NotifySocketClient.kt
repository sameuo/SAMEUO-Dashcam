package com.sameuo.dashcam.data.protocol.notify

import com.sameuo.dashcam.core.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket

/** Events the dashcam pushes over the TCP 3333 notify channel. */
enum class NotifyEventType { LOW_BATTERY, CARD_FULL, CARD_ERROR, EMERGENCY_LOCKED, RECORDING_STOPPED, UNKNOWN }

data class DeviceEvent(val type: NotifyEventType, val raw: String)

/**
 * Connects to the Novatek notify socket (default port 3333) and exposes device-pushed
 * events (low battery, SD full / error, emergency file locked…). Auto-reconnects.
 */
class NotifySocketClient(
    private val hostProvider: () -> String,
    private val port: Int = 3333,
) {
    private val _events = MutableSharedFlow<DeviceEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<DeviceEvent> = _events.asSharedFlow()
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                var socket: Socket? = null
                try {
                    socket = Socket()
                    socket.connect(InetSocketAddress(hostProvider(), port), 3000)
                    socket.soTimeout = 0
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                    var line = reader.readLine()
                    while (line != null && isActive) {
                        if (line.isNotBlank()) _events.tryEmit(DeviceEvent(classify(line), line))
                        line = reader.readLine()
                    }
                } catch (t: Throwable) {
                    AppLog.w("notify socket: ${t.message}")
                } finally {
                    runCatching { socket?.close() }
                }
                delay(3000)
            }
        }
    }

    fun stop() { job?.cancel(); job = null }

    private fun classify(raw: String): NotifyEventType {
        val s = raw.lowercase()
        return when {
            "battery" in s || "low" in s && "bat" in s -> NotifyEventType.LOW_BATTERY
            "full" in s -> NotifyEventType.CARD_FULL
            "card" in s && ("err" in s || "error" in s || "remove" in s) -> NotifyEventType.CARD_ERROR
            "emerg" in s || "gsensor" in s || "lock" in s -> NotifyEventType.EMERGENCY_LOCKED
            else -> NotifyEventType.UNKNOWN
        }
    }
}
