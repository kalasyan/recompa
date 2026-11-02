package io.github.kalasyan.recompa.ui.theme

import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kalasyan.recompa.R
import io.github.kalasyan.recompa.data.AppStyle

/**
 * The colours of one style. Every style is dark: a launcher looks the same in every system
 * theme, so cover art stands out.
 */
data class StylePalette(
    /** Focus glow, primary actions, "update" badges. */
    val accent: Color,
    /** Text on top of [accent]. */
    val onAccent: Color,
    /** Screen colour behind the art; scrims fade into it. */
    val night: Color,
    /** "Installed" badges and other good news. */
    val secondary: Color,
    /** Body text. */
    val text: Color,
    /** Secondary text. */
    val muted: Color,
    /** Corner radius of covers, panels and tiles. */
    val corner: Dp,
)

private val Palettes = mapOf(
    AppStyle.CLASSIC to StylePalette(
        accent = Color(0xFFFF7A3D), onAccent = Color(0xFF1A0A00), night = Color(0xFF0A0D14),
        secondary = Color(0xFF7FD1B9), text = Color(0xFFE9ECF3), muted = Color(0xFFA7AFC0), corner = 12.dp,
    ),
    AppStyle.SHELVES to StylePalette(
        accent = Color(0xFF57E3A0), onAccent = Color(0xFF00210F), night = Color(0xFF05070C),
        secondary = Color(0xFF57E3A0), text = Color(0xFFF2F4F8), muted = Color(0xFF9AA3B2), corner = 8.dp,
    ),
    AppStyle.LIST to StylePalette(
        accent = Color(0xFF4FC3F7), onAccent = Color(0xFF04202C), night = Color(0xFF101418),
        secondary = Color(0xFF57E3A0), text = Color(0xFFE3E8ED), muted = Color(0xFF8995A2), corner = 4.dp,
    ),
    AppStyle.ARCADE to StylePalette(
        accent = Color(0xFF00FFF0), onAccent = Color(0xFF07010F), night = Color(0xFF07010F),
        secondary = Color(0xFFFFE14D), text = Color(0xFFF4ECFF), muted = Color(0xFFB9A6E0), corner = 0.dp,
    ),
    AppStyle.MOSAIC to StylePalette(
        accent = Color(0xFF6C78FF), onAccent = Color(0xFFFFFFFF), night = Color(0xFF1D1F26),
        secondary = Color(0xFF3DDC97), text = Color(0xFFF1F2F6), muted = Color(0xFF9EA3B3), corner = 0.dp,
    ),
    AppStyle.SHOWCASE to StylePalette(
        accent = Color(0xFFE8D5B0), onAccent = Color(0xFF1C160C), night = Color(0xFF0C0D11),
        secondary = Color(0xFF9FD8B5), text = Color(0xFFF3F1EC), muted = Color(0xFFAAABB5), corner = 6.dp,
    ),
)

fun paletteOf(style: AppStyle): StylePalette = Palettes.getValue(style)

/** The style in use; screens read it to pick their layout. */
val LocalAppStyle = staticCompositionLocalOf { AppStyle.CLASSIC }
val LocalPalette = staticCompositionLocalOf { paletteOf(AppStyle.CLASSIC) }

/** Accent used for focus rings and primary actions. */
val Accent: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.accent

/** Colour of the screen behind the art; scrims fade into it. */
val Night: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.night

val PressStart = FontFamily(Font(R.font.press_start_2p))
val Rajdhani = FontFamily(Font(R.font.rajdhani_medium, FontWeight.Medium), Font(R.font.rajdhani_bold, FontWeight.Bold))

@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, vararg weights: FontWeight) = FontFamily(
    weights.map { Font(res, it, variationSettings = FontVariation.Settings(FontVariation.weight(it.weight))) },
)

val SpaceGrotesk = variable(R.font.space_grotesk, FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)
val Playfair = variable(R.font.playfair_display, FontWeight.Normal, FontWeight.Bold, FontWeight.Black)

private val Base = Typography()
private val ClassicType = Base.copy(
    displaySmall = Base.displaySmall.copy(fontWeight = FontWeight.Black),
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = Base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

private fun Typography.withHeadings(family: FontFamily, size: (TextStyle) -> TextStyle = { it }) = copy(
    displayLarge = size(displayLarge.copy(fontFamily = family)),
    displayMedium = size(displayMedium.copy(fontFamily = family)),
    displaySmall = size(displaySmall.copy(fontFamily = family)),
    headlineLarge = size(headlineLarge.copy(fontFamily = family)),
    headlineMedium = size(headlineMedium.copy(fontFamily = family)),
    headlineSmall = size(headlineSmall.copy(fontFamily = family)),
    titleLarge = size(titleLarge.copy(fontFamily = family)),
)

private fun Typography.withAll(family: FontFamily) = Typography(
    displayLarge = displayLarge.copy(fontFamily = family), displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family), headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family), headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family), titleMedium = titleMedium.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family), bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family), bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family), labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family),
)

