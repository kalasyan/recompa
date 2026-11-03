package io.github.kalasyan.recompa.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kalasyan.recompa.R
import io.github.kalasyan.recompa.ui.theme.Accent
import io.github.kalasyan.recompa.ui.theme.Night

/** One row of the Shelves library. */
private class Shelf(val key: String, val title: String, val items: List<PortItem>)

/**
 * Shelves: the selected port fills the top of the screen with its art, logo and description;
 * below, rows of wide tiles: installed ports, the newest ones, then one row per platform.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ShelvesLibrary(ui: LibraryUi) {
    val selected = ui.selected
    val showRecent = ui.filter == Filter.ALL && ui.platform == null && !ui.searching
    val installedTitle = stringResource(R.string.shelf_installed)
    val newTitle = stringResource(R.string.shelf_new)
    val platformNames = ui.platforms.associate { (code, _) -> code to platformName(code) }
    val shelves = remember(ui.items, ui.statuses, showRecent, platformNames) {
        buildList {
            if (showRecent) {
                ui.items.filter { ui.isInstalled(it) }.takeIf { it.isNotEmpty() }?.let { add(Shelf("installed", installedTitle, it)) }
                ui.items.filter { it.port.id in ui.newIds }.takeIf { it.isNotEmpty() }?.let { add(Shelf("new", newTitle, it)) }
            }
            ui.items.groupBy { it.port.originalPlatform }
                .entries.sortedBy { (code, _) -> ui.platforms.indexOfFirst { it.first == code } }
                .forEach { (code, items) -> add(Shelf("p:$code", platformNames[code] ?: code, items)) }
        }
    }
    // The selected port takes focus on the first platform row it is in, not in the recent rows.
    val home = shelves.indexOfFirst { it.key.startsWith("p:") && it.items.any { item -> item == selected } }
    val columnState = rememberLazyListState()
    val rowStates = remember { mutableMapOf<String, LazyListState>() }
    val portrait = isPortrait()
    // A portrait screen with a single shelf (one platform picked) would show one row and leave
    // the rest empty: its tiles fill a grid instead.
    val gridded = portrait && shelves.size == 1
    val gridState = rememberLazyGridState()
    FocusSelectedOnChange(ui, gridded) {
        if (gridded) {
            gridState.scrollToItem(shelves[0].items.indexOf(selected).coerceAtLeast(0))
        } else if (home >= 0) {
            columnState.scrollToItem(home)
            val shelf = shelves[home]
            rowStates.getOrPut(shelf.key) { LazyListState() }.scrollToItem(shelf.items.indexOf(selected).coerceAtLeast(0))
        }
    }

    Box(Modifier.fillMaxSize().background(Night)) {
        // In portrait the art is a banner behind the top of the screen, not three quarters of it.
        Crossfade(selected?.port, animationSpec = tween(400), label = "shelves-hero") { port ->
            if (port != null) PortHero(port, Modifier.fillMaxWidth().fillMaxHeight(if (portrait) 0.34f else 0.75f))
        }
        if (portrait) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Night.copy(alpha = 0.55f), 0.12f to Night.copy(alpha = 0.25f), 0.34f to Night)))
        } else {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(0f to Night.copy(alpha = 0.95f), 0.55f to Night.copy(alpha = 0.4f), 1f to Color.Transparent),
                ),
            )
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.3f to Color.Transparent, 0.62f to Night)))
        }

        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 28.dp, end = 12.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.app_name).uppercase(),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, letterSpacing = 1.sp),
                    color = Color.White,
                )
                Row(
                    Modifier.weight(1f).padding(start = 20.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ui.filters.forEach { f -> UnderlineTab(stringResource(f.label), f == ui.filter) { ui.onFilter(f) } }
                }
                LibraryActions(ui, compact = true)
            }
            ui.searchBar(Modifier.padding(horizontal = 28.dp, vertical = 4.dp).fillMaxWidth(0.6f))
            if (selected != null && !ui.secondShowing) {
                Column(Modifier.padding(start = 28.dp, top = 6.dp, end = 28.dp).fillMaxWidth(if (portrait) 1f else 0.55f)) {
                    PortTitle(selected.port, logoHeight = 56.dp, style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(portFacts(selected), style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.75f))
                    selected.port.description?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ButtonHint("A", stringResource(R.string.hint_open))
                        ButtonHint("Y", stringResource(R.string.favorite))
                        ButtonHint("Select", stringResource(ui.sort.label))
                        ButtonHint("L2/R2", ui.platform?.let { platformName(it) } ?: stringResource(R.string.hint_platform))
                    }
                }
            }
            if (ui.items.isEmpty()) {
                ui.emptyState(Modifier.weight(1f))
            } else if (gridded) {
                val shelf = shelves[0]
                Row(Modifier.padding(start = 28.dp, top = 10.dp, bottom = 2.dp), verticalAlignment = Alignment.Bottom) {
                    Text(shelf.title, style = MaterialTheme.typography.titleSmall, color = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text(shelf.items.size.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    gridItems(shelf.items, key = { it.port.id }) { item ->
                        ShelfTile(ui, item, progress = false, focus = item == selected, modifier = Modifier.fillMaxWidth())
                    }
                }
            } else {
                LazyColumn(
                    state = columnState,
                    contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(shelves, key = { it.key }) { shelf ->
                        val isHome = shelves.indexOf(shelf) == home
                        Column {
                            Row(Modifier.padding(start = 28.dp, bottom = 6.dp), verticalAlignment = Alignment.Bottom) {
                                Text(shelf.title, style = MaterialTheme.typography.titleSmall, color = Color.White)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    shelf.items.size.toString(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            LazyRow(
                                state = rowStates.getOrPut(shelf.key) { LazyListState() },
                                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(shelf.items, key = { it.port.id }) { item ->
                                    ShelfTile(
                                        ui,
                                        item,
                                        progress = shelf.key == "installed",
                                        focus = isHome && item == selected,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShelfTile(ui: LibraryUi, item: PortItem, progress: Boolean, focus: Boolean, modifier: Modifier = Modifier.width(168.dp)) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.07f else 1f, label = "tile-scale")
    val shape = RoundedCornerShape(8.dp)
    val unavailable = !item.compatible || item.port.recommendedFork == null
    Box(
        modifier
            .aspectRatio(16f / 9f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (focus) Modifier.focusRequester(ui.focusTarget) else Modifier)
            .onFocusChanged { if (it.isFocused) ui.onSelect(item) }
            .border(if (focused) 3.dp else 0.dp, if (focused) Color.White else Color.Transparent, shape)
            .clip(shape)
            .clickable(interactionSource = interaction, indication = null) { ui.onOpen(item) },
    ) {
        PortHero(item.port, Modifier.matchParentSize().graphicsLayer { alpha = if (unavailable) 0.45f else 1f })
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.8f))))
        Box(Modifier.align(Alignment.BottomStart).padding(8.dp).widthIn(max = 140.dp)) {
            PortTitle(item.port, logoHeight = 30.dp, style = MaterialTheme.typography.labelLarge.copy(color = Color.White))
        }
        ui.badge(item)?.let { (text, color) -> CornerBadge(text, color, Modifier.align(Alignment.TopEnd).padding(6.dp)) }
        if (ui.isFavorite(item)) {
            Icon(
                Icons.Filled.Favorite,
                contentDescription = stringResource(R.string.favorite),
                tint = Color(0xFFFF6B8A),
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp).height(16.dp),
            )
        }
        if (progress) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Accent))
        }
    }
}

/** A text tab that is underlined when selected, as in a console's top menu. */
@Composable
internal fun UnderlineTab(label: String, selected: Boolean, color: Color = Color.White, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(
        Modifier
            .keepInView(selected)
            .clip(RoundedCornerShape(6.dp))
            .background(if (focused) Color.White.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected || focused) color else color.copy(alpha = 0.55f),
            maxLines = 1,
        )
        Spacer(Modifier.height(3.dp))
        Box(Modifier.width(if (selected) 24.dp else 0.dp).height(2.dp).background(color))
    }
}

