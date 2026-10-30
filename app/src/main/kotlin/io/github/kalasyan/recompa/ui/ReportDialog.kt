package io.github.kalasyan.recompa.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.kalasyan.recompa.R
import io.github.kalasyan.recompa.core.catalog.Port
import io.github.kalasyan.recompa.data.FeedbackClient
import io.github.kalasyan.recompa.data.FeedbackResult
import kotlinx.coroutines.launch

/**
 * The catalog's feedback endpoint, or null when reports and suggestions still go through the
 * GitHub issue forms (the player needs an account there).
 */
val LocalFeedbackUrl = staticCompositionLocalOf<String?> { null }

private val results = listOf(
    "great" to R.string.report_great,
    "playable" to R.string.report_playable,
    "broken" to R.string.report_broken,
)

/**
 * Sends a compatibility report through [feedbackUrl], without a GitHub account. [onSent] gets
 * the result, device and version that were sent, to show the report right away.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportDialog(
    port: Port,
    device: String,
    installedTag: String?,
    feedbackUrl: String,
    onSent: (result: String, device: String, tag: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var result by rememberSaveable { mutableStateOf<String?>(null) }
    var model by rememberSaveable { mutableStateOf(device) }
    var version by rememberSaveable { mutableStateOf(installedTag.orEmpty()) }
    var notes by rememberSaveable { mutableStateOf("") }
    var sending by rememberSaveable { mutableStateOf(false) }
    var failure by rememberSaveable { mutableStateOf<String?>(null) }
    var tried by rememberSaveable { mutableStateOf(false) }
    val sentText = stringResource(R.string.report_sent)
    val messages = feedbackMessages()

    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        title = { Text(stringResource(R.string.report_title, port.name)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.report_result), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    results.forEach { (key, label) ->
                        FilterChip(
                            selected = result == key,
                            onClick = { result = key },
                            label = { Text(stringResource(label)) },
                            modifier = Modifier.focusRing(),
                        )
                    }
                }
                if (tried && result == null) {
                    Text(stringResource(R.string.report_pick_result), color = MaterialTheme.colorScheme.error)
                }
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.report_device)) },
                    isError = tried && model.isBlank(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = version,
                    onValueChange = { version = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.report_version)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it.take(1000) },
                    label = { Text(stringResource(R.string.report_notes)) },
                    placeholder = { Text(stringResource(R.string.report_notes_hint)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                failure?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error)
                    TextButton(
                        onClick = {
                            context.startSafely(Intent(Intent.ACTION_VIEW, reportUrl(port, model.trim(), version.trim().ifEmpty { null })))
                            onDismiss()
                        },
                        modifier = Modifier.focusRing(),
                    ) { Text(stringResource(R.string.feedback_on_github)) }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !sending,
                onClick = {
                    tried = true
                    val picked = result ?: return@TextButton
                    if (model.isBlank()) return@TextButton
                    sending = true
                    failure = null
                    scope.launch {
                        val sent = FeedbackClient(feedbackUrl)
                            .sendReport(port.id, model.trim(), version.trim().ifEmpty { null }, picked, notes.trim())
                        sending = false
                        if (sent == FeedbackResult.Sent) {
                            onSent(picked, model.trim(), version.trim().ifEmpty { null })
                            Toast.makeText(context, sentText, Toast.LENGTH_LONG).show()
                            onDismiss()
                        } else {
                            failure = messages(sent)
                        }
                    }
                },
                modifier = Modifier.focusRing(),
            ) { Text(stringResource(if (sending) R.string.feedback_sending else R.string.feedback_send)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !sending, modifier = Modifier.focusRing()) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

/** Why sending failed, in words, for each [FeedbackResult] other than [FeedbackResult.Sent]. */
@Composable
internal fun feedbackMessages(): (FeedbackResult) -> String {
    val tooMany = stringResource(R.string.feedback_too_many)
    val notFound = stringResource(R.string.suggest_repo_not_found)
    val failed = stringResource(R.string.feedback_failed)
    return { result ->
        when (result) {
            FeedbackResult.TooMany -> tooMany
            FeedbackResult.RepoNotFound -> notFound
            else -> failed
        }
    }
}
