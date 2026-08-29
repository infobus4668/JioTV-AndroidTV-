package com.fenyx.jtv.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.fenyx.jtv.data.Channel
import com.fenyx.jtv.data.EpgProgram
import com.fenyx.jtv.data.EpgRepository
import com.fenyx.jtv.theme.DeviceForm
import com.fenyx.jtv.theme.LocalDeviceForm
import com.fenyx.jtv.theme.Surface
import com.fenyx.jtv.theme.TvDarkBackground
import com.fenyx.jtv.theme.TvDarkSurfaceVariant
import com.fenyx.jtv.theme.TvFocusBorder
import com.fenyx.jtv.theme.TvLiveRed
import com.fenyx.jtv.theme.TvOnSurface
import com.fenyx.jtv.theme.TvOnSurfaceVariant
import com.fenyx.jtv.theme.TvPrimaryContainer

/**
 * Full time-grid EPG guide: channels down the side, a shared horizontally-scrolling time axis
 * across the top and every row, an hour header that stays in sync, and a red "now" marker drawn
 * on whichever programme is live.
 *
 * Weak-TV guardrails (same contract as the compact rows mode):
 *  - Programme data comes ONLY from the existing lazy native fetcher ([onRequestEpg], capped at
 *    4 concurrent requests by MainViewModel) — nothing new hits the network here.
 *  - The guide shows a bounded window (now → +5h) so each row lays out a handful of blocks,
 *    not a whole day; rows themselves are virtualized by the LazyColumn.
 *  - One shared 30s clock ([now]) hoisted by MainScreen drives every row.
 */
private val GUIDE_PX_PER_MIN = 6.dp
private const val GUIDE_WINDOW_HOURS = 5

/**
 * Channel-name column of the guide. Fixed 170dp was tuned for TVs; on a portrait phone it ate
 * ~45% of the window, so narrow screens get a slimmer column (the timeline itself already scrolls).
 * 128dp is the phone floor: below it ("Bhagyam TV"-length names) wrapped mid-word at ~3 chars/line.
 */
@Composable
private fun guideChannelColWidth(): Dp = when (LocalDeviceForm.current) {
    DeviceForm.TV -> 170.dp
    DeviceForm.TABLET -> 140.dp
    DeviceForm.PHONE -> 128.dp
}

@Composable
fun EpgTimeGrid(
    channels: List<Channel>,
    channelIndexMap: Map<String, Int>,
    epgData: Map<String, List<EpgProgram>>,
    now: Long,
    onRequestEpg: (String) -> Unit,
    onChannelClick: (Int) -> Unit,
    // Past, replayable blocks launch a catch-up replay instead of the live feed.
    onCatchupClick: (Int, EpgProgram) -> Unit = { _, _ -> },
    firstItemFocus: FocusRequester,
    modifier: Modifier = Modifier
) {
    // ─── Time-shift (browse the past) ───
    // 0 = live window (now → +5h). Each unit shifts the whole window 1h INTO THE PAST, up to 24h —
    // matching the native EPG's coverage (offset=0 = yesterday + today). Past blocks stay
    // replay-clickable; the header's ◀ / NOW / ▶ controls and the ⏮/⏭ remote keys move it.
    var shiftH by remember { mutableIntStateOf(0) }
    fun shiftWindow(delta: Int) {
        val next = (shiftH + delta).coerceIn(0, 24)
        if (next != shiftH) {
            shiftH = next
            android.util.Log.d("EpgGrid", "time-shift -> -$shiftH h")
        }
    }

    // Window starts on the current full hour (minus the shift) so the header is plain hour ticks.
    val windowStart = remember(now, shiftH) {
        now / 3_600_000L * 3_600_000L - shiftH * 3_600_000L
    }
    val windowEnd = windowStart + GUIDE_WINDOW_HOURS * 3_600_000L
    val channelColWidth = guideChannelColWidth()

    // ONE scroll state shared by the hour header and every row → they always move together.
    val hScroll = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            // ⏮ / ⏭ shift the timeline ±1h from anywhere in the guide (rows keep ↑↓/PgUp-Dn).
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown) {
                    when (e.key) {
                        Key.MediaPrevious -> { shiftWindow(+1); true }
                        Key.MediaNext -> { shiftWindow(-1); true }
                        else -> false
                    }
                } else false
            }
    ) {
        GuideHourHeader(
            windowStartMs = windowStart,
            hScroll = hScroll,
            shiftH = shiftH,
            channelColWidth = channelColWidth,
            onShift = ::shiftWindow,
            onNow = { shiftH = 0 }
        )
        Spacer(modifier = Modifier.height(2.dp))
        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier
                .fillMaxSize()
                .tvFastNavKeys(LocalFocusManager.current, pageRows = 5),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(
                start = 12.dp, end = 12.dp, bottom = 16.dp
            )
        ) {
            itemsIndexed(items = channels, key = { _, ch -> ch.id }) { index, channel ->
                LaunchedEffect(channel.id) {
                    onRequestEpg(channel.id)
                }
                GuideRow(
                    channel = channel,
                    programs = epgData[channel.id].orEmpty(),
                    windowStartMs = windowStart,
                    windowEndMs = windowEnd,
                    now = now,
                    hScroll = hScroll,
                    channelColWidth = channelColWidth,
                    onClick = { onChannelClick(channelIndexMap[channel.id] ?: index) },
                    onCatchup = { prog -> onCatchupClick(channelIndexMap[channel.id] ?: index, prog) },
                    modifier = if (index == 0) Modifier.focusRequester(firstItemFocus) else Modifier
                )
            }
        }
    }
}