/**
 * Shelves port page: the port's art full screen with its logo, facts and actions on the left;
 * below, tabs for screenshots, files, reports, versions, requirements and forks.
 */
@Composable
internal fun ShelvesPortPage(item: PortItem, header: @Composable () -> Unit) {
    val port = item.port
    val tabs = portTabs(item, galleryHeight = 150.dp)
    var tab by rememberSaveable(port.id) { mutableStateOf(0) }
    val scroll = rememberScrollState()
    // In portrait the art is a banner on top and the text uses the whole width under it.
    val portrait = isPortrait()
    Box(Modifier.fillMaxSize().background(Night)) {
        if (portrait) {
            PortHero(port, Modifier.fillMaxWidth().fillMaxHeight(0.42f))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.12f to Night.copy(alpha = 0.2f), 0.42f to Night)))
        } else {
            PortHero(port, Modifier.fillMaxSize())
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(0f to Night.copy(alpha = 0.97f), 0.5f to Night.copy(alpha = 0.6f), 1f to Night.copy(alpha = 0.1f)),
                ),
            )
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 0.75f to Night)))
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .dpadScroll(scroll)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = if (portrait) 20.dp else 32.dp, vertical = 12.dp),
        ) {
            header()
            if (portrait) Spacer(Modifier.height(96.dp))
            Column(Modifier.fillMaxWidth(if (portrait) 1f else 0.6f).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PortTitle(port, logoHeight = 80.dp, style = MaterialTheme.typography.displaySmall)
                Text(
                    listOf(port.game, portFacts(item)).joinToString(" · "),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.75f),
                )
                port.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f)) }
                PortActions(item)
                PortStats(item)
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                tabs.forEachIndexed { index, t -> UnderlineTab(t.title, index == tab) { tab = index } }
            }
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .widthIn(max = 880.dp)
                    .background(Night.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                    .padding(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { tabs.getOrNull(tab)?.content?.invoke() }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
