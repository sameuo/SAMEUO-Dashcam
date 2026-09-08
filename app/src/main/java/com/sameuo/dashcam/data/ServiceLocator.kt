package com.sameuo.dashcam.data

import android.content.Context
import com.sameuo.dashcam.data.connectivity.DeviceWifiManager
import com.sameuo.dashcam.data.download.DownloadEngine
import com.sameuo.dashcam.data.firmware.FirmwareUpdater
import com.sameuo.dashcam.data.local.db.SameuoDatabase
import com.sameuo.dashcam.data.local.prefs.AppSettings
import com.sameuo.dashcam.data.protocol.DeviceHttpClient
import com.sameuo.dashcam.data.protocol.chip.ProtocolFactory
import com.sameuo.dashcam.data.repository.ConnectionRepository
import com.sameuo.dashcam.data.repository.DeviceRepository
import com.sameuo.dashcam.data.repository.LocalMediaRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Tiny hand-rolled service locator. Deliberately no Hilt/Dagger/KSP so the project
 * builds on a clean toolchain without annotation processors. Initialised once in
 * [com.sameuo.dashcam.SameuoApp].
 */
object ServiceLocator {

    lateinit var appContext: Context
        private set

    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: SameuoDatabase by lazy { SameuoDatabase(appContext) }
    val settings: AppSettings by lazy { AppSettings(appContext) }
    val wifi: DeviceWifiManager by lazy { DeviceWifiManager(appContext) }

    val http: DeviceHttpClient by lazy { DeviceHttpClient() }
    private val protocolFactory: ProtocolFactory by lazy { ProtocolFactory(http) }

    val connection: ConnectionRepository by lazy {
        ConnectionRepository(protocolFactory, wifi, database, settings, appScope)
    }
    val device: DeviceRepository by lazy { DeviceRepository(connection) }

    val downloadEngine: DownloadEngine by lazy {
        DownloadEngine(
            appContext = appContext,
            db = database,
            scope = appScope,
            clientProvider = { connection.currentClient },
            baseDirOverride = { null },
        )
    }
    val localMedia: LocalMediaRepository by lazy {
        LocalMediaRepository(appContext, database, downloadEngine)
    }

    fun firmwareUpdater(): FirmwareUpdater? {
        val client = connection.currentClient ?: return null
        return FirmwareUpdater(client, http)
    }

    fun init(context: Context) {
        appContext = context.applicationContext
    }
}
