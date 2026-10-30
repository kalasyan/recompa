package io.github.kalasyan.recompa.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.kalasyan.recompa.R
import io.github.kalasyan.recompa.core.catalog.Port
import io.github.kalasyan.recompa.core.catalog.PortSuggestion

/**
 * Lets players suggest a port for the catalog: they paste its GitHub repository and Recompa
 * opens the suggestion issue form (.github/ISSUE_TEMPLATE/port.yml) filled in, so they only
 * have to send it. The catalog maintainer approves it from the private admin panel.
 */
@Composable
fun SuggestPortDialog(ports: List<Port>, initialGame: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var url by rememberSaveable { mutableStateOf("") }
    var game by rememberSaveable { mutableStateOf(initialGame) }
    var notes by rememberSaveable { mutableStateOf("") }
    var tried by rememberSaveable { mutableStateOf(false) }

    val repo = PortSuggestion.repoFromUrl(url)
    val existing = repo?.let { PortSuggestion.portWithRepo(it, ports) }
    val problem = when {
        repo == null && (tried || url.isNotBlank() && "/" in url) -> stringResource(R.string.suggest_bad_url)
        existing != null -> stringResource(R.string.suggest_already, existing.name)
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.suggest_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.suggest_help), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.suggest_url)) },
                    placeholder = { Text("https://github.com/…") },
                    isError = problem != null,
                    supportingText = problem?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = game,
                    onValueChange = { game = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.suggest_game)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.suggest_notes)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.suggest_github_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    tried = true
                    if (repo != null && existing == null) {
                        context.startSafely(Intent(Intent.ACTION_VIEW, suggestionUrl(repo, game.trim(), notes.trim())))
                        onDismiss()
                    }
                },
                modifier = Modifier.focusRing(),
            ) { Text(stringResource(R.string.suggest_send)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.focusRing()) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** The suggestion issue form, filled in. The admin panel reads the same field labels. */
private fun suggestionUrl(repo: String, game: String, notes: String): Uri =
    Uri.parse("https://github.com/kalasyan/recompa/issues/new").buildUpon()
        .appendQueryParameter("template", "port.yml")
        .appendQueryParameter("title", "[port] ${game.ifEmpty { repo }}")
        .appendQueryParameter("repo", "https://github.com/$repo")
        .apply { if (game.isNotEmpty()) appendQueryParameter("game", game) }
        .apply { if (notes.isNotEmpty()) appendQueryParameter("notes", notes) }
        .build()
