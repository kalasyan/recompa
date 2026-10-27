package io.github.kalasyan.recompa.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.kalasyan.recompa.R
import io.github.kalasyan.recompa.install.installedPackagesWithSigners
import io.github.kalasyan.recompa.install.signerSha256
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class AppEntry(val label: String, val packageName: String, val versionName: String?, val signers: List<String>) {
    /** The lines to paste into a fork in `catalog/ports/<id>.yaml`. */
    fun manifestSnippet(): String = buildString {
        appendLine("    package: $packageName")
        signers.firstOrNull()?.let { appendLine("    signer_sha256: $it") }
        append("    # versión instalada: ${versionName ?: "?"}")
    }
}

/**
 * Lists user-installed apps with their package name and signing certificate, so testers
 * can fill `package` and `signer_sha256` in the catalog without extra tools.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) { loadApps(context) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.diagnostics_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        val list = apps
        if (list == null) {
            Text(stringResource(R.string.loading_apps), Modifier.padding(padding).padding(16.dp))
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.diagnostics_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(list, key = { it.packageName }) { app ->
                Card(onClick = { copy(context, app.manifestSnippet()) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(app.label, style = MaterialTheme.typography.titleSmall)
                        Text("${app.packageName} · ${app.versionName ?: "?"}", style = MaterialTheme.typography.bodySmall)
                        app.signers.forEach {
                            Text(it, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }
}

private fun loadApps(context: Context): List<AppEntry> {
    val pm = context.packageManager
    return pm.installedPackagesWithSigners()
        .filter { info ->
            val app = info.applicationInfo ?: return@filter false
            app.flags and ApplicationInfo.FLAG_SYSTEM == 0 && info.packageName != context.packageName
        }
        .map { info ->
            AppEntry(
                label = info.applicationInfo?.loadLabel(pm)?.toString() ?: info.packageName,
                packageName = info.packageName,
                versionName = info.versionName,
                signers = info.signerSha256(),
            )
        }
        .sortedBy { it.label.lowercase() }
}

private fun copy(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText("Recompa", text))
    Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
}
