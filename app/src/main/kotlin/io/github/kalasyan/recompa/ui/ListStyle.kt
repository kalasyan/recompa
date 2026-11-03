package io.github.kalasyan.recompa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.github.kalasyan.recompa.R
import io.github.kalasyan.recompa.core.catalog.PortSort
import io.github.kalasyan.recompa.core.install.PortStatus
import io.github.kalasyan.recompa.ui.theme.Accent
import io.github.kalasyan.recompa.ui.theme.Night
import io.github.kalasyan.recompa.ui.theme.Rajdhani

/** A row of the List library: a letter heading or a port. */
private sealed interface ListEntry {
    data class Letter(val letter: String) : ListEntry
    data class Entry(val item: PortItem) : ListEntry
}

private fun initial(name: String): String = name.firstOrNull()?.uppercaseChar()?.takeIf { it.isLetter() }?.toString() ?: "#"

/**
 * List: every port in the chosen order (with letter headings when by name) on the left; the selected port's art, cover,
 * facts and screenshots on the right. Like the menus of retro frontends.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ListLibrary(ui: LibraryUi) {
    val selected = ui.selected
    val entries = remember(ui.items, ui.sort) {
        buildList {
            var last: String? = null
            // By name the list is one A to Z run with letter headings, compatible or not, so each
            // letter shows up once; other orders have no headings.
            val key: ((PortItem) -> String)? = when (ui.sort) {
                PortSort.DEFAULT -> { item -> item.port.name.lowercase() }
                PortSort.GAME -> { item -> PortSort.sortKey(item.port) }
                else -> null
            }
            (if (key != null) ui.items.sortedBy(key) else ui.items).forEach { item ->
                if (key != null) {
                    val letter = initial(key(item))
                    if (letter != last) add(ListEntry.Letter(letter))
                    last = letter
                }
                add(ListEntry.Entry(item))
            }
        }
    }
    val listState = rememberLazyListState()
    FocusSelectedOnChange(ui) {
        val index = entries.indexOfFirst { it is ListEntry.Entry && it.item == selected }
        // One row of context above, which is often the letter heading.
        listState.scrollToItem((index - 1).coerceAtLeast(0))
    }
    val rail = Night.copy(alpha = 1f).let { Color(red = it.red * 0.7f, green = it.green * 0.7f, blue = it.blue * 0.7f) }
    val preview = selected != null && !ui.secondShowing

    val portrait = isPortrait()
    val railColumn: @Composable (Modifier) -> Unit = { modifier ->
        Column(
            modifier
                .background(rail)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("◀", color = Accent, fontSize = 11.sp)
                Text(
                    (ui.platform?.let { platformName(it) } ?: stringResource(R.string.list_all_platforms)).uppercase(),
                    style = TextStyle(fontFamily = Rajdhani, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 2.sp),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp).weight(1f, fill = false),
                )
                Text("▶", color = Accent, fontSize = 11.sp)
                Spacer(Modifier.weight(1f))
                Text(
                    "${ui.items.size} / ${ui.filterCounts[Filter.ALL] ?: ui.items.size}",
                    style = TextStyle(fontFamily = Rajdhani, fontWeight = FontWeight.Bold, fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!preview) LibraryActions(ui, compact = portrait)
            }
            Row(
                Modifier.padding(horizontal = 10.dp, vertical = 4.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                ui.filters.forEach { f -> UnderlineTab(stringResource(f.label), f == ui.filter, color = Accent) { ui.onFilter(f) } }
            }
            ui.searchBar(Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth())
            if (ui.items.isEmpty()) {
                ui.emptyState(Modifier.weight(1f))
            } else {
                LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 8.dp), modifier = Modifier.weight(1f)) {
                    itemsIndexed(entries, key = { _, e -> if (e is ListEntry.Entry) e.item.port.id else "#" + (e as ListEntry.Letter).letter }) { _, entry ->
                        when (entry) {
                            is ListEntry.Letter -> Text(
                                entry.letter,
                                style = TextStyle(fontFamily = Rajdhani, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 2.sp),
                                color = Accent,
                                modifier = Modifier.padding(start = 16.dp, top = 6.dp, bottom = 2.dp),
                            )
                            is ListEntry.Entry -> ListRow(ui, entry.item, showPlatform = !preview || portrait)
                        }
                    }
                }
            }
            FlowRow(
                Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.25f)).padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ButtonHint("L1/R1", stringResource(R.string.hint_filter))
                ButtonHint("L2/R2", stringResource(R.string.hint_platform))
                ButtonHint("Y", stringResource(R.string.favorite))
                ButtonHint("Select", stringResource(R.string.sort_title))
            }
        }
    }
    if (portrait) {
        // Portrait: the selected port as a banner on top, the list under it with the whole width.
        Column(Modifier.fillMaxSize().background(Night).windowInsetsPadding(WindowInsets.safeDrawing)) {
            if (preview && selected != null) ListPreview(ui, selected, Modifier.fillMaxWidth().height(230.dp), banner = true)
            railColumn(Modifier.fillMaxWidth().weight(1f))
        }
        return
    }
    Row(Modifier.fillMaxSize().background(Night)) {
        railColumn(Modifier.then(if (preview) Modifier.fillMaxWidth(0.44f) else Modifier.fillMaxWidth()).fillMaxHeight())
        if (preview && selected != null) ListPreview(ui, selected, Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun ListRow(ui: LibraryUi, item: PortItem, showPlatform: Boolean) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val unavailable = !item.compatible || item.port.recommendedFork == null
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (item == ui.selected) Modifier.focusRequester(ui.focusTarget) else Modifier)
            .onFocusChanged { if (it.isFocused) ui.onSelect(item) }
            // Right would jump to the preview's buttons; the list is navigated up and down.
            .focusProperties { right = FocusRequester.Cancel }
            .background(
                if (focused) Brush.horizontalGradient(listOf(Accent.copy(alpha = 0.32f), Accent.copy(alpha = 0.04f)))
                else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent)),
            )
            .clickable(interactionSource = interaction, indication = null) { ui.onOpen(item) }
            .padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).height(34.dp).background(if (focused) Accent else Color.Transparent))
        Spacer(Modifier.width(13.dp))
        PortCover(item.port, Modifier.width(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            item.port.name,
            style = TextStyle(fontFamily = Rajdhani, fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium, fontSize = 16.sp),
            color = when {
                focused -> Color.White
                unavailable -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (showPlatform) {
            Text(
                platformName(item.port.originalPlatform),
                style = TextStyle(fontFamily = Rajdhani, fontWeight = FontWeight.Medium, fontSize = 14.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
        if (ui.isFavorite(item)) {
            Icon(Icons.Filled.Favorite, contentDescription = stringResource(R.string.favorite), tint = Color(0xFFFF6B8A), modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
        }
        ui.badge(item)?.let { (text, color) -> CornerBadge(text, color) }
    }
}

/** What the selected port is and how it stands here, as a label and a value per line. */
@Composable
private fun portFactsTable(ui: LibraryUi?, item: PortItem): List<Pair<String, String>> {
    val port = item.port
    val reports = port.reportSummary()
    val status = ui?.statuses?.get(port.id)
    return listOf(
        stringResource(R.string.fact_original) to "${port.game} · ${platformName(port.originalPlatform)}",
        stringResource(R.string.fact_type) to typeName(port.type),
        stringResource(R.string.fact_status) to when {
            status is PortStatus.UpdateAvailable -> stringResource(R.string.status_update)
            status != null && status != PortStatus.NotInstalled -> stringResource(R.string.status_installed)
            port.recommendedFork == null -> stringResource(R.string.no_release)
            !item.compatible -> stringResource(R.string.not_compatible)
            else -> stringResource(R.string.status_ready)
        },
        stringResource(R.string.fact_reports) to
            if (reports.total == 0) stringResource(R.string.reports_none)
            else stringResource(R.string.reports_summary, reports.total, reports.workingPercent),
    )
}

