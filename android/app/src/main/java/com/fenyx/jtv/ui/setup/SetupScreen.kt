package com.fenyx.jtv.ui.setup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.compose.foundation.shape.CircleShape
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
    onChooseJtv: () -> Unit = {},
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

        // fillMaxWidth + weight(1f) so the three cards SHARE the row width equally and always fit any
        // screen — fixed widths overflowed narrow TVs and clipped the third card.
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TvDimens.SpaceMd)) {
            SetupCard(
                title = "Connect with a code",
                subtitle = "Enter your JTV Server access code. No URL needed.",
                icon = { KeyGlyph() },
                modifier = Modifier.weight(1f).focusRequester(firstCard),
                onClick = onChooseJtv
            )
            SetupCard(
                title = "Sign in with Jio number",
                subtitle = "Enter your mobile number and OTP on this TV.",
                icon = { PhoneGlyph() },
                modifier = Modifier.weight(1f),
                onClick = onChoosePhone
            )
            SetupCard(
                title = "Use your own server",
                subtitle = "Enter your server URL and access code.",
                icon = { ServerGlyph() },
                modifier = Modifier.weight(1f),
                onClick = onChooseServer
            )
        }
    }
}

/** Server-rack glyph: two stacked rack units, each with status lights. Accent-coloured to match the
 *  phone glyph so the two setup cards read as one icon set. */
@Composable
private fun ServerGlyph(color: Color = TvPrimary) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(2) {
            Box(
                modifier = Modifier
                    .size(width = 54.dp, height = 20.dp)
                    .border(2.dp, color, RoundedCornerShape(5.dp)),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.padding(start = 9.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(color))
                    Box(Modifier.size(6.dp).clip(CircleShape).background(color.copy(alpha = 0.4f)))
                }
            }
        }
    }
}

/** Phone glyph matching [ServerGlyph]'s line-art style (rounded body, speaker slit + home dot). */
@Composable
private fun PhoneGlyph(color: Color = TvPrimary) {
    Box(
        modifier = Modifier
            .size(width = 34.dp, height = 54.dp)
            .border(2.5.dp, color, RoundedCornerShape(8.dp))
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 7.dp)
                .size(width = 10.dp, height = 2.5.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 5.dp)
                .size(5.dp)
                .clip(CircleShape)
                .background(color)
        )
    }
}

/** Key glyph for the JTV Server card = "connect with an access code". Same accent line-art weight as
 *  the phone/server glyphs so the three cards read as one set, distinct from the server-rack. */
@Composable
private fun KeyGlyph(color: Color = TvPrimary) {
    Canvas(modifier = Modifier.size(width = 60.dp, height = 46.dp)) {
        val s = 3.dp.toPx()
        val ringR = 12.dp.toPx()
        val cy = size.height / 2f
        val cx = ringR + s / 2f
        // Bow (ring) + a clearly proportioned hole.
        drawCircle(color, radius = ringR, center = Offset(cx, cy), style = Stroke(width = s))
        drawCircle(color, radius = ringR * 0.42f, center = Offset(cx, cy), style = Stroke(width = s))
        // Blade (shaft) reaching to the right edge, with two teeth at the tip.
        val shaftStart = cx + ringR
        val shaftEnd = size.width - s / 2f
        drawLine(color, Offset(shaftStart, cy), Offset(shaftEnd, cy), strokeWidth = s, cap = StrokeCap.Round)
        val toothLong = 11.dp.toPx()
        drawLine(color, Offset(shaftEnd, cy), Offset(shaftEnd, cy + toothLong), strokeWidth = s, cap = StrokeCap.Round)
        drawLine(color, Offset(shaftEnd - 11.dp.toPx(), cy), Offset(shaftEnd - 11.dp.toPx(), cy + toothLong * 0.7f), strokeWidth = s, cap = StrokeCap.Round)
    }
}

@Composable
private fun SetupCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(240.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
        // No focus scale on the setup cards (the border marks focus) — per user preference.
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
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
            // Fixed icon area so glyphs of different heights (phone vs server) occupy the same space —
            // otherwise the centered content shifts and the cards look mismatched.
            Box(modifier = Modifier.height(56.dp), contentAlignment = Alignment.Center) {
                icon?.invoke()
            }
            Spacer(Modifier.height(TvDimens.SpaceMd))
            // Reserve two lines for the title so 1-line and 2-line titles start their subtitle at the
            // same y across all cards.
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TvOnBackground,
                textAlign = TextAlign.Center,
                minLines = 2,
                maxLines = 2
            )
            Spacer(Modifier.height(TvDimens.SpaceSm))
            // Reserve exactly two lines for the subtitle on EVERY card so the icon+title+subtitle block
            // is the same height everywhere — otherwise a longer subtitle (self-hosted) overflowed the
            // fixed card height and clipped, making the cards look unequal.
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = TvOnSurfaceVariant,
                textAlign = TextAlign.Center,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
