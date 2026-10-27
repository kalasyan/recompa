package io.github.kalasyan.recompa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kalasyan.recompa.R
import io.github.kalasyan.recompa.core.catalog.Fork
import io.github.kalasyan.recompa.core.device.CompatibilityIssue
import io.github.kalasyan.recompa.data.AndroidDevice
import io.github.kalasyan.recompa.ui.theme.Accent
import io.github.kalasyan.recompa.ui.theme.Night

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PortDetailScreen(item: PortItem, onBack: () -> Unit) {
    val port = item.port
    val manager = rememberInstallManager()
    val revision by manager.revision.collectAsStateWithLifecycle()
    val status = remember(port, revision) { manager.status(port) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        PortBackdrop(port, Modifier.fillMaxWidth().height(if (wide) maxHeight else 360.dp))
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp),
        ) {
            IconButton(
                onClick = onBack,
                colors = IconButtonDefaults.iconButtonColors(containerColor = Panel),
                modifier = Modifier.padding(top = 8.dp).focusRing(CircleShape),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            Spacer(Modifier.height(if (wide) 16.dp else 96.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                PortCover(port, Modifier.width(if (wide) 200.dp else 120.dp))
                // A translucent panel keeps the text readable over bright art.
                Column(
                    Modifier
                        .weight(1f)
                        .widthIn(max = 640.dp)
                        .background(Panel, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PortTitle(
                        port,
                        logoHeight = if (wide) 96.dp else 64.dp,
                        style = if (wide) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        stringResource(R.string.original_game, port.game),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Pill(platformName(port.originalPlatform))
                        Pill(typeName(port.type))
                        StatusPill(item, status)
                    }
                    if (wide) Actions(item)
                    if (wide) port.description?.let { Description(it) }
                }
            }

            if (!wide) {
                Spacer(Modifier.height(16.dp))
                Column(
                    Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Actions(item)
                    port.description?.let { Description(it) }
                }
            }

            Spacer(Modifier.height(24.dp))
            Column(Modifier.widthIn(max = 880.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (port.assets.isNotEmpty()) {
                    Section(stringResource(R.string.section_files)) { AssetWizard(port) }
                }
                Section(stringResource(R.string.section_requirements)) {
                    Text(stringResource(R.string.req_abi, port.requirements.abi.joinToString()))
                    Text(stringResource(R.string.req_sdk, AndroidDevice.versionName(port.requirements.minSdk)))
                    if (port.requirements.vulkan) Text(stringResource(R.string.req_vulkan))
                }
                Section(stringResource(R.string.section_forks)) {
                    port.forks.forEach { fork -> ForkRow(fork, recommended = fork == port.recommendedFork) }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun Actions(item: PortItem) {
    Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item.issues.forEach { issue ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Text(issueText(issue), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (item.port.recommendedFork != null) {
            Box(Modifier.widthIn(max = 420.dp)) { InstallActions(item) }
        } else {
            Text(stringResource(R.string.no_release), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Description(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
        modifier = Modifier.widthIn(max = 640.dp),
    )
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun ForkRow(fork: Fork, recommended: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .padding(end = 12.dp)
                .width(8.dp)
                .height(8.dp)
                .background(if (recommended) Accent else Color.White.copy(alpha = 0.2f), CircleShape),
        )
        Column(Modifier.weight(1f)) {
            Text(fork.repo, style = MaterialTheme.typography.bodyLarge)
            val release = fork.release
            Text(
                if (release == null) stringResource(R.string.no_release)
                else stringResource(R.string.fork_release, release.tag, release.asset.name),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (recommended) Pill(stringResource(R.string.fork_recommended), color = Accent, textColor = Night)
            if (fork.official) Pill(stringResource(R.string.fork_official))
            if (fork.testedOn.isNotEmpty()) Pill(stringResource(R.string.fork_tested_on, fork.testedOn.joinToString()))
        }
    }
}

@Composable
private fun issueText(issue: CompatibilityIssue): String = when (issue) {
    is CompatibilityIssue.UnsupportedAbi -> stringResource(R.string.issue_abi, issue.required.joinToString())
    is CompatibilityIssue.AndroidTooOld -> stringResource(R.string.issue_sdk, AndroidDevice.versionName(issue.requiredSdk))
    CompatibilityIssue.NoVulkan -> stringResource(R.string.issue_vulkan)
}
