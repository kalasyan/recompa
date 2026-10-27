package io.github.kalasyan.recompa.ui

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kalasyan.recompa.R
import io.github.kalasyan.recompa.core.catalog.PortSearch
import io.github.kalasyan.recompa.core.install.PortStatus
import io.github.kalasyan.recompa.ui.theme.Accent
import io.github.kalasyan.recompa.ui.theme.Night
import kotlinx.coroutines.launch

private val TopBarHeight = 56.dp
private const val FocusScale = 1.12f

private enum class Filter(val label: Int) {
    ALL(R.string.filter_all),
    COMPATIBLE(R.string.filter_compatible),
    INSTALLED(R.string.filter_installed),
    FAVORITES(R.string.filter_favorites),
}

@Composable
fun CatalogScreen(
    state: CatalogState,
    onRetry: () -> Unit,
    onOpen: (PortItem) -> Unit,
    onDiagnostics: () -> Unit,
    onSettings: () -> Unit,
) {
    when (state) {
        is CatalogState.Ready -> Library(state, onOpen, onDiagnostics, onSettings)
        else -> Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            TopBar(filter = null, onFilter = {}, offline = false, onDiagnostics = onDiagnostics, onSettings = onSettings)
            Column(
                Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (state is CatalogState.Failed) {
                    val message = when (state.reason) {
                        CatalogState.Reason.NETWORK -> R.string.error_network
                        CatalogState.Reason.NEWER_CATALOG -> R.string.error_newer_catalog
                    }
                    Text(stringResource(message), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                } else {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.loading))
                }
            }
        }
    }
}

/**
 * The library: the selected port's art fills the background, its details sit beside or above
 * a grid of covers. Built for a gamepad: the D-pad moves between covers, A opens one, L1/R1
 * switch between All, Compatible, Installed and Favourites, L2/R2 between original platforms,
 * Y marks the selected port as a favourite and X opens the search.
 */
