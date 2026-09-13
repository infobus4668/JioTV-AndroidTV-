package com.fenyx.jtv.theme

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Mouse-wheel support for HORIZONTAL scrollables. A plain (unshifted) mouse wheel maps to the
 * vertical axis in Compose, so mouse-primary devices (TV emulator, PC emulators, air-mouse boxes)
 * could not reach category chips / favorites / EPG timeline content beyond the first viewport —
 * vertical lists already work natively.
 *
 * Intercepted on the [PointerEventPass.Initial] pass so the wheel delta is routed to the
 * horizontal axis and the vertical ancestors are stopped from ALSO scrolling. Both wheel axes are
 * honored (vertical wheel + horizontal wheel/trackpad swipes); when the list is already at the
 * edge nothing is consumed, so wheeling past the end of a rail still scrolls the page like before.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.mouseWheelToHorizontal(state: LazyListState): Modifier =
    pointerInput(state) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type != PointerEventType.Scroll) continue
                val change = event.changes.firstOrNull() ?: continue
                val delta = change.scrollDelta.y + change.scrollDelta.x
                if (delta == 0f) continue
                val consumed = state.dispatchRawDelta(delta)
                if (consumed != 0f) change.consume()
            }
        }
    }

/** [ScrollState] variant (used by the EPG guide's shared timeline scroll). */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.mouseWheelToHorizontal(state: ScrollState): Modifier =
    pointerInput(state) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type != PointerEventType.Scroll) continue
                val change = event.changes.firstOrNull() ?: continue
                val delta = change.scrollDelta.y + change.scrollDelta.x
                if (delta == 0f) continue
                val consumed = state.dispatchRawDelta(delta)
                if (consumed != 0f) change.consume()
            }
        }
    }
