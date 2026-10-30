package io.github.kalasyan.recompa.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kalasyan.recompa.R
import io.github.kalasyan.recompa.data.AppLanguage

/** Language, frontend mode and update notifications. */
@Composable
fun SettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = rememberUserPrefs()
    val frontend by prefs.frontendMode.collectAsStateWithLifecycle()
    val notify by prefs.updateNotifications.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LanguagePicker()
                SettingSwitch(
                    title = stringResource(R.string.settings_frontend),
                    summary = stringResource(R.string.settings_frontend_help),
                    checked = frontend,
                    onChange = prefs::setFrontendMode,
                )
                if (frontend) {
                    OutlinedButton(
                        onClick = { context.startSafely(Intent(Settings.ACTION_HOME_SETTINGS)) },
                        modifier = Modifier.focusRing(),
                    ) { Text(stringResource(R.string.settings_choose_home)) }
                }
                OutlinedButton(
                    onClick = { context.startSafely(Intent(Settings.ACTION_SETTINGS)) },
                    modifier = Modifier.focusRing(),
                ) { Text(stringResource(R.string.settings_android)) }
                SettingSwitch(
                    title = stringResource(R.string.settings_notify),
                    summary = stringResource(R.string.settings_notify_help),
                    checked = notify,
                    onChange = prefs::setUpdateNotifications,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.focusRing()) { Text(stringResource(R.string.dismiss)) }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguagePicker() {
    val activity = LocalContext.current.findActivity()
    val current = remember { AppLanguage.current(activity) }
    Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppLanguage.supported.forEach { (code, name) ->
                FilterChip(
                    selected = code == current,
                    onClick = { if (code != current) AppLanguage.set(activity, code) },
                    label = { Text(name) },
                    modifier = Modifier.focusRing(),
                )
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> error("No activity")
}

@Composable
private fun SettingSwitch(title: String, summary: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .focusRing(RoundedCornerShape(12.dp))
            .toggleable(value = checked, onValueChange = onChange, role = Role.Switch)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/**
 * Asks once for the permission to show notifications (Android 13+), so the background check
 * can say when ports have updates. Waits until the welcome dialog has been seen.
 */
@Composable
fun NotificationPermissionRequest() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val prefs = rememberUserPrefs()
    val notify by prefs.updateNotifications.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val ready = remember { welcomeSeen(context) }
    LaunchedEffect(notify, ready) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (notify && ready && !granted && !prefs.askedNotificationPermission) {
            prefs.askedNotificationPermission = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
