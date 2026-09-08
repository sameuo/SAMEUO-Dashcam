package com.sameuo.dashcam.data.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the Wi-Fi link to the dashcam. On Android 10+ we cannot silently switch the
 * global Wi-Fi, so we request a peer-to-peer network and *bind the process* to it —
 * otherwise HTTP requests to 192.168.1.254 leak out over cellular and fail.
 */
class DeviceWifiManager(private val context: Context) {

    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private val _state = MutableStateFlow(WifiLinkState())
    val state: StateFlow<WifiLinkState> = _state.asStateFlow()

    private var boundNetwork: Network? = null
    private var callback: ConnectivityManager.NetworkCallback? = null

    val isBound: Boolean get() = boundNetwork != null

    /** Ask the OS to connect to the camera AP and bind our process once it is available. */
    fun requestAndBind(ssid: String, password: String?, onResult: (Boolean, String?) -> Unit) {
        release()
        if (!wifi.isWifiEnabled) @Suppress("DEPRECATION") runCatching { wifi.isWifiEnabled = true }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val specifier = WifiNetworkSpecifier.Builder().apply {
                setSsid(ssid)
                if (!password.isNullOrEmpty()) setWpa2Passphrase(password)
            }.build()
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(specifier)
                .build()
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    cm.bindProcessToNetwork(network)
                    boundNetwork = network
                    _state.value = WifiLinkState(connected = true, ssid = ssid, bound = true)
                    onResult(true, null)
                }
                override fun onUnavailable() {
                    _state.value = WifiLinkState(connected = false, ssid = ssid)
                    onResult(false, "Network unavailable")
                }
                override fun onLost(network: Network) {
                    boundNetwork = null
                    _state.value = WifiLinkState(connected = false, ssid = ssid)
                }
            }
            callback = cb
            cm.requestNetwork(request, cb)
        } else {
            @Suppress("DEPRECATION")
            val cfg = android.net.wifi.WifiConfiguration().apply {
                SSID = "\"$ssid\""
                if (password.isNullOrEmpty()) {
                    allowedKeyManagement.set(android.net.wifi.WifiConfiguration.KeyMgmt.NONE)
                } else {
                    preSharedKey = "\"$password\""
                }
            }
            @Suppress("DEPRECATION")
            val netId = wifi.addNetwork(cfg)
            @Suppress("DEPRECATION")
            val ok = netId != -1 && wifi.enableNetwork(netId, true)
            _state.value = WifiLinkState(connected = ok, ssid = ssid, bound = ok)
            onResult(ok, if (ok) null else "Failed to add Wi-Fi network")
        }
    }

    /** Bind to whatever Wi-Fi network is already current (user connected manually in Settings). */
    fun bindCurrent() {
        val active = cm.activeNetwork
        val caps = active?.let { cm.getNetworkCapabilities(it) }
        if (active != null && caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
            cm.bindProcessToNetwork(active)
            boundNetwork = active
            _state.value = _state.value.copy(connected = true, bound = true, ssid = currentSsid().orEmpty())
        }
    }

    fun currentSsid(): String? {
        @Suppress("DEPRECATION")
        val info = wifi.connectionInfo ?: return null
        return info.ssid?.removePrefix("\"")?.removeSuffix("\"")?.takeIf { it != "<unknown ssid>" }
    }

    fun release() {
        callback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        callback = null
        cm.bindProcessToNetwork(null)
        boundNetwork = null
        _state.value = WifiLinkState()
    }
}

data class WifiLinkState(
    val connected: Boolean = false,
    val bound: Boolean = false,
    val ssid: String = "",
)
