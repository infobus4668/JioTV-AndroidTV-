package com.fenyx.jtv.ui.main

import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.key.*
import androidx.compose.ui.Modifier

/**
 * TV fast-navigation for long focus-driven lists (Home grid, EPG rows, player channel sidebar).
 *
 * Plain D-pad steps stay NATIVE (single item per press). Two accelerations are layered on top:
 *  - HOLD: once key auto-repeat kicks in hard (repeatCount ≥ 6), every other repeat event hops
 *    [hops] rows instead of one — a natural "press-and-hold to crawl fast" feel.
 *  - PAGE: CH+/CH− and PageUp/PageDown jump [pageRows] rows at once, like TiviMate.
 *
 * Preview-phase + non-consumed fallback means normal navigation is untouched; only the
 * accelerated cases are intercepted. Pure key handling — unit-tested indirectly via usage.
 */
fun Modifier.tvFastNavKeys(
    focusManager: FocusManager,
    pageRows: Int = 8,
    holdHops: Int = 3
): Modifier = onPreviewKeyEvent { e ->
    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
    val rc = e.nativeKeyEvent.repeatCount
    when (e.key) {
        Key.DirectionDown -> {
            if (rc >= 6 && rc % 2 == 0) {
                repeat(holdHops) { focusManager.moveFocus(FocusDirection.Down) }
                true
            } else false // native single step
        }
        Key.DirectionUp -> {
            if (rc >= 6 && rc % 2 == 0) {
                repeat(holdHops) { focusManager.moveFocus(FocusDirection.Up) }
                true
            } else false
        }
        // CH− / PageDown → page down. (Some remotes emit ChannelDown for CH−.)
        Key.ChannelDown, Key.PageDown -> {
            repeat(pageRows.coerceAtLeast(1)) { focusManager.moveFocus(FocusDirection.Down) }
            true
        }
        // CH+ / PageUp → page up.
        Key.ChannelUp, Key.PageUp -> {
            repeat(pageRows.coerceAtLeast(1)) { focusManager.moveFocus(FocusDirection.Up) }
            true
        }
        else -> false
    }
}
