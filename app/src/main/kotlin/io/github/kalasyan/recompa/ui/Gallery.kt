package io.github.kalasyan.recompa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import io.github.kalasyan.recompa.R
import kotlinx.coroutines.launch

private val ShotShape = RoundedCornerShape(10.dp)

/**
 * A row of the game's screenshots. Each one is focusable with the gamepad and opens
 * [GalleryViewer] full screen.
 */
@Composable
fun ScreenshotRow(urls: List<String>, game: String, height: Dp) {
    var open by rememberSaveable { mutableStateOf<Int?>(null) }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        itemsIndexed(urls) { index, url ->
            AsyncImage(
                model = url,
                contentDescription = stringResource(R.string.gallery_image, game, index + 1, urls.size),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .height(height)
                    .aspectRatio(16f / 9f)
                    .focusRing(ShotShape)
                    .clip(ShotShape)
                    .background(Color.White.copy(alpha = 0.06f))
                    .clickable { open = index },
            )
        }
    }
    open?.let { start -> GalleryViewer(urls, game, start, onDismiss = { open = null }) }
}

/** Full-screen images, swiped or moved with the D-pad and L1/R1. B or Back closes it. */
@Composable
private fun GalleryViewer(urls: List<String>, game: String, start: Int, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val pager = rememberPagerState(initialPage = start) { urls.size }
        val scope = rememberCoroutineScope()
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { focus.requestFocus() }
        fun go(delta: Int) {
            val target = (pager.currentPage + delta).coerceIn(0, urls.lastIndex)
            scope.launch { pager.animateScrollToPage(target) }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .focusRequester(focus)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft, Key.ButtonL1 -> { go(-1); true }
                        Key.DirectionRight, Key.ButtonR1 -> { go(1); true }
                        Key.ButtonB, Key.Escape -> { onDismiss(); true }
                        else -> false
                    }
                }
                .focusable(),
        ) {
            HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                AsyncImage(
                    model = urls[page],
                    contentDescription = stringResource(R.string.gallery_image, game, page + 1, urls.size),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp)) {
                IconButton(
                    onClick = onDismiss,
                    colors = IconButtonDefaults.iconButtonColors(containerColor = Panel),
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.gallery_close), tint = Color.White)
                }
                if (urls.size > 1) {
                    Text(
                        "${pager.currentPage + 1} / ${urls.size}",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .background(Panel, CircleShape)
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}
