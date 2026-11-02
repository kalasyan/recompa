package io.github.kalasyan.recompa.install

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.kalasyan.recompa.RecompaApplication

/** Pause, resume and cancel buttons of the download notifications. */
class DownloadActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val portId = intent.getStringExtra(EXTRA_PORT_ID) ?: return
        val manager = (context.applicationContext as RecompaApplication).installManager
        when (intent.action) {
            ACTION_PAUSE -> manager.pause(portId)
            ACTION_RESUME -> manager.resume(portId)
            ACTION_CANCEL -> manager.cancelDownload(portId)
        }
        // Recompa restarted since the pause: there is nothing left to resume from here.
        if (intent.action != ACTION_PAUSE && manager.phases.value[portId] !is InstallPhase.Downloading) {
            DownloadService.cancelPaused(context, portId)
        }
    }

    companion object {
        const val ACTION_PAUSE = "io.github.kalasyan.recompa.action.PAUSE_DOWNLOAD"
        const val ACTION_RESUME = "io.github.kalasyan.recompa.action.RESUME_DOWNLOAD"
        const val ACTION_CANCEL = "io.github.kalasyan.recompa.action.CANCEL_DOWNLOAD"
        private const val EXTRA_PORT_ID = "io.github.kalasyan.recompa.extra.PORT_ID"

        fun pendingIntent(context: Context, action: String, portId: String): PendingIntent {
            val intent = Intent(context, DownloadActionReceiver::class.java)
                .setAction(action)
                .setPackage(context.packageName)
                .putExtra(EXTRA_PORT_ID, portId)
            return PendingIntent.getBroadcast(
                context,
                (action + portId).hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
