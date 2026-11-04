package io.github.kalasyan.recompa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.kalasyan.recompa.BuildConfig
import io.github.kalasyan.recompa.R
import io.github.kalasyan.recompa.core.catalog.Port
import io.github.kalasyan.recompa.core.catalog.PortSort
import io.github.kalasyan.recompa.core.install.PortStatus
import io.github.kalasyan.recompa.ui.theme.Accent
import io.github.kalasyan.recompa.ui.theme.Night

/**
 * Everything a library layout shows and can do. The library keeps the state (filters, search,
 * selection) and handles the shoulder buttons; each style only lays it out.
 */
internal class LibraryUi(
    /** Ports in the current filter, platform and search, in the order of [sort]. */
    val items: List<PortItem>,
    /** Ports in the current filter, genre and search, before picking a platform. */
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
    /** Genres of the current filter and search with their counts, in the catalog's order. */
    val genres: List<Pair<String, Int>>,
    /** How many ports the current filter and search have, whatever their genre. */
    val genreTotal: Int,
    val genre: String?,
    val onGenre: (String?) -> Unit,
    val sort: PortSort,
    /** Moves on to the next order, like the Select button. */
    val onNextSort: () -> Unit,
    /** Picks an order, from the sort button's menu. */
    val onSort: (PortSort) -> Unit,
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

/** Platform, type, first genre and whether the port runs here, in one line. */
@Composable
internal fun portFacts(item: PortItem): String = listOfNotNull(
    platformName(item.port.originalPlatform),
    typeName(item.port.type),
    item.port.genres.firstOrNull()?.let { genreName(it) },
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
internal fun LibraryActions(
    ui: LibraryUi,
    tint: Color = Color.White,
    container: Color = Color.Transparent,
    /**
     * Icons only for the sort and update-all buttons (the update one with its count), for
     * styles whose header has no room for their labels; they name the order elsewhere.
     */
    compact: Boolean = false,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (ui.offline) Pill(stringResource(R.string.offline_badge))
        if (compact && (ui.updates > 0 || ui.updatingAll)) {
            IconButton(onClick = ui.onUpdateAll, enabled = !ui.updatingAll, modifier = Modifier.focusRing(CircleShape)) {
                Box {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = if (ui.updatingAll) stringResource(R.string.updating_all) else stringResource(R.string.update_all, ui.updates),
                        tint = Accent,
                    )
                    if (!ui.updatingAll) {
                        Text(
                            "${ui.updates}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Night,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 8.dp, y = (-6).dp)
                                .background(Accent, CircleShape)
                                .padding(horizontal = 4.dp),
                        )
                    }
                }
            }
        } else if (ui.updates > 0 || ui.updatingAll) {
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
        val context = LocalContext.current
        val colors = IconButtonDefaults.iconButtonColors(containerColor = container, contentColor = tint)
        if (compact) {
            SortButton(ui.sort, ui.onSort, tint, iconOnly = true)
        } else {
            SortButton(ui.sort, ui.onSort, tint)
        }
        if (ui.genres.isNotEmpty() || ui.genre != null) GenreButton(ui, tint, iconOnly = compact)
        IconButton(onClick = { context.openKofi() }, colors = colors, modifier = Modifier.focusRing(CircleShape)) {
            Icon(KofiCup, contentDescription = stringResource(R.string.settings_kofi))
        }
        IconButton(onClick = ui.onSearch, colors = colors, modifier = Modifier.focusRing(CircleShape)) {
            Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search))
        }
        IconButton(onClick = ui.onSuggest, colors = colors, modifier = Modifier.focusRing(CircleShape)) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.suggest_title))
        }
        IconButton(onClick = ui.onSettings, colors = colors, modifier = Modifier.focusRing(CircleShape)) {
            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
        }
        // Diagnostics (hashes and signers of installed apps) only in nightlies, not in releases.
        if (BuildConfig.DEBUG) {
            IconButton(onClick = ui.onDiagnostics, colors = colors, modifier = Modifier.focusRing(CircleShape)) {
                Icon(Icons.Filled.Info, contentDescription = stringResource(R.string.diagnostics_title))
            }
        }
    }
}

