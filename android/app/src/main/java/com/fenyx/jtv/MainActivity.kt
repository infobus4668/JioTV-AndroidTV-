package com.fenyx.jtv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import com.fenyx.jtv.theme.DeviceForm
import com.fenyx.jtv.theme.JioTVGoTVTheme
import com.fenyx.jtv.theme.LocalDeviceForm
import com.fenyx.jtv.theme.LocalIsTouch
import com.fenyx.jtv.theme.deviceForm
import com.fenyx.jtv.theme.isTouchDevice

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge + hidden system bars so the app's navy background fills the ENTIRE screen (incl.
        // any area the keyboard leaves) instead of the OS painting black at the edges. Removing this
        // made the black area at the bottom larger, so it's kept on.
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()

        // No runtime storage-permission request: the app uses only app-scoped storage, so the prompt
        // was unnecessary and awkward to dismiss with a TV remote.
        val isTouch = isTouchDevice()
        val form = deviceForm()
        setContent {
            CompositionLocalProvider(
                LocalIsTouch provides isTouch,
                LocalDeviceForm provides form
            ) {
                JioTVGoTVTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.background)
                    ) {
                        MainNavigation()
                    }
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Some OEMs (MIUI/HyperOS in particular) ignore the onCreate hide() — the window isn't
        // attached yet — and re-show the status bar over the player, clipping the channel banner
        // (seen in on-device captures: half-cut channel name under the clock). Re-asserting the
        // hidden state on every focus gain keeps the player truly immersive.
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
