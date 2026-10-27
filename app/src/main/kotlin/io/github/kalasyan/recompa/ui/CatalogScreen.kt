package io.github.kalasyan.recompa.ui

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kalasyan.recompa.R
import io.github.kalasyan.recompa.core.install.PortStatus
import io.github.kalasyan.recompa.ui.theme.Accent
import io.github.kalasyan.recompa.ui.theme.Night

private val TopBarHeight = 56.dp

private enum class Filter(val label: Int) {
    ALL(R.string.filter_all),
    COMPATIBLE(R.string.filter_compatible),
    INSTALLED(R.string.filter_installed),
}

@Composable
fun CatalogScreen(
    state: CatalogState,
    onRetry: () -> Unit,
    onOpen: (PortItem) -> Unit,
    onDiagnostics: () -> Unit,
) {
    when (state) {
        is CatalogState.Ready -> Library(state, onOpen, onDiagnostics)
        else -> Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            TopBar(filter = null, onFilter = {}, offline = false, onDiagnostics = onDiagnostics)
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
 * The library: the selected port's art fills the background, its details sit on top, and the
 * covers below. Built for a gamepad: the D-pad moves between covers, A opens one, and L1/R1
 * switch between lists.
 */
@Composable
private fun Library(state: CatalogState.Ready, onOpen: (PortItem) -> Unit, onDiagnostics: () -> Unit) {
    val manager = rememberInstallManager()
    val revision by manager.revision.collectAsStateWithLifecycle()
    LifecycleResumeEffect(manager) {
        manager.refresh()
        onPauseOrDispose { }
    }
    val statuses = remember(state.items, revision) { state.items.associate { it.port.id to manager.status(it.port) } }

    var filter by rememberSaveable { mutableStateOf(Filter.ALL) }
    val visible = state.items.filter {
        when (filter) {
            Filter.ALL -> true
            Filter.COMPATIBLE -> it.compatible && it.port.recommendedFork != null
            Filter.INSTALLED -> statuses[it.port.id] != PortStatus.NotInstalled
        }
    }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = visible.firstOrNull { it.port.id == selectedId } ?: visible.firstOrNull()

    Box(
        Modifier.fillMaxSize().onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val step = when (event.key.nativeKeyCode) {
                AndroidKeyEvent.KEYCODE_BUTTON_L1 -> -1
                AndroidKeyEvent.KEYCODE_BUTTON_R1 -> 1
                else -> return@onPreviewKeyEvent false
            }
            filter = Filter.entries[(filter.ordinal + step).mod(Filter.entries.size)]
            true
        },
    ) {
        PortBackdrop(selected?.port, Modifier.fillMaxSize())
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            // Landscape handhelds have little height: one shelf of covers instead of a grid.
            val shelf = maxHeight < 520.dp
            val featuredHeight = if (shelf) 164.dp else 256.dp
            // Shelf covers take whatever height is left under the top bar and the details.
            val shelfCoverWidth = ((maxHeight - TopBarHeight - featuredHeight - 64.dp) * 2f / 3f).coerceIn(84.dp, 150.dp)
            val gridState = rememberLazyGridState()
            val rowState = rememberLazyListState()
            val focusTarget = remember { FocusRequester() }
            // Keep the selected cover in view and focused, also when coming back from a port's page.
            LaunchedEffect(filter, shelf) {
                val index = visible.indexOfFirst { it.port.id == selected?.port?.id }.coerceAtLeast(0)
                if (shelf) rowState.scrollToItem(index) else gridState.scrollToItem(index)
                withFrameNanos { }
                runCatching { focusTarget.requestFocus() }
            }
            Column(Modifier.fillMaxSize()) {
                TopBar(filter, onFilter = { filter = it }, offline = state.fromCache, onDiagnostics = onDiagnostics)
                if (selected != null) {
                    Featured(
                        selected,
                        statuses[selected.port.id],
                        compact = shelf,
                        modifier = Modifier.height(featuredHeight).padding(horizontal = 24.dp),
                    )
                }
                if (visible.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(if (state.items.isEmpty()) R.string.empty_catalog else R.string.empty_filter),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    return@Column
                }
                val poster: @Composable (PortItem, Modifier) -> Unit = { item, modifier ->
                    Poster(
                        item = item,
                        status = statuses[item.port.id],
                        onClick = {
                            selectedId = item.port.id
                            onOpen(item)
                        },
                        onFocused = { selectedId = item.port.id },
                        modifier = modifier,
                        focusRequester = focusTarget.takeIf { item == selected },
                    )
                }
                if (shelf) {
                    Spacer(Modifier.weight(1f))
                    LazyRow(
                        state = rowState,
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(visible, key = { it.port.id }) { item ->
                            poster(item, Modifier.width(shelfCoverWidth))
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(minSize = 128.dp),
                        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        items(visible, key = { it.port.id }) { item ->
                            poster(item, Modifier)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(filter: Filter?, onFilter: (Filter) -> Unit, offline: Boolean, onDiagnostics: () -> Unit) {
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
        if (filter != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Filter.entries.forEach { option ->
                    FilterChip(
                        modifier = Modifier.focusRing(),
                        selected = option == filter,
                        onClick = { onFilter(option) },
                        label = { Text(stringResource(option.label)) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color.White.copy(alpha = 0.06f),
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedContainerColor = Color.White,
                            selectedLabelColor = Night,
                        ),
                        border = null,
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        if (offline) Pill(stringResource(R.string.offline_badge))
        IconButton(onClick = onDiagnostics, modifier = Modifier.focusRing(CircleShape)) {
            Icon(Icons.Filled.Info, contentDescription = stringResource(R.string.diagnostics_title))
        }
    }
}

/** Logo, labels and description of the selected port. */
@Composable
private fun Featured(item: PortItem, status: PortStatus?, compact: Boolean, modifier: Modifier = Modifier) {
    val port = item.port
    // A translucent panel keeps the text readable over bright art.
    Box(modifier) {
        Column(
            Modifier
                .fillMaxWidth(0.7f)
                .padding(top = if (compact) 0.dp else 24.dp)
                .background(Panel, RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            PortTitle(
                port,
                logoHeight = if (compact) 56.dp else 96.dp,
                style = if (compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
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
                    maxLines = if (compact) 2 else 3,
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
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.12f else 1f, label = "poster-scale")
    LaunchedEffect(focused) { if (focused) onFocused() }
    val unavailable = !item.compatible || item.port.recommendedFork == null

    Column(modifier.graphicsLayer { scaleX = scale; scaleY = scale }) {
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
