package com.fenyx.jtv.theme

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.hardware.input.InputManager
import android.os.Build
import android.view.InputDevice
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

/**
 * True when a mouse-style pointer is available (real TV boxes with a USB/air mouse, and the
 * Android TV emulator whose only pointer is the host mouse). Mouse-capable non-touch devices get
 * the on-screen player dock (hover-to-focus + click) and video click gestures on top of the
 * pure D-pad UX. Defaults to false so any composition outside [com.fenyx.jtv.MainActivity]
 * behaves remote-first.
 */
val LocalHasMouse = staticCompositionLocalOf { false }

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

/**
 * A single connected pointer-class input device, with the source capabilities the app cares about.
 * Enumerating real devices is the most reliable signal: PC-class emulators inject the host mouse as
 * a plain touch screen, and some report a relative mouse (`SOURCE_MOUSE_RELATIVE`, API 26) rather
 * than an absolute one — feature flags alone miss both.
 */
private data class PointerDevice(
    val name: String,
    val touchscreen: Boolean,
    val mouse: Boolean,
    val touchpad: Boolean,
    val stylus: Boolean,
    val trackball: Boolean
) {
    val anyMouseLike: Boolean get() = mouse || touchpad || stylus || trackball
}

private fun InputDevice.hasSource(source: Int): Boolean = (sources and source) == source

/** All connected pointer-class input devices (touchscreens, mice, touchpads, styluses, trackballs). */
private fun Context.pointerDevices(): List<PointerDevice> = runCatching {
    val im = getSystemService(Context.INPUT_SERVICE) as? InputManager
    im?.inputDeviceIds?.toList()
        ?.mapNotNull { id -> im.getInputDevice(id) }
        ?.filter { (it.sources and InputDevice.SOURCE_CLASS_POINTER) != 0 }
        ?.map { d ->
            PointerDevice(
                name = d.name ?: "",
                touchscreen = d.hasSource(InputDevice.SOURCE_TOUCHSCREEN),
                // SOURCE_MOUSE_RELATIVE is API 26; the constant is inlined, safe on minSdk 24.
                mouse = d.hasSource(InputDevice.SOURCE_MOUSE) ||
                    d.hasSource(InputDevice.SOURCE_MOUSE_RELATIVE),
                touchpad = d.hasSource(InputDevice.SOURCE_TOUCHPAD),
                stylus = d.hasSource(InputDevice.SOURCE_STYLUS),
                trackball = d.hasSource(InputDevice.SOURCE_TRACKBALL)
            )
        }
        ?: emptyList()
}.getOrDefault(emptyList())

/** True when a real touch panel is present, per InputManager (covers emulators/ChromeOS that only advertise `faketouch`). */
private fun Context.hasTouchInputDevice(): Boolean = pointerDevices().any { it.touchscreen }

fun Context.isTouchDevice(): Boolean = runCatching {
    val uiModeType = resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
    val isTv = uiModeType == Configuration.UI_MODE_TYPE_TELEVISION
    // Android TV stays TV-first even if a touch overlay/panel happens to be attached: the D-pad
    // is the primary input and loading phone touch UX there hurts more than it helps.
    if (isTv) return@runCatching false
    // `faketouch` is the public feature string for emulated/limited touch (no public constant).
    val feature = packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN) ||
        packageManager.hasSystemFeature("android.hardware.faketouch")
    feature || hasTouchInputDevice()
}.getOrDefault(false)

/**
 * Mouse-style pointer present? A real mouse/touchpad reports through InputManager. PC-class
 * emulators (BlueStacks, LDPlayer, MEmu, Nox...) frequently inject the pointer as a plain touch
 * screen AND spoof the device fingerprint, so a detected emulator whose only pointer is the host
 * mouse counts too — missing it leaves the app unusable there. Real phones/tablets report neither,
 * keeping them pure touch.
 */
fun Context.hasMouseDevice(): Boolean = runCatching {
    val fromDevices = pointerDevices().any { it.anyMouseLike }
    fromDevices || isEmulator()
}.getOrDefault(false)

/**
 * Running under a PC-class Android emulator (phone + TV images). Detection is intentionally strict:
 * bare substrings like "generic"/"emulator" appear on legitimate cheap TV boxes, and a false
 * positive there wrongly flips a remote-only TV into the on-screen-dock UX.
 */
fun Context.isEmulator(): Boolean = runCatching {
    val product = Build.PRODUCT.orEmpty()
    val fingerprint = Build.FINGERPRINT.orEmpty()
    val manufacturer = Build.MANUFACTURER.orEmpty()
    val hardware = Build.HARDWARE.orEmpty()
    val model = Build.MODEL.orEmpty()
    val brand = Build.BRAND.orEmpty()
    val haystack = "$product $fingerprint $manufacturer $hardware $model $brand".lowercase()
    // Whole-token view; used for short markers that would substring-match real brands
    // (e.g. "Innox" contains "nox", "Candy" contains "andy").
    val tokens = haystack.split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }

    // Unambiguous emulator brands/products — safe as substrings.
    val named = listOf(
        "bluestacks", "bignox", "ldplayer", "ld_box", "memu",
        "waydroid", "droid4x", "genymotion", "droidx", "leidian"
    )
    if (named.any { haystack.contains(it) }) return@runCatching true
    // Short/risky markers matched against whole tokens only.
    if (tokens.any { it == "nox" || it == "andy" || it == "vbox" }) return@runCatching true

    // AOSP/QEMU images: the virtual GPU/board name is specific and rarely spoofed.
    val qemu = hardware.contains("goldfish") || hardware.contains("ranchu")
    // SDK emulator images use an `sdk_*`/emulator product or model.
    val sdkImage = product.startsWith("sdk_") ||
        product.contains("emulator") ||
        model.contains("sdk_")
    qemu || sdkImage
}.getOrDefault(false)

/** Human-readable input-capability summary for the Settings diagnostics row. */
fun Context.inputDebugSummary(): String = runCatching {
    val pointers = pointerDevices()
    val touchscreen = pointers.any { it.touchscreen }
    val mouse = pointers.any { it.mouse }
    val touchpad = pointers.any { it.touchpad }
    val stylus = pointers.any { it.stylus }
    val names = pointers.take(3).joinToString(" | ") { it.name }
    buildString {
        append("touchscreen=$touchscreen · mouse=$mouse · touchpad=$touchpad · stylus=$stylus · emu=${isEmulator()}")
        if (names.isNotEmpty()) append("  ·  $names")
    }
}.getOrDefault("unavailable")
