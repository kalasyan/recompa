package io.github.kalasyan.recompa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.kalasyan.recompa.R
import io.github.kalasyan.recompa.core.update.Inline
import io.github.kalasyan.recompa.core.update.NotesBlock
import io.github.kalasyan.recompa.data.AppLanguage
import io.github.kalasyan.recompa.data.AppStyle
import io.github.kalasyan.recompa.data.WhatsNew
import io.github.kalasyan.recompa.ui.theme.LocalAppStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Shows this version's release notes the first time Recompa opens after an update. */
@Composable
fun WhatsNewOnUpdate() {
    val context = LocalContext.current
    val whatsNew = remember(context) { WhatsNew(context) }
    // A new install records the notes as seen and shows the welcome dialog instead.
    var show by remember { mutableStateOf(whatsNew.due() && welcomeSeen(context)) }
    if (show) {
        WhatsNewDialog(whatsNew, onDismiss = {
            whatsNew.markSeen()
            show = false
        })
    }
}

/**
 * The release notes of [whatsNew]'s version, in the app's language, drawn like the settings of
 * the current style. Up/Down (or the touch screen) scroll them; A or B closes them.
 */
@Composable
fun WhatsNewDialog(whatsNew: WhatsNew, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val version = whatsNew.version
    val blocks = remember(whatsNew) { whatsNew.notes(AppLanguage.current(context)) }
    if (version == null || blocks.isEmpty()) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val style = LocalAppStyle.current
        val skin = skinFor(style)
        val scroll = rememberScrollState()
        val scope = rememberCoroutineScope()
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            // The dialog's window takes a moment to get focus after it opens.
            withFrameNanos { }
            delay(250)
            runCatching { focus.requestFocus() }
        }
        val title = stringResource(R.string.whats_new_title)
        val subtitle = "Recompa $version"
        val hints = listOf("▲ ▼" to stringResource(R.string.whats_new_scroll), "A" to stringResource(R.string.whats_new_ok))
        val content: @Composable ColumnScope.() -> Unit = { NotesContent(blocks, skin, subtitle.takeUnless { skin.inline }) }
        Box(
            Modifier
                .fillMaxSize()
                .focusRequester(focus)
                .focusable()
                .onPreviewKeyEvent { event ->
                    val step = (scroll.viewportSize * 0.6f).coerceAtLeast(200f)
                    when (event.key) {
                        Key.DirectionDown, Key.PageDown -> {
                            if (event.type == KeyEventType.KeyDown) scope.launch { scroll.animateScrollBy(step) }
                            true
                        }
                        Key.DirectionUp, Key.PageUp -> {
                            if (event.type == KeyEventType.KeyDown) scope.launch { scroll.animateScrollBy(-step) }
                            true
                        }
                        Key.ButtonA, Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                            if (event.type == KeyEventType.KeyUp) onDismiss()
                            true
                        }
                        else -> false
                    }
                },
        ) {
            when (style) {
                AppStyle.CLASSIC -> ClassicSettings(skin, onDismiss, title, scroll, content)
                AppStyle.SHELVES -> ShelvesSettings(skin, onDismiss, title, scroll, content)
                AppStyle.LIST -> ListSettings(skin, subtitle, title, hints, scroll, content)
                AppStyle.ARCADE -> ArcadeSettings(skin, subtitle, title, hints, scroll, content)
                AppStyle.MOSAIC -> MosaicSettings(skin, onDismiss, title, scroll, content)
                AppStyle.SHOWCASE -> ShowcaseSettings(skin, onDismiss, title, scroll, content)
            }
        }
    }
}

@Composable
private fun NotesContent(blocks: List<NotesBlock>, skin: SettingsSkin, subtitle: String?) {
    val body = skin.help.copy(color = Color.White.copy(alpha = 0.87f), fontSize = 15.sp, lineHeight = 22.sp, fontStyle = FontStyle.Normal)
    val headingColor = if (skin.inline) skin.optionSelectedText else skin.accent
    val heading = skin.label.copy(color = headingColor)
    val links = TextLinkStyles(SpanStyle(color = headingColor, textDecoration = TextDecoration.Underline))
    val card = skin.row.takeIf { it.alpha > 0f }
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (card != null) Modifier.clip(skin.shape).background(card).padding(16.dp) else Modifier)
            .padding(horizontal = if (skin.inline) 24.dp else 0.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        subtitle?.let { Text(it, style = skin.help) }
        blocks.forEachIndexed { index, block ->
            when (block) {
                is NotesBlock.Heading -> Text(
                    block.text.annotated(skin.uppercase, links),
                    style = heading,
                    modifier = if (index > 0) Modifier.padding(top = 8.dp) else Modifier,
                )
                is NotesBlock.Paragraph -> Text(block.text.annotated(false, links), style = body)
                is NotesBlock.Bullet -> Row {
                    Text(skin.marker?.takeIf { it.isNotBlank() } ?: "•", style = body.copy(color = headingColor))
                    Spacer(Modifier.width(10.dp))
                    Text(block.text.annotated(false, links), style = body, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private fun List<Inline>.annotated(uppercase: Boolean, links: TextLinkStyles): AnnotatedString = buildAnnotatedString {
    for (run in this@annotated) {
        val text = if (uppercase) run.text.uppercase() else run.text
        val url = run.url
        when {
            url != null -> withLink(LinkAnnotation.Url(url, links)) { append(text) }
            run.bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color.White)) { append(text) }
            run.code -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(text) }
            else -> append(text)
        }
    }
}
