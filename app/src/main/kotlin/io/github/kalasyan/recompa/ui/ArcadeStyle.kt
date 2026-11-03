package io.github.kalasyan.recompa.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kalasyan.recompa.R
import io.github.kalasyan.recompa.ui.theme.PressStart
import kotlin.math.abs
import kotlin.math.pow

private val Cyan = Color(0xFF00FFF0)
private val Pink = Color(0xFFFF2D95)
private val Yellow = Color(0xFFFFE14D)
private val Violet = Color(0xFFB9A6E0)
private val Deep = Color(0xFF07010F)

private fun pixel(size: Int, color: Color = Color.White) = TextStyle(fontFamily = PressStart, fontSize = size.sp, lineHeight = (size * 1.5).sp, color = color)

/** A synthwave sunset: violet sky, a pink horizon and a cyan grid floor, with CRT scanlines on top. */
@Composable
internal fun SynthwaveBackground(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val horizon = size.height * 0.46f
        drawRect(Brush.verticalGradient(0f to Color(0xFF140326), 1f to Color(0xFF2A0845), endY = horizon), size = size.copy(height = horizon))
        drawRect(Deep, topLeft = Offset(0f, horizon), size = size.copy(height = size.height - horizon))
        drawLine(Pink, Offset(0f, horizon), Offset(size.width, horizon), strokeWidth = 3f)
        val grid = Cyan.copy(alpha = 0.35f)
        val center = size.width / 2
        // Lines running to the vanishing point on the horizon.
        for (i in -14..14) {
            val bottomX = center + i * size.width / 9f
            drawLine(grid, Offset(center + i * size.width / 60f, horizon), Offset(bottomX, size.height), strokeWidth = 2f)
        }
        // Cross lines, closer together towards the horizon.
        for (i in 1..9) {
            val y = horizon + (size.height - horizon) * (i / 9f).pow(2.2f)
            drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 2f)
        }
        val step = 3.dp.toPx()
        var y = 0f
        while (y < size.height) {
            drawLine(Color.Black.copy(alpha = 0.22f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            y += step
        }
    }
}

/**
 * Arcade: a cabinet's attract screen. The covers turn like a wheel with the selected one in
 * the middle, big and lit in neon; its name and platform sit below in pixel type.
 */