/** The selected port beside the list, or above it as a shorter [banner] in portrait. */
@Composable
private fun ListPreview(ui: LibraryUi, item: PortItem, modifier: Modifier, banner: Boolean = false) {
    val port = item.port
    Box(modifier) {
        PortHero(port, Modifier.fillMaxWidth().fillMaxHeight(if (banner) 1f else 0.55f))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.15f to Color.Transparent, (if (banner) 0.8f else 0.55f) to Night)))
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp)) {
            if (banner) {
                // The banner is narrow: the buttons get their own line so a wide logo can't run under them.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    LibraryActions(ui, container = Night.copy(alpha = 0.5f), compact = true)
                }
            } else {
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.weight(1f).padding(top = 4.dp)) {
                        PortTitle(port, logoHeight = 52.dp, style = MaterialTheme.typography.headlineSmall)
                    }
                    LibraryActions(ui, container = Night.copy(alpha = 0.5f))
                }
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.Bottom) {
                PortCover(port, Modifier.width(if (banner) 64.dp else 84.dp))
                Spacer(Modifier.width(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(port.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    portFactsTable(ui, item).forEach { (label, value) ->
                        Row {
                            Text(label, style = factStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(88.dp))
                            Text(value, style = factStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            // The banner has no room for the description and screenshots.
            if (!banner) port.description?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            val shots = if (banner) emptyList() else port.art?.screenshots.orEmpty().take(3)
            if (shots.isNotEmpty()) {
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    shots.forEach { url ->
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.weight(1f).aspectRatio(16f / 9f).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.06f)),
                        )
                    }
                }
            }
        }
    }
}

