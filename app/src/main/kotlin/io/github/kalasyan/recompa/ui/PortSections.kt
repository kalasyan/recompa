package io.github.kalasyan.recompa.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kalasyan.recompa.R
import io.github.kalasyan.recompa.core.catalog.Port
import io.github.kalasyan.recompa.core.catalog.Release
import io.github.kalasyan.recompa.core.install.PortStatus
import io.github.kalasyan.recompa.data.AndroidDevice
import io.github.kalasyan.recompa.install.InstallError
import io.github.kalasyan.recompa.install.InstallPhase
import io.github.kalasyan.recompa.ui.theme.Accent
import io.github.kalasyan.recompa.ui.theme.Night

/**
 * The recommended fork's latest release and the earlier ones the catalog keeps, to go back
 * to one when an update breaks something.
 */
@Composable
fun VersionsSection(item: PortItem) {
    val port = item.port
    val fork = port.recommendedFork ?: return
    val latest = fork.release ?: return
    val manager = rememberInstallManager()
    val phases by manager.phases.collectAsStateWithLifecycle()
    val revision by manager.revision.collectAsStateWithLifecycle()
    val status = remember(port, revision) { manager.status(port) }
    val installedTag = remember(port, revision) { manager.installedTag(port) }
    val phase = phases[port.id] ?: InstallPhase.Idle
    val busy = phase !is InstallPhase.Idle && phase !is InstallPhase.Failed && phase != InstallPhase.NeedsPermission
    // Going back only makes sense for the fork that is installed, or before installing anything.
    val canPick = item.compatible && !busy && (status is PortStatus.NotInstalled || status is PortStatus.UpToDate ||
        status is PortStatus.UpdateAvailable)

    Text(
        stringResource(R.string.versions_help),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (phase is InstallPhase.Failed && phase.error == InstallError.Downgrade) {
        Text(stringResource(R.string.error_downgrade), color = MaterialTheme.colorScheme.error)
    }
    (listOf(latest) + fork.history).forEach { release ->
        VersionRow(
            release = release,
            latest = release == latest,
            installed = status != PortStatus.NotInstalled && release.tag == installedTag,
            enabled = canPick,
            onInstall = { manager.install(port, fork, release) },
        )
    }
}

@Composable
private fun VersionRow(release: Release, latest: Boolean, installed: Boolean, enabled: Boolean, onInstall: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(release.name.ifBlank { release.tag }, style = MaterialTheme.typography.bodyLarge)
                if (latest) {
                    Spacer(Modifier.width(8.dp))
                    Pill(stringResource(R.string.version_latest), color = Accent, textColor = Night)
                }
            }
            release.publishedAt?.take(10)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (installed) {
            Pill(stringResource(R.string.status_installed), color = MaterialTheme.colorScheme.secondary, textColor = Night)
        } else {
            OutlinedButton(onClick = onInstall, enabled = enabled, modifier = Modifier.focusRing()) {
                Text(stringResource(R.string.version_install))
            }
        }
    }
}

/** How the port runs on players' devices, this device first, and a button to add a report. */
@Composable
fun ReportsSection(port: Port) {
    val context = LocalContext.current
    val manager = rememberInstallManager()
    val device = remember { AndroidDevice.modelName() }
    val reports = port.reports.sortedByDescending { it.device.equals(device, ignoreCase = true) }
    val feedbackUrl = LocalFeedbackUrl.current
    var reporting by rememberSaveable { mutableStateOf(false) }
    if (reporting && feedbackUrl != null) {
        ReportDialog(port, device, manager.installedTag(port), feedbackUrl, onDismiss = { reporting = false })
    }

    if (reports.isEmpty()) {
        Text(stringResource(R.string.reports_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    reports.forEach { report ->
        val mine = report.device.equals(device, ignoreCase = true)
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (mine) stringResource(R.string.reports_this_device, report.device) else report.device,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (mine) Accent else MaterialTheme.colorScheme.onSurface,
                )
                report.lastTag?.let {
                    Text(
                        stringResource(R.string.reports_last_tag, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (report.great > 0) Pill(stringResource(R.string.reports_great, report.great), color = MaterialTheme.colorScheme.secondary, textColor = Night)
                if (report.playable > 0) Pill(stringResource(R.string.reports_playable, report.playable), color = Accent, textColor = Night)
                if (report.broken > 0) {
                    Pill(
                        stringResource(R.string.reports_broken, report.broken),
                        color = MaterialTheme.colorScheme.errorContainer,
                        textColor = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
    }
    OutlinedButton(
        onClick = {
            if (feedbackUrl != null) {
                reporting = true
            } else {
                context.startSafely(Intent(Intent.ACTION_VIEW, reportUrl(port, device, manager.installedTag(port))))
            }
        },
        modifier = Modifier.padding(top = 8.dp).focusRing(),
    ) { Text(stringResource(R.string.reports_add)) }
    Text(
        stringResource(if (feedbackUrl != null) R.string.reports_help_direct else R.string.reports_help),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The compatibility issue form (.github/ISSUE_TEMPLATE/compat.yml), filled in as far as possible. */
internal fun reportUrl(port: Port, device: String, tag: String?): Uri =
    Uri.parse("https://github.com/kalasyan/recompa/issues/new").buildUpon()
        .appendQueryParameter("template", "compat.yml")
        .appendQueryParameter("title", "[compat] ${port.name} · $device")
        .appendQueryParameter("port", port.id)
        .appendQueryParameter("device", device)
        .apply { if (tag != null) appendQueryParameter("version", tag) }
        .build()