@Composable
private fun Library(
    state: CatalogState.Ready,
    onOpen: (PortItem) -> Unit,
    onDiagnostics: () -> Unit,
    onSettings: () -> Unit,
) {
    val manager = rememberInstallManager()
    val prefs = rememberUserPrefs()
    val favorites by prefs.favorites.collectAsStateWithLifecycle()
    val updatingAll by manager.updatingAll.collectAsStateWithLifecycle()
    val revision by manager.revision.collectAsStateWithLifecycle()
    LifecycleResumeEffect(manager) {
        manager.refresh()
        onPauseOrDispose { }
    }
    val statuses = remember(state.items, revision) { state.items.associate { it.port.id to manager.status(it.port) } }
    val updates = remember(state.items, revision) { manager.pendingUpdates(state.items.map { it.port }) }

    var filter by rememberSaveable { mutableStateOf(Filter.ALL) }
    var platform by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    if (searching) {
        BackHandler {
            query = ""
            searching = false
        }
    }
    val inList = state.items.filter {
        val inFilter = when (filter) {
            Filter.ALL -> true
            Filter.COMPATIBLE -> it.compatible && it.port.recommendedFork != null
            Filter.INSTALLED -> statuses[it.port.id] != PortStatus.NotInstalled
            Filter.FAVORITES -> it.port.id in favorites
        }
        inFilter && PortSearch.matches(it.port, query)
    }
    // Platforms of the current list, the most common first and "other" last.
    val platforms = inList.groupingBy { it.port.originalPlatform }.eachCount().entries
        .sortedWith(compareBy({ it.key == "other" }, { -it.value }))
        .map { it.key to it.value }
    // A platform with no ports in the current list (say, after switching to Installed) is dropped.
    val activePlatform = platform?.takeIf { code -> platforms.any { it.first == code } }
    LaunchedEffect(activePlatform) { platform = activePlatform }
    val visible = inList.filter { activePlatform == null || it.port.originalPlatform == activePlatform }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = visible.firstOrNull { it.port.id == selectedId } ?: visible.firstOrNull()

    Box(
        Modifier.fillMaxSize().onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key.nativeKeyCode) {
                AndroidKeyEvent.KEYCODE_BUTTON_L1 -> filter = filter.cycle(-1)
                AndroidKeyEvent.KEYCODE_BUTTON_R1 -> filter = filter.cycle(1)
                AndroidKeyEvent.KEYCODE_BUTTON_L2, AndroidKeyEvent.KEYCODE_BUTTON_R2 -> {
                    val options = listOf(null) + platforms.map { it.first }
                    val step = if (event.key.nativeKeyCode == AndroidKeyEvent.KEYCODE_BUTTON_L2) -1 else 1
                    platform = options[(options.indexOf(activePlatform) + step).mod(options.size)]
                }
                AndroidKeyEvent.KEYCODE_BUTTON_Y -> selected?.let { prefs.toggleFavorite(it.port.id) }
                AndroidKeyEvent.KEYCODE_BUTTON_X -> searching = true
                else -> return@onPreviewKeyEvent false
            }
            true
        },
    ) {
        PortBackdrop(selected?.port, Modifier.fillMaxSize())
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            // Landscape handhelds have little height: details on the left, covers on the right.
            val side = maxWidth > maxHeight && maxHeight < 600.dp
            val gridState = rememberLazyGridState()
            val focusTarget = remember { FocusRequester() }
            // Keep the selected cover in view and focused, also when coming back from a port's page.
            LaunchedEffect(filter, activePlatform, side) {
                val index = visible.indexOfFirst { it.port.id == selected?.port?.id }.coerceAtLeast(0)
                gridState.scrollToItem(index)
                withFrameNanos { }
                runCatching { focusTarget.requestFocus() }
            }
            val grid: @Composable (Modifier, Dp) -> Unit = { modifier, minCover ->
                if (visible.isEmpty()) {
                    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(if (state.items.isEmpty()) R.string.empty_catalog else R.string.empty_filter),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(minSize = minCover),
                        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = modifier,
                    ) {
                        items(visible, key = { it.port.id }) { item ->
                            Poster(
                                item = item,
                                status = statuses[item.port.id],
                                favorite = item.port.id in favorites,
                                onClick = {
                                    selectedId = item.port.id
                                    onOpen(item)
                                },
                                onFocused = { selectedId = item.port.id },
                                focusRequester = focusTarget.takeIf { item == selected },
                            )
                        }
                    }
                }
            }
            Column(Modifier.fillMaxSize()) {
                TopBar(
                    filter,
                    onFilter = { filter = it },
                    offline = state.fromCache,
                    onDiagnostics = onDiagnostics,
                    onSettings = onSettings,
                    onSearch = { searching = true },
                    updates = updates.size,
                    updatingAll = updatingAll,
                    onUpdateAll = { manager.updateAll(state.items.map { it.port }) },
                )
                if (searching) {
                    SearchField(
                        query,
                        onQuery = { query = it },
                        onClose = {
                            query = ""
                            searching = false
                        },
                        onDone = { runCatching { focusTarget.requestFocus() } },
                    )
                }
                PlatformBar(platforms, activePlatform, total = inList.size, onPlatform = { platform = it })
                if (side) {
                    Row(Modifier.weight(1f)) {
                        if (selected != null) {
                            Featured(
                                selected,
                                statuses[selected.port.id],
                                compact = true,
                                modifier = Modifier.fillMaxWidth(0.38f).padding(start = 24.dp, top = 12.dp),
                            )
                        }
                        grid(Modifier.weight(1f), 96.dp)
                    }
                } else {
                    if (selected != null) {
                        Featured(
                            selected,
                            statuses[selected.port.id],
                            compact = false,
                            modifier = Modifier.height(232.dp).padding(horizontal = 24.dp).fillMaxWidth(0.7f),
                        )
                    }
                    grid(Modifier.weight(1f), 128.dp)
                }
            }
        }
    }
}

private fun Filter.cycle(step: Int) = Filter.entries[(ordinal + step).mod(Filter.entries.size)]

