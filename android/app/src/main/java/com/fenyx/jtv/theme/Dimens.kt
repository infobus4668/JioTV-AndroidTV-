package com.fenyx.jtv.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * TV spacing + overscan tokens.
 *
 * Android TV design guidelines call for an overscan-safe margin so nothing critical sits at the very
 * edge of the panel (older/large TVs crop the edges). On 1080p that is ~48dp horizontal / 27dp
 * vertical. Apply [Modifier.tvOverscan] at the root of each full-screen surface.
 *
 * The overscan margins are TV-only: phones and tablets don't crop edges, so the same screens use
 * compact margins there via the adaptive accessors below ([overscanH]/[overscanV]/[tvOverscan]).
 * Hardcoding `TvDimens.Overscan*` in a screen is a bug — always go through the adaptive accessors.
 */
object TvDimens {
    val OverscanHorizontal = 48.dp
    val OverscanVertical = 27.dp

    // General spacing scale used across screens.
    val SpaceXs = 4.dp
    val SpaceSm = 8.dp
    val SpaceMd = 16.dp
    val SpaceLg = 24.dp
    val SpaceXl = 32.dp

    // A subtle focus-scale for cards/rows (border does most of the 10-foot focus cue; too much zoom
    // looks jumpy). Keep this gentle.
    const val FocusedScale = 1.03f
}

/** Current window width in dp — recomposes on rotation/split-screen/resize. */
@Composable
fun screenWidthDp(): Int = LocalConfiguration.current.screenWidthDp

/** True when the current window is narrower than the phone/tablet convention (600dp). */
@Composable
fun isCompactWidth(): Boolean = screenWidthDp() < TABLET_MIN_SMALLEST_WIDTH_DP

/** Overscan-safe horizontal margin for the CURRENT device (48dp TV / 24dp tablet / 16dp phone). */
@Composable
fun overscanH(): Dp = when (LocalDeviceForm.current) {
    DeviceForm.TV -> TvDimens.OverscanHorizontal
    DeviceForm.TABLET -> 24.dp
    DeviceForm.PHONE -> 16.dp
}

/** Overscan-safe vertical margin for the CURRENT device (27dp TV / 16dp tablet / 12dp phone). */
@Composable
fun overscanV(): Dp = when (LocalDeviceForm.current) {
    DeviceForm.TV -> TvDimens.OverscanVertical
    DeviceForm.TABLET -> 16.dp
    DeviceForm.PHONE -> 12.dp
}

@Composable
fun adaptiveScreenPadding(): PaddingValues = PaddingValues(horizontal = overscanH(), vertical = overscanV())

/** Overscan-safe padding for a full-screen surface, resolving per device form. */
@Composable
fun Modifier.tvOverscan(): Modifier =
    this.padding(horizontal = overscanH(), vertical = overscanV())

/**
 * Clamp a preferred fixed size to a fraction of the current window width. Used by overlays that
 * were designed at TV widths (e.g. a 348dp sidebar) so they can never exceed the screen on phones:
 * on wide screens the fixed size wins; on narrow ones it shrinks to [fraction] of the window.
 */
@Composable
fun Dp.coerceMaxWindowFraction(fraction: Float): Dp {
    val w = screenWidthDp()
    if (w <= 0) return this
    val limit = (w * fraction).dp
    return if (this > limit) limit else this
}
