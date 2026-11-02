package io.github.kalasyan.recompa.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kalasyan.recompa.R
import io.github.kalasyan.recompa.data.AppLanguage
import io.github.kalasyan.recompa.data.AppStyle
import io.github.kalasyan.recompa.ui.theme.Accent
import io.github.kalasyan.recompa.ui.theme.LocalAppStyle
import io.github.kalasyan.recompa.ui.theme.Night
import io.github.kalasyan.recompa.ui.theme.Playfair
import io.github.kalasyan.recompa.ui.theme.PressStart
import io.github.kalasyan.recompa.ui.theme.Rajdhani
import io.github.kalasyan.recompa.ui.theme.SpaceGrotesk

/** One line of the settings: a choice among options, an on/off switch or a button. */
private sealed interface SettingItem {
    val id: String
    val title: String
    val help: String?

    class Choice(
        override val id: String,
        override val title: String,
        override val help: String?,
        val options: List<String>,
        val selected: Int,
        /** Picking applies at once, unless this is set: then moving through options waits for A. */
        val confirm: Boolean = false,
        val onPick: (Int) -> Unit,
    ) : SettingItem

    class Toggle(
        override val id: String,
        override val title: String,
        override val help: String?,
        val checked: Boolean,
        val onChange: (Boolean) -> Unit,
    ) : SettingItem

    class Action(
        override val id: String,
        override val title: String,
        override val help: String?,
        val onClick: () -> Unit,
    ) : SettingItem
}

/**
 * How the settings look in one style. Rows and options take focus themselves and draw their
 * focus in their own shape, so the highlight always matches what it surrounds.
 */
private class SettingsSkin(
    val shape: Shape,
    val title: TextStyle,
    val label: TextStyle,
    val help: TextStyle,
    val accent: Color,
    /** Background of each row; transparent for list-like styles. */
    val row: Color,
    val rowFocused: Color,
    val focusBorder: Color,
    val option: Color,
    val optionText: Color,
    val optionSelected: Color,
    val optionSelectedText: Color,
    /** Values on one line, changed with Left/Right (List, Arcade); otherwise options are laid out. */
    val inline: Boolean,
    val uppercase: Boolean = false,
    val marker: String? = null,
    val divider: Color? = null,
    val switch: @Composable (Boolean) -> Unit,
)

/**
 * The settings screen: language, style, frontend mode, the second screen and update
 * notifications, drawn in the current style's own way.
 */
@Composable
fun SettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val prefs = rememberUserPrefs()
    val frontend by prefs.frontendMode.collectAsStateWithLifecycle()
    val notify by prefs.updateNotifications.collectAsStateWithLifecycle()
    val secondScreen by prefs.secondScreen.collectAsStateWithLifecycle()
    val style by prefs.style.collectAsStateWithLifecycle()
    val language = remember { AppLanguage.current(activity) }
    val languages = AppLanguage.supported.entries.toList()
    val styles = AppStyle.entries

    val items = buildList {
        add(
            SettingItem.Choice(
                "language", stringResource(R.string.settings_language), null,
                options = languages.map { it.value },
                selected = languages.indexOfFirst { it.key == language }.coerceAtLeast(0),
                // Changing the language restarts the screen, so it waits for a confirmation.
                confirm = true,
                onPick = { index -> languages[index].key.takeIf { it != language }?.let { AppLanguage.set(activity, it) } },
            ),
        )
        add(
            SettingItem.Choice(
                "style", stringResource(R.string.settings_style), stringResource(style.help),
                options = styles.map { stringResource(it.label) },
                selected = styles.indexOf(style),
                onPick = { prefs.setStyle(styles[it]) },
            ),
        )
        add(SettingItem.Toggle("frontend", stringResource(R.string.settings_frontend), stringResource(R.string.settings_frontend_help), frontend, prefs::setFrontendMode))
        if (frontend) {
            add(SettingItem.Action("home", stringResource(R.string.settings_choose_home), null) { context.startSafely(Intent(Settings.ACTION_HOME_SETTINGS)) })
        }
        add(SettingItem.Toggle("second", stringResource(R.string.settings_second_screen), stringResource(R.string.settings_second_screen_help), secondScreen, prefs::setSecondScreen))
        add(SettingItem.Toggle("notify", stringResource(R.string.settings_notify), stringResource(R.string.settings_notify_help), notify, prefs::setUpdateNotifications))
        add(SettingItem.Action("android", stringResource(R.string.settings_android), null) { context.startSafely(Intent(Settings.ACTION_SETTINGS)) })
    }

    // Focus stays on the same line when the style (and so the whole screen) changes.
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    var focusedId by remember { mutableStateOf("language") }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val currentStyle = LocalAppStyle.current
        val skin = skinFor(currentStyle)
        LaunchedEffect(currentStyle) {
            // Wait for the rows to be laid out, then focus the line that was focused before.
            withFrameNanos { }
            withFrameNanos { }
            runCatching { requesters[focusedId]?.requestFocus() }
        }
        val rows: @Composable ColumnScope.() -> Unit = {
            items.forEach { item ->
                SettingRow(
                    item,
                    skin,
                    requesters.getOrPut(item.id) { FocusRequester() },
                    onFocused = { focusedId = item.id },
                )
                skin.divider?.let { HorizontalDivider(color = it) }
            }
        }
        val focusedHelp = items.firstOrNull { it.id == focusedId }?.help
        when (currentStyle) {
            AppStyle.CLASSIC -> ClassicSettings(skin, onDismiss, rows)
            AppStyle.SHELVES -> ShelvesSettings(skin, onDismiss, rows)
            AppStyle.LIST -> ListSettings(skin, focusedHelp, rows)
            AppStyle.ARCADE -> ArcadeSettings(skin, focusedHelp, rows)
            AppStyle.MOSAIC -> MosaicSettings(skin, onDismiss, rows)
            AppStyle.SHOWCASE -> ShowcaseSettings(skin, onDismiss, rows)
        }
    }
}

