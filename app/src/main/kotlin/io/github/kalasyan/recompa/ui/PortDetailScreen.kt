package io.github.kalasyan.recompa.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.kalasyan.recompa.R
import io.github.kalasyan.recompa.core.catalog.Fork
import io.github.kalasyan.recompa.core.device.CompatibilityIssue
import io.github.kalasyan.recompa.data.AndroidDevice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortDetailScreen(item: PortItem, onBack: () -> Unit) {
    val port = item.port
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(port.name) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            Text(stringResource(R.string.original_game, port.game), style = MaterialTheme.typography.titleMedium)
            port.description?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }

            item.issues.forEach { issue ->
                Spacer(Modifier.height(8.dp))
                Text(issueText(issue), color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(16.dp))
            Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.install_soon))
            }

            Section(stringResource(R.string.section_requirements)) {
                Text(stringResource(R.string.req_abi, port.requirements.abi.joinToString()))
                Text(stringResource(R.string.req_sdk, AndroidDevice.versionName(port.requirements.minSdk)))
                if (port.requirements.vulkan) Text(stringResource(R.string.req_vulkan))
            }

            Section(stringResource(R.string.section_forks)) {
                port.forks.forEach { fork -> ForkRow(fork, recommended = fork == port.recommendedFork) }
            }

            if (port.assets.isNotEmpty()) {
                Section(stringResource(R.string.section_files)) {
                    port.assets.forEach { Text("• ${it.label}") }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.files_disclaimer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Spacer(Modifier.height(24.dp))
    Text(title, style = MaterialTheme.typography.titleSmall)
    HorizontalDivider(Modifier.padding(vertical = 8.dp))
    content()
}

@Composable
private fun ForkRow(fork: Fork, recommended: Boolean) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row {
            Text(fork.repo, style = MaterialTheme.typography.bodyLarge)
            val tags = buildList {
                if (recommended) add(stringResource(R.string.fork_recommended))
                if (fork.official) add(stringResource(R.string.fork_official))
                if (!fork.verified) add(stringResource(R.string.fork_unverified))
            }
            if (tags.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Text(
                    tags.joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
        val release = fork.release
        Text(
            if (release == null) stringResource(R.string.no_release)
            else stringResource(R.string.fork_release, release.tag, release.asset.name),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun issueText(issue: CompatibilityIssue): String = when (issue) {
    is CompatibilityIssue.UnsupportedAbi -> stringResource(R.string.issue_abi, issue.required.joinToString())
    is CompatibilityIssue.AndroidTooOld -> stringResource(R.string.issue_sdk, AndroidDevice.versionName(issue.requiredSdk))
    CompatibilityIssue.NoVulkan -> stringResource(R.string.issue_vulkan)
}
