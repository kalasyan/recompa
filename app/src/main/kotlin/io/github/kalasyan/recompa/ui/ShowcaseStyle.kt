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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import io.github.kalasyan.recompa.R
import io.github.kalasyan.recompa.ui.theme.Accent
import io.github.kalasyan.recompa.ui.theme.Night
import io.github.kalasyan.recompa.ui.theme.Playfair
import kotlin.math.abs

private val CaseWidth = 124.dp

/**
 * Showcase: the game boxes stand on a lit shelf and turn towards the middle, where the selected
 * one faces the player, larger, over its reflection.
 */
@Composable
internal fun ShowcaseLibrary(ui: LibraryUi) {
    val selected = ui.selected
    val listState = rememberLazyListState()
    FocusSelectedOnChange(ui) { listState.scrollToItem(it) }
    LaunchedEffect(ui.selectedIndex) { if (ui.selectedIndex >= 0) listState.animateScrollToItem(ui.selectedIndex) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Night)
            .background(Brush.radialGradient(listOf(Color(0xFF2C2F3A), Night), radius = 1400f)),
    ) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge.copy(fontFamily = Playfair))
                Row(
                    Modifier
                        .padding(start = 18.dp)
                        .weight(1f, fill = false)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.07f))
                        .horizontalScroll(rememberScrollState())
                        .padding(3.dp),
                ) {
                    ui.filters.forEach { f -> Segment(stringResource(f.label), f == ui.filter) { ui.onFilter(f) } }
                }
                Spacer(Modifier.weight(1f))
                LibraryActions(ui)
            }
            ui.searchBar(Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp).fillMaxWidth(0.6f))
            if (ui.items.isEmpty()) {
                ui.emptyState(Modifier.weight(1f))
            } else {
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    // A portrait screen has height to spare: bigger boxes.
                    val caseWidth = if (maxHeight > maxWidth) CaseWidth * 1.35f else CaseWidth
                    val side = (maxWidth - caseWidth) / 2
                    // The shelf's edge, a soft line under the boxes.
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .padding(top = 150.dp * (caseWidth / CaseWidth))
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.25f), Color.Transparent))),
                    )
                    LazyRow(
                        state = listState,
                        contentPadding = PaddingValues(horizontal = side.coerceAtLeast(0.dp)),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        itemsIndexed(ui.items, key = { _, it -> it.port.id }) { index, item ->
                            Case(ui, item, index, listState, caseWidth)
                        }
                    }
                }
            }
            if (selected != null) {
                Text(
                    selected.port.name,
                    style = MaterialTheme.typography.headlineSmall.copy(fontFamily = Playfair),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                )
                if (!ui.secondShowing) {
                    Text(
                        portFacts(selected).uppercase(),
                        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                ) {
                    ButtonHint("A", stringResource(R.string.hint_open))
                    ButtonHint("Y", stringResource(R.string.favorite))
                    ButtonHint("Select", stringResource(ui.sort.label))
                    Text(
                        "${ui.selectedIndex + 1} / ${ui.items.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun Segment(label: String, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) Color.Black else Color.White.copy(alpha = if (focused) 1f else 0.7f),
        maxLines = 1,
        modifier = Modifier
            .keepInView(selected)
            .clip(RoundedCornerShape(50))
            .background(if (selected) Color.White else if (focused) Color.White.copy(alpha = 0.2f) else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
private fun Case(ui: LibraryUi, item: PortItem, index: Int, listState: androidx.compose.foundation.lazy.LazyListState, caseWidth: Dp) {
    val distance = abs(index - ui.selectedIndex)
    Column(
        Modifier
            .width(caseWidth)
            .zIndex(-distance.toFloat())
            .graphicsLayer {
                // Turn each box by how far its centre is from the middle of the shelf.
                val info = listState.layoutInfo
                val shown = info.visibleItemsInfo.firstOrNull { it.index == index }
                val offset = if (shown == null) 0f else {
                    val middle = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                    ((shown.offset + shown.size / 2f) - middle) / shown.size
                }
                rotationY = (-offset * 32f).coerceIn(-60f, 60f)
                val scale = (1.18f - abs(offset) * 0.14f).coerceIn(0.72f, 1.18f)
                scaleX = scale
                scaleY = scale
                cameraDistance = 14f * density
            }
            .then(if (item == ui.selected) Modifier.focusRequester(ui.focusTarget) else Modifier)
            .onFocusChanged { if (it.isFocused) ui.onSelect(item) }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { ui.onOpen(item) },
    ) {
        Box(
            Modifier
                .then(if (distance == 0) Modifier.shadow(28.dp, RoundedCornerShape(6.dp), ambientColor = Accent, spotColor = Accent) else Modifier),
        ) {
            PortCover(item.port, Modifier.fillMaxWidth())
            if (distance > 0) Box(Modifier.matchParentSize().clip(RoundedCornerShape(6.dp)).background(Night.copy(alpha = 0.45f)))
            ui.badge(item)?.let { (text, color) -> CornerBadge(text, color, Modifier.align(Alignment.TopEnd).padding(6.dp)) }
            if (ui.isFavorite(item)) {
                Icon(
                    Icons.Filled.Favorite,
                    contentDescription = stringResource(R.string.favorite),
                    tint = Accent,
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                )
            }
            if (distance == 0) Box(Modifier.matchParentSize().border(2.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(6.dp)))
        }
        // The reflection: the bottom of the box, upside down, fading into the shelf.
        Box(Modifier.fillMaxWidth().height(46.dp).padding(top = 3.dp).clipToBounds()) {
            PortCover(
                item.port,
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(Alignment.Top, unbounded = true)
                    .graphicsLayer {
                        scaleY = -1f
                        alpha = 0.22f
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                    },
            )
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Night))))
        }
    }
}

/**
 * Showcase port page: the box turned in 3D on the left; title, facts, actions and the sections
 * on the right, set like a booklet.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ShowcasePortPage(item: PortItem, header: @Composable () -> Unit) {
    val port = item.port
    val scroll = rememberScrollState()
    val portrait = isPortrait()
    val cover: @Composable (Modifier) -> Unit = { modifier ->
        PortCover(
            port,
            modifier
                .graphicsLayer {
                    rotationY = 20f
                    cameraDistance = 14f * density
                }
                .shadow(30.dp, RoundedCornerShape(6.dp)),
        )
    }
    // In portrait the box stands above the text, which then has the whole width.
    Row(
        Modifier
            .fillMaxSize()
            .background(Night)
            .background(Brush.radialGradient(listOf(Color(0xFF2C2F3A), Night), radius = 1400f))
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        if (!portrait) {
            Box(Modifier.fillMaxWidth(0.32f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                cover(Modifier.padding(28.dp))
            }
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(scroll)
                .dpadScroll(scroll)
                .padding(start = if (portrait) 20.dp else 0.dp, end = if (portrait) 20.dp else 32.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            header()
            if (portrait) {
                Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    cover(Modifier.width(170.dp))
                }
            }
            Text(port.name, style = MaterialTheme.typography.displaySmall.copy(fontFamily = Playfair))
            Text(
                "${port.game} · ${platformName(port.originalPlatform)}",
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = Playfair, fontStyle = FontStyle.Italic),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            port.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.widthIn(max = 560.dp)) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                portStats(item).forEach { stat ->
                    Column {
                        Text(stat.label.uppercase(), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stat.value, style = MaterialTheme.typography.titleLarge.copy(fontFamily = Playfair))
                    }
                }
            }
            PortActions(item)
            portTabs(item, galleryHeight = 120.dp).forEach { tab ->
                HorizontalDivider(Modifier.padding(top = 12.dp), color = Color.White.copy(alpha = 0.12f))
                Text(tab.title, style = MaterialTheme.typography.titleLarge.copy(fontFamily = Playfair), modifier = Modifier.padding(vertical = 4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { tab.content() }
            }
        }
    }
}