@Composable
private fun TopBar(
    filter: Filter?,
    onFilter: (Filter) -> Unit,
    offline: Boolean,
    onDiagnostics: () -> Unit,
    onSettings: () -> Unit,
    onSearch: (() -> Unit)? = null,
    updates: Int = 0,
    updatingAll: Boolean = false,
    onUpdateAll: () -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(TopBarHeight)
            .background(Brush.verticalGradient(listOf(Panel, Color.Transparent)))
            .padding(start = 24.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            color = Accent,
        )
        Spacer(Modifier.width(20.dp))
        // The chips scroll when the bar is too narrow; a focused chip scrolls itself into view.
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
        ) {
            filter?.let { current ->
                Filter.entries.forEach { option ->
                    Chip(stringResource(option.label), selected = option == current, onClick = { onFilter(option) })
                }
            }
        }
        if (offline) Pill(stringResource(R.string.offline_badge))
        if (updates > 0 || updatingAll) {
            Button(
                onClick = onUpdateAll,
                enabled = !updatingAll,
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Night),
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier.padding(start = 8.dp).focusRing(),
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(
                    if (updatingAll) stringResource(R.string.updating_all) else stringResource(R.string.update_all, updates),
                    maxLines = 1,
                )
            }
        }
        if (onSearch != null) {
            IconButton(onClick = onSearch, modifier = Modifier.focusRing(CircleShape)) {
                Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search))
            }
        }
        IconButton(onClick = onSettings, modifier = Modifier.focusRing(CircleShape)) {
            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
        }
        IconButton(onClick = onDiagnostics, modifier = Modifier.focusRing(CircleShape)) {
            Icon(Icons.Filled.Info, contentDescription = stringResource(R.string.diagnostics_title))
        }
    }
}

/** Search by name or game; opens with the keyboard focused, closes with Back or the X. */
@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit, onClose: () -> Unit, onDone: () -> Unit) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { focus.requestFocus() }
    }
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        singleLine = true,
        placeholder = { Text(stringResource(R.string.search_hint)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            IconButton(onClick = onClose, modifier = Modifier.focusRing(CircleShape)) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.search_close))
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {
            keyboard?.hide()
            onDone()
        }),
        shape = RoundedCornerShape(50),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Panel,
            unfocusedContainerColor = Panel,
            focusedBorderColor = Accent,
        ),
        modifier = Modifier
            .padding(horizontal = 24.dp, vertical = 4.dp)
            .fillMaxWidth(0.6f)
            .focusRequester(focus),
    )
}

/** One chip per original platform in the current list, with how many ports each has. */
@Composable
private fun PlatformBar(platforms: List<Pair<String, Int>>, selected: String?, total: Int, onPlatform: (String?) -> Unit) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Lazy rows don't scroll to a focused chip on their own: bring it fully into view, one chip
    // of context to its left, whenever it gets focus or is picked with L2/R2.
    val reveal: (Int) -> Unit = { index ->
        val info = listState.layoutInfo
        val shown = info.visibleItemsInfo.firstOrNull { it.index == index }
        val fits = shown != null && shown.offset >= info.viewportStartOffset &&
            shown.offset + shown.size <= info.viewportEndOffset - info.afterContentPadding
        if (!fits) scope.launch { listState.animateScrollToItem((index - 1).coerceAtLeast(0)) }
    }
    LaunchedEffect(selected) {
        reveal(if (selected == null) 0 else platforms.indexOfFirst { it.first == selected } + 1)
    }
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().height(44.dp),
    ) {
        item(key = "all") {
            Chip(
                stringResource(R.string.platform_all, total),
                selected = selected == null,
                onClick = { onPlatform(null) },
                onFocused = { reveal(0) },
            )
        }
        itemsIndexed(platforms, key = { _, it -> it.first }) { index, (code, count) ->
            Chip(
                "${platformName(code)} · $count",
                selected = code == selected,
                onClick = { onPlatform(code) },
                onFocused = { reveal(index + 1) },
            )
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit, onFocused: () -> Unit = {}) {
    // The focus ring is the chip's own border, so it follows the chip's exact shape.
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    LaunchedEffect(focused) { if (focused) onFocused() }
    FilterChip(
        interactionSource = interaction,
        shape = RoundedCornerShape(50),
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Panel,
            labelColor = MaterialTheme.colorScheme.onSurface,
            selectedContainerColor = Color.White,
            selectedLabelColor = Night,
        ),
        border = if (focused) BorderStroke(2.dp, Accent) else null,
    )
}

