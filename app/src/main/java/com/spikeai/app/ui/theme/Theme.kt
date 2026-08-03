package com.spikeai.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Extra Spike-specific colors not covered by Material's [androidx.compose.material3.ColorScheme]. */
data class SpikeColors(
    val isDark: Boolean,
    val bgTop: Color,
    val bgBottom: Color,
    val card: Color,
    val cardBorder: Color,
    val textPrimary: Color,
    val textMuted: Color,
    val accent: Color,
    val orange: Color,
    val green: Color,
    val blue: Color,
    val red: Color,
    val purple: Color,
    val teal: Color,
    val cyan: Color,
    /**
     * High-priority marker (gold) for *non-text* use — icons, borders, fills. Separate
     * from [red], which means attention/destructive.
     *
     * Non-text UI only has to clear WCAG 1.4.11's 3:1, so this is the full-strength gold.
     * Small text tinted gold must use [priorityHighText] instead.
     */
    val priorityHigh: Color,
    /**
     * High-priority gold for *small text*, which WCAG 1.4.3 holds to 4.5:1 — a stricter
     * floor than [priorityHigh] has to meet. In the dark theme one gold clears both, so
     * these are the same colour; on the light theme's white cards this is a deeper tone.
     */
    val priorityHighText: Color,
    /**
     * Label colour for text drawn *on* a [priorityHigh] fill. Both themes' golds are
     * light enough to need dark ink rather than white.
     */
    val onPriorityHigh: Color,
) {
    /** Full-screen vertical background gradient (top → bottom). */
    val backgroundBrush: Brush get() = Brush.verticalGradient(listOf(bgTop, bgBottom))
}

private val DarkSpikeColors = SpikeColors(
    isDark = true,
    bgTop = DarkBgTop,
    bgBottom = DarkBgBottom,
    card = DarkCard,
    cardBorder = DarkCardBorder,
    textPrimary = DarkOnBg,
    textMuted = DarkOnBgMuted,
    accent = SpikeAccent,
    orange = SpikeOrange,
    green = SpikeGreen,
    blue = SpikeBlue,
    red = SpikeRed,
    purple = SpikePurple,
    teal = SpikeTeal,
    cyan = SpikeCyan,
    priorityHigh = SpikeGold,
    priorityHighText = SpikeGold,
    onPriorityHigh = SpikeGoldInk,
)

private val LightSpikeColors = SpikeColors(
    isDark = false,
    bgTop = LightBgTop,
    bgBottom = LightBgBottom,
    card = LightCard,
    cardBorder = LightCardBorder,
    textPrimary = LightOnBg,
    textMuted = LightOnBgMuted,
    accent = SpikeAccent,
    orange = SpikeOrange,
    green = SpikeGreen,
    blue = SpikeBlue,
    red = SpikeRedDeep,
    purple = SpikePurple,
    teal = SpikeTeal,
    cyan = SpikeCyan,
    priorityHigh = SpikeGoldRich,
    priorityHighText = SpikeGoldDeep,
    onPriorityHigh = SpikeGoldInk,
)

val LocalSpikeColors = staticCompositionLocalOf { DarkSpikeColors }

private val DarkColorScheme = darkColorScheme(
    primary = SpikeAccent,
    onPrimary = Color.White,
    secondary = SpikeGreen,
    tertiary = QuoteViolet,
    background = DarkBgBottom,
    onBackground = DarkOnBg,
    surface = DarkCard,
    onSurface = DarkOnBg,
    surfaceVariant = DarkCardBorder,
    onSurfaceVariant = DarkOnBgMuted,
    outline = DarkCardBorder,
    error = SpikeRed,
)

private val LightColorScheme = lightColorScheme(
    primary = SpikeAccent,
    onPrimary = Color.White,
    secondary = SpikeGreen,
    tertiary = QuoteVioletDeep,
    background = LightBgBottom,
    onBackground = LightOnBg,
    surface = LightCard,
    onSurface = LightOnBg,
    surfaceVariant = LightCardBorder,
    onSurfaceVariant = LightOnBgMuted,
    outline = LightCardBorder,
    error = SpikeRedDeep,
)

/** App theme. [themePreference]: "system" | "light" | "dark". */
@Composable
fun SpikeAITheme(
    themePreference: String = "system",
    content: @Composable () -> Unit
) {
    val darkTheme = when (themePreference) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val spikeColors = if (darkTheme) DarkSpikeColors else LightSpikeColors
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(LocalSpikeColors provides spikeColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SpikeTypography,
            content = content
        )
    }
}

/** Convenience accessor: `SpikeTheme.colors`. */
object SpikeTheme {
    val colors: SpikeColors
        @Composable get() = LocalSpikeColors.current
}