@Composable
private fun skinFor(style: AppStyle): SettingsSkin {
    val type = MaterialTheme.typography
    val accent = Accent
    val night = Night
    return when (style) {
        AppStyle.CLASSIC -> SettingsSkin(
            shape = RoundedCornerShape(14.dp),
            title = type.headlineSmall.copy(color = accent),
            label = type.titleSmall, help = type.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
            accent = accent,
            row = Color.White.copy(alpha = 0.04f), rowFocused = Color.White.copy(alpha = 0.1f), focusBorder = Color.White,
            option = Color.White.copy(alpha = 0.08f), optionText = Color.White,
            optionSelected = accent, optionSelectedText = night,
            inline = false,
            switch = { PillSwitch(it, on = accent, knobOn = night) },
        )
        AppStyle.SHELVES -> SettingsSkin(
            shape = RoundedCornerShape(8.dp),
            title = type.headlineMedium.copy(fontWeight = FontWeight.Black, color = Color.White),
            label = type.titleMedium.copy(fontWeight = FontWeight.Bold), help = type.bodySmall.copy(color = Color.White.copy(alpha = 0.6f)),
            accent = accent,
            row = Color.Transparent, rowFocused = Color.White.copy(alpha = 0.12f), focusBorder = Color.White,
            option = Color.White.copy(alpha = 0.12f), optionText = Color.White,
            optionSelected = Color.White, optionSelectedText = Color.Black,
            inline = false,
            divider = Color.White.copy(alpha = 0.08f),
            switch = { PillSwitch(it, on = accent, knobOn = Color.White) },
        )
        AppStyle.LIST -> SettingsSkin(
            shape = RectangleShape,
            title = TextStyle(fontFamily = Rajdhani, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = 3.sp, color = Color.White),
            label = TextStyle(fontFamily = Rajdhani, fontWeight = FontWeight.Medium, fontSize = 18.sp, color = Color.White),
            help = TextStyle(fontFamily = Rajdhani, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant),
            accent = accent,
            row = Color.Transparent, rowFocused = accent.copy(alpha = 0.22f), focusBorder = Color.Transparent,
            option = Color.Transparent, optionText = Color.White,
            optionSelected = Color.Transparent, optionSelectedText = accent,
            inline = true,
            marker = "",
            switch = { TextSwitch(it, on = accent, style = TextStyle(fontFamily = Rajdhani, fontWeight = FontWeight.Bold, fontSize = 17.sp)) },
        )
        AppStyle.ARCADE -> {
            val pixel = TextStyle(fontFamily = PressStart, fontSize = 11.sp, lineHeight = 16.sp, color = Color.White)
            SettingsSkin(
                shape = RectangleShape,
                title = pixel.copy(fontSize = 20.sp, lineHeight = 26.sp, color = Color(0xFFFFE14D), shadow = Shadow(Color(0xFFFF2D95), Offset(5f, 5f), 0f)),
                label = pixel,
                help = TextStyle(fontSize = 13.sp, color = Color(0xFFB9A6E0)),
                accent = accent,
                row = Color.Transparent, rowFocused = Color(0xFF00FFF0).copy(alpha = 0.14f), focusBorder = Color.Transparent,
                option = Color.Transparent, optionText = Color.White,
                optionSelected = Color.Transparent, optionSelectedText = Color(0xFFFFE14D),
                inline = true, uppercase = true, marker = "▶",
                switch = { TextSwitch(it, on = Color(0xFF00FFF0), off = Color(0xFFFF2D95), style = pixel) },
            )
        }
        AppStyle.MOSAIC -> SettingsSkin(
            shape = RectangleShape,
            title = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 34.sp, color = Color.White),
            label = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White),
            help = TextStyle(fontFamily = SpaceGrotesk, fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f)),
            accent = accent,
            row = Color(0xFF2B2E38), rowFocused = Color(0xFF353946), focusBorder = Color.White,
            option = Color(0xFF1D1F26), optionText = Color.White,
            optionSelected = Color(0xFF5865F2), optionSelectedText = Color.White,
            inline = false,
            switch = { BlockSwitch(it, on = Color(0xFF3DDC97)) },
        )
        AppStyle.SHOWCASE -> SettingsSkin(
            shape = RoundedCornerShape(50),
            title = TextStyle(fontFamily = Playfair, fontWeight = FontWeight.Bold, fontSize = 30.sp, color = Color.White),
            label = TextStyle(fontFamily = Playfair, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White),
            help = type.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontStyle = FontStyle.Italic),
            accent = accent,
            row = Color.Transparent, rowFocused = Color.White.copy(alpha = 0.06f), focusBorder = accent,
            option = Color.Transparent, optionText = Color.White.copy(alpha = 0.75f),
            optionSelected = Color.White, optionSelectedText = Color.Black,
            inline = false,
            divider = Color.White.copy(alpha = 0.1f),
            switch = { PillSwitch(it, on = Color.Transparent, knobOn = accent, outline = accent) },
        )
    }
}