/** Logo, labels and description of the selected port. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Featured(item: PortItem, status: PortStatus?, compact: Boolean, modifier: Modifier = Modifier) {
    val port = item.port
    // A translucent panel keeps the text readable over bright art.
    Box(modifier) {
        Column(
            Modifier
                .padding(top = if (compact) 0.dp else 16.dp)
                .background(Panel, RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            PortTitle(
                port,
                logoHeight = if (compact) 64.dp else 96.dp,
                style = if (compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(platformName(port.originalPlatform))
                Pill(typeName(port.type))
                StatusPill(item, status)
            }
            port.description?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    maxLines = if (compact) 5 else 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun StatusPill(item: PortItem, status: PortStatus?) {
    when {
        status is PortStatus.UpdateAvailable ->
            Pill(stringResource(R.string.status_update), color = Accent, textColor = Night)
        status != null && status != PortStatus.NotInstalled ->
            Pill(stringResource(R.string.status_installed), color = MaterialTheme.colorScheme.secondary, textColor = Night)
        item.port.recommendedFork == null -> Pill(stringResource(R.string.no_release))
        !item.compatible ->
            Pill(stringResource(R.string.not_compatible), color = MaterialTheme.colorScheme.errorContainer, textColor = MaterialTheme.colorScheme.onErrorContainer)
        else -> Unit
    }
}

@Composable
private fun Poster(
    item: PortItem,
    status: PortStatus?,
    favorite: Boolean,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) FocusScale else 1f, label = "poster-scale")
    // Only the cover takes focus, so the grid would scroll just the cover into view and leave the
    // name under the bottom edge. Bring in the whole poster, name included, as big as it grows.
    val reveal = remember { BringIntoViewRequester() }
    var size by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(focused) {
        if (!focused) return@LaunchedEffect
        onFocused()
        val growX = size.width * (FocusScale - 1f) / 2
        val growY = size.height * (FocusScale - 1f) / 2
        reveal.bringIntoView(Rect(-growX, -growY, size.width + growX, size.height + growY))
    }
    val unavailable = !item.compatible || item.port.recommendedFork == null

    Column(
        modifier
            .bringIntoViewRequester(reveal)
            .onSizeChanged { size = it }
            .graphicsLayer { scaleX = scale; scaleY = scale },
    ) {
        Box(
            Modifier
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .shadow(if (focused) 24.dp else 0.dp, CoverShape, ambientColor = Accent, spotColor = Accent)
                .border(
                    width = if (focused) 4.dp else 1.dp,
                    color = if (focused) Color.White else Color.White.copy(alpha = 0.08f),
                    shape = CoverShape,
                )
                .clickable(interactionSource = interaction, indication = ripple(), onClick = onClick),
        ) {
            PortCover(item.port, Modifier.fillMaxWidth().graphicsLayer { alpha = if (unavailable) 0.45f else 1f })
            val badge = when {
                status is PortStatus.UpdateAvailable -> stringResource(R.string.status_update) to Accent
                status != null && status != PortStatus.NotInstalled ->
                    stringResource(R.string.status_installed) to MaterialTheme.colorScheme.secondary
                else -> null
            }
            badge?.let { (text, color) ->
                Pill(text, color = color, textColor = Night, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
            }
            if (favorite) {
                Icon(
                    Icons.Filled.Favorite,
                    contentDescription = stringResource(R.string.favorite),
                    tint = Accent,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(Panel, CircleShape)
                        .padding(4.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            item.port.name,
            style = MaterialTheme.typography.labelLarge,
            color = if (focused) Night else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (focused) {
                Modifier.background(Accent, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
            } else {
                Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            },
        )
    }
}
