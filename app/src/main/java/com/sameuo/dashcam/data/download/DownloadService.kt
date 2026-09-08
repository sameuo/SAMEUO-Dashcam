package com.sameuo.dashcam.data.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.sameuo.dashcam.R
import com.sameuo.dashcam.data.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** Keeps the download queue alive outside the UI and surfaces progress in the notification shade. */
class DownloadService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startInForeground(0, 0, null)
        val engine = ServiceLocator.downloadEngine
        engine.tasks.onEach { tasks ->
            val running = tasks.count { it.status == DownloadStatus.RUNNING }
            val queued = tasks.count { it.status == DownloadStatus.QUEUED }
            val current = tasks.firstOrNull { it.status == DownloadStatus.RUNNING }
            if (running + queued == 0) {
                stopSelfCompat()
            } else {
                val pct = current?.let { (it.progress * 100).toInt() } ?: 0
                startInForeground(running + queued, pct, current?.file?.name)
            }
        }.launchIn(scope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private fun startInForeground(pending: Int, pct: Int, name: String?) {
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle(getString(R.string.app_name) + "  •  " +
                if (pending > 0) "Downloading ($pending)" else "Idle")
            .setContentText(name ?: "")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOnlyAlertOnce(true)
            .setProgress(100, pct, pct == 0 && pending > 0)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun stopSelfCompat() = scope.launch {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL, getString(R.string.download_channel_name), NotificationManager.IMPORTANCE_LOW).apply {
                description = getString(R.string.download_channel_desc)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    override fun onDestroy() { job.cancel(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "sameuo_downloads"
        private const val NOTIF_ID = 4201
        fun start(context: Context) {
            val i = Intent(context, DownloadService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i)
            else context.startService(i)
        }
    }
}
