package com.fenyx.jtv.ui.setup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.fenyx.jtv.data.ServerClient
import com.fenyx.jtv.data.SettingsManager
import com.fenyx.jtv.theme.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Server-mode setup: enter the JTV proxy server URL + access token, then "Connect" pulls the shared
 * credentials. On success we persist the server config + credentials; Navigation then flips to the app
 * automatically (authData becomes non-null).
 */
@Composable
fun ServerSetupScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val scope = rememberCoroutineScope()

    var serverUrl by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var isConnecting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Prefill from any previously saved config (e.g. re-connecting from Settings).
    LaunchedEffect(Unit) {
        serverUrl = settingsManager.serverUrlFlow.first()
        token = settingsManager.serverTokenFlow.first()
    }

    val urlFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { urlFocus.requestFocus() } }

    fun connect() {
        if (serverUrl.isBlank()) { error = "Enter the server URL."; return }
        isConnecting = true
        error = null
        scope.launch {
            val result = ServerClient.fetchCredentials(serverUrl, token)
            isConnecting = false
            result.onSuccess { authData ->
                settingsManager.setServerConfig(ServerClient.normalizeBaseUrl(serverUrl), token)
                settingsManager.setSetupMode("server")
                settingsManager.saveAuthData(authData) // flips Navigation into the app
            }.onFailure { error = it.message ?: "Connection failed." }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TvDarkBackground)
            .tvOverscan(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Connect to JTV Proxy Server",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = TvOnBackground
        )
        Spacer(Modifier.height(TvDimens.SpaceSm))
        Text(
            "Pull shared credentials from your self-hosted server.",
            style = MaterialTheme.typography.bodyLarge,
            color = TvOnSurfaceVariant
        )
        Spacer(Modifier.height(TvDimens.SpaceXl))

        error?.let {
            Text(it, color = TvError, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(TvDimens.SpaceMd))
        }

        TvField(
            label = "Server URL",
            value = serverUrl,
            onValueChange = { serverUrl = it },
            placeholder = "http://192.168.1.10:8080",
            keyboardType = KeyboardType.Uri,
            modifier = Modifier.focusRequester(urlFocus)
        )
        Spacer(Modifier.height(TvDimens.SpaceMd))
        TvField(
            label = "Access Token",
            value = token,
            onValueChange = { token = it },
            placeholder = "server access token",
            keyboardType = KeyboardType.Password
        )
        Spacer(Modifier.height(TvDimens.SpaceXl))

        Row(horizontalArrangement = Arrangement.spacedBy(TvDimens.SpaceMd)) {
            Surface(
                onClick = { onBack() },
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = TvDarkSurfaceVariant,
                    focusedContainerColor = TvDarkSurface
                )
            ) {
                Text("Back", modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp), color = TvOnSurface)
            }
            Surface(
                onClick = { if (!isConnecting) connect() },
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                scale = ClickableSurfaceDefaults.scale(focusedScale = TvDimens.FocusedScale),
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

@Composable
private fun TvField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier
) {
    Column(modifier = Modifier.width(520.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = TvOnSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(TvDarkSurfaceVariant)
                .border(1.dp, TvPrimary.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (value.isEmpty()) {
                Text(placeholder, color = TvOnSurfaceVariant, fontSize = 16.sp)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = TvOnSurface, fontSize = 16.sp),
                cursorBrush = SolidColor(TvPrimary),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
