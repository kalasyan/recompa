package io.github.kalasyan.recompa.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kalasyan.recompa.R
import io.github.kalasyan.recompa.RecompaApplication
import io.github.kalasyan.recompa.core.install.ApkCheckResult
import io.github.kalasyan.recompa.core.install.PortStatus
import io.github.kalasyan.recompa.install.InstallError
import io.github.kalasyan.recompa.install.InstallManager
import io.github.kalasyan.recompa.install.InstallPhase

@Composable
fun rememberInstallManager(): InstallManager {
    val context = LocalContext.current
    return remember { (context.applicationContext as RecompaApplication).installManager }
}

/** Install, update, open and uninstall buttons for a port, with progress and errors. */
@Composable
fun InstallActions(item: PortItem, manager: InstallManager = rememberInstallManager()) {
    val context = LocalContext.current
    val port = item.port
    val phases by manager.phases.collectAsStateWithLifecycle()
    val revision by manager.revision.collectAsStateWithLifecycle()
    LifecycleResumeEffect(manager) {
        manager.refresh()
        onPauseOrDispose { }
    }
    val status = remember(port, revision) { manager.status(port) }
    val phase = phases[port.id] ?: InstallPhase.Idle
    val fork = port.recommendedFork

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (phase) {
            is InstallPhase.Downloading -> {
                Text(stringResource(R.string.phase_downloading))
                val progress = phase.progress
                if (progress != null) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
            InstallPhase.Verifying -> Busy(stringResource(R.string.phase_verifying))
            InstallPhase.Installing -> Busy(stringResource(R.string.phase_installing))
            InstallPhase.AwaitingUser -> Busy(stringResource(R.string.phase_awaiting_user))
            InstallPhase.NeedsPermission -> {
                Text(stringResource(R.string.needs_permission))
                Button(modifier = Modifier.focusRing(), onClick = {
                    context.startSafely(manager.unknownSourcesSettingsIntent())
                    manager.dismiss(port.id)
                }) { Text(stringResource(R.string.open_settings)) }
            }
            is InstallPhase.Failed -> {
                Text(errorText(phase.error), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { manager.dismiss(port.id) }, modifier = Modifier.focusRing()) { Text(stringResource(R.string.dismiss)) }
            }
            InstallPhase.Idle -> Unit
        }

        val busy = phase !is InstallPhase.Idle && phase !is InstallPhase.Failed && phase != InstallPhase.NeedsPermission
        val canInstall = fork != null && item.compatible && !busy
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            when (status) {
                PortStatus.NotInstalled -> Button(
                    onClick = { manager.install(port, fork!!) },
                    enabled = canInstall,
                    modifier = Modifier.weight(1f).focusRing(),
                ) { Text(stringResource(R.string.action_install)) }
                is PortStatus.UpdateAvailable -> {
                    Button(onClick = { manager.install(port, fork!!) }, enabled = canInstall, modifier = Modifier.weight(1f).focusRing()) {
                        Text(stringResource(R.string.action_update, status.tag))
                    }
                    OpenButton(manager, item, Modifier.weight(1f))
                }
                PortStatus.UpToDate, PortStatus.UnknownSigner, is PortStatus.OtherFork ->
                    OpenButton(manager, item, Modifier.weight(1f))
            }
        }
        when (status) {
            PortStatus.UnknownSigner -> Note(stringResource(R.string.status_unknown_signer))
            is PortStatus.OtherFork -> Note(stringResource(R.string.status_other_fork, status.fork.repo))
            else -> Unit
        }
        if (status != PortStatus.NotInstalled) {
            TextButton(onClick = { manager.uninstallIntent(port)?.let(context::startSafely) }, modifier = Modifier.focusRing()) {
                Text(stringResource(R.string.action_uninstall))
            }
        }
    }
}

@Composable
private fun OpenButton(manager: InstallManager, item: PortItem, modifier: Modifier) {
    val context = LocalContext.current
    OutlinedButton(onClick = { manager.launchIntent(item.port)?.let(context::startSafely) }, modifier = modifier.focusRing()) {
        Text(stringResource(R.string.action_open))
    }
}

@Composable
private fun Busy(label: String) {
    Text(label)
    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
internal fun errorText(error: InstallError): String = when (error) {
    InstallError.Network -> stringResource(R.string.error_download)
    InstallError.InvalidApk -> stringResource(R.string.error_invalid_apk)
    InstallError.Aborted -> stringResource(R.string.error_aborted)
    InstallError.Storage -> stringResource(R.string.error_storage)
    is InstallError.Blocked -> stringResource(R.string.error_blocked)
    is InstallError.Conflict -> stringResource(R.string.error_conflict)
    is InstallError.Incompatible -> stringResource(R.string.error_incompatible)
    is InstallError.Other -> stringResource(R.string.error_other, error.message.orEmpty())
    is InstallError.Check -> when (val result = error.result) {
        ApkCheckResult.HashUnavailable -> stringResource(R.string.error_hash_unavailable)
        is ApkCheckResult.HashMismatch -> stringResource(R.string.error_hash_mismatch)
        is ApkCheckResult.WrongPackage -> stringResource(R.string.error_wrong_package, result.actual)
        is ApkCheckResult.WrongSigner -> stringResource(R.string.error_wrong_signer)
        is ApkCheckResult.ConflictsWithInstalled -> stringResource(R.string.error_conflict)
        ApkCheckResult.Ok -> ""
    }
}

internal fun Context.startSafely(intent: Intent) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        // Nothing to open: the button simply does nothing on this device.
    }
}
