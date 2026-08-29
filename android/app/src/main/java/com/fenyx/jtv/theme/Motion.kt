package com.fenyx.jtv.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Shared motion tokens so every screen/panel/dialog animates with one consistent feel.
 *
 * TVs do poorly with long animations — keep everything short (~180ms). All durations honor the
 * system animator scale ("Remove animations" accessibility setting): a 0 scale collapses every
 * transition to an instant switch instead of leaving the UI feeling broken.
 */
object TvMotion {

    /** Standard panel/overlay transition duration at 1x. */
    const val BASE_MS = 180

    /** Slightly longer duration for large surfaces (sheets, dialogs). */
    const val SHEET_MS = 240

    /**
     * Effective duration for [base] milliseconds after applying the global animator scale.
     * Returns 0 when animations are disabled system-wide, which Compose renders instantly.
     */
    @Composable
    fun ms(base: Int = BASE_MS): Int {
        val context = LocalContext.current
        return remember(base) {
            val scale = runCatching {
                android.provider.Settings.Global.getFloat(
                    context.contentResolver,
                    android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f
                )
            }.getOrDefault(1f)
            (base * scale).toInt().coerceIn(0, 1000)
        }
    }
}
