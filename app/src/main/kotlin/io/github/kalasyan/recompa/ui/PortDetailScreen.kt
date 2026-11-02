package io.github.kalasyan.recompa.ui

import io.github.kalasyan.recompa.core.catalog.Port
import androidx.compose.ui.semantics.Role
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.border
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kalasyan.recompa.R
import io.github.kalasyan.recompa.core.catalog.Fork
import io.github.kalasyan.recompa.core.device.CompatibilityIssue
import io.github.kalasyan.recompa.data.AndroidDevice
import io.github.kalasyan.recompa.ui.theme.Accent
import io.github.kalasyan.recompa.ui.theme.Night
import io.github.kalasyan.recompa.ui.theme.LocalAppStyle
import io.github.kalasyan.recompa.data.AppStyle

/**
 * A port's page. Each style lays it out its own way; all of them show the same actions and
 * sections, and the same Back and Favourite buttons on top.
 */
@Composable
fun PortDetailScreen(item: PortItem, onBack: () -> Unit) {
    val port = item.port
    val prefs = rememberUserPrefs()
    val favorites by prefs.favorites.collectAsStateWithLifecycle()
    val favorite = port.id in favorites
    val header: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(
                onClick = onBack,
                colors = IconButtonDefaults.iconButtonColors(containerColor = Panel),
                modifier = Modifier.focusRing(CircleShape),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            IconButton(
                onClick = { prefs.toggleFavorite(port.id) },
                colors = IconButtonDefaults.iconButtonColors(containerColor = Panel),
                modifier = Modifier.focusRing(CircleShape),
            ) {
                Icon(
                    if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = stringResource(if (favorite) R.string.favorite_remove else R.string.favorite_add),
                    tint = if (favorite) Accent else Color.White,
                )
            }
        }
    }
    when (LocalAppStyle.current) {
        AppStyle.CLASSIC -> ClassicPortPage(item, header)
        AppStyle.SHELVES -> ShelvesPortPage(item, header)
        AppStyle.LIST -> ListPortPage(item, header)
        AppStyle.ARCADE -> ArcadePortPage(item, header)
        AppStyle.MOSAIC -> MosaicPortPage(item, header)
        AppStyle.SHOWCASE -> ShowcasePortPage(item, header)
    }
}

/** The original page: art on top, cover and details side by side, then the sections. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClassicPortPage(item: PortItem, header: @Composable () -> Unit) {
    val port = item.port
    val manager = rememberInstallManager()
    val revision by manager.revision.collectAsStateWithLifecycle()
    val status = remember(port, revision) { manager.status(port) }

    val scroll = rememberScrollState()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        PortBackdrop(port, Modifier.fillMaxWidth().height(if (wide) maxHeight else 360.dp))
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .dpadScroll(scroll)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp),
        ) {
            Row(Modifier.padding(top = 8.dp)) { header() }
            Spacer(Modifier.height(if (wide) 16.dp else 96.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                PortCover(port, Modifier.width(if (wide) 200.dp else 120.dp))
                // A translucent panel keeps the text readable over bright art.
                Column(
                    Modifier
                        .weight(1f)
                        .widthIn(max = 640.dp)
                        .background(Panel, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PortTitle(
                        port,
                        logoHeight = if (wide) 96.dp else 64.dp,
                        style = if (wide) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        stringResource(R.string.original_game, port.game),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Pill(platformName(port.originalPlatform))
                        Pill(typeName(pickedFork(port).type ?: port.type))
                        StatusPill(item, status)
                    }
                    if (wide) PortActions(item)
                    if (wide) port.description?.let { Description(it) }
                }
            }

            if (!wide) {
                Spacer(Modifier.height(16.dp))
                Column(
                    Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    PortActions(item)
                    port.description?.let { Description(it) }
                }
            }

            Spacer(Modifier.height(24.dp))
            Column(Modifier.widthIn(max = 880.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                portTabs(item, galleryHeight = if (wide) 160.dp else 120.dp).forEach { tab ->
                    Section(tab.title) { tab.content() }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
internal fun PortActions(item: PortItem) {
    Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item.issues.forEach { issue ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Text(issueText(issue), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (item.port.recommendedFork != null) {
            val fork = pickedFork(item.port)
            if (item.port.versions.size > 1) VersionPicker(item.port, fork) { PickedForks.pick(item.port, it) }
            Box(Modifier.widthIn(max = 420.dp)) { InstallActions(item, fork) }
        } else {
            Text(stringResource(R.string.no_release), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Description(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
        modifier = Modifier.widthIn(max = 640.dp),
    )
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

/**
 * The ports of this game a player can install, to pick one: each with how players rate it,
 * the recommended (best rated) one marked, and the installed ones.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VersionPicker(port: Port, selected: Fork, onPick: (Fork) -> Unit) {
    val manager = rememberInstallManager()
    val revision by manager.revision.collectAsStateWithLifecycle()
    val installed = remember(port, revision) { manager.installedForks(port).map { it.repo }.toSet() }
    val recommended = port.recommendedFork
    Column(Modifier.widthIn(max = 520.dp).selectableGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.versions_pick_title), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(R.string.versions_pick_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        port.versions.forEach { fork ->
            val shape = RoundedCornerShape(12.dp)
            val chosen = fork.repo == selected.repo
            Row(
                Modifier
                    .fillMaxWidth()
                    .focusRing(shape)
                    .clip(shape)
                    .background(if (chosen) Accent.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f))
                    .border(1.dp, if (chosen) Accent else Color.Transparent, shape)
                    .selectable(selected = chosen, role = Role.RadioButton, onClick = { onPick(fork) })
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = chosen, onClick = null)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(forkLabel(fork), style = MaterialTheme.typography.bodyLarge)
                    val details = listOfNotNull(
                        fork.repo.takeIf { fork.name != null },
                        fork.type?.takeIf { it != port.type }?.let { typeName(it) },
                        fork.release?.tag,
                        ratingText(fork),
                    )
                    Text(
                        details.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (fork == recommended) Pill(stringResource(R.string.fork_recommended), color = Accent, textColor = Night)
                    if (fork.repo in installed) {
                        Pill(stringResource(R.string.status_installed), color = MaterialTheme.colorScheme.secondary, textColor = Night)
                    }
                }
            }
        }
    }
}

/** How players rate [fork], in a few words. */
@Composable
private fun ratingText(fork: Fork): String {
    val reports = fork.reports
    if (reports.isEmpty()) return stringResource(R.string.fork_no_reports)
    val good = reports.sumOf { it.great + it.playable }
    val broken = reports.sumOf { it.broken }
    return stringResource(R.string.fork_rating, good, broken)
}

