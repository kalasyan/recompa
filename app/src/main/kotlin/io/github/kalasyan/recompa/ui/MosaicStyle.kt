package io.github.kalasyan.recompa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kalasyan.recompa.R
import io.github.kalasyan.recompa.ui.theme.Accent
import io.github.kalasyan.recompa.ui.theme.Night

private val Indigo = Color(0xFF5865F2)
private val Coral = Color(0xFFFF5A5F)
private val Mint = Color(0xFF3DDC97)
private val Slate = Color(0xFF2B2E38)
private val TileGap = 8.dp

/** Ports per block: one big tile (2×2) and eight small ones (4 columns, 2 rows). */
private const val PerBlock = 9

/** In portrait a block is one big tile and four small ones (2 columns, 2 rows). */
private const val PortraitPerBlock = 5

/**
 * Mosaic: a menu in large type on the left (one entry per filter) and flat tiles on the right:
 * counters that are also shortcuts, then blocks of one big tile and eight small ones.
 */
@Composable
internal fun MosaicLibrary(ui: LibraryUi) {
    val portrait = isPortrait()
    val perBlock = if (portrait) PortraitPerBlock else PerBlock
    val blocks = remember(ui.items, perBlock) { ui.items.chunked(perBlock) }
    val listState = rememberLazyListState()
    // Item 0 is the row of counters.
    FocusSelectedOnChange(ui, portrait) { listState.scrollToItem(if (it < perBlock) 0 else 1 + it / perBlock) }
    if (portrait) {
        PortraitMosaic(ui, blocks, listState)
        return
    }

    Row(Modifier.fillMaxSize().background(Night).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Column(
            Modifier.width(176.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(start = 22.dp, top = 16.dp, end = 8.dp),
        ) {
            Text(
                stringResource(R.string.app_name).uppercase(),
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 4.sp, fontWeight = FontWeight.Bold),
                color = Accent,
            )
            Spacer(Modifier.height(14.dp))
            ui.filters.forEach { f ->
                MenuEntry(stringResource(f.label), (ui.filterCounts[f] ?: 0).toString(), selected = f == ui.filter) { ui.onFilter(f) }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "L2/R2 · " + (ui.platform?.let { platformName(it) } ?: stringResource(R.string.list_all_platforms)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(Modifier.fillMaxWidth().padding(end = 12.dp), horizontalArrangement = Arrangement.End) { LibraryActions(ui) }
            ui.searchBar(Modifier.padding(top = 12.dp, end = 20.dp).fillMaxWidth(0.7f))
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val unit = (maxWidth - 20.dp - TileGap * 5) / 6
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(top = 4.dp, end = 20.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(TileGap),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(key = "counters") { Counters(ui, unit) }
                    if (ui.items.isEmpty()) {
                        item(key = "empty") { ui.emptyState(Modifier.height(unit * 2)) }
                    }
                    itemsIndexed(blocks, key = { index, _ -> "block$index" }) { index, block ->
                        Block(ui, block, unit, mirrored = index % 2 == 1, smallColumns = 4)
                    }
                }
            }
        }
    }
}

/**
 * Mosaic on a portrait screen: the menu runs along the top as a scrolling row and the tiles get
 * the whole width, four units across.
 */
@Composable
private fun PortraitMosaic(ui: LibraryUi, blocks: List<List<PortItem>>, listState: LazyListState) {
    Column(Modifier.fillMaxSize().background(Night).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.app_name).uppercase(),
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 4.sp, fontWeight = FontWeight.Bold),
                color = Accent,
            )
            Spacer(Modifier.weight(1f))
            LibraryActions(ui)
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            ui.filters.forEach { f ->
                MenuEntry(
                    stringResource(f.label),
                    (ui.filterCounts[f] ?: 0).toString(),
                    selected = f == ui.filter,
                    modifier = Modifier.keepInView(f == ui.filter),
                ) { ui.onFilter(f) }
            }
        }
        Text(
            "L2/R2 · " + (ui.platform?.let { platformName(it) } ?: stringResource(R.string.list_all_platforms)),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 8.dp),
        )
        ui.searchBar(Modifier.padding(horizontal = 20.dp, vertical = 4.dp).fillMaxWidth())
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val unit = (maxWidth - 40.dp - TileGap * 3) / 4
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(TileGap),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "counters") { Counters(ui, unit) }
                if (ui.items.isEmpty()) {
                    item(key = "empty") { ui.emptyState(Modifier.height(unit * 2)) }
                }
                itemsIndexed(blocks, key = { index, _ -> "block$index" }) { index, block ->
                    Block(ui, block, unit, mirrored = index % 2 == 1, smallColumns = 2)
                }
            }
        }
    }
}