@Composable
private fun SettingRow(item: SettingItem, skin: SettingsSkin, requester: FocusRequester, onFocused: () -> Unit) {
    if (skin.inline) InlineRow(item, skin, requester, onFocused) else LaidOutRow(item, skin, requester, onFocused)
}

private fun SettingsSkin.text(s: String) = if (uppercase) s.uppercase() else s

/**
 * A row with its value on the right, as in a game's options menu: Left/Right change a choice,
 * A switches a toggle or runs an action.
 */
@Composable
private fun InlineRow(item: SettingItem, skin: SettingsSkin, requester: FocusRequester, onFocused: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    var pending by remember(item.id, (item as? SettingItem.Choice)?.selected) { mutableStateOf((item as? SettingItem.Choice)?.selected ?: 0) }
    val step: (Int) -> Unit = { delta ->
        if (item is SettingItem.Choice) {
            pending = (pending + delta).mod(item.options.size)
            if (!item.confirm) item.onPick(pending)
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .focusRequester(requester)
            .onFocusChanged { if (it.isFocused) onFocused() }
            .onPreviewKeyEvent { event ->
                if (item !is SettingItem.Choice || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> step(-1)
                    Key.DirectionRight -> step(1)
                    else -> return@onPreviewKeyEvent false
                }
                true
            }
            .background(if (focused) skin.rowFocused else skin.row)
            .clickable(interactionSource = interaction, indication = null) {
                when (item) {
                    is SettingItem.Choice -> if (item.confirm && pending != item.selected) item.onPick(pending) else step(1)
                    is SettingItem.Toggle -> item.onChange(!item.checked)
                    is SettingItem.Action -> item.onClick()
                }
            }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).height(26.dp).background(if (focused) skin.accent else Color.Transparent))
        skin.marker?.let { marker ->
            Text(if (focused && marker.isNotEmpty()) "$marker " else "  ", style = skin.label.copy(color = skin.accent), modifier = Modifier.padding(start = 10.dp))
        }
        Text(
            skin.text(item.title),
            style = skin.label.copy(color = if (focused) Color.White else skin.label.color.copy(alpha = 0.75f)),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 10.dp),
        )
        when (item) {
            is SettingItem.Choice -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 16.dp)) {
                Text("◀", style = skin.label.copy(color = if (focused) skin.accent else Color.Transparent, fontSize = 11.sp))
                Text(
                    skin.text(item.options[pending]),
                    style = skin.label.copy(color = skin.optionSelectedText),
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                Text("▶", style = skin.label.copy(color = if (focused) skin.accent else Color.Transparent, fontSize = 11.sp))
            }
            is SettingItem.Toggle -> Box(Modifier.padding(end = 16.dp)) { skin.switch(item.checked) }
            is SettingItem.Action -> Text("↗", style = skin.label.copy(color = skin.accent), modifier = Modifier.padding(end = 20.dp))
        }
    }
    if (item is SettingItem.Choice && item.confirm && pending != item.selected) {
        Text(
            skin.text(stringResource(R.string.settings_press_a)),
            style = skin.help.copy(color = skin.accent),
            modifier = Modifier.padding(start = 26.dp, bottom = 4.dp),
        )
    }
}

