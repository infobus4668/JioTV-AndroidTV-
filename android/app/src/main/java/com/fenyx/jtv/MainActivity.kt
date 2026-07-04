package com.fenyx.jtv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import com.fenyx.jtv.theme.JioTVGoTVTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge + hidden system bars so the app's navy background fills the ENTIRE screen (incl.
        // any area the keyboard leaves) instead of the OS painting black at the edges. Removing this
        // made the black area at the bottom larger, so it's kept on.
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // No runtime storage-permission request: the app uses only app-scoped storage, so the prompt
        // was unnecessary and awkward to dismiss with a TV remote.
        setContent {
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
