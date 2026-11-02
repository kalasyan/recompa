package io.github.kalasyan.recompa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.kalasyan.recompa.R
import io.github.kalasyan.recompa.core.catalog.Port
import io.github.kalasyan.recompa.core.install.PortStatus
import io.github.kalasyan.recompa.ui.theme.Accent
import io.github.kalasyan.recompa.ui.theme.Night

/**
 * Everything a library layout shows and can do. The library keeps the state (filters, search,
 * selection) and handles the shoulder buttons; each style only lays it out.
 */
internal class LibraryUi(
    /** Ports in the current filter, platform and search, in catalog order. */
    val items: List<PortItem>,
    /** Ports in the current filter and search, before picking a platform. */
    val listCount: Int,
    val catalogEmpty: Boolean,
    val selected: PortItem?,
    val statuses: Map<String, PortStatus>,
    val favorites: Set<String>,
    val newIds: Set<String>,
    val filter: Filter,
    /** The filters on offer: New is left out when the catalog has no dates. */
    val filters: List<Filter>,
    /** How many ports of the whole catalog each filter has. */
    val filterCounts: Map<Filter, Int>,
    val onFilter: (Filter) -> Unit,
    /** Original platforms of the current list with their counts, the most common first. */
    val platforms: List<Pair<String, Int>>,
    val platform: String?,
    val onPlatform: (String?) -> Unit,
    val offline: Boolean,
    val updates: Int,
    val updatingAll: Boolean,
    val onUpdateAll: () -> Unit,
    val searching: Boolean,
    val query: String,
    val onSearch: () -> Unit,
    val onSuggest: () -> Unit,
    val onSettings: () -> Unit,
    val onDiagnostics: () -> Unit,
    /** Opens the port's page. */
    val onOpen: (PortItem) -> Unit,
    /** The gamepad moved onto this port. */
    val onSelect: (PortItem) -> Unit,
    /** Attach to the selected port's element, so focus comes back to it. */
    val focusTarget: FocusRequester,
    /** The second screen shows the selected port, so this one can leave its details out. */
    val secondShowing: Boolean,
    /** The search field while searching, nothing otherwise. */
    val searchBar: @Composable (Modifier) -> Unit,
    /** What to show when no port matches. */
    val emptyState: @Composable (Modifier) -> Unit,
) {
    val selectedIndex: Int get() = items.indexOfFirst { it.port.id == selected?.port?.id }

    fun isFavorite(item: PortItem) = item.port.id in favorites

    fun isInstalled(item: PortItem) = statuses[item.port.id].let { it != null && it != PortStatus.NotInstalled }
}

/** A port's corner badge: Update, Installed or New, or none. */
@Composable
internal fun LibraryUi.badge(item: PortItem): Pair<String, Color>? {
    val status = statuses[item.port.id]
    return when {
        status is PortStatus.UpdateAvailable -> stringResource(R.string.status_update) to Accent
        status != null && status != PortStatus.NotInstalled ->
            stringResource(R.string.status_installed) to MaterialTheme.colorScheme.secondary
        item.port.id in newIds -> stringResource(R.string.status_new) to Color.White
        else -> null
    }
}

/** Platform, type and whether the port runs here, in one line. */
@Composable
internal fun portFacts(item: PortItem): String = listOfNotNull(
    platformName(item.port.originalPlatform),
    typeName(item.port.type),
    when {
        item.port.recommendedFork == null -> stringResource(R.string.no_release)
        !item.compatible -> stringResource(R.string.not_compatible)
        else -> null
    },
).joinToString(" · ")

/** Players' reports added up: how many, and the share that play well or are playable. */
internal class ReportSummary(val total: Int, val great: Int, val playable: Int, val broken: Int) {
    val workingPercent: Int get() = if (total == 0) 0 else (100 * (great + playable) / total)
}

internal fun Port.reportSummary() = ReportSummary(
    total = reports.sumOf { it.total },
    great = reports.sumOf { it.great },
    playable = reports.sumOf { it.playable },
    broken = reports.sumOf { it.broken },
)

/** The port's wide art (hero, else first screenshot) over its platform colours. */
@Composable
internal fun PortHero(port: Port, modifier: Modifier = Modifier, alignment: Alignment = Alignment.TopCenter) {
    Box(modifier.background(platformBrush(port.originalPlatform))) {
        FallbackImage(port.art?.backdrops.orEmpty(), alignment = alignment, modifier = Modifier.matchParentSize())
    }
}

/** A small label in a corner of a cover or tile. */
@Composable
internal fun CornerBadge(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = Night,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.background(color, RoundedCornerShape(3.dp)).padding(horizontal = 5.dp, vertical = 2.dp),
    )
}

/**
 * The library's actions as icons: update all (when there are updates), search, suggest a port,
 * settings and diagnostics.
 */
@Composable
internal fun LibraryActions(ui: LibraryUi, tint: Color = Color.White, container: Color = Color.Transparent) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (ui.offline) Pill(stringResource(R.string.offline_badge))
        if (ui.updates > 0 || ui.updatingAll) {
            TextButton(onClick = ui.onUpdateAll, enabled = !ui.updatingAll, modifier = Modifier.focusRing()) {
                Icon(Icons.Filled.Refresh, contentDescription = null, tint = Accent)
                Spacer(Modifier.width(6.dp))
                Text(
                    if (ui.updatingAll) stringResource(R.string.updating_all) else stringResource(R.string.update_all, ui.updates),
                    color = Accent,
                    maxLines = 1,
                )
            }
        }
        val colors = IconButtonDefaults.iconButtonColors(containerColor = container, contentColor = tint)
        IconButton(onClick = ui.onSearch, colors = colors, modifier = Modifier.focusRing(CircleShape)) {
            Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search))
        }
        IconButton(onClick = ui.onSuggest, colors = colors, modifier = Modifier.focusRing(CircleShape)) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.suggest_title))
        }
        IconButton(onClick = ui.onSettings, colors = colors, modifier = Modifier.focusRing(CircleShape)) {
            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
        }
        IconButton(onClick = ui.onDiagnostics, colors = colors, modifier = Modifier.focusRing(CircleShape)) {
            Icon(Icons.Filled.Info, contentDescription = stringResource(R.string.diagnostics_title))
        }
    }
}

/** A gamepad button and what it does, for the hint lines at the bottom of some styles. */
@Composable
internal fun ButtonHint(button: String, label: String, color: Color = Color.White) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            button,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier
                .background(color.copy(alpha = 0.18f), CircleShape)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = color.copy(alpha = 0.85f))
    }
}
