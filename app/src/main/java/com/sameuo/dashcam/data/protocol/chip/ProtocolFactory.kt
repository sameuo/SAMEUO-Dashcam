package com.sameuo.dashcam.data.protocol.chip

import com.sameuo.dashcam.data.protocol.DeviceClient
import com.sameuo.dashcam.data.protocol.DeviceEndpoint
import com.sameuo.dashcam.data.protocol.DeviceHttpClient
import com.sameuo.dashcam.data.protocol.NovatekDeviceClient

/**
 * Builds the right [DeviceClient] for a [ChipPlatform].
 *
 * Novatek and Novatek-compatible firmwares share the `/?custom=1&cmd=` HTTP
 * dialect and XML responses, so they reuse [NovatekDeviceClient]. Platforms with
 * a genuinely different dialect get their own adapter (TODO) behind [DeviceClient].
 */
class ProtocolFactory(
    private val http: DeviceHttpClient = DeviceHttpClient(),
) {
    fun create(
        platform: ChipPlatform = ChipPlatform.NOVATEK,
        host: String? = null,
    ): DeviceClient {
        val endpoint = DeviceEndpoint(host = host ?: platform.defaultHost)
        return when (platform) {
            ChipPlatform.NOVATEK -> NovatekDeviceClient(endpoint, http)
            // Allwinner V536 speaks a near-identical custom-command dialect with a
            // different command map; reuse transport, override command map later.
            ChipPlatform.ALLWINNER,
            ChipPlatform.SIGMASTAR,
            ChipPlatform.HISILICON,
            ChipPlatform.EEASYTECH -> NovatekDeviceClient(endpoint, http) // compatibility fallback
            else -> NovatekDeviceClient(endpoint, http) // placeholder until dedicated adapter
        }
    }
}
