package com.fenyx.jtv.theme

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * TV spacing + overscan tokens.
 *
 * Android TV design guidelines call for an overscan-safe margin so nothing critical sits at the very
 * edge of the panel (older/large TVs crop the edges). On 1080p that is ~48dp horizontal / 27dp
 * vertical. Apply [Modifier.tvOverscan] at the root of each full-screen surface.
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

/** Overscan-safe padding for a full-screen TV surface. */
fun Modifier.tvOverscan(): Modifier =
    this.padding(horizontal = TvDimens.OverscanHorizontal, vertical = TvDimens.OverscanVertical)