@Composable
private fun MenuEntry(label: String, count: String, selected: Boolean, modifier: Modifier = Modifier.fillMaxWidth(), onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(
        modifier
            .background(if (focused) Color.White.copy(alpha = 0.1f) else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = if (selected || focused) Color.White else Color.White.copy(alpha = 0.35f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(count, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.45f))
    }
}

/** Counters that double as shortcuts: ports shown, updates (installs them), new ports, search. */
@Composable
private fun Counters(ui: LibraryUi, unit: Dp) {
    Row(Modifier.fillMaxWidth().height(maxOf(unit * 0.8f, 84.dp)), horizontalArrangement = Arrangement.spacedBy(TileGap)) {
        CounterTile(ui.items.size.toString(), stringResource(R.string.mosaic_ports), Indigo, Color.White, onClick = null)
        CounterTile(
            if (ui.updatingAll) "…" else ui.updates.toString(),
            stringResource(R.string.mosaic_updates),
            if (ui.updates > 0) Coral else Slate,
            Color.White,
            onClick = ui.onUpdateAll.takeIf { ui.updates > 0 && !ui.updatingAll },
        )
        if (ui.newIds.isNotEmpty()) {
            CounterTile("+${ui.newIds.size}", stringResource(R.string.mosaic_new), Mint, Color(0xFF00331C), onClick = { ui.onFilter(Filter.NEW) })
        }
        CounterTile(null, stringResource(R.string.search), Slate, Color.White, onClick = ui.onSearch)
    }
}

@Composable
private fun RowScope.CounterTile(value: String?, label: String, color: Color, text: Color, onClick: (() -> Unit)?) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .background(color)
            .then(if (onClick != null) Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick) else Modifier)
            .border(if (focused) 4.dp else 0.dp, if (focused) Color.White else Color.Transparent)
            .padding(10.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        if (value != null) {
            Text(value, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = text)
        } else {
            Icon(Icons.Filled.Search, contentDescription = null, tint = text)
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = text.copy(alpha = 0.85f), maxLines = 2)
    }
}

@Composable
private fun Block(ui: LibraryUi, ports: List<PortItem>, unit: Dp, mirrored: Boolean, smallColumns: Int) {
    val big = ports.first()
    val small = ports.drop(1)
    Row(Modifier.fillMaxWidth().height(unit * 2 + TileGap), horizontalArrangement = Arrangement.spacedBy(TileGap)) {
        val bigTile: @Composable () -> Unit = {
            Tile(ui, big, Modifier.width(unit * 2 + TileGap).fillMaxHeight(), wide = true)
        }
        if (!mirrored) bigTile()
        Column(Modifier.width(unit * smallColumns + TileGap * (smallColumns - 1)), verticalArrangement = Arrangement.spacedBy(TileGap)) {
            small.chunked(smallColumns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(TileGap)) {
                    row.forEach { Tile(ui, it, Modifier.size(unit), wide = false) }
                }
            }
        }
        if (mirrored) bigTile()
    }
}