private val factStyle = TextStyle(fontFamily = Rajdhani, fontWeight = FontWeight.Medium, fontSize = 14.sp)

/** How a page with a side menu of sections looks: List and Arcade share the layout. */
internal class SideTabsSkin(
    val tabText: TextStyle,
    val railColor: Color,
    val selectedColor: Color,
    val uppercase: Boolean = false,
    val marker: String? = null,
    val background: @Composable BoxScope.() -> Unit,
    val frame: Modifier = Modifier,
)

/**
 * A port's page with its sections in a side menu, the chosen one on the right. The first entry
 * is the overview: cover, facts, actions and description. Moving through the menu with the
 * D-pad shows each section; Right goes into it.
 */
@Composable
internal fun SideTabsPage(item: PortItem, header: @Composable () -> Unit, skin: SideTabsSkin) {
    val port = item.port
    val tabs = listOf(PortTab(stringResource(R.string.section_overview)) { Overview(item) }) + portTabs(item, galleryHeight = 130.dp)
    var tab by rememberSaveable(port.id) { mutableStateOf(0) }
    val scroll = rememberScrollState()
    val content: @Composable ColumnScope.() -> Unit = {
        val current = tabs.getOrNull(tab) ?: tabs.first()
        if (tab != 0) {
            Text(
                if (skin.uppercase) current.title.uppercase() else current.title,
                style = skin.tabText.copy(fontSize = skin.tabText.fontSize * 1.4f),
                color = Color.White,
            )
            Spacer(Modifier.height(6.dp))
        }
        current.content()
        Spacer(Modifier.height(24.dp))
    }
    if (isPortrait()) {
        // Portrait: the menu becomes a row of tabs under the title, the section gets the width.
        Box(Modifier.fillMaxSize().background(Night)) {
            skin.background(this)
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).then(skin.frame)) {
                Column(Modifier.fillMaxWidth().background(skin.railColor).padding(top = 12.dp)) {
                    Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        header()
                        Box(Modifier.weight(1f).padding(start = 12.dp)) {
                            PortTitle(port, logoHeight = 40.dp, style = skin.tabText.copy(fontSize = skin.tabText.fontSize * 1.2f, color = Color.White))
                        }
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp, start = 6.dp, end = 6.dp)) {
                        tabs.forEachIndexed { index, t ->
                            TopTab(
                                if (skin.uppercase) t.title.uppercase() else t.title,
                                selected = index == tab,
                                skin = skin,
                                onSelect = { tab = index },
                            )
                        }
                    }
                }
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scroll)
                        .dpadScroll(scroll)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    content = content,
                )
            }
        }
        return
    }
    Box(Modifier.fillMaxSize().background(Night)) {
        skin.background(this)
        Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).then(skin.frame)) {
            Column(
                Modifier.width(196.dp).fillMaxHeight().background(skin.railColor).padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Box(Modifier.padding(horizontal = 14.dp)) { header() }
                Box(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    PortTitle(port, logoHeight = 40.dp, style = skin.tabText.copy(fontSize = skin.tabText.fontSize * 1.2f, color = Color.White))
                }
                tabs.forEachIndexed { index, t ->
                    SideTab(
                        if (skin.uppercase) t.title.uppercase() else t.title,
                        selected = index == tab,
                        skin = skin,
                        onSelect = { tab = index },
                    )
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(scroll)
                    .dpadScroll(scroll)
                    .padding(horizontal = 22.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                content = content,
            )
        }
    }
}

