package io.github.kalasyan.recompa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kalasyan.recompa.R
import io.github.kalasyan.recompa.core.install.PortStatus
import io.github.kalasyan.recompa.data.AndroidDevice
import io.github.kalasyan.recompa.ui.theme.LocalPalette

/** One section of a port's page: a titled block, or a tab in the styles that use tabs. */
internal class PortTab(val title: String, val content: @Composable () -> Unit)

/** The sections of a port's page, in the order every style shows them. */
@Composable
internal fun portTabs(item: PortItem, galleryHeight: Dp): List<PortTab> {
    val port = item.port
    val screenshots = port.art?.screenshots.orEmpty()
    return buildList {
        if (screenshots.isNotEmpty()) {
            add(PortTab(stringResource(R.string.section_gallery)) { ScreenshotRow(screenshots, port.game, height = galleryHeight) })
        }
        if (port.assets.isNotEmpty()) add(PortTab(stringResource(R.string.section_files)) { AssetWizard(port) })
        add(
            PortTab(stringResource(R.string.section_requirements)) {
                Text(stringResource(R.string.req_abi, port.requirements.abi.joinToString()))
                Text(stringResource(R.string.req_sdk, AndroidDevice.versionName(port.requirements.minSdk)))
                if (port.requirements.vulkan) Text(stringResource(R.string.req_vulkan))
            },
        )
        if (port.recommendedFork?.release != null) add(PortTab(stringResource(R.string.section_versions)) { VersionsSection(item) })
        add(PortTab(stringResource(R.string.section_reports)) { ReportsSection(port) })
        add(
            PortTab(stringResource(R.string.section_forks)) {
                port.forks.forEach { fork -> ForkRow(fork, recommended = fork == port.recommendedFork) }
            },
        )
    }
}

/** One fact for the stats rows: a big value over a small label. */
internal class PortStat(val value: String, val label: String)

/** How well the port runs for players, how many reports, the installed or latest version, the size. */
@Composable
internal fun portStats(item: PortItem): List<PortStat> {
    val port = item.port
    val manager = rememberInstallManager()
    val revision by manager.revision.collectAsStateWithLifecycle()
    val status = remember(port, revision) { manager.status(port) }
    val installedTag = remember(port, revision) { manager.installedTag(port) }
    val reports = port.reportSummary()
    val release = port.recommendedFork?.release
    return buildList {
        if (reports.total > 0) {
            add(PortStat("${reports.workingPercent}%", stringResource(R.string.stat_working)))
            add(PortStat(reports.total.toString(), stringResource(R.string.stat_reports)))
        }
        val tag = (if (status != PortStatus.NotInstalled) installedTag else null) ?: release?.tag
        tag?.let { add(PortStat(it.removePrefix("v").take(16), stringResource(R.string.stat_version))) }
        release?.asset?.size?.let { add(PortStat(sizeText(it), stringResource(R.string.stat_size))) }
    }
}

/** [portStats] as a row of small boxes. */
@Composable
internal fun PortStats(
    item: PortItem,
    box: Color = Color.White.copy(alpha = 0.08f),
    valueStyle: TextStyle = MaterialTheme.typography.titleLarge,
    labelColor: Color = Color.White.copy(alpha = 0.7f),
) {
    val shape = RoundedCornerShape(LocalPalette.current.corner)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        portStats(item).forEach { stat ->
            Column(Modifier.background(box, shape).padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(stat.value, style = valueStyle, maxLines = 1)
                Text(stat.label, style = MaterialTheme.typography.labelMedium, color = labelColor)
            }
        }
    }
}

private fun sizeText(bytes: Long): String = when {
    bytes >= 1_048_576 -> "${(bytes + 524_288) / 1_048_576} MB"
    else -> "${((bytes + 512) / 1024).coerceAtLeast(1)} KB"
}