/** A row that shows its options laid out: chips for choices, a switch for toggles. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun LaidOutRow(item: SettingItem, skin: SettingsSkin, requester: FocusRequester, onFocused: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    // Choices focus their options; toggles and actions are one focusable row.
    val wholeRow = item !is SettingItem.Choice
    Column(
        Modifier
            .fillMaxWidth()
            .clip(skin.shape.takeIf { skin.row != Color.Transparent } ?: RectangleShape)
            .background(if (focused) skin.rowFocused else skin.row)
            .then(
                if (wholeRow) {
                    Modifier
                        .focusRequester(requester)
                        .onFocusChanged { if (it.isFocused) onFocused() }
                        .border(if (focused) 2.dp else 0.dp, if (focused) skin.focusBorder else Color.Transparent, skin.shape.takeIf { skin.row != Color.Transparent } ?: RectangleShape)
                        .clickable(interactionSource = interaction, indication = null) {
                            when (item) {
                                is SettingItem.Toggle -> item.onChange(!item.checked)
                                is SettingItem.Action -> item.onClick()
                                is SettingItem.Choice -> Unit
                            }
                        }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.title, style = skin.label)
                item.help?.let { Text(it, style = skin.help) }
            }
            when (item) {
                is SettingItem.Toggle -> Box(Modifier.padding(start = 16.dp)) { skin.switch(item.checked) }
                is SettingItem.Action -> Text("↗", style = skin.label.copy(color = skin.accent), modifier = Modifier.padding(start = 16.dp))
                is SettingItem.Choice -> Unit
            }
        }
        if (item is SettingItem.Choice) {
            @Suppress("DEPRECATION")
            FlowRow(
                // Moving into the options lands on the chosen one, not the nearest.
                Modifier.focusProperties { enter = { requester } }.focusGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item.options.forEachIndexed { index, label ->
                    OptionChip(
                        label,
                        selected = index == item.selected,
                        skin = skin,
                        modifier = if (index == item.selected) Modifier.focusRequester(requester) else Modifier,
                        onFocused = onFocused,
                        onClick = { if (index != item.selected) item.onPick(index) },
                    )
                }
            }
        }
    }
}

/** One option of a choice. Its focus border follows its own shape. */
@Composable
private fun OptionChip(label: String, selected: Boolean, skin: SettingsSkin, modifier: Modifier, onFocused: () -> Unit, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val underline = skin.option == Color.Transparent && skin.optionSelected != Color.Transparent && skin.shape == RoundedCornerShape(50)
    Text(
        label,
        style = skin.label.copy(
            fontSize = skin.help.fontSize * 1.15f,
            color = if (selected) skin.optionSelectedText else skin.optionText,
            fontWeight = if (selected) FontWeight.Bold else skin.label.fontWeight,
        ),
        maxLines = 1,
        modifier = modifier
            .onFocusChanged { if (it.isFocused) onFocused() }
            .clip(skin.shape)
            .background(if (selected) skin.optionSelected else if (focused && underline) Color.White.copy(alpha = 0.1f) else skin.option)
            .border(
                width = if (focused) 2.dp else if (underline && !selected) 1.dp else 0.dp,
                color = if (focused) skin.focusBorder else if (underline) Color.White.copy(alpha = 0.25f) else Color.Transparent,
                shape = skin.shape,
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

@Composable
private fun PillSwitch(checked: Boolean, on: Color, knobOn: Color, outline: Color? = null) {
    val knob by animateDpAsState(if (checked) 20.dp else 0.dp, label = "switch")
    Box(
        Modifier
            .size(width = 44.dp, height = 24.dp)
            .clip(CircleShape)
            .background(if (checked) on else Color.White.copy(alpha = 0.15f))
            .then(if (outline != null) Modifier.border(1.5.dp, if (checked) outline else Color.White.copy(alpha = 0.3f), CircleShape) else Modifier)
            .padding(3.dp),
    ) {
        Box(Modifier.offset(x = knob).size(18.dp).clip(CircleShape).background(if (checked) knobOn else Color.White.copy(alpha = 0.7f)))
    }
}

@Composable
private fun BlockSwitch(checked: Boolean, on: Color) {
    Row {
        Text(
            stringResource(R.string.settings_off).uppercase(),
            style = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 12.sp),
            color = if (checked) Color.White.copy(alpha = 0.4f) else Color.White,
            modifier = Modifier.background(if (checked) Color(0xFF1D1F26) else Color(0xFFFF5A5F)).padding(horizontal = 10.dp, vertical = 6.dp),
        )
        Text(
            stringResource(R.string.settings_on).uppercase(),
            style = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 12.sp),
            color = if (checked) Color(0xFF00331C) else Color.White.copy(alpha = 0.4f),
            modifier = Modifier.background(if (checked) on else Color(0xFF1D1F26)).padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun TextSwitch(checked: Boolean, on: Color, style: TextStyle, off: Color = Color.White.copy(alpha = 0.45f)) {
    Text(
        stringResource(if (checked) R.string.settings_on else R.string.settings_off).uppercase(),
        style = style,
        color = if (checked) on else off,
    )
}

/** Close hint for the styles without a close button: B or Back. */
@Composable
private fun BackHint(color: Color = Color.White) = ButtonHint("B", stringResource(R.string.dismiss), color)

@Composable
private fun CloseButton(skin: SettingsSkin, onDismiss: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Text(
        stringResource(R.string.dismiss),
        style = skin.label.copy(fontSize = skin.help.fontSize * 1.15f, color = if (focused) skin.optionSelectedText else Color.White),
        modifier = Modifier
            .clip(skin.shape)
            .background(if (focused) skin.optionSelected else skin.option)
            .clickable(interactionSource = interaction, indication = null, onClick = onDismiss)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun Scrim(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).windowInsetsPadding(WindowInsets.safeDrawing), content = content)
}

/** Classic: a rounded card in the middle of the screen. */
@Composable
private fun ClassicSettings(skin: SettingsSkin, onDismiss: () -> Unit, rows: @Composable ColumnScope.() -> Unit) {
    Scrim {
        Column(
            Modifier
                .align(Alignment.Center)
                .padding(16.dp)
                .widthIn(max = 680.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_title), style = skin.title, modifier = Modifier.weight(1f))
                CloseButton(skin, onDismiss)
            }
            Spacer(Modifier.height(12.dp))
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp), content = rows)
        }
    }
}

