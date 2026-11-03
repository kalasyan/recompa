package io.github.kalasyan.recompa.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/**
 * Whether the screen is taller than it is wide. The styles were drawn for landscape handhelds;
 * in portrait they stack what they put side by side, so nothing gets squeezed into a sliver.
 */
@Composable
internal fun isPortrait(): Boolean {
    val configuration = LocalConfiguration.current
    return configuration.screenWidthDp < configuration.screenHeightDp
}
