package io.github.kalasyan.recompa.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kalasyan.recompa.R
import io.github.kalasyan.recompa.core.update.AppRelease
import io.github.kalasyan.recompa.data.AppUpdater
import io.github.kalasyan.recompa.install.InstallPhase

/** Offers a newer stable version of Recompa and installs it in place. */
@Composable
fun UpdateBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val manager = rememberInstallManager()
    var release by remember { mutableStateOf<AppRelease?>(null) }
    var dismissed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { release = AppUpdater().check() }

    val available = release ?: return
    if (dismissed) return
    val phases by manager.phases.collectAsStateWithLifecycle()
    val phase = phases[AppUpdater.UPDATE_ID] ?: InstallPhase.Idle
    val port = remember(available) { AppUpdater.asPort(available, context.packageName) }

    Card(modifier.padding(16.dp).widthIn(max = 480.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.update_available, available.version), style = MaterialTheme.typography.titleSmall)
            when (phase) {
                is InstallPhase.Downloading, InstallPhase.Verifying, InstallPhase.Installing, InstallPhase.AwaitingUser ->
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                InstallPhase.NeedsPermission -> Text(stringResource(R.string.needs_permission))
                is InstallPhase.Failed -> Text(errorText(phase.error), color = MaterialTheme.colorScheme.error)
                InstallPhase.Idle -> Unit
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (phase == InstallPhase.NeedsPermission) {
                    Button(onClick = {
                        context.startSafely(manager.unknownSourcesSettingsIntent())
                        manager.dismiss(AppUpdater.UPDATE_ID)
                    }) { Text(stringResource(R.string.open_settings)) }
                } else {
                    Button(
                        onClick = { manager.install(port, port.forks.first()) },
                        enabled = phase == InstallPhase.Idle || phase is InstallPhase.Failed,
                    ) { Text(stringResource(R.string.update_install)) }
                }
                TextButton(onClick = { dismissed = true }) { Text(stringResource(R.string.update_later)) }
            }
        }
    }
}
