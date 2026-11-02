package io.github.kalasyan.recompa.ui

import android.os.SystemClock
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kalasyan.recompa.R
import io.github.kalasyan.recompa.core.catalog.DeliveryMode
import io.github.kalasyan.recompa.core.catalog.Port
import io.github.kalasyan.recompa.data.AndroidDevice
import io.github.kalasyan.recompa.display.SecondScreen
import io.github.kalasyan.recompa.install.InstallPhase
import io.github.kalasyan.recompa.ui.theme.Night
import kotlinx.coroutines.delay

/** How long without input before the second screen turns into a slideshow. */
private const val IDLE_MS = 90_000L
private const val SLIDE_MS = 9_000L
private const val SHOT_MS = 5_000L

/**
 * The second screen: the selected port in full, its screenshots and the files it needs when its
 * page is open, the downloads at the bottom, and the catalog's art when nobody is playing.
 */
@Composable
fun SecondScreenContent(screen: SecondScreen) {
    val selected by screen.selected.collectAsStateWithLifecycle()
    val opened by screen.opened.collectAsStateWithLifecycle()
    val items by screen.items.collectAsStateWithLifecycle()
    val lastInput by screen.lastInput.collectAsStateWithLifecycle()
    var idle by remember { mutableStateOf(false) }
    LaunchedEffect(lastInput) {
        idle = false
        delay(IDLE_MS - (SystemClock.uptimeMillis() - lastInput))
        idle = true
    }
    val slides = remember(items) { items.filter { it.port.art?.backdrops.orEmpty().isNotEmpty() }.shuffled() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            val item = opened ?: selected
            when {
                idle && slides.isNotEmpty() -> Slideshow(slides)
                item != null -> Showcase(item, open = opened != null)
                else -> Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.displayMedium,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            Downloads(screen, Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing))
        }
    }
}