/** Shelves: a full-height sheet sliding in from the right, like a console's quick menu. */
@Composable
private fun ShelvesSettings(skin: SettingsSkin, onDismiss: () -> Unit, rows: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.3f), 0.5f to Color.Black.copy(alpha = 0.75f))),
    ) {
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .fillMaxWidth(0.52f)
                .background(Night.copy(alpha = 0.97f))
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 28.dp, vertical = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_title), style = skin.title, modifier = Modifier.weight(1f))
                CloseButton(skin, onDismiss)
            }
            Spacer(Modifier.height(12.dp))
            Column(Modifier.verticalScroll(rememberScrollState()), content = rows)
        }
    }
}

/** List: a frontend's options menu, full screen, with the focused line's help at the bottom. */
@Composable
private fun ListSettings(skin: SettingsSkin, help: String?, rows: @Composable ColumnScope.() -> Unit) {
    val night = Night
    Column(Modifier.fillMaxSize().background(night).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Text(
            stringResource(R.string.settings_title).uppercase(),
            style = skin.title,
            modifier = Modifier.padding(start = 24.dp, top = 18.dp, bottom = 10.dp),
        )
        Column(Modifier.weight(1f).widthIn(max = 760.dp).verticalScroll(rememberScrollState()), content = rows)
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color(red = night.red * 0.7f, green = night.green * 0.7f, blue = night.blue * 0.7f))
                .padding(horizontal = 24.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(help ?: " ", style = skin.help, maxLines = 3, minLines = 2)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ButtonHint("◀ ▶", stringResource(R.string.settings_hint_change))
                ButtonHint("A", stringResource(R.string.settings_hint_select))
                BackHint()
            }
        }
    }
}