@Composable
internal fun ArcadeLibrary(ui: LibraryUi) {
    val selected = ui.selected
    val listState = rememberLazyListState()
    FocusSelectedOnChange(ui) { listState.scrollToItem(it) }
    // Keep the selected cover in the middle while moving along the wheel.
    LaunchedEffect(ui.selectedIndex) { if (ui.selectedIndex >= 0) listState.animateScrollToItem(ui.selectedIndex) }

    Box(Modifier.fillMaxSize().background(Deep)) {
        SynthwaveBackground(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Column(Modifier.align(Alignment.TopStart)) {
                    Text("PORTS", style = pixel(8, Violet))
                    Spacer(Modifier.height(4.dp))
                    Text("${ui.filterCounts[Filter.ALL] ?: ui.items.size}", style = pixel(11))
                }
                Text(
                    "RECOMPA",
                    style = pixel(22, Yellow).copy(shadow = Shadow(Pink, Offset(5f, 5f), 0f)),
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp),
                )
                Box(Modifier.align(Alignment.TopEnd)) { LibraryActions(ui, tint = Cyan, compact = true) }
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
            ) {
                ui.filters.forEach { f ->
                    PixelTab(stringResource(f.label).uppercase(), selected = f == ui.filter) { ui.onFilter(f) }
                }
            }
            Text(
                "◀ " + (ui.platform?.let { platformName(it) } ?: stringResource(R.string.list_all_platforms)).uppercase() + " ▶",
                style = pixel(8, Violet),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            ui.searchBar(Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp).fillMaxWidth(0.6f))
            if (ui.items.isEmpty()) {
                ui.emptyState(Modifier.weight(1f))
            } else {
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    // A portrait screen has height to spare: bigger covers on the wheel.
                    val scale = if (maxHeight > maxWidth) 1.4f else 1f
                    val side = (maxWidth - 128.dp * scale) / 2
                    LazyRow(
                        state = listState,
                        contentPadding = PaddingValues(horizontal = side.coerceAtLeast(0.dp)),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        itemsIndexed(ui.items, key = { _, it -> it.port.id }) { index, item ->
                            WheelCover(ui, item, distance = abs(index - ui.selectedIndex), scale = scale)
                        }
                    }
                }
            }
            if (selected != null) {
                Text(
                    selected.port.name.uppercase(),
                    style = pixel(13).copy(shadow = Shadow(Pink, Offset(4f, 4f), 0f)),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                )
                Text(
                    portFacts(selected).uppercase(),
                    style = pixel(8, Cyan),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 24.dp, end = 24.dp),
                )
            }
            Text(
                listOf(
                    "A " + stringResource(R.string.hint_open),
                    "L2/R2 " + stringResource(R.string.hint_platform),
                    "Y " + stringResource(R.string.favorite),
                    "SELECT " + stringResource(ui.sort.label),
                ).joinToString("  ·  ").uppercase(),
                style = pixel(8),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun PixelTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Text(
        (if (selected) "▶ " else "") + label,
        style = pixel(9, if (selected) Cyan else Violet),
        modifier = Modifier
            .keepInView(selected)
            .focusRing(RectangleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(6.dp),
    )
}

@Composable
private fun WheelCover(ui: LibraryUi, item: PortItem, distance: Int, scale: Float = 1f) {
    val width by animateDpAsState(
        when (distance) {
            0 -> 128.dp
            1 -> 96.dp
            else -> 80.dp
        } * scale,
        label = "wheel-width",
    )
    val dim by animateFloatAsState(
        when (distance) {
            0 -> 0f
            1 -> 0.3f
            else -> 0.5f
        },
        label = "wheel-dim",
    )
    val centre = distance == 0
    Box(
        Modifier
            .width(width)
            .then(if (item == ui.selected) Modifier.focusRequester(ui.focusTarget) else Modifier)
            .onFocusChanged { if (it.isFocused) ui.onSelect(item) }
            .then(if (centre) Modifier.shadow(24.dp, RectangleShape, ambientColor = Cyan, spotColor = Cyan) else Modifier)
            .border(if (centre) 4.dp else 0.dp, Pink)
            .padding(if (centre) 4.dp else 0.dp)
            .border(if (centre) 3.dp else 2.dp, if (centre) Cyan else Color(0xFF2A0845))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { ui.onOpen(item) },
    ) {
        PortCover(item.port, Modifier.fillMaxWidth())
        Box(Modifier.matchParentSize().graphicsLayer { alpha = dim }.background(Deep))
        ui.badge(item)?.let { (text, color) ->
            Text(
                text.uppercase(),
                style = pixel(6, Deep),
                // One line: a long label ("ACTUALIZACIÓN") broke in two on small covers.
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).background(color).padding(horizontal = 2.dp, vertical = 3.dp),
            )
        }
        if (ui.isFavorite(item)) {
            Icon(
                Icons.Filled.Favorite,
                contentDescription = stringResource(R.string.favorite),
                tint = Pink,
                modifier = Modifier.align(Alignment.TopStart).padding(5.dp).size(14.dp),
            )
        }
    }
}

/** Arcade port page: the side menu of sections in a neon frame over the synthwave floor. */
@Composable
internal fun ArcadePortPage(item: PortItem, header: @Composable () -> Unit) {
    SideTabsPage(
        item,
        header,
        SideTabsSkin(
            tabText = pixel(9),
            railColor = Deep.copy(alpha = 0.6f),
            selectedColor = Cyan,
            uppercase = true,
            marker = "▶",
            background = { SynthwaveBackground(Modifier.matchParentSize()) },
            frame = Modifier
                .padding(12.dp)
                .border(3.dp, Pink)
                .padding(3.dp)
                .border(3.dp, Cyan)
                .background(Deep.copy(alpha = 0.88f)),
        ),
    )
}
