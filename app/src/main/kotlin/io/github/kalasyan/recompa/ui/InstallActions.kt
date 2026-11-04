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
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kalasyan.recompa.R
import io.github.kalasyan.recompa.RecompaApplication
import io.github.kalasyan.recompa.core.install.ApkCheck
import io.github.kalasyan.recompa.core.catalog.Fork
import io.github.kalasyan.recompa.core.install.ApkCheckResult
import io.github.kalasyan.recompa.core.install.PortStatus
import io.github.kalasyan.recompa.data.UserPrefs
import io.github.kalasyan.recompa.display.SecondScreen
import io.github.kalasyan.recompa.install.InstallError
import io.github.kalasyan.recompa.install.InstallManager
import io.github.kalasyan.recompa.install.InstallPhase
import io.github.kalasyan.recompa.install.LinkCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun rememberInstallManager(): InstallManager {
    val context = LocalContext.current
    return remember { (context.applicationContext as RecompaApplication).installManager }
}

@Composable
fun rememberUserPrefs(): UserPrefs {
    val context = LocalContext.current
    return remember { (context.applicationContext as RecompaApplication).prefs }
}

@Composable
fun rememberSecondScreen(): SecondScreen {
    val context = LocalContext.current
    return remember { (context.applicationContext as RecompaApplication).secondScreen }
}

/**
 * Install, update, open and uninstall buttons for [fork], the version of the port the player
 * picked, with progress and errors.
 */