// Pixel type is wide: headings and labels shrink so they fit where the others do.
private fun pixel(style: TextStyle, size: Int) = style.copy(fontFamily = PressStart, fontSize = size.sp, lineHeight = (size * 1.5).sp, letterSpacing = 0.sp)

private fun typographyOf(style: AppStyle): Typography = when (style) {
    AppStyle.CLASSIC, AppStyle.SHELVES -> ClassicType
    AppStyle.LIST -> ClassicType.copy(
        titleLarge = ClassicType.titleLarge.copy(fontFamily = Rajdhani, fontWeight = FontWeight.Bold),
        titleMedium = ClassicType.titleMedium.copy(fontFamily = Rajdhani, fontWeight = FontWeight.Bold, fontSize = 18.sp),
        titleSmall = ClassicType.titleSmall.copy(fontFamily = Rajdhani, fontWeight = FontWeight.Bold, fontSize = 16.sp),
        labelLarge = ClassicType.labelLarge.copy(fontFamily = Rajdhani, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 0.6.sp),
    ).withHeadings(Rajdhani) { it.copy(fontWeight = FontWeight.Bold) }
    AppStyle.ARCADE -> ClassicType.copy(
        displaySmall = pixel(ClassicType.displaySmall, 24),
        headlineMedium = pixel(ClassicType.headlineMedium, 18),
        headlineSmall = pixel(ClassicType.headlineSmall, 15),
        titleLarge = pixel(ClassicType.titleLarge, 13),
        titleMedium = pixel(ClassicType.titleMedium, 11),
        titleSmall = pixel(ClassicType.titleSmall, 10),
        labelLarge = pixel(ClassicType.labelLarge, 10),
    )
    AppStyle.MOSAIC -> ClassicType.withAll(SpaceGrotesk)
    AppStyle.SHOWCASE -> ClassicType.withHeadings(Playfair) { it.copy(fontWeight = FontWeight.Bold) }
}

// Stronger focus and press overlays than Material's defaults, for gamepad navigation.
@OptIn(ExperimentalMaterial3Api::class)
private val Ripple = RippleConfiguration(
    color = Color.White,
    rippleAlpha = RippleAlpha(draggedAlpha = 0.16f, focusedAlpha = 0.35f, hoveredAlpha = 0.08f, pressedAlpha = 0.3f),
)

private fun colorsOf(p: StylePalette) = darkColorScheme(
    primary = p.accent,
    onPrimary = p.onAccent,
    primaryContainer = lerp(p.night, p.accent, 0.35f),
    onPrimaryContainer = lerp(Color.White, p.accent, 0.2f),
    secondary = p.secondary,
    onSecondary = p.night,
    secondaryContainer = lerp(p.night, p.secondary, 0.2f),
    onSecondaryContainer = lerp(Color.White, p.secondary, 0.3f),
    background = p.night,
    onBackground = p.text,
    surface = p.night,
    onSurface = p.text,
    surfaceVariant = lerp(p.night, Color.White, 0.1f),
    onSurfaceVariant = p.muted,
    surfaceContainerLowest = lerp(p.night, Color.Black, 0.3f),
    surfaceContainerLow = lerp(p.night, Color.White, 0.04f),
    surfaceContainer = lerp(p.night, Color.White, 0.06f),
    surfaceContainerHigh = lerp(p.night, Color.White, 0.1f),
    surfaceContainerHighest = lerp(p.night, Color.White, 0.14f),
    outline = lerp(p.night, Color.White, 0.22f),
    outlineVariant = lerp(p.night, Color.White, 0.15f),
    error = Color(0xFFFF8A80),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecompaTheme(style: AppStyle = AppStyle.CLASSIC, content: @Composable () -> Unit) {
    val palette = paletteOf(style)
    MaterialTheme(colorScheme = colorsOf(palette), typography = typographyOf(style)) {
        CompositionLocalProvider(
            LocalRippleConfiguration provides Ripple,
            LocalAppStyle provides style,
            LocalPalette provides palette,
            content = content,
        )
    }
}
