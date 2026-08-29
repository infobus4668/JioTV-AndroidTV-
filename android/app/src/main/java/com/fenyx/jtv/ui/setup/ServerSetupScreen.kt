package com.fenyx.jtv.ui.setup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import com.fenyx.jtv.theme.Surface
import androidx.tv.material3.Text
import com.fenyx.jtv.data.ServerClient
import com.fenyx.jtv.data.SettingsManager
import com.fenyx.jtv.theme.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Server-mode setup: enter the JTV proxy server URL + access code, then "Connect" pulls the shared
 * credentials. On success we persist the server config + credentials; Navigation then flips to the app
 * automatically (authData becomes non-null).
 *
 * Text entry uses the system on-screen keyboard: each field is a plain focusable [TvField]; landing
 * focus on it opens the IME.
 */
@Composable
fun ServerSetupScreen(
    onBack: () -> Unit,
    jtvMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val scope = rememberCoroutineScope()

    var serverUrl by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var isConnecting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Prefill from any previously saved config (e.g. re-connecting from Settings). In JTV mode the URL
    // is hardcoded, so we only prefill the code.
    LaunchedEffect(Unit) {
        if (!jtvMode) serverUrl = settingsManager.serverUrlFlow.first()
        token = settingsManager.serverTokenFlow.first()
    }

    val urlFocus = remember { FocusRequester() }
    val tokenFocus = remember { FocusRequester() }
    val connectFocus = remember { FocusRequester() }
    // Focus the code field first in JTV mode (there's no URL field to focus). Landing focus on a field
    // opens the system keyboard for it.
    LaunchedEffect(Unit) { runCatching { (if (jtvMode) tokenFocus else urlFocus).requestFocus() } }

    fun connect() {
        if (!jtvMode && serverUrl.isBlank()) { error = "Enter the server URL."; return }
        if (token.isBlank()) { error = "Enter your access code."; return }
        isConnecting = true
        error = null
        scope.launch {
            val result = if (jtvMode)
                ServerClient.fetchCredentials(ServerClient.JTV_SERVER_URLS, token)
            else
                ServerClient.fetchCredentials(serverUrl, token)
            isConnecting = false
            result.onSuccess { authData ->
                if (jtvMode) {
                    // URL is hardcoded (tried LAN then internet) — store just the code.
                    settingsManager.setServerConfig("", token)
                    settingsManager.setSetupMode("jtv")
                } else {
                    settingsManager.setServerConfig(ServerClient.normalizeBaseUrl(serverUrl), token)
                    settingsManager.setSetupMode("server")
                }
                settingsManager.saveAuthData(authData) // flips Navigation into the app
            }.onFailure { error = it.message ?: "Connection failed." }
        }
    }

    // Hardware BACK returns to the setup chooser instead of exiting the app.
    androidx.activity.compose.BackHandler { onBack() }

    // Full-bleed dark background across the WHOLE window (behind any keyboard/inset area) so the IME
    // opening doesn't reveal a black bar where safeDrawingPadding pushes the content up.
    Box(modifier = Modifier.fillMaxSize().background(TvDarkBackground)) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .tvOverscan(),
            horizontalAlignment = Alignment.CenterHorizontally,
            // Top-anchored (not centered) so the fields stay in the upper area — the keyboard overlays
            // the empty lower area and NOTHING shifts when it opens.
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(Modifier.height(TvDimens.SpaceXl))
            Text(
                if (jtvMode) "Connect to JTV Server" else "Connect to JTV Proxy Server",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = TvOnBackground
            )
            Spacer(Modifier.height(TvDimens.SpaceSm))
            Text(
                if (jtvMode) "Just enter your access code — the server address is built in."
                else "Pull shared credentials from your self-hosted server.",
                style = MaterialTheme.typography.bodyLarge,
                color = TvOnSurfaceVariant
            )
            Spacer(Modifier.height(TvDimens.SpaceXl))

            error?.let {
                Text(it, color = TvError, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(TvDimens.SpaceMd))
            }

            // Self-hosted mode shows the URL field; JTV mode hides it (URL is hardcoded).
            if (!jtvMode) {
                TvField(
                    label = "Server URL",
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    placeholder = "http://192.168.1.10:8080",
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next,
                    keyboardActions = KeyboardActions(onNext = { runCatching { tokenFocus.requestFocus() } }),
                    modifier = Modifier.focusRequester(urlFocus)
                )
                Spacer(Modifier.height(TvDimens.SpaceMd))
            }
            TvField(
                label = "Access Code",
                value = token,
                onValueChange = { token = it },
                // Purely illustrative placeholder — must NOT resemble any real/active code.
                placeholder = "e.g. 7XK2Q9",
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done,
                keyboardActions = KeyboardActions(onDone = { runCatching { connectFocus.requestFocus() } }),
                modifier = Modifier.focusRequester(tokenFocus)
            )
            Spacer(Modifier.height(TvDimens.SpaceXl))

            Row(horizontalArrangement = Arrangement.spacedBy(TvDimens.SpaceMd)) {
                Surface(
                    onClick = { onBack() },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                    // No focus-scale — the border/colour marks focus (per user preference).
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = TvDarkSurfaceVariant,
                        focusedContainerColor = TvDarkSurface
                    )
                ) {
                    Text("Back", modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp), color = TvOnSurface)
                }
                Surface(
                    onClick = { if (!isConnecting) connect() },
                    modifier = Modifier.focusRequester(connectFocus),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = TvPrimaryContainer,
                        focusedContainerColor = TvPrimary
                    )
                ) {
                    Text(
                        if (isConnecting) "Connecting…" else "Connect",
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 14.dp),
                        color = TvOnBackground,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun TvField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    imeAction: ImeAction = ImeAction.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    modifier: Modifier = Modifier
) {
    // Plain focusable field (no click-to-edit, no Surface → no focus-scale). D-pad onto it and the
    // system keyboard opens; press Back to close it, then D-pad away.
    val keyboard = LocalSoftwareKeyboardController.current
    val view = LocalView.current
    var focused by remember { mutableStateOf(false) }
    Column(
        // Designed at 520dp for TVs; shrink to the window on narrow phones instead of overflowing.
        modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 520.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = TvOnSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(TvDarkSurfaceVariant)
                .border(
                    BorderStroke(if (focused) 2.dp else 1.dp, if (focused) TvPrimary else TvPrimary.copy(alpha = 0.4f)),
                    RoundedCornerShape(10.dp)
                )
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = TvOnSurface, fontSize = 16.sp),
                cursorBrush = SolidColor(TvPrimary),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
                keyboardActions = keyboardActions,
                // The caller's focusRequester (initial focus / Next-Done chaining) rides on the field.
                modifier = modifier
                    .fillMaxWidth()
                    .onFocusChanged { st ->
                        focused = st.isFocused
                        if (st.isFocused) {
                            // Focus alone doesn't reliably pop the TV IME. Ask via the Compose
                            // controller AND poke the platform InputMethodManager as a fallback for
                            // boxes where the controller is a no-op.
                            keyboard?.show()
                            runCatching {
                                val imm = view.context.getSystemService(
                                    android.content.Context.INPUT_METHOD_SERVICE
                                ) as? android.view.inputmethod.InputMethodManager
                                imm?.showSoftInput(view, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                            }
                        }
                    },
                decorationBox = { inner ->
                    if (value.isEmpty()) Text(placeholder, color = TvOnSurfaceVariant, fontSize = 16.sp)
                    inner()
                }
            )
        }
    }
}