/** Arcade: an "options" screen in a neon frame over the synthwave floor. */
@Composable
private fun ArcadeSettings(skin: SettingsSkin, help: String?, rows: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xFF07010F))) {
        SynthwaveBackground(Modifier.matchParentSize())
        Column(
            Modifier
                .align(Alignment.Center)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp)
                .widthIn(max = 760.dp)
                .fillMaxHeight()
                .border(3.dp, Color(0xFFFF2D95))
                .padding(3.dp)
                .border(3.dp, Color(0xFF00FFF0))
                .background(Color(0xFF07010F).copy(alpha = 0.9f))
                .padding(18.dp),
        ) {
            Text(
                stringResource(R.string.settings_title).uppercase(),
                style = skin.title,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 14.dp),
            )
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), content = rows)
            Text(help ?: " ", style = skin.help, maxLines = 3, minLines = 2, modifier = Modifier.padding(top = 8.dp))
            Text(
                listOf(
                    "◀▶ " + stringResource(R.string.settings_hint_change),
                    "A " + stringResource(R.string.settings_hint_select),
                    "B " + stringResource(R.string.dismiss),
                ).joinToString("   ").uppercase(),
                style = skin.label.copy(fontSize = 8.sp),
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp),
            )
        }
    }
}

/** Mosaic: the title in large type on the left, every setting a flat tile on the right. */
@Composable
private fun MosaicSettings(skin: SettingsSkin, onDismiss: () -> Unit, rows: @Composable ColumnScope.() -> Unit) {
    Row(Modifier.fillMaxSize().background(Night).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Column(Modifier.width(200.dp).fillMaxHeight().padding(start = 22.dp, top = 18.dp)) {
            Text(
                stringResource(R.string.app_name).uppercase(),
                style = skin.label.copy(fontSize = 12.sp, letterSpacing = 4.sp, color = skin.accent),
            )
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.settings_title), style = skin.title)
            Spacer(Modifier.weight(1f))
            Box(Modifier.padding(bottom = 18.dp)) { CloseButton(skin, onDismiss) }
        }
        Column(
            Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(top = 18.dp, end = 20.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = rows,
        )
    }
}

/** Showcase: an elegant card with serif headings and hairlines between settings. */
@Composable
private fun ShowcaseSettings(skin: SettingsSkin, onDismiss: () -> Unit, rows: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(Color(0xFF2C2F3A).copy(alpha = 0.97f), Night.copy(alpha = 0.97f)), radius = 1400f))
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(Modifier.align(Alignment.TopCenter).widthIn(max = 640.dp).fillMaxHeight().padding(horizontal = 16.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_title), style = skin.title, modifier = Modifier.weight(1f))
                CloseButton(skin, onDismiss)
            }
            HorizontalDivider(Modifier.padding(top = 10.dp), color = skin.accent.copy(alpha = 0.5f))
            Column(Modifier.verticalScroll(rememberScrollState()), content = rows)
        }
    }
}

private val AppStyle.label: Int
    get() = when (this) {
        AppStyle.CLASSIC -> R.string.style_classic
        AppStyle.SHELVES -> R.string.style_shelves
        AppStyle.LIST -> R.string.style_list
        AppStyle.ARCADE -> R.string.style_arcade
        AppStyle.MOSAIC -> R.string.style_mosaic
        AppStyle.SHOWCASE -> R.string.style_showcase
    }

private val AppStyle.help: Int
    get() = when (this) {
        AppStyle.CLASSIC -> R.string.style_classic_help
        AppStyle.SHELVES -> R.string.style_shelves_help
        AppStyle.LIST -> R.string.style_list_help
        AppStyle.ARCADE -> R.string.style_arcade_help
        AppStyle.MOSAIC -> R.string.style_mosaic_help
        AppStyle.SHOWCASE -> R.string.style_showcase_help
    }

private tailrec fun Context.findActivity(): Activity = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> error("No activity")
}

/**
 * Asks once for the permission to show notifications (Android 13+), so the background check
 * can say when ports have updates. Waits until the welcome dialog has been seen.
 */
@Composable
fun NotificationPermissionRequest() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val prefs = rememberUserPrefs()
    val notify by prefs.updateNotifications.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val ready = remember { welcomeSeen(context) }
    LaunchedEffect(notify, ready) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (notify && ready && !granted && !prefs.askedNotificationPermission) {
            prefs.askedNotificationPermission = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
