package com.fenyx.jtv.ui.setup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.compose.foundation.shape.RoundedCornerShape
import com.fenyx.jtv.theme.*

/**
 * First-boot setup chooser. Presents the two ways to sign in as large, focusable TV cards:
 *  - **Phone** — the existing OTP login on this device.
 *  - **Server** — pull shared credentials from a self-hosted JTV proxy server (log in once, every TV).
 */
@Composable
fun SetupScreen(
    onChoosePhone: () -> Unit,
    onChooseServer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val firstCard = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstCard.requestFocus() } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TvDarkBackground)
            .tvOverscan(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Welcome to JTV",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = TvOnBackground
        )
        Spacer(Modifier.height(TvDimens.SpaceSm))
        Text(
            "Choose how you want to set up",
            style = MaterialTheme.typography.titleMedium,
            color = TvOnSurfaceVariant
        )
        Spacer(Modifier.height(TvDimens.SpaceXl))

        Row(horizontalArrangement = Arrangement.spacedBy(TvDimens.SpaceLg)) {
            SetupCard(
                emoji = "📱",
                title = "Sign in with Jio number",
                subtitle = "Enter your mobile number and OTP on this TV.",
                modifier = Modifier.focusRequester(firstCard),
                onClick = onChoosePhone
            )
            SetupCard(
                emoji = "🖧",
                title = "Connect to JTV Proxy Server",
                subtitle = "Pull shared credentials from your server — log in once, use every TV.",
                onClick = onChooseServer
            )
        }
    }
}

@Composable
private fun SetupCard(
    emoji: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.width(360.dp).height(240.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = TvDimens.FocusedScale),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = TvDarkSurface,
            focusedContainerColor = TvDarkSurfaceVariant
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, TvPrimary), shape = RoundedCornerShape(16.dp))
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(TvDimens.SpaceLg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(emoji, fontSize = 44.sp)
            Spacer(Modifier.height(TvDimens.SpaceMd))
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TvOnBackground,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(TvDimens.SpaceSm))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = TvOnSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