/** A section of the side-menu pages as a tab along the top, for portrait screens. */
@Composable
private fun TopTab(label: String, selected: Boolean, skin: SideTabsSkin, onSelect: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(
        Modifier
            .keepInView(selected)
            .onFocusChanged { if (it.isFocused) onSelect() }
            .background(if (focused) skin.selectedColor.copy(alpha = 0.28f) else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, onClick = onSelect),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            label,
            style = skin.tabText,
            color = if (selected || focused) Color.White else Color.White.copy(alpha = 0.6f),
            maxLines = 1,
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 6.dp),
        )
        Box(Modifier.width(if (selected) 28.dp else 0.dp).height(3.dp).background(skin.selectedColor))
    }
}

@Composable
private fun SideTab(label: String, selected: Boolean, skin: SideTabsSkin, onSelect: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .onFocusChanged { if (it.isFocused) onSelect() }
            .background(if (focused || selected) skin.selectedColor.copy(alpha = if (focused) 0.28f else 0.12f) else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).height(32.dp).background(if (selected) skin.selectedColor else Color.Transparent))
        Text(
            (if (skin.marker != null) (if (selected) skin.marker else "  ") + " " else "") + label,
            style = skin.tabText,
            color = if (selected || focused) Color.White else Color.White.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 14.dp),
        )
    }
}

/** First section of the side-menu pages: cover, facts, actions, stats and description. */
@Composable
private fun Overview(item: PortItem) {
    val port = item.port
    // In portrait the actions and numbers go under the cover, with the whole width.
    val portrait = isPortrait()
    Row(horizontalArrangement = Arrangement.spacedBy(if (portrait) 14.dp else 18.dp)) {
        PortCover(port, Modifier.width(if (portrait) 96.dp else 130.dp))
        Column(Modifier.weight(1f).widthIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(port.name, style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.original_game, port.game), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(portFacts(item), style = MaterialTheme.typography.labelLarge, color = Accent)
            if (!portrait) {
                PortActions(item)
                PortStats(item)
            }
        }
    }
    if (portrait) {
        PortActions(item)
        PortStats(item)
    }
    port.description?.let {
        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f), modifier = Modifier.padding(top = 10.dp).widthIn(max = 680.dp))
    }
}

@Composable
internal fun ListPortPage(item: PortItem, header: @Composable () -> Unit) {
    val night = Night
    SideTabsPage(
        item,
        header,
        SideTabsSkin(
            tabText = TextStyle(fontFamily = Rajdhani, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 0.5.sp),
            railColor = Color(red = night.red * 0.7f, green = night.green * 0.7f, blue = night.blue * 0.7f),
            selectedColor = Accent,
            background = {
                PortHero(item.port, Modifier.matchParentSize())
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(0f to night.copy(alpha = 0.75f), 0.4f to night.copy(alpha = 0.96f))))
            },
        ),
    )
}
