package io.github.kalasyan.recompa.install

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat
import io.github.kalasyan.recompa.RecompaApplication

/** Receives the outcome of a PackageInstaller session committed by [InstallManager]. */
class InstallResultReceiver : BroadcastReceiver() {
    @SuppressLint("UnsafeIntentLaunch") // The confirmation intent comes from the system installer.
    override fun onReceive(context: Context, intent: Intent) {
        val portId = intent.getStringExtra(EXTRA_PORT_ID) ?: return
        val manager = (context.applicationContext as RecompaApplication).installManager
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(confirm)
                }
                manager.onAwaitingUser(portId)
            }
            PackageInstaller.STATUS_SUCCESS -> manager.onInstalled(portId)
            else -> manager.onFailed(portId, status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
        }
    }

    companion object {
        const val EXTRA_PORT_ID = "io.github.kalasyan.recompa.extra.PORT_ID"
    }
}
