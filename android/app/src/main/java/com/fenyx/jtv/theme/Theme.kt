package com.fenyx.jtv.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme

private val TvDarkColorScheme = darkColorScheme(
    primary = TvPrimary,
    onPrimary = TvOnPrimary,
    primaryContainer = TvPrimaryContainer,
    onPrimaryContainer = TvOnPrimaryContainer,
    secondary = TvSecondary,
    onSecondary = TvOnSecondary,
    background = TvDarkBackground,
    onBackground = TvOnBackground,
    surface = TvDarkSurface,
    onSurface = TvOnSurface,
    surfaceVariant = TvDarkSurfaceVariant,
    onSurfaceVariant = TvOnSurfaceVariant,
    error = TvError,
)

/**
 * Shrink the 10-foot type scale toward phone proportions. Every style scales uniformly so relative
 * hierarchy is preserved; weights/families/letter-spacing carry over untouched.
 */
private fun Typography.scaled(factor: Float): Typography = Typography(
    displayLarge = scaled(displayLarge, factor),
    displayMedium = scaled(displayMedium, factor),
    displaySmall = scaled(displaySmall, factor),
    headlineLarge = scaled(headlineLarge, factor),
    headlineMedium = scaled(headlineMedium, factor),
    headlineSmall = scaled(headlineSmall, factor),
    titleLarge = scaled(titleLarge, factor),
    titleMedium = scaled(titleMedium, factor),
    titleSmall = scaled(titleSmall, factor),
    bodyLarge = scaled(bodyLarge, factor),
    bodyMedium = scaled(bodyMedium, factor),
    bodySmall = scaled(bodySmall, factor),
    labelLarge = scaled(labelLarge, factor),
    labelMedium = scaled(labelMedium, factor),
    labelSmall = scaled(labelSmall, factor),
)

private fun scaled(style: TextStyle, factor: Float): TextStyle =
    style.copy(fontSize = style.fontSize * factor, lineHeight = style.lineHeight * factor)

@Composable
fun JioTVGoTVTheme(
    content: @Composable () -> Unit,
) {
    // Android TV is always dark — force dark theme. Wire in the TV type scale (Type.kt) so all
    // screens get 10-foot-legible text; previously typography was left at the phone-sized defaults.
    // Phones/tablets are held at arm's length rather than viewed across a room, so the same scale
    // shrinks proportionally there.
    val typography = when (LocalDeviceForm.current) {
        DeviceForm.TV -> Typography
        else -> Typography.scaled(0.88f)
    }
    MaterialTheme(
        colorScheme = TvDarkColorScheme,
        typography = typography,
        content = content
    )
}
