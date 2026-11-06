package com.snapdown.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

// Background update download: foreground service + notification progress.
// Reason for this file: the old download lived in the Settings composable's
// coroutine scope, so leaving Settings / home / the app cancelled it.
// A foreground service keeps downloading even in background, and the
// notification shows the live % in the status bar.
object UpdateDownloadState {
    val progress = MutableStateFlow<Int?>(null)
    val status = MutableStateFlow("")
}

class UpdateDownloadService : Service() {

    companion object {
        const val EXTRA_VERSION = "version"
        const val EXTRA_URL = "download_url"
        const val EXTRA_FILE_NAME = "file_name"
        const val CHANNEL_ID = "snapdown_update"
        const val NOTIF_ID = 1001

        fun start(context: Context, info: UpdateChecker.UpdateInfo) {
            val intent = Intent(context, UpdateDownloadService::class.java).apply {
                putExtra(EXTRA_VERSION, info.version)
                putExtra(EXTRA_URL, info.downloadUrl)
                putExtra(EXTRA_FILE_NAME, info.fileName)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastNotified = -1
    private var lastNotifyTime = 0L
    private var downloading = false

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "App update",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "SnapDown update download progress" }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY
        if (downloading) return START_STICKY
        val version = intent.getStringExtra(EXTRA_VERSION).orEmpty()
        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        val fileName = intent.getStringExtra(EXTRA_FILE_NAME).orEmpty()
        if (version.isBlank() || url.isBlank() || fileName.isBlank()) return START_NOT_STICKY

        downloading = true
        val notif = buildNotification(version, 0, downloading = true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notif)
        }
        UpdateDownloadState.progress.value = 0
        UpdateDownloadState.status.value = "Background me download ho raha hai — notification me progress dekho."

        scope.launch {
            try {
                val info = UpdateChecker.UpdateInfo(version, url, fileName)
                val file = UpdateChecker.downloadApk(applicationContext, info) { p ->
                    UpdateDownloadState.progress.value = p
                    val now = System.currentTimeMillis()
                    if (p != lastNotified && (now - lastNotifyTime > 400 || p >= 100)) {
                        lastNotified = p
                        lastNotifyTime = now
                        notifyProgress(version, p)
                    }
                }
                if (file == null) {
                    UpdateDownloadState.progress.value = null
                    UpdateDownloadState.status.value = "Download failed, try again."
                    notifyFinished(version, success = false)
                } else {
                    UpdateDownloadState.progress.value = 100
                    UpdateDownloadState.status.value = "Downloaded. Installer khul raha hai, Install dabao."
                    notifyFinished(version, success = true)
                    UpdateChecker.installApk(applicationContext, file)
                }
            } catch (_: Exception) {
                UpdateDownloadState.progress.value = null
                UpdateDownloadState.status.value = "Download failed, try again."
                notifyFinished(version, success = false)
            } finally {
                downloading = false
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildNotification(version: String, progress: Int, downloading: Boolean): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SnapDown update $version download ho raha hai")
            .setContentText("$progress%")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, false)
            .setOngoing(downloading)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent())
            .build()
    }

    private fun notifyProgress(version: String, progress: Int) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIF_ID, buildNotification(version, progress, downloading = true))
    }

    private fun notifyFinished(version: String, success: Boolean) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(
                if (success) "SnapDown update $version download ho gaya"
                else "SnapDown update download fail ho gaya"
            )
            .setContentText(
                if (success) "Installer khul raha hai, Install dabao."
                else "Dobara Update App dabao."
            )
            .setSmallIcon(
                if (success) android.R.drawable.stat_sys_download_done
                else android.R.drawable.stat_notify_error
            )
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
        manager.notify(NOTIF_ID, notif)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