/**
 * The version of each port the player picked on its page, shared by the actions and the
 * sections of every style. Kept while the app runs; a port nobody picked a version of shows
 * the installed one, else the recommended one.
 */
internal object PickedForks {
    private val picked = mutableStateMapOf<String, String>()

    fun pick(port: Port, fork: Fork) {
        picked[port.id] = fork.repo
    }

    fun repo(port: Port): String? = picked[port.id]
}

/** The version of [port] its page is about: picked, else installed, else recommended. */
@Composable
internal fun pickedFork(port: Port): Fork {
    val manager = rememberInstallManager()
    val revision by manager.revision.collectAsStateWithLifecycle()
    val installed = remember(port, revision) { manager.installedFork(port) }
    return port.fork(PickedForks.repo(port)) ?: installed ?: port.recommendedFork ?: port.forks.first()
}

@Composable
internal fun ForkRow(fork: Fork, recommended: Boolean) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(12.dp)
    // The whole row opens the fork's repo, so players can check where the APK comes from.
    Row(
        Modifier
            .fillMaxWidth()
            .focusRing(shape)
            .clip(shape)
            .clickable(onClickLabel = stringResource(R.string.fork_open_repo)) {
                context.startSafely(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/${fork.repo}")))
            }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .padding(end = 12.dp)
                .width(8.dp)
                .height(8.dp)
                .background(if (recommended) Accent else Color.White.copy(alpha = 0.2f), CircleShape),
        )
        Column(Modifier.weight(1f)) {
            fork.name?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Text("${fork.repo} ↗", style = MaterialTheme.typography.bodyLarge, color = Accent)
            val release = fork.release
            Text(
                if (release == null) stringResource(R.string.no_release)
                else stringResource(R.string.fork_release, release.tag, release.asset.name),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (recommended) Pill(stringResource(R.string.fork_recommended), color = Accent, textColor = Night)
            if (fork.official) Pill(stringResource(R.string.fork_official))
            if (fork.testedOn.isNotEmpty()) Pill(stringResource(R.string.fork_tested_on, fork.testedOn.joinToString()))
        }
    }
}

@Composable
internal fun issueText(issue: CompatibilityIssue): String = when (issue) {
    is CompatibilityIssue.UnsupportedAbi -> stringResource(R.string.issue_abi, issue.required.joinToString())
    is CompatibilityIssue.AndroidTooOld -> stringResource(R.string.issue_sdk, AndroidDevice.versionName(issue.requiredSdk))
    CompatibilityIssue.NoVulkan -> stringResource(R.string.issue_vulkan)
}