@Composable
fun InstallActions(item: PortItem, fork: Fork, manager: InstallManager = rememberInstallManager()) {
    val context = LocalContext.current
    val port = item.port
    val phases by manager.phases.collectAsStateWithLifecycle()
    val revision by manager.revision.collectAsStateWithLifecycle()
    LifecycleResumeEffect(manager) {
        manager.refresh()
        onPauseOrDispose { }
    }
    val status = remember(port, fork, revision) { manager.status(port, fork) }
    val phase = phases[port.id] ?: InstallPhase.Idle
    val linked = remember(port, fork, revision) { manager.isLinked(port, fork) }
    var picking by remember { mutableStateOf(false) }
    var mismatch by remember { mutableStateOf<LinkCandidate?>(null) }
    var confirmAnyway by remember { mutableStateOf(false) }
    var confirmReinstall by remember { mutableStateOf(false) }

    // The user came back from uninstalling a build Android would not update: install the new one.
    LaunchedEffect(status) {
        if (status == PortStatus.NotInstalled) manager.resumeReinstall(port)
    }
    if (confirmAnyway) {
        ConfirmDialog(
            title = stringResource(R.string.install_anyway_title),
            text = stringResource(R.string.install_anyway_text),
            confirm = stringResource(R.string.action_install_anyway),
            onConfirm = {
                confirmAnyway = false
                manager.installAnyway(port.id)
            },
            onDismiss = { confirmAnyway = false },
        )
    }
    if (confirmReinstall) {
        ConfirmDialog(
            title = stringResource(R.string.reinstall_title),
            text = stringResource(R.string.reinstall_text),
            confirm = stringResource(R.string.action_reinstall),
            onConfirm = {
                confirmReinstall = false
                manager.uninstallToReinstall(port)?.let(context::startSafely)
            },
            onDismiss = { confirmReinstall = false },
        )
    }

    if (picking) {
        LinkPickerDialog(
            manager = manager,
            onPick = { app ->
                picking = false
                // A build of another version is linked as that one.
                val match = manager.matchingFork(port, app.packageName)
                if (match != null) manager.link(port, match, app.packageName) else mismatch = app
            },
            onDismiss = { picking = false },
        )
    }
    mismatch?.let { app ->
        LinkMismatchDialog(
            app = app,
            portName = port.name,
            onLinkAnyway = {
                manager.link(port, fork, app.packageName)
                mismatch = null
            },
            onDismiss = { mismatch = null },
        )
    }

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
                TextButton(onClick = { manager.pause(port.id) }, modifier = Modifier.focusRing()) {
                    Text(stringResource(R.string.action_pause))
                }
            }
            is InstallPhase.Paused -> {
                Text(stringResource(R.string.phase_paused))
                LinearProgressIndicator(progress = { phase.progress ?: 0f }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { manager.resume(port.id) }, modifier = Modifier.focusRing()) {
                        Text(stringResource(R.string.action_resume))
                    }
                    TextButton(onClick = { manager.cancelDownload(port.id) }, modifier = Modifier.focusRing()) {
                        Text(stringResource(R.string.cancel))
                    }
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        phase.error.canInstallAnyway() -> TextButton(onClick = { confirmAnyway = true }, modifier = Modifier.focusRing()) {
                            Text(stringResource(R.string.action_install_anyway))
                        }
                        phase.error.needsUninstall() && status != PortStatus.NotInstalled -> TextButton(
                            onClick = { confirmReinstall = true },
                            modifier = Modifier.focusRing(),
                        ) { Text(stringResource(R.string.action_reinstall)) }
                    }
                    TextButton(onClick = { manager.dismiss(port.id) }, modifier = Modifier.focusRing()) { Text(stringResource(R.string.dismiss)) }
                }
            }
            InstallPhase.Idle -> Unit
        }

        val busy = phase !is InstallPhase.Idle && phase !is InstallPhase.Failed && phase != InstallPhase.NeedsPermission
        val canInstall = fork.release != null && item.compatible && !busy
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            when (status) {
                PortStatus.NotInstalled -> Button(
                    onClick = { manager.install(port, fork) },
                    enabled = canInstall,
                    modifier = Modifier.weight(1f).focusRing(),
                ) {
                    val size = fork.release?.asset?.size
                    Text(if (size != null) stringResource(R.string.action_install_size, sizeText(size)) else stringResource(R.string.action_install))
                }
                is PortStatus.UpdateAvailable -> {
                    Button(onClick = { manager.install(port, fork) }, enabled = canInstall, modifier = Modifier.weight(1f).focusRing()) {
                        Text(stringResource(R.string.action_update, status.tag))
                    }
                    OpenButton(manager, item, fork, Modifier.weight(1f))
                }
                PortStatus.UpToDate, PortStatus.UnknownSigner -> OpenButton(manager, item, fork, Modifier.weight(1f))
                // The package is taken by another version: it has to go first.
                is PortStatus.OtherFork -> OutlinedButton(
                    onClick = { manager.uninstallIntent(port, status.fork)?.let(context::startSafely) },
                    modifier = Modifier.weight(1f).focusRing(),
                ) { Text(stringResource(R.string.action_uninstall_other, forkLabel(status.fork))) }
            }
        }
        when (status) {
            PortStatus.UnknownSigner ->
                Note(stringResource(if (linked) R.string.status_linked_mismatch else R.string.status_unknown_signer))
            is PortStatus.OtherFork -> Note(stringResource(R.string.status_other_fork, forkLabel(status.fork)))
            is PortStatus.UpdateAvailable -> if (manager.isHeld(port, fork)) {
                Note(stringResource(R.string.status_held, manager.installedTag(port, fork).orEmpty()))
            }
            else -> Unit
        }
        if (status.installed) {
            val installedTag = manager.installedTag(port, fork)
            fork.blockedReason(installedTag)?.let { blocked ->
                Text(
                    blocked.reason?.let { stringResource(R.string.status_blocked_reason, installedTag.orEmpty(), it) }
                        ?: stringResource(R.string.status_blocked, installedTag.orEmpty()),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (status == PortStatus.NotInstalled && !busy) {
            TextButton(onClick = { picking = true }, modifier = Modifier.focusRing()) {
                Text(stringResource(R.string.action_link))
            }
        }
        if (status.installed) {
            val installedSize by produceState<Long?>(null, port, fork, revision) {
                value = withContext(Dispatchers.IO) { manager.installedSize(port, fork) }
            }
            installedSize?.let { Note(stringResource(R.string.installed_size, sizeText(it))) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { manager.uninstallIntent(port, fork)?.let(context::startSafely) }, modifier = Modifier.focusRing()) {
                    Text(stringResource(R.string.action_uninstall))
                }
                TextButton(onClick = { manager.appInfoIntent(port, fork)?.let(context::startSafely) }, modifier = Modifier.focusRing()) {
                    Text(stringResource(R.string.action_app_info))
                }
                if (linked) {
                    TextButton(onClick = { manager.unlink(port, fork) }, modifier = Modifier.focusRing()) {
                        Text(stringResource(R.string.action_unlink))
                    }
                }
            }
        }
    }
}

private fun InstallError.canInstallAnyway() = this is InstallError.Check && ApkCheck.canOverride(result)

/** Android only installs the new build after the current one is removed. */
private fun InstallError.needsUninstall() = this == InstallError.Downgrade || this is InstallError.Conflict ||
    this is InstallError.Check && result is ApkCheckResult.ConflictsWithInstalled

@Composable
private fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        // The safe choice is the confirm slot, so a stray press of A does not go ahead.
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.focusRing()) { Text(stringResource(R.string.cancel)) }
        },
        dismissButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.focusRing()) {
                Text(confirm, color = MaterialTheme.colorScheme.error)
            }
        },
    )
}

@Composable
private fun OpenButton(manager: InstallManager, item: PortItem, fork: Fork, modifier: Modifier) {
    val context = LocalContext.current
    OutlinedButton(onClick = { manager.launchIntent(item.port, fork)?.let(context::startSafely) }, modifier = modifier.focusRing()) {
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
    is InstallError.NoSpace -> stringResource(R.string.error_no_space, sizeText(error.needed), sizeText(error.available))
    InstallError.Downgrade -> stringResource(R.string.error_downgrade)
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

/** How a version is called on screen: its own name, else its repository. */
internal fun forkLabel(fork: Fork): String = fork.name ?: fork.repo
