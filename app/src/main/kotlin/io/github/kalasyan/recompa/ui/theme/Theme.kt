package io.github.kalasyan.recompa.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

/** Accent used for focus rings and primary actions. */
val Accent = Color(0xFFFF7A3D)

/** Colour of the screen behind the art; scrims fade into it. */
val Night = Color(0xFF0A0D14)

// A launcher looks the same in every system theme: always dark, so cover art stands out.
private val Colors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF1A0A00),
    primaryContainer = Color(0xFF5A2A10),
    onPrimaryContainer = Color(0xFFFFDBCB),
    secondary = Color(0xFF7FD1B9),
    onSecondary = Color(0xFF00382C),
    secondaryContainer = Color(0xFF1B3A33),
    onSecondaryContainer = Color(0xFFB4F0DC),
    background = Night,
    onBackground = Color(0xFFE9ECF3),
    surface = Night,
    onSurface = Color(0xFFE9ECF3),
    surfaceVariant = Color(0xFF1C2230),
    onSurfaceVariant = Color(0xFFA7AFC0),
    surfaceContainerLowest = Color(0xFF07090E),
    surfaceContainerLow = Color(0xFF111520),
    surfaceContainer = Color(0xFF151A26),
    surfaceContainerHigh = Color(0xFF1C2230),
    surfaceContainerHighest = Color(0xFF242B3B),
    outline = Color(0xFF3A4255),
    outlineVariant = Color(0xFF2A3142),
    error = Color(0xFFFF8A80),
)

private val Base = Typography()
private val Type = Base.copy(
    displaySmall = Base.displaySmall.copy(fontWeight = FontWeight.Black),
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = Base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

@Composable
fun RecompaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = Type, content = content)
}
