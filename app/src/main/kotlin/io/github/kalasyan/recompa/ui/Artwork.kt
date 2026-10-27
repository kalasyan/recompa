package io.github.kalasyan.recompa.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.github.kalasyan.recompa.R
import io.github.kalasyan.recompa.core.catalog.Port
import io.github.kalasyan.recompa.ui.theme.Night

val CoverShape = RoundedCornerShape(12.dp)

/** Translucent background for text that sits on top of art. */
val Panel = Night.copy(alpha = 0.72f)

/**
 * A thick white ring while this element, or the control inside it, has focus, so the gamepad
 * cursor is easy to follow. Material's own focus overlay is too subtle on a TV-like screen.
 */
fun Modifier.focusRing(shape: Shape = RoundedCornerShape(50)): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    onFocusChanged { focused = it.hasFocus }
        .then(if (focused) Modifier.border(3.dp, Color.White, shape) else Modifier)
}

/** Vertical 2:3 cover. Until the art loads, or when the catalog has none, a generated one. */
@Composable
fun PortCover(port: Port, modifier: Modifier = Modifier) {
    var loaded by remember(port.art?.cover) { mutableStateOf(false) }
    Box(modifier.aspectRatio(2f / 3f).clip(CoverShape).background(platformBrush(port.originalPlatform))) {
        if (!loaded) GeneratedCover(port)
        port.art?.cover?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = port.game,
                contentScale = ContentScale.Crop,
                onSuccess = { loaded = true },
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

@Composable
private fun BoxScope.GeneratedCover(port: Port) {
    Text(
        port.game.take(1).uppercase(),
        style = TextStyle(fontSize = 160.sp, fontWeight = MaterialTheme.typography.displaySmall.fontWeight),
        color = Color.White.copy(alpha = 0.08f),
        modifier = Modifier.align(Alignment.TopEnd).padding(end = 4.dp),
    )
    Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
        Text(
            platformName(port.originalPlatform).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.7f),
        )
        Text(
            port.game,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Full-bleed background for [port]: its hero art, else its blurred cover, faded into the
 * screen colour so text on top stays readable.
 */
@Composable
fun PortBackdrop(port: Port?, modifier: Modifier = Modifier) {
    Box(modifier.background(Night)) {
        Crossfade(targetState = port, animationSpec = tween(450), label = "backdrop") { shown ->
            Box(Modifier.fillMaxSize()) {
                if (shown != null) {
                    Box(Modifier.matchParentSize().alpha(0.35f).background(platformBrush(shown.originalPlatform)))
                    val hero = shown.art?.hero
                    val cover = shown.art?.cover
                    when {
                        hero != null -> AsyncImage(
                            model = hero,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            alignment = Alignment.TopCenter,
                            modifier = Modifier.matchParentSize().alpha(0.75f),
                        )
                        cover != null -> AsyncImage(
                            model = cover,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize().blur(32.dp).alpha(0.45f),
                        )
                    }
                }
            }
        }
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(0f to Night.copy(alpha = 0.25f), 0.45f to Night.copy(alpha = 0.55f), 0.8f to Night),
            ),
        )
        Box(
            Modifier.matchParentSize().background(
                Brush.horizontalGradient(0f to Night.copy(alpha = 0.85f), 0.6f to Color.Transparent),
            ),
        )
    }
}

/** The game's logo when the catalog has one, its name otherwise. */
@Composable
fun PortTitle(port: Port, logoHeight: Dp, style: TextStyle, modifier: Modifier = Modifier) {
    var failed by remember(port.art?.logo) { mutableStateOf(false) }
    val logo = port.art?.logo
    if (logo != null && !failed) {
        AsyncImage(
            model = logo,
            contentDescription = port.name,
            contentScale = ContentScale.Fit,
            alignment = Alignment.CenterStart,
            onError = { failed = true },
            modifier = modifier.height(logoHeight),
        )
    } else {
        Text(port.name, style = style, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = modifier)
    }
}

/** Small rounded label, for platform, type and status. */
@Composable
fun Pill(text: String, modifier: Modifier = Modifier, color: Color = Color.White.copy(alpha = 0.12f), textColor: Color = Color.White) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = textColor,
        maxLines = 1,
        modifier = modifier.clip(RoundedCornerShape(50)).background(color).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
fun platformName(code: String): String = PLATFORMS[code] ?: stringResource(R.string.platform_other)

@Composable
fun typeName(type: String): String = when (type) {
    "recomp" -> stringResource(R.string.type_recomp)
    "decomp" -> stringResource(R.string.type_decomp)
    else -> stringResource(R.string.type_source_port)
}

private val PLATFORMS = mapOf(
    "n64" to "Nintendo 64",
    "gamecube" to "GameCube",
    "wii" to "Wii",
    "wiiu" to "Wii U",
    "ds" to "Nintendo DS",
    "switch" to "Switch",
    "snes" to "Super Nintendo",
    "genesis" to "Mega Drive",
    "gba" to "Game Boy Advance",
    "ps1" to "PlayStation",
    "ps2" to "PlayStation 2",
    "psp" to "PSP",
    "xbox" to "Xbox",
    "xbox360" to "Xbox 360",
    "pc" to "PC",
)

/** A colour pair per platform family, for generated covers and empty backdrops. */
fun platformBrush(code: String): Brush {
    val (top, bottom) = when (code) {
        "n64" -> Color(0xFF2E7D32) to Color(0xFF0D2B4E)
        "gamecube", "wii", "wiiu" -> Color(0xFF5E35B1) to Color(0xFF1A1440)
        "snes", "ds", "gba", "switch" -> Color(0xFFC62828) to Color(0xFF3A0D16)
        "genesis" -> Color(0xFF1565C0) to Color(0xFF0A1A3A)
        "ps1", "ps2", "psp" -> Color(0xFF37474F) to Color(0xFF0E1620)
        "xbox", "xbox360" -> Color(0xFF558B2F) to Color(0xFF13240C)
        else -> Color(0xFF8D4A2B) to Color(0xFF221109)
    }
    return Brush.linearGradient(listOf(top, bottom))
}
