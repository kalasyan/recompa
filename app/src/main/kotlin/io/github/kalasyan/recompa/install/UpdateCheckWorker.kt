package io.github.kalasyan.recompa.install

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.kalasyan.recompa.BuildConfig
import io.github.kalasyan.recompa.MainActivity
import io.github.kalasyan.recompa.R
import io.github.kalasyan.recompa.RecompaApplication
import io.github.kalasyan.recompa.data.CatalogRepository
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

/**
 * Twice a day, downloads the catalog and notifies when installed ports have updates.
 * It only tells; installing still happens in the app, with "update all".
 */
class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    @SuppressLint("MissingPermission") // canNotify() checks it; lint can't see through the helper.
    override suspend fun doWork(): Result {
        val app = applicationContext as RecompaApplication
        if (!app.prefs.updateNotifications.value) return Result.success()
        val index = try {
            CatalogRepository(BuildConfig.CATALOG_URL, File(app.filesDir, "catalog/index.json")).load().index
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Result.retry()
        }
        val pending = app.installManager.pendingUpdates(index.ports)
        val notifications = NotificationManagerCompat.from(app)
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (pending.isEmpty()) {
            notifications.cancel(NOTIFICATION_ID)
            prefs.edit().remove(LAST_NOTIFIED).apply()
            return Result.success()
        }
        // Don't repeat the same notification every check: only when the set of updates changes.
        val signature = pending.joinToString("|") { "${it.id}@${it.recommendedFork?.release?.tag}" }
        if (signature == prefs.getString(LAST_NOTIFIED, null) || !canNotify(app)) return Result.success()

        createChannel(app)
        val open = PendingIntent.getActivity(
            app,
            0,
            Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val names = pending.joinToString(", ") { it.name }
        val notification = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(app.resources.getQuantityString(R.plurals.updates_notification_title, pending.size, pending.size))
            .setContentText(names)
            .setStyle(NotificationCompat.BigTextStyle().bigText(names))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            notifications.notify(NOTIFICATION_ID, notification)
            prefs.edit().putString(LAST_NOTIFIED, signature).apply()
        } catch (e: SecurityException) {
            // The permission was revoked between the check and the call.
        }
        return Result.success()
    }

    private fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.updates_channel),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val WORK_NAME = "port-updates"
        private const val CHANNEL_ID = "port-updates"
        private const val NOTIFICATION_ID = 1
        private const val PREFS = "update_check"
        private const val LAST_NOTIFIED = "last_notified"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(12, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInitialDelay(1, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