@Composable
private fun GuideHourHeader(
    windowStartMs: Long,
    hScroll: androidx.compose.foundation.ScrollState,
    shiftH: Int,
    channelColWidth: Dp,
    onShift: (Int) -> Unit,
    onNow: () -> Unit
) {
    val format = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.35f))
            .padding(vertical = 6.dp)
    ) {
        // ─── Time-shift controls (◀ back / NOW snap / ▶ forward) ───
        // Sit in the channel-column slot directly above the rows, so ↑ from the first row lands
        // on them naturally.
        Row(
            modifier = Modifier.width(channelColWidth).padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            HeaderKey("◀", enabled = shiftH < 24, onClick = { onShift(+1) })
            HeaderKey(
                label = if (shiftH == 0) "NOW" else "-${shiftH}h",
                enabled = shiftH > 0,
                highlight = shiftH > 0,
                onClick = onNow
            )
            HeaderKey("▶", enabled = shiftH > 0, onClick = { onShift(-1) })
        }
        // enabled=false: the header must never eat gestures/focus — it only mirrors the shared
        // scroll position driven by the rows.
        // Phones get half-hour ticks: at 6dp/min a phone viewport shows ~35 minutes of timeline,
        // so hour-only ticks left a single label ("03:30") stranded with nothing to scroll by.
        val tickMs = if (LocalDeviceForm.current == DeviceForm.PHONE) 1_800_000L else 3_600_000L
        val windowEndMs = windowStartMs + GUIDE_WINDOW_HOURS * 3_600_000L
        Box(modifier = Modifier.weight(1f).horizontalScroll(hScroll, enabled = false)) {
            Row {
                var tick = windowStartMs
                while (tick < windowEndMs) {
                    Box(modifier = Modifier.width(GUIDE_PX_PER_MIN * (tickMs / 60_000L).toInt())) {
                        Text(
                            format.format(java.util.Date(tick)),
                            color = TvOnSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    tick += tickMs
                }
            }
        }
    }
}

/** Small focusable key in the guide header (◀ / NOW / ▶). Dimmed when its action is unavailable. */
@Composable
private fun HeaderKey(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    highlight: Boolean = false
) {
    Surface(
        onClick = onClick,
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(RoundedCornerShape(7.dp)),
        scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
            containerColor = if (highlight) TvPrimaryContainer.copy(alpha = 0.55f) else TvDarkSurfaceVariant,
            focusedContainerColor = if (highlight) Color(0xFFFFB300) else TvDarkSurfaceVariant,
            contentColor = if (highlight) Color(0xFFFFB300) else TvOnSurface,
            focusedContentColor = if (highlight) Color.Black else TvOnSurface
        ),
        border = androidx.tv.material3.ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, TvFocusBorder),
                shape = RoundedCornerShape(7.dp)
            )
        )
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (!enabled) TvOnSurfaceVariant else Color.Unspecified,
                // "NOW" must never wrap — at the old 14dp padding inside the 112dp phone column it
                // broke into "NO / W".
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun GuideRow(
    channel: Channel,
    programs: List<EpgProgram>,
    windowStartMs: Long,
    windowEndMs: Long,
    now: Long,
    hScroll: androidx.compose.foundation.ScrollState,
    channelColWidth: Dp,
    onClick: () -> Unit,
    onCatchup: (EpgProgram) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val timeFormat = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()) }

    Row(modifier = modifier.fillMaxWidth().height(64.dp)) {
        // ─── Channel cell ───
        // clickable() alone gives zero visual focus on D-pad TVs — the cell looked identical
        // focused or not. Track focus and draw the standard focus border.
        var cellFocused by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier
                .width(channelColWidth)
                .onFocusChanged { cellFocused = it.isFocused }
                .clip(RoundedCornerShape(8.dp))
                .background(com.fenyx.jtv.theme.TvDarkSurface)
                .border(
                    if (cellFocused) 2.dp else 0.dp,
                    TvFocusBorder,
                    RoundedCornerShape(8.dp)
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (channel.logoUrl.isNotEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(channel.logoUrl).size(72).build(),
                    contentDescription = null,
                    modifier = Modifier.size(34.dp).clip(RoundedCornerShape(5.dp)).background(Color.White),
                    contentScale = ContentScale.Fit
                )
            } else {
                Box(
                    modifier = Modifier.size(34.dp).clip(RoundedCornerShape(5.dp)).background(TvDarkSurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(channel.name.take(1), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                channel.name,
                color = TvOnSurface,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // ─── Timeline axis ───
        Box(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(hScroll)
        ) {
            Row(
                modifier = Modifier.height(64.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                var cursor = windowStartMs
                // Sorted, clipped view of this channel's programmes within the visible window.
                // The original programme travels with each block so past ones can launch replays.
                val clipped = remember(programs, windowStartMs, windowEndMs) {
                    programs.mapNotNull { p ->
                        EpgRepository.clipProgramToWindow(p.startMs, p.stopMs, windowStartMs, windowEndMs)
                            ?.let { ClippedBlock(p.title, it.first, it.last + 1, p) }
                    }.sortedBy { it.s }
                }
                clipped.forEach { b ->
                    if (b.s > cursor) GapBlock(cursor until b.s)
                    val isPast = b.prog.stopMs <= now
                    ProgramBlock(
                        title = b.title,
                        startMs = b.s,
                        endMs = b.e,
                        containsNow = now >= b.s && now < b.e,
                        timeLabel = timeFormat.format(java.util.Date(b.s)),
                        isReplayable = isPast && b.prog.isReplayable,
                        onClick = { if (isPast && b.prog.isReplayable) onCatchup(b.prog) else onClick() }
                    )
                    cursor = b.e
                }
                if (cursor < windowEndMs) GapBlock(cursor until windowEndMs)
            }
        }
    }
}

/** A programme block clipped to the guide window, carrying its source [prog] for replay clicks. */
private data class ClippedBlock(val title: String, val s: Long, val e: Long, val prog: EpgProgram)

/** Empty filler where the guide has no programme data. */
@Composable
private fun RowScope.GapBlock(range: LongRange) {
    Box(
        modifier = Modifier
            .width((range.lengthMinutes() * 6).dp.coerceAtLeast(24.dp))
            .fillMaxHeight()
            .clip(RoundedCornerShape(6.dp))
            .background(TvDarkBackground.copy(alpha = 0.7f))
    )
}

/** One programme cell; highlighted while live, with a red bar marking the current moment. Past,
 *  replayable blocks get a ▶ badge and launch a catch-up replay on click (see [isReplayable]). */
@Composable
private fun RowScope.ProgramBlock(
    title: String,
    startMs: Long,
    endMs: Long,
    containsNow: Boolean,
    timeLabel: String,
    isReplayable: Boolean = false,
    onClick: () -> Unit
) {
    // D-pad focus cue: programme blocks are clickable but showed no focused state at all.
    var blockFocused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .width((((endMs - startMs) / 60_000L * 6).toInt()).dp.coerceAtLeast(28.dp))
            .fillMaxHeight()
            .onFocusChanged { blockFocused = it.isFocused }
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    containsNow -> TvPrimaryContainer.copy(alpha = 0.55f)
                    isReplayable -> com.fenyx.jtv.theme.TvDarkSurface.copy(alpha = 0.9f)
                    else -> TvDarkSurfaceVariant
                }
            )
            .border(
                if (blockFocused) 2.dp else 1.dp,
                when {
                    blockFocused -> TvFocusBorder
                    containsNow -> TvLiveRed.copy(alpha = 0.8f)
                    isReplayable -> Color(0xFFFFB300).copy(alpha = 0.45f)
                    else -> Color.Transparent
                },
                RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (containsNow) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(1.dp))
                    .background(TvLiveRed)
            )
            Spacer(modifier = Modifier.width(6.dp))
        } else if (isReplayable) {
            Text("▶", color = Color(0xFFFFB300), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(4.dp))
        }
        Column {
            Text(
                title,
                color = if (containsNow) TvOnSurface else if (isReplayable) Color.White.copy(alpha = 0.85f) else TvOnSurface,
                fontSize = 11.sp,
                fontWeight = if (containsNow) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(timeLabel, color = TvOnSurfaceVariant, fontSize = 9.sp)
        }
    }
}

private fun LongRange.lengthMinutes(): Int =
    (((endInclusive - start) / 60_000L).toInt()).coerceAtLeast(0)
