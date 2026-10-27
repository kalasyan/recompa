package io.github.kalasyan.recompa.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.kalasyan.recompa.R
import io.github.kalasyan.recompa.assets.AssetFiles
import io.github.kalasyan.recompa.core.catalog.Asset
import io.github.kalasyan.recompa.core.catalog.DeliveryMode
import io.github.kalasyan.recompa.core.catalog.Port
import io.github.kalasyan.recompa.core.rom.RomMatch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File

/** Where the user is in preparing one of a port's files. */
private sealed interface AssetStep {
    data object Idle : AssetStep
    data object Working : AssetStep
    data class Ready(val file: File, val fileName: String, val verified: Boolean, val sha1: String?) : AssetStep
    data class WrongVersion(val sha1: String) : AssetStep
    data object BadArchive : AssetStep
    data object Failed : AssetStep
    data class Delivered(val ready: Ready) : AssetStep
}

/** Lets the user pick their own copy of each file a port needs, checks it and hands it to the port. */
@Composable
fun AssetWizard(port: Port) {
    val context = LocalContext.current
    val files = remember { AssetFiles(context.applicationContext) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        port.assets.forEach { AssetCard(port, it, files) }
        Text(
            stringResource(R.string.files_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AssetCard(port: Port, asset: Asset, files: AssetFiles) {
    val context = LocalContext.current
    val manager = rememberInstallManager()
    val scope = rememberCoroutineScope()
    var step by remember(port.id, asset.id) { mutableStateOf<AssetStep>(AssetStep.Idle) }

    fun launchStep(block: suspend () -> AssetStep) {
        val previous = step
        step = AssetStep.Working
        scope.launch {
            step = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (previous is AssetStep.Ready) previous else AssetStep.Failed
            }
        }
    }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) launchStep {
            when (val match = files.prepare(port, asset, uri)) {
                is RomMatch.Accepted -> AssetStep.Ready(match.file, match.fileName, verified = true, sha1 = null)
                is RomMatch.Unverifiable -> AssetStep.Ready(match.file, match.fileName, verified = false, sha1 = match.sha1)
                is RomMatch.WrongVersion -> AssetStep.WrongVersion(match.sha1)
                RomMatch.BadArchive -> AssetStep.BadArchive
            }
        }
    }
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val ready = step as? AssetStep.Ready
        if (uri != null && ready != null) launchStep { files.saveTo(ready.file, uri); AssetStep.Delivered(ready) }
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val ready = step as? AssetStep.Ready
        if (uri != null && ready != null) launchStep { files.saveToFolder(ready.file, ready.fileName, uri); AssetStep.Delivered(ready) }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(asset.label, style = MaterialTheme.typography.titleSmall)

            when (val current = step) {
                AssetStep.Idle -> Text(stringResource(R.string.asset_pick_help), style = MaterialTheme.typography.bodySmall)
                AssetStep.Working -> {
                    Text(stringResource(R.string.asset_working))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                is AssetStep.Ready -> {
                    if (current.verified) {
                        Text(stringResource(R.string.asset_verified, current.fileName), color = OkGreen)
                    } else {
                        Text(stringResource(R.string.asset_unverifiable, current.fileName))
                        current.sha1?.let { Sha1(it) }
                    }
                    deliveryHint(asset)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
                is AssetStep.WrongVersion -> {
                    Text(stringResource(R.string.asset_wrong_version), color = MaterialTheme.colorScheme.error)
                    Sha1(current.sha1)
                }
                AssetStep.BadArchive -> Text(
                    stringResource(if (asset.zipOk) R.string.asset_empty_zip else R.string.asset_no_zip),
                    color = MaterialTheme.colorScheme.error,
                )
                AssetStep.Failed -> Text(stringResource(R.string.asset_failed), color = MaterialTheme.colorScheme.error)
                is AssetStep.Delivered -> {
                    Text(stringResource(R.string.asset_delivered, current.ready.fileName), color = OkGreen)
                    asset.instructionsMd?.let { Text(it.trim(), style = MaterialTheme.typography.bodySmall) }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val ready = (step as? AssetStep.Ready) ?: (step as? AssetStep.Delivered)?.ready
                val working = step == AssetStep.Working
                if (ready != null) {
                    Button(enabled = !working, onClick = {
                        when (asset.delivery.mode) {
                            DeliveryMode.IN_APP_PICKER -> saveFile.launch(ready.fileName)
                            DeliveryMode.SHARED_FOLDER -> pickFolder.launch(null)
                            DeliveryMode.INTENT -> launchStep {
                                context.startSafely(files.shareIntent(port, ready.file, ready.fileName, manager.packageName(port)))
                                AssetStep.Delivered(ready)
                            }
                        }
                    }) { Text(stringResource(deliverLabel(asset.delivery.mode))) }
                }
                val pickLabel = stringResource(if (step == AssetStep.Idle) R.string.asset_pick else R.string.asset_pick_other)
                val onPick = { pickFile.launch(arrayOf("*/*")) }
                if (ready == null) Button(onClick = onPick, enabled = !working) { Text(pickLabel) }
                else OutlinedButton(onClick = onPick, enabled = !working) { Text(pickLabel) }
            }
        }
    }
}

private val OkGreen = Color(0xFF2E7D32)

@Composable
private fun Sha1(sha1: String) {
    SelectionContainer {
        Text(
            stringResource(R.string.asset_sha1, sha1),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun deliveryHint(asset: Asset): String? = when (asset.delivery.mode) {
    DeliveryMode.IN_APP_PICKER -> stringResource(R.string.asset_hint_picker)
    DeliveryMode.SHARED_FOLDER -> asset.delivery.path?.let { stringResource(R.string.asset_hint_folder, it) }
    DeliveryMode.INTENT -> null
}

private fun deliverLabel(mode: DeliveryMode): Int = when (mode) {
    DeliveryMode.IN_APP_PICKER -> R.string.asset_save
    DeliveryMode.SHARED_FOLDER -> R.string.asset_save_folder
    DeliveryMode.INTENT -> R.string.asset_send
}