@Composable
private fun Showcase(item: PortItem, open: Boolean) {
    val port = item.port
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > maxHeight
        PortBackdrop(port, Modifier.fillMaxSize())
        val screenshots = port.art?.screenshots.orEmpty()
        val side: (@Composable (Modifier) -> Unit) = { modifier ->
            when {
                open && port.assets.isNotEmpty() -> FilesHelp(port, modifier)
                open && screenshots.isNotEmpty() -> Screenshots(screenshots, modifier)
                else -> Reports(port, modifier)
            }
        }
        val padding = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(28.dp)
        if (wide) {
            Row(padding, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Info(item, Modifier.weight(1f))
                side(Modifier.weight(1f))
            }
        } else {
            Column(padding, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Info(item, Modifier.fillMaxWidth())
                side(Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}

/** Logo, labels, compatibility and the whole description. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Info(item: PortItem, modifier: Modifier) {
    val port = item.port
    val manager = rememberInstallManager()
    val revision by manager.revision.collectAsStateWithLifecycle()
    val status = remember(port, revision) { manager.status(port) }
    Column(
        modifier.background(Panel, RoundedCornerShape(16.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PortTitle(port, logoHeight = 112.dp, style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.original_game, port.game),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill(platformName(port.originalPlatform))
            Pill(typeName(port.type))
            StatusPill(item, status)
        }
        item.issues.forEach { issue ->
            Text(issueText(issue), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        port.description?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                maxLines = 10,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** How the port runs on players' devices, this one first. Reporting stays on the port's page. */
@Composable
private fun Reports(port: Port, modifier: Modifier) {
    val device = remember { AndroidDevice.modelName() }
    val reports = port.reports.sortedByDescending { it.device.equals(device, ignoreCase = true) }
    Column(
        modifier.background(Panel, RoundedCornerShape(16.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.section_reports), style = MaterialTheme.typography.titleMedium)
        if (reports.isEmpty()) {
            Text(stringResource(R.string.reports_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        reports.take(6).forEach { report ->
            val mine = report.device.equals(device, ignoreCase = true)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (mine) stringResource(R.string.reports_this_device, report.device) else report.device,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (report.great > 0) Pill(stringResource(R.string.reports_great, report.great), color = MaterialTheme.colorScheme.secondary, textColor = Night)
                    if (report.playable > 0) Pill(stringResource(R.string.reports_playable, report.playable), color = MaterialTheme.colorScheme.primary, textColor = Night)
                    if (report.broken > 0) {
                        Pill(
                            stringResource(R.string.reports_broken, report.broken),
                            color = MaterialTheme.colorScheme.errorContainer,
                            textColor = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
        }
    }
}

/** What the files the port needs are and where they go, while the player picks them on the main screen. */
@Composable
private fun FilesHelp(port: Port, modifier: Modifier) {
    Column(
        modifier
            .background(Panel, RoundedCornerShape(16.dp))
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.section_files), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.second_screen_files_help),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        port.assets.forEach { asset ->
            Spacer(Modifier.height(4.dp))
            Text(asset.label, style = MaterialTheme.typography.titleSmall)
            asset.accepted.forEach { file ->
                listOfNotNull(file.label, file.format).joinToString(" · ").takeIf { it.isNotEmpty() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    stringResource(R.string.asset_sha1, file.sha1),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val hint = when (asset.delivery.mode) {
                DeliveryMode.IN_APP_PICKER -> stringResource(R.string.asset_hint_picker)
                DeliveryMode.SHARED_FOLDER -> asset.delivery.path?.let { stringResource(R.string.asset_hint_folder, it) }
                DeliveryMode.INTENT -> null
            }
            hint?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            asset.instructionsMd?.let { Text(it.trim(), style = MaterialTheme.typography.bodySmall) }
        }
    }
}

/** The port's screenshots, one after another. */
@Composable
private fun Screenshots(urls: List<String>, modifier: Modifier) {
    var index by remember(urls) { mutableIntStateOf(0) }
    LaunchedEffect(urls) {
        while (true) {
            delay(SHOT_MS)
            index = (index + 1) % urls.size
        }
    }
    Box(modifier) {
        Crossfade(targetState = urls[index % urls.size], animationSpec = tween(800), label = "screenshot") { url ->
            FallbackImage(
                listOf(url),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** The catalog's hero art with a slow zoom, for when the device sits idle. */
@Composable
private fun Slideshow(slides: List<PortItem>) {
    var index by remember(slides) { mutableIntStateOf(0) }
    LaunchedEffect(slides) {
        while (true) {
            delay(SLIDE_MS)
            index = (index + 1) % slides.size
        }
    }
    Crossfade(targetState = slides[index % slides.size], animationSpec = tween(1200), label = "slideshow") { item ->
        val zoom = remember(item) { Animatable(1f) }
        LaunchedEffect(item) { zoom.animateTo(1.08f, tween((SLIDE_MS + 1200).toInt(), easing = LinearEasing)) }
        Box(Modifier.fillMaxSize().background(Night)) {
            FallbackImage(
                item.port.art?.backdrops.orEmpty(),
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = zoom.value
                    scaleY = zoom.value
                },
            )
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Night.copy(alpha = 0f), 1f to Night.copy(alpha = 0.85f))))
            PortTitle(
                item.port,
                logoHeight = 80.dp,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.align(Alignment.BottomStart).windowInsetsPadding(WindowInsets.safeDrawing).padding(32.dp),
            )
        }
    }
}

/** Every port that is downloading, installing or failed, with its progress and touch buttons to pause or resume. */
@Composable
private fun Downloads(screen: SecondScreen, modifier: Modifier) {
    val manager = rememberInstallManager()
    val phases by manager.phases.collectAsStateWithLifecycle()
    val active = phases.entries.filter { it.value != InstallPhase.Idle }
    if (active.isEmpty()) return
    Column(
        modifier
            .padding(16.dp)
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .background(Night.copy(alpha = 0.9f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.downloads_channel), style = MaterialTheme.typography.titleSmall)
        active.take(4).forEach { (portId, phase) ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        manager.label(portId),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        phaseText(phase),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (phase is InstallPhase.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 12.dp).widthIn(max = 360.dp),
                    )
                }
                when (phase) {
                    is InstallPhase.Downloading -> phase.progress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
                        ?: LinearProgressIndicator(Modifier.fillMaxWidth())
                    is InstallPhase.Paused -> phase.progress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
                    InstallPhase.Verifying, InstallPhase.Installing -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    else -> Unit
                }
                // A touch here may take the gamepad's focus with it; give it back to the library.
                fun act(action: () -> Unit) {
                    action()
                    screen.refocusLibrary()
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (phase) {
                        is InstallPhase.Downloading -> OutlinedButton(onClick = { act { manager.pause(portId) } }) {
                            Text(stringResource(R.string.action_pause))
                        }
                        is InstallPhase.Paused -> {
                            Button(onClick = { act { manager.resume(portId) } }) { Text(stringResource(R.string.action_resume)) }
                            TextButton(onClick = { act { manager.cancelDownload(portId) } }) { Text(stringResource(R.string.cancel)) }
                        }
                        is InstallPhase.Failed -> TextButton(onClick = { act { manager.dismiss(portId) } }) {
                            Text(stringResource(R.string.dismiss))
                        }
                        else -> Unit
                    }
                }
            }
        }
        if (active.size > 4) {
            Text(stringResource(R.string.downloads_more, active.size - 4), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun phaseText(phase: InstallPhase): String = when (phase) {
    InstallPhase.Idle -> ""
    is InstallPhase.Downloading -> phase.progress?.let { "${stringResource(R.string.phase_downloading)} ${(it * 100).toInt()} %" }
        ?: stringResource(R.string.phase_downloading)
    is InstallPhase.Paused -> stringResource(R.string.phase_paused)
    InstallPhase.Verifying -> stringResource(R.string.phase_verifying)
    InstallPhase.Installing -> stringResource(R.string.phase_installing)
    InstallPhase.AwaitingUser -> stringResource(R.string.phase_awaiting_user)
    InstallPhase.NeedsPermission -> stringResource(R.string.needs_permission)
    is InstallPhase.Failed -> errorText(phase.error)
}
