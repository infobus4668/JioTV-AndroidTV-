package com.fenyx.jtv.theme

import android.util.Log
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import androidx.tv.material3.ClickableSurfaceBorder
import androidx.tv.material3.ClickableSurfaceColors
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ClickableSurfaceGlow
import androidx.tv.material3.ClickableSurfaceScale
import androidx.tv.material3.ClickableSurfaceShape
import androidx.compose.foundation.LocalIndication

private const val INPUT_TAG = "JTV-INPUT"

/**
 * Hover-to-focus that ALSO focuses on press/move, not just enter. Some emulators (BlueStacks,
 * LDPlayer…) never deliver PointerEventType.Enter for the cursor, and deliver the click as a
 * bare press — focusing on the Initial pass means the node is focused BEFORE the press reaches
 * [Surface]'s own tap detector, so the focus border and the press visuals engage immediately.
 * (The click itself is fired by [Surface]'s pointerClicks detector — tv-material's Surface is
 * key-events-only and never handles pointer input.)
 */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.mouseFocusRobust(focusRequester: FocusRequester): Modifier = this
    .pointerInput(focusRequester) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val type = event.type
                if (type == PointerEventType.Enter || type == PointerEventType.Press ||
                    type == PointerEventType.Move
                ) {
                    Log.d(INPUT_TAG, "Surface hover/press -> focus ($type)")
                    // requestFocus() throws if the node isn't attached yet (a pointer event can
                    // arrive in the frame before layout on some emulators) — never let a mouse
                    // hover crash the app it is meant to make usable.
                    runCatching { focusRequester.requestFocus() }
                }
            }
        }
    }

@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.mouseHoverToFocus(focusRequester: FocusRequester): Modifier = this
    .focusRequester(focusRequester)
    .mouseFocusRobust(focusRequester)

/**
 * Hover-to-focus for nodes that ALREADY attach their own [FocusRequester] (attaching a second
 * `focusRequester` modifier to the same node overwrites the first). Mouse-only: no-op on touch
 * where hover doesn't exist.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.mouseHoverRequests(focusRequester: FocusRequester): Modifier =
    this.mouseFocusRobust(focusRequester)

@Composable
fun Surface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    shape: ClickableSurfaceShape = ClickableSurfaceDefaults.shape(),
    colors: ClickableSurfaceColors = ClickableSurfaceDefaults.colors(),
    scale: ClickableSurfaceScale = ClickableSurfaceDefaults.scale(),
    border: ClickableSurfaceBorder = ClickableSurfaceDefaults.border(),
    glow: ClickableSurfaceGlow = ClickableSurfaceDefaults.glow(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable BoxScope.() -> Unit
) {
    val isTouch = LocalIsTouch.current
    // Mouse-capable devices (TV boxes with an air-mouse, PC emulators) get hover/press focus
    // even in touch UX — on those devices the mouse is a first-class pointer, and clicks must
    // focus their target node to be reliable.
    val hasMouse = LocalHasMouse.current
    val focusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()
    // tv-material's clickable Surface fires onClick ONLY from D-pad/Enter KEY events — its
    // tvClickable is handleDPadEnter + focusable + semantics, with NO pointer handling at all
    // (verified against the tv-material 1.0.0 and 1.1.0 sources). A mouse tap (TV emulator,
    // PC emulators) or a finger tap therefore only FOCUSES the node: the purple focus border
    // lights up and the click silently dies. Run our own tap/long-press detector so pointer
    // clicks actually fire. No double-fire risk: tv-material has no pointer path to race with,
    // and D-pad OK / long-OK still arrive via handleDPadEnter (keys only).
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    // Disabled surfaces fire no pointer taps: the tap detector is our own (tv-material has none),
    // so without this gate a disabled surface would still respond to mouse/touch clicks.
    val pointerClicks = if ((isTouch || hasMouse) && enabled) {
        Modifier.pointerInput(Unit) {
            detectTapGestures(
                // Press/release interactions drive the pressed colors/scale/border on tv-material.
                onPress = { offset ->
                    val press = PressInteraction.Press(offset)
                    coroutineScope.launch { interactionSource.emit(press) }
                    val released = tryAwaitRelease()
                    coroutineScope.launch {
                        interactionSource.emit(
                            if (released) PressInteraction.Release(press)
                            else PressInteraction.Cancel(press)
                        )
                    }
                },
                onTap = { currentOnClick() },
                // detectTapGestures suppresses the trailing tap after a long-press itself.
                onLongPress = { currentOnLongClick?.invoke() }
            )
        }
    } else {
        Modifier
    }
    androidx.tv.material3.Surface(
        onClick = onClick,
        modifier = modifier
            .then(if (!isTouch || hasMouse) Modifier.mouseHoverToFocus(focusRequester) else Modifier)
            .then(pointerClicks)
            .indication(interactionSource, LocalIndication.current),
        enabled = enabled,
        onLongClick = onLongClick,
        shape = shape,
        colors = colors,
        scale = scale,
        border = border,
        glow = glow,
        interactionSource = interactionSource,
        content = content
    )
}
