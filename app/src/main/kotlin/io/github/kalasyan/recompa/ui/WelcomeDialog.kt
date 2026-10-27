package io.github.kalasyan.recompa.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.kalasyan.recompa.R

private const val PREFS = "welcome"
private const val SEEN = "seen"

/** Explains what Recompa does and the one permission it needs, on first launch only. */
@Composable
fun WelcomeDialog() {
    val context = LocalContext.current
    val manager = rememberInstallManager()
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var show by remember { mutableStateOf(!prefs.getBoolean(SEEN, false)) }
    if (!show) return

    fun close() {
        prefs.edit().putBoolean(SEEN, true).apply()
        show = false
    }

    AlertDialog(
        onDismissRequest = ::close,
        title = { Text(stringResource(R.string.welcome_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.welcome_what))
                Text(stringResource(R.string.welcome_files))
                if (!manager.canInstall()) Text(stringResource(R.string.welcome_permission))
            }
        },
        confirmButton = {
            if (manager.canInstall()) {
                TextButton(onClick = ::close) { Text(stringResource(R.string.welcome_start)) }
            } else {
                TextButton(onClick = {
                    context.startSafely(manager.unknownSourcesSettingsIntent())
                    close()
                }) { Text(stringResource(R.string.open_settings)) }
            }
        },
        dismissButton = if (manager.canInstall()) null else {
            { TextButton(onClick = ::close) { Text(stringResource(R.string.welcome_later)) } }
        },
    )
}
