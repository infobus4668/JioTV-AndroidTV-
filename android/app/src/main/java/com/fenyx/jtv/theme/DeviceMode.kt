package com.fenyx.jtv.theme

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * True on finger-touch devices (phones/tablets); false on TV boxes and remote-only hardware.
 * Touch-specific UX (tap gestures, on-screen control clusters, direct text entry) keys off this;
 * TV UX (D-pad focus, hover-to-focus for mice) keys off its negation. Defaults to false so any
 * composition outside [com.fenyx.jtv.MainActivity] behaves as TV-first.
 */
val LocalIsTouch = staticCompositionLocalOf { false }

/**
 * Coarse form factor driving LAYOUT decisions (spacing, panel widths, stacking). Distinct from
 * [LocalIsTouch], which only drives INPUT behaviour: a tablet is touch-input but large enough to
 * keep the roomy TV layout, while a phone gets the compact one. Defaults to TV so any composition
 * outside MainActivity behaves TV-first.
 */
enum class DeviceForm { TV, TABLET, PHONE }

val LocalDeviceForm = staticCompositionLocalOf { DeviceForm.TV }

/** 600dp is the Android convention for the phone/tablet split (smallest-width). */
const val TABLET_MIN_SMALLEST_WIDTH_DP = 600

fun Context.deviceForm(): DeviceForm = runCatching {
    val uiModeType = resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
    if (uiModeType == Configuration.UI_MODE_TYPE_TELEVISION) {
        DeviceForm.TV
    } else if (resources.configuration.smallestScreenWidthDp >= TABLET_MIN_SMALLEST_WIDTH_DP) {
        DeviceForm.TABLET
    } else {
        DeviceForm.PHONE
    }
}.getOrDefault(DeviceForm.TV)

fun Context.isTouchDevice(): Boolean = runCatching {
    val uiModeType = resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
    val isTv = uiModeType == Configuration.UI_MODE_TYPE_TELEVISION
    !isTv && packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
}.getOrDefault(false)
