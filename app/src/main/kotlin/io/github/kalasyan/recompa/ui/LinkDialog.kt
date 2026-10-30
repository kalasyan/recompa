package io.github.kalasyan.recompa.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.kalasyan.recompa.R
import io.github.kalasyan.recompa.install.InstallManager
import io.github.kalasyan.recompa.install.LinkCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Lets the user pick the installed app that is this port, when Recompa did not install it. */
@Composable
fun LinkPickerDialog(manager: InstallManager, onPick: (LinkCandidate) -> Unit, onDismiss: () -> Unit) {
    var apps by remember { mutableStateOf<List<LinkCandidate>?>(null) }
    LaunchedEffect(manager) {
        apps = withContext(Dispatchers.IO) { manager.linkCandidates() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.link_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.link_help), style = MaterialTheme.typography.bodySmall)
                val list = apps
                when {
                    list == null -> Text(stringResource(R.string.loading_apps))
                    list.isEmpty() -> Text(stringResource(R.string.link_empty))
                    else -> LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(list, key = { it.packageName }) { app ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .focusRing(RoundedCornerShape(8.dp))
                                    .clickable { onPick(app) }
                                    .padding(horizontal = 8.dp, vertical = 10.dp),
                            ) {
                                Text(app.label, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    app.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.focusRing()) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/**
 * Warns that the picked app does not look like the one in the port's repository, so the user
 * does not uninstall it believing Recompa will put the same app back.
 */
@Composable
fun LinkMismatchDialog(app: LinkCandidate, portName: String, onLinkAnyway: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.link_mismatch_title)) },
        text = { Text(stringResource(R.string.link_mismatch, app.label, portName)) },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.focusRing()) { Text(stringResource(R.string.cancel)) }
        },
        dismissButton = {
            TextButton(onClick = onLinkAnyway, modifier = Modifier.focusRing()) { Text(stringResource(R.string.link_anyway)) }
        },
    )
}