/** The name of an order, as the sort button and the settings show it. */
internal val PortSort.label: Int
    get() = when (this) {
        PortSort.DEFAULT -> R.string.sort_default
        PortSort.GAME -> R.string.sort_game
        PortSort.ADDED -> R.string.sort_added
        PortSort.UPDATED -> R.string.sort_updated
        PortSort.RATING -> R.string.sort_rating
        PortSort.DOWNLOADS -> R.string.sort_downloads
    }

/**
 * Shows the library's order (or just its icon, with [iconOnly]) and opens a small menu right
 * there to pick another. Select on the gamepad still moves on to the next one.
 */
@Composable
internal fun SortButton(sort: PortSort, onPick: (PortSort) -> Unit, tint: Color = Color.White, iconOnly: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    Box {
        val description = stringResource(R.string.sort_title) + ": " + stringResource(sort.label)
        if (iconOnly) {
            IconButton(onClick = { open = true }, modifier = Modifier.focusRing(CircleShape)) {
                Icon(SortIcon, contentDescription = description, tint = tint)
            }
        } else {
            TextButton(onClick = { open = true }, modifier = Modifier.focusRing()) {
                Icon(SortIcon, contentDescription = stringResource(R.string.sort_title), tint = tint)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(sort.label), color = tint, maxLines = 1, style = MaterialTheme.typography.labelLarge)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Text(
                stringResource(R.string.sort_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            PortSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.label)) },
                    onClick = {
                        open = false
                        if (option != sort) onPick(option)
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = if (option == sort) Accent else Color.Transparent,
                        )
                    },
                    modifier = Modifier.focusRing(),
                )
            }
        }
    }
}

/**
 * Shows the genre the library is limited to (or just an icon, with [iconOnly]; lit when a genre
 * is picked) and opens a menu to pick another, with how many ports each has.
 */
@Composable
internal fun GenreButton(ui: LibraryUi, tint: Color = Color.White, iconOnly: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    val picked = ui.genre
    val label = picked?.let { genreName(it) } ?: stringResource(R.string.genre_title)
    Box {
        if (iconOnly) {
            IconButton(onClick = { open = true }, modifier = Modifier.focusRing(CircleShape)) {
                Icon(GenreIcon, contentDescription = stringResource(R.string.genre_title) + ": " + label, tint = if (picked != null) Accent else tint)
            }
        } else {
            TextButton(onClick = { open = true }, modifier = Modifier.focusRing()) {
                Icon(GenreIcon, contentDescription = null, tint = if (picked != null) Accent else tint)
                Spacer(Modifier.width(6.dp))
                Text(label, color = if (picked != null) Accent else tint, maxLines = 1, style = MaterialTheme.typography.labelLarge)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val options = listOf<Pair<String?, Int>>(null to ui.genreTotal) + ui.genres
            options.forEach { (genre, count) ->
                DropdownMenuItem(
                    text = { Text((genre?.let { genreName(it) } ?: stringResource(R.string.genre_all)) + " · " + count) },
                    onClick = {
                        open = false
                        if (genre != picked) ui.onGenre(genre)
                    },
                    leadingIcon = {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = if (genre == picked) Accent else Color.Transparent)
                    },
                    modifier = Modifier.focusRing(),
                )
            }
        }
    }
}

/** Shapes of different kinds (Material's "category"), for the genre button. */
internal val GenreIcon: ImageVector by lazy {
    ImageVector.Builder("Genre", 24.dp, 24.dp, 24f, 24f)
        .addPath(
            addPathNodes("M12,2l-5.5,9h11L12,2zM17.5,13c-2.49,0 -4.5,2.01 -4.5,4.5s2.01,4.5 4.5,4.5 4.5,-2.01 4.5,-4.5 -2.01,-4.5 -4.5,-4.5zM3,21.5h8v-8H3v8z"),
            fill = SolidColor(Color.Black),
        )
        .build()
}

/** Three lines getting shorter (Material's "sort"), for the sort button. */
internal val SortIcon: ImageVector by lazy {
    ImageVector.Builder("Sort", 24.dp, 24.dp, 24f, 24f)
        .addPath(addPathNodes("M3,18h6v-2H3v2zM3,6v2h18V6H3zM3,13h12v-2H3v2z"), fill = SolidColor(Color.Black))
        .build()
}

/** The order after this one, as Select and the sort button cycle them. */
internal fun PortSort.next(): PortSort = PortSort.entries[(ordinal + 1) % PortSort.entries.size]

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
