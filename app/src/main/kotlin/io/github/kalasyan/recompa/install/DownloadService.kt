package io.github.kalasyan.recompa.install

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.kalasyan.recompa.MainActivity
import io.github.kalasyan.recompa.R
import io.github.kalasyan.recompa.RecompaApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch

/**
 * Keeps Recompa running while ports download, verify and install, so leaving the app
 * does not stop a download of hundreds of MB. It shows the progress as a notification
 * and stops itself when nothing is left to do. The work itself stays in [InstallManager].
 */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android requires startForeground soon after every startForegroundService call.
        val manager = (application as RecompaApplication).installManager
        startInForeground(notification(manager, manager.phases.value.active()))
        if (!started) {
            started = true
            scope.launch {
                combine(manager.phases, manager.updatingAll) { phases, updatingAll -> phases.active() to updatingAll }
                    .conflate()
                    .collect { (active, updatingAll) ->
                        if (active.isEmpty() && !updatingAll) {
                            ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        } else {
                            notify(notification(manager, active))
                            // Android drops notifications posted too often; the latest progress still shows.
                            delay(UPDATE_INTERVAL_MS)
                        }
                    }
            }
        }
        return START_NOT_STICKY
    }

    /** Android 15 limits how long a data sync service runs; the download itself goes on. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startInForeground(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    @SuppressLint("MissingPermission") // canNotify() checks it; lint can't see through the helper.
    private fun notify(notification: Notification) {
        if (!canNotify(this)) return
        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // The permission was revoked between the check and the call.
        }
    }

    private fun notification(manager: InstallManager, active: List<Pair<String, InstallPhase>>): Notification {
        createChannel(this, CHANNEL_ID, R.string.downloads_channel, NotificationManager.IMPORTANCE_LOW)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openApp(this))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        val first = active.firstOrNull()
        if (first == null) {
            // Between two ports of "update all", waiting for the user to confirm one.
            return builder.setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.phase_awaiting_user))
                .build()
        }
        val (portId, phase) = first
        builder.setContentTitle(manager.label(portId))
        if (active.size > 1) builder.setSubText(getString(R.string.downloads_more, active.size - 1))
        when (phase) {
            is InstallPhase.Downloading -> {
                val progress = phase.progress
                builder.setContentText(getString(R.string.phase_downloading))
                if (progress != null) builder.setProgress(100, (progress * 100).toInt(), false) else builder.setProgress(0, 0, true)
            }
            InstallPhase.Verifying -> builder.setContentText(getString(R.string.phase_verifying)).setProgress(0, 0, true)
            else -> builder.setContentText(getString(R.string.phase_installing)).setProgress(0, 0, true)
        }
        return builder.build()
    }

    companion object {
        private const val CHANNEL_ID = "downloads"
        private const val CONFIRM_CHANNEL_ID = "install-confirm"
        private const val NOTIFICATION_ID = 2
        private const val UPDATE_INTERVAL_MS = 500L

        private fun Map<String, InstallPhase>.active() = entries
            .filter { (_, phase) -> phase is InstallPhase.Downloading || phase == InstallPhase.Verifying || phase == InstallPhase.Installing }
            .map { it.key to it.value }

        /** Called when an install starts. If Android refuses, the download still runs while the app is open. */
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
            } catch (e: IllegalStateException) {
                // ForegroundServiceStartNotAllowedException: the app was already in the background.
            }
        }

        /**
         * Android does not let a background app open the install confirmation, so when a
         * download finishes while the user is elsewhere, a notification opens it instead.
         */
        @SuppressLint("MissingPermission") // canNotify() checks it; lint can't see through the helper.
        fun notifyConfirm(context: Context, portId: String, label: String, confirm: Intent) {
            if (isInForeground() || !canNotify(context)) return
            createChannel(context, CONFIRM_CHANNEL_ID, R.string.confirm_channel, NotificationManager.IMPORTANCE_HIGH)
            val open = PendingIntent.getActivity(
                context,
                portId.hashCode(),
                confirm,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, CONFIRM_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.confirm_notification_title, label))
                .setContentText(context.getString(R.string.confirm_notification_text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            try {
                NotificationManagerCompat.from(context).notify(confirmTag(portId), NOTIFICATION_ID, notification)
            } catch (e: SecurityException) {
                // The permission was revoked between the check and the call.
            }
        }

        fun cancelConfirm(context: Context, portId: String) =
            NotificationManagerCompat.from(context).cancel(confirmTag(portId), NOTIFICATION_ID)

        private fun confirmTag(portId: String) = "confirm:$portId"

        private fun isInForeground(): Boolean {
            val info = ActivityManager.RunningAppProcessInfo()
            ActivityManager.getMyMemoryState(info)
            return info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        }

        private fun canNotify(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        private fun createChannel(context: Context, id: String, name: Int, importance: Int) {
            val channel = NotificationChannel(id, context.getString(name), importance)
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
