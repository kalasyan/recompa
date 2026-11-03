package io.github.kalasyan.recompa.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration

/** Whether the app's window is taller than wide, measured; null outside [ProvidePortrait]. */
private val LocalPortrait = compositionLocalOf<Boolean?> { null }

/**
 * Whether the screen is taller than it is wide. The styles were drawn for landscape handhelds;
 * in portrait they stack what they put side by side, so nothing gets squeezed into a sliver.
 */
@Composable
internal fun isPortrait(): Boolean {
    LocalPortrait.current?.let { return it }
    val configuration = LocalConfiguration.current
    return configuration.screenWidthDp < configuration.screenHeightDp
}

/**
 * Measures the window and tells [isPortrait] what it is. The activity handles rotation itself
 * and its context is wrapped for the app language, so the configuration Compose sees could keep
 * the old orientation after turning the device: the layout stayed in landscape until a restart.
 */
@Composable
internal fun ProvidePortrait(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalPortrait provides (maxWidth < maxHeight), content = content)
    }
}