@Composable
private fun Tile(ui: LibraryUi, item: PortItem, modifier: Modifier, wide: Boolean) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val unavailable = !item.compatible || item.port.recommendedFork == null
    Box(
        modifier
            .then(if (item == ui.selected) Modifier.focusRequester(ui.focusTarget) else Modifier)
            .onFocusChanged { if (it.isFocused) ui.onSelect(item) }
            .clipToBounds()
            .clickable(interactionSource = interaction, indication = null) { ui.onOpen(item) },
    ) {
        if (wide) {
            PortHero(item.port, Modifier.matchParentSize())
        } else {
            Box(Modifier.matchParentSize().background(platformBrush(item.port.originalPlatform))) {
                FallbackImage(item.port.art?.covers.orEmpty(), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter, modifier = Modifier.matchParentSize())
            }
        }
        if (unavailable) Box(Modifier.matchParentSize().background(Night.copy(alpha = 0.55f)))
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.75f))))
        Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
            if (wide) {
                PortTitle(item.port, logoHeight = 44.dp, style = MaterialTheme.typography.titleLarge.copy(color = Color.White))
                Text(portFacts(item), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f), maxLines = 1)
            } else {
                Text(
                    item.port.name,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        ui.badge(item)?.let { (text, color) ->
            Text(
                text.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.Black,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).background(color).padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }
        if (ui.isFavorite(item)) {
            Icon(
                Icons.Filled.Favorite,
                contentDescription = stringResource(R.string.favorite),
                tint = Color.White,
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp).size(14.dp),
            )
        }
        if (focused) Box(Modifier.matchParentSize().border(4.dp, Color.White))
    }
}

/**
 * Mosaic port page: tiles for the art, the cover, the actions and the numbers, then each
 * section as a flat panel.
 */
@Composable
internal fun MosaicPortPage(item: PortItem, header: @Composable () -> Unit) {
    val port = item.port
    val scroll = rememberScrollState()
    val stats = portStats(item)
    val statColors = listOf(Indigo, Slate, Slate, Slate)
    val portrait = isPortrait()
    Column(
        Modifier
            .fillMaxSize()
            .background(Night)
            .verticalScroll(scroll)
            .dpadScroll(scroll)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(TileGap),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            header()
            Spacer(Modifier.width(16.dp))
            Column {
                Text(port.name, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
                Text(port.game, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.fillMaxWidth().height(if (portrait) 180.dp else 220.dp), horizontalArrangement = Arrangement.spacedBy(TileGap)) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                PortHero(port, Modifier.matchParentSize())
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.7f))))
                Box(Modifier.align(Alignment.BottomStart).padding(12.dp).widthIn(max = 360.dp)) {
                    PortTitle(port, logoHeight = 60.dp, style = MaterialTheme.typography.headlineMedium.copy(color = Color.White))
                }
            }
            Box(Modifier.fillMaxHeight().aspectRatio(2f / 3f)) { PortCover(port, Modifier.fillMaxSize()) }
        }
        val actions: @Composable (Modifier) -> Unit = { modifier ->
            Column(modifier.background(Slate).padding(12.dp)) {
                Text(portFacts(item), style = MaterialTheme.typography.labelLarge, color = Accent)
                PortActions(item)
            }
        }
        // In portrait the actions take a full-width tile and the numbers a row of their own.
        if (portrait) actions(Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TileGap)) {
            if (!portrait) actions(Modifier.weight(2f))
            stats.forEachIndexed { index, stat ->
                Column(
                    Modifier.weight(1f).height(96.dp).background(statColors.getOrElse(index) { Slate }).padding(12.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stat.label.uppercase(), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp), color = Color.White.copy(alpha = 0.75f))
                    Text(stat.value, style = (if (portrait) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall).copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        port.description?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth().background(Slate.copy(alpha = 0.6f)).padding(14.dp))
        }
        portTabs(item, galleryHeight = 140.dp).forEach { tab ->
            Column(Modifier.fillMaxWidth().background(Slate.copy(alpha = 0.6f)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(tab.title.uppercase(), style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp, fontWeight = FontWeight.Bold), color = Accent)
                Spacer(Modifier.height(4.dp))
                tab.content()
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
