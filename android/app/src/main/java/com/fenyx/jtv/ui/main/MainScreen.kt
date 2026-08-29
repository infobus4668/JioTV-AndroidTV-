package com.fenyx.jtv.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.fenyx.jtv.theme.Surface
import androidx.tv.material3.MaterialTheme
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.fenyx.jtv.data.SettingsManager
import com.fenyx.jtv.theme.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    onChannelClick: (Int, String?) -> Unit,
    onSettingsClick: () -> Unit,
    onSearchClick: () -> Unit = {},
    // Catch-up intent from the EPG time-grid's past blocks.
    onCatchupClick: (Int, com.fenyx.jtv.data.EpgProgram) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = viewModel()
) {
    val channels by viewModel.channels.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val selectedGroup by viewModel.selectedGroup.collectAsState()

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val settingsManager = remember { SettingsManager(context) }
    // Grid tile size: Comfortable (default) / Compact / Large — a real win with 1,300+ channels.
    // 0 = "Auto": pick the best fit for the panel so 720p TVs aren't stuck with 1080p-sized tiles.
    val gridTileDpRaw by settingsManager.gridDensityDpFlow.collectAsState(initial = 0)
    val autoCompactWindow = isCompactWidth()
    val autoWideWindow = screenWidthDp() >= 800
    val gridTileDp = remember(gridTileDpRaw, autoCompactWindow, autoWideWindow) {
        if (gridTileDpRaw > 0) gridTileDpRaw
        else {
            // Auto: on compact windows the 0.28 clamp below does the work, so keep the comfortable
            // 150 base; on large screens use the window width as a TV-resolution proxy
            // (1080p ≈ 960dp wide at density 2.0, 720p ≈ 640dp).
            if (autoCompactWindow || autoWideWindow) 150 else 120
        }
    }
    // EPG layout: off / rows (now+next) / grid (scrolling time axis). Migrated from the legacy
    // boolean automatically inside SettingsManager when unset.
    val epgStyle by settingsManager.epgStyleFlow.collectAsState(initial = SettingsManager.EPG_STYLE_OFF)
    val epgData by viewModel.epgData.collectAsState()
    val favoriteChannels by viewModel.favoriteChannels.collectAsState()
    val languageFilter by viewModel.languageFilter.collectAsState()
    val categoryCounts by viewModel.categoryCounts.collectAsState()
    val filteredChannels by viewModel.filteredChannels.collectAsState()
    // Favorites pinned row: same filtered list the grid shows (honors language filter + sort).
    val favoriteRow = remember(filteredChannels, favoriteChannels) {
        if (favoriteChannels.isEmpty()) emptyList() else filteredChannels.filter { it.id in favoriteChannels }.take(12)
    }

    LaunchedEffect(Unit) {
        viewModel.fetchChannels()
    }
    
    // EPG is driven by Jio's NATIVE per-channel guide (reliable, keyed by channel_id, correct ms
    // epochs) — filled per visible row below via fetchNativeEpgIfMissing. We intentionally do NOT
    // auto-download/parse the XMLTV source here: the default source's IDs (ts…/sun…) don't map to Jio
    // channel_ids so it shows nothing, and parsing its ~19 MB file on every EPG entry hammered weak TVs.
    // The XMLTV path stays available only via the manual "Refresh EPG Data" button in Settings, for
    // users who point EPG Source URL at a Jio-ID-keyed feed.

    // Single shared 30s clock for every EPG row. Previously each visible row ran its own
    // `while(true){ delay(30s) }` ticker and recomposed independently — on a full EPG screen that was
    // ~20 coroutines + 20 separate recomposition passes. One hoisted clock is far lighter on weak CPUs.
    var epgNow by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(epgStyle) {
        if (epgStyle != SettingsManager.EPG_STYLE_OFF) {
            while (true) {
                epgNow = System.currentTimeMillis()
                kotlinx.coroutines.delay(30_000)
            }
        }
    }

    // Layout: horizontal filter chip rows above a full-width grid (Google TV pattern) instead of a
    // left sidebar. One OK press changes any filter with the grid updating in place behind the
    // chips — no dialogs, and the grid gets ~2 extra tile columns vs the old 210dp sidebar.
    Column(modifier = modifier.fillMaxSize().background(TvDarkBackground)) {

        // ─── Top Bar ───
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = overscanH(),
                    end = overscanH(),
                    top = TvDimens.SpaceSm
                )
                .focusGroup(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TopBarIconButton(Icons.Default.Search, "Search", tint = TvPrimary, onClick = onSearchClick)
            // Language filtering lives in Settings now; this indicator appears only while a filter
            // is active so it's obvious why the list is shorter — OK jumps straight to Settings.
            if (languageFilter.isNotEmpty()) {
                Spacer(modifier = Modifier.width(TvDimens.SpaceSm))
                TvFilterChip(
                    text = "Languages · ${languageFilter.size}",
                    count = null,
                    selected = true,
                    onClick = onSettingsClick
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            TopBarIconButton(Icons.Default.Refresh, "Refresh", tint = TvOnSurfaceVariant, onClick = { viewModel.retry() })
            Spacer(modifier = Modifier.width(TvDimens.SpaceSm))
            TopBarIconButton(Icons.Default.Settings, "Settings", tint = TvOnSurfaceVariant, onClick = onSettingsClick)
        }

        // ─── Category chips (single-select; count = channels after the language filter) ───
        val chipGroups = remember(groups, favoriteChannels) {
            buildList {
                add(MainViewModel.GROUP_ALL)
                if (favoriteChannels.isNotEmpty()) add(MainViewModel.GROUP_FAVORITES)
                addAll(groups)
            }
        }
        CategoryChipRow(
            groups = chipGroups,
            selectedGroup = selectedGroup,
            counts = categoryCounts,
            onSelect = { viewModel.setSelectedGroup(it) }
        )

        // ─── Content Area (Channel Grid) ───
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (isLoading && channels.isEmpty()) {
                // Skeleton placeholders shaped like the real layout: the screen reads as "loading"
                // instantly instead of a bare spinner, and there is no layout jump when data lands.
                HomeSkeleton(epgRows = epgStyle != SettingsManager.EPG_STYLE_OFF)
            } else if (error != null) {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("⚠", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = error!!,
                        color = TvOnSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Surface(
                        onClick = { viewModel.retry() },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = TvPrimaryContainer,
                            focusedContainerColor = TvPrimary
                        )
                    ) {
                        Text(
                            "Retry",
                            modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp),
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else {
                // Pre-compute channel index map once (O(n)) instead of indexOf per item (O(n²))
                val allChannels = viewModel.getAllChannels()
                val channelIndexMap = remember(allChannels) {
                    allChannels.withIndex().associate { (i, ch) -> ch.id to i }
                }

                // Initial focus: drop focus onto the first channel once per screen entry after the list
                // appears, so the first D-pad press works — and so returning from the player (which
                // recomposes Home fresh) re-establishes focus instead of leaving the remote dead.
                // Uses plain `remember` (not rememberSaveable) so each fresh entry re-requests; the guard
                // stops category switches within one entry from yanking focus back to the grid.
                val firstItemFocus = remember { FocusRequester() }
                var initialFocusDone by remember { mutableStateOf(false) }
                LaunchedEffect(filteredChannels.isNotEmpty()) {
                    if (!initialFocusDone && filteredChannels.isNotEmpty()) {
                        runCatching { firstItemFocus.requestFocus() }
                        initialFocusDone = true
                    }
                }

                Column(modifier = Modifier.fillMaxSize()) {

                    // ─── Pinned rows: ★ Favorites ───
                    // Local-only horizontal rail above the main grid. Hidden when irrelevant so
                    // the grid keeps every pixel when there's nothing useful to pin.
                    if (favoriteRow.isNotEmpty() && selectedGroup != MainViewModel.GROUP_FAVORITES) {
                        ChannelRail(
                            title = "★ Favorites",
                            channels = favoriteRow,
                            channelIndexMap = channelIndexMap,
                            launchGroup = MainViewModel.GROUP_FAVORITES,
                            onChannelClick = onChannelClick,
                            enterMs = TvMotion.ms()
                        )
                    }

                    if (filteredChannels.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            val msg = when {
                                selectedGroup == MainViewModel.GROUP_FAVORITES && languageFilter.isEmpty() ->
                                    "No favorites yet\nTap the ⭐ key in the player to add channels here"
                                languageFilter.isNotEmpty() ->
                                    "No channels match the selected language${if (languageFilter.size == 1) "" else "s"}"
                                else -> "No channels in this category"
                            }
                            Text(msg, color = TvOnSurfaceVariant, textAlign = TextAlign.Center)
                        }
                    } else if (epgStyle == SettingsManager.EPG_STYLE_GRID) {
                        EpgTimeGrid(
                            channels = filteredChannels,
                            channelIndexMap = channelIndexMap,
                            epgData = epgData,
                            now = epgNow,
                            onRequestEpg = viewModel::fetchNativeEpgIfMissing,
                            onChannelClick = { index -> onChannelClick(index, selectedGroup) },
                            onCatchupClick = onCatchupClick,
                            firstItemFocus = firstItemFocus,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (epgStyle == SettingsManager.EPG_STYLE_ROWS) {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.tvFastNavKeys(focusManager).focusRestorer(),
                            contentPadding = PaddingValues(
                                start = overscanH(), end = overscanH(),
                                top = TvDimens.SpaceSm, bottom = overscanV()
                            )
                        ) {
                            itemsIndexed(items = filteredChannels, key = { _, ch -> ch.id }) { index, channel ->
                                val channelIndex = channelIndexMap[channel.id] ?: 0

                                val programs = epgData[channel.id] ?: emptyList()
                                LaunchedEffect(channel.id) {
                                    if (programs.isEmpty()) {
                                        viewModel.fetchNativeEpgIfMissing(channel.id)
                                    }
                                }

                                EpgChannelRow(
                                    channel = channel,
                                    epgPrograms = programs,
                                    now = epgNow,
                                    onClick = { onChannelClick(channelIndex, selectedGroup) },
                                    modifier = if (index == 0) Modifier.focusRequester(firstItemFocus) else Modifier
                                )
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            // Portrait phones (~360-430dp) fit only 2 giant columns at the TV-tuned
                            // 150dp tile, wasting most of the panel. Clamp the preferred tile to
                            // 28% of the window on narrow screens so 3 columns always fit; TVs and
                            // tablets (>=600dp) keep the user's chosen density untouched.
                            columns = GridCells.Adaptive(gridTileDp.dp.coerceMaxWindowFraction(0.28f)),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            // focusRestorer keeps your place in the grid when you leave and come back
                            // (e.g. return from the player), instead of snapping to the first card.
                            // tvFastNavKeys layers hold-acceleration + CH±/PgUp-Dn page jumps on top
                            // of native single-step D-pad navigation — essential for 1,300+ channels.
                            modifier = Modifier.tvFastNavKeys(focusManager).focusRestorer(),
                            // Overscan-safe: extra room at the edges so focused cards (which scale
                            // up) and edge columns aren't clipped by the panel edge. Resolves per
                            // device — phones/tablets get compact margins instead of TV overscan.
                            contentPadding = PaddingValues(
                                start = overscanH(), end = overscanH(),
                                top = TvDimens.SpaceSm, bottom = overscanV()
                            )
                        ) {
                            itemsIndexed(items = filteredChannels, key = { _, ch -> ch.id }) { index, channel ->
                                val channelIndex = channelIndexMap[channel.id] ?: 0

                                ChannelCard(
                                    channel = channel,
                                    onClick = { onChannelClick(channelIndex, selectedGroup) },
                                    number = channel.channelNumber.takeIf { it > 0 } ?: (index + 1),
                                    modifier = if (index == 0) Modifier.focusRequester(firstItemFocus) else Modifier
                                )
                            }
                        }
                    }
                }
            }


        }
    }
}

@Composable
fun ChannelCard(
    channel: com.fenyx.jtv.data.Channel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    // Positional zap number (1-based list index). Falls back to the channel's own number; hidden when 0.
    number: Int = 0
) {
    val context = LocalContext.current
    // Compact windows (portrait phones) get a smaller logo and tighter padding so the shrunken
    // tile doesn't look empty or crowd its two-line name against the LIVE badge.
    val compact = isCompactWidth()
    val logoSize = if (compact) 44.dp else 56.dp
    val tileNumber = if (number > 0) number else channel.channelNumber

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        // Clear 10-foot focus cue: the card scales up (was disabled at 1.0f) plus the focus border.
        scale = ClickableSurfaceDefaults.scale(focusedScale = TvDimens.FocusedScale),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = TvDarkSurface,
            focusedContainerColor = TvDarkSurfaceVariant
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, TvFocusBorder),
                shape = RoundedCornerShape(12.dp)
            )
        )
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(if (compact) 8.dp else 12.dp)) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Channel logo
            if (channel.logoUrl.isNotEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(channel.logoUrl)
                        .size(112) // Downsample to 2x display size (56dp) to save memory
                        .build(),
                    contentDescription = channel.name,
                    modifier = Modifier
                        .size(logoSize)
                        .clip(CircleShape)
                        .background(TvDarkSurfaceVariant),
                    contentScale = ContentScale.Crop
                )
            } else {
                // Fallback icon
                Box(
                    modifier = Modifier
                        .size(logoSize)
                        .clip(CircleShape)
                        .background(TvDarkSurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = TvOnSurfaceVariant,
                        modifier = Modifier.size(if (compact) 22.dp else 28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Channel name
            Text(
                channel.name,
                style = MaterialTheme.typography.bodySmall,
                color = TvOnSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium
            )

            // LIVE badge
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .background(TvLiveRed.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    "LIVE",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = TvLiveRed,
                    letterSpacing = 1.sp
                )
            }
        }

        // Channel number badge (top-start corner) — matches the zap/numpad numbering.
        if (tileNumber > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(if (compact) 4.dp else 6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    tileNumber.toString(),
                    fontSize = if (compact) 9.sp else 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }
    }
}
}

@Composable
fun EpgChannelRow(
    channel: com.fenyx.jtv.data.Channel,
    epgPrograms: List<com.fenyx.jtv.data.EpgProgram>,
    now: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // Reuse a single formatter instance instead of allocating per-recomposition
    val timeFormat = remember { java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()) }
    // `now` is a single shared 30s clock hoisted to MainScreen (one ticker for the whole list).
    val currentProgram = remember(epgPrograms, now) { epgPrograms.find { it.startMs <= now && it.stopMs > now } }
    val nextPrograms = remember(epgPrograms, now) { epgPrograms.filter { it.startMs > now }.take(3) }
    // Narrow windows can't fit the logo column + NOW card + three "next" cards: the fixed 100dp
    // channel column alone ate ~30% of a portrait phone. Compact devices get a slimmer column and
    // drop the "next" cards entirely so NOW keeps its room.
    val compact = isCompactWidth()
    val channelColWidth = if (compact) 76.dp else 100.dp
    val showNextCards = !compact

    Surface(
        modifier = modifier.fillMaxWidth().heightIn(min = 100.dp),
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = TvDarkSurface,
            focusedContainerColor = TvDarkSurfaceVariant
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, TvFocusBorder),
                shape = RoundedCornerShape(8.dp)
            )
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().wrapContentHeight().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Logo and Name
            Column(
                modifier = Modifier.width(channelColWidth),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (channel.logoUrl.isNotEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(channel.logoUrl).size(96).build(),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)).background(Color.White),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Box(
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)).background(TvDarkSurfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = channel.name.take(1),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    channel.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = TvOnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            // Timeline
            Row(modifier = Modifier.weight(1f)) {
                if (currentProgram != null) {
                    val progress = ((now - currentProgram.startMs).toFloat() / (currentProgram.stopMs - currentProgram.startMs)).coerceIn(0f, 1f)
                    
                    Box(
                        modifier = Modifier
                            .weight(0.45f)
                            .background(TvPrimaryContainer.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .border(1.dp, TvPrimary.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(TvLiveRed))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("NOW PLAYING", color = TvLiveRed, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(currentProgram.title, color = TvOnBackground, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(modifier = Modifier.height(8.dp))
                            androidx.compose.material3.LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(1.5.dp)),
                                color = TvPrimary,
                                trackColor = TvDarkSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("${timeFormat.format(java.util.Date(currentProgram.startMs))} - ${timeFormat.format(java.util.Date(currentProgram.stopMs))}", color = TvOnSurfaceVariant, fontSize = 11.sp)
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                } else if (epgPrograms.isEmpty()) {
                    Text("No EPG Data Available", color = TvOnSurfaceVariant, modifier = Modifier.align(Alignment.CenterVertically))
                }

                if (showNextCards) nextPrograms.forEach { prog ->
                    Box(
                        modifier = Modifier
                            .weight(0.25f)
                            .padding(end = 8.dp)
                            .background(TvDarkBackground.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Text(prog.title, color = TvOnSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(timeFormat.format(java.util.Date(prog.startMs)), color = TvOnSurfaceVariant, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

/** Compact icon-only top-bar button (Search / Refresh / Settings). */
@Composable
private fun TopBarIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit
) {
    // Touch devices get a taller hit area (>=44dp) so the top-bar buttons are thumb-friendly on
    // phones; TVs keep the compact remote-driven size.
    val vpad = if (LocalIsTouch.current) 12.dp else 8.dp
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = TvDarkSurface,
            focusedContainerColor = TvDarkSurfaceVariant
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, TvFocusBorder),
                shape = RoundedCornerShape(10.dp)
            )
        )
    ) {
        Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = vpad)) {
            Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(22.dp))
        }
    }
}

/** Pill chip used by the category row and the active-filter indicator. [count] renders as a "· n" suffix. */
@Composable
private fun TvFilterChip(
    text: String,
    count: Int?,
    selected: Boolean,
    onClick: () -> Unit
) {
    // Taller tap target on touch devices (chips are the primary filter control on phones); TVs keep
    // the compact size so the 10-foot chip row stays tight.
    val vpad = if (LocalIsTouch.current) 10.dp else 7.dp
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(20.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) TvPrimaryContainer.copy(alpha = 0.45f) else TvDarkSurface,
            focusedContainerColor = TvPrimaryContainer
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, TvFocusBorder),
                shape = RoundedCornerShape(20.dp)
            )
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = vpad),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text,
                color = if (selected) TvPrimary else TvOnSurface,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1
            )
            if (count != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "· $count",
                    color = if (selected) TvPrimary.copy(alpha = 0.8f) else TvOnSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

/**
 * Single-select category chips with live channel counts (under the current language filter). The
 * selected chip auto-scrolls into view, so a restored category like "Sports" is visible on entry.
 */
@Composable
private fun CategoryChipRow(
    groups: List<String>,
    selectedGroup: String?,
    counts: Map<String, Int>,
    onSelect: (String) -> Unit
) {
    if (groups.isEmpty()) return
    val listState = rememberLazyListState()
    LaunchedEffect(selectedGroup, groups) {
        val idx = groups.indexOf(selectedGroup).takeIf { it >= 0 } ?: 0
        runCatching { listState.animateScrollToItem(idx) }
    }
    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth().focusGroup().focusRestorer(),
        horizontalArrangement = Arrangement.spacedBy(TvDimens.SpaceSm),
        contentPadding = PaddingValues(
            start = overscanH(), end = overscanH(),
            top = TvDimens.SpaceSm, bottom = 0.dp
        )
    ) {
        itemsIndexed(groups, key = { _, g -> g }) { _, group ->
            val isSelected = selectedGroup == group ||
                (selectedGroup == null && group == MainViewModel.GROUP_ALL)
            val label = when (group) {
                MainViewModel.GROUP_ALL -> "All"
                MainViewModel.GROUP_FAVORITES -> "★ Favorites"
                else -> com.fenyx.jtv.data.CategoryIcons.decorate(group)
            }
            TvFilterChip(
                text = label,
                // Absent from the map (0 channels after the language filter) used to render with no
                // count at all — inconsistent with its siblings. Show "· 0" like every other chip.
                count = counts[group] ?: 0,
                selected = isSelected,
                onClick = { onSelect(group) }
            )
        }
    }
}

/** One pulsing skeleton placeholder box. A single shared alpha animation keeps this cheap. */
@Composable
private fun SkeletonBox(modifier: Modifier, shape: RoundedCornerShape) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(TvDarkSurfaceVariant.copy(alpha = alpha))
    )
}

/**
 * Loading placeholder shaped like the real Home content (tile grid or EPG rows), so nothing jumps
 * when data arrives. Pure local drawing — no blur shaders, safe for weak TV GPUs.
 */
@Composable
private fun HomeSkeleton(epgRows: Boolean) {
    if (epgRows) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = overscanH()),
            verticalArrangement = Arrangement.spacedBy(TvDimens.SpaceSm)
        ) {
            repeat(8) {
                SkeletonBox(Modifier.fillMaxWidth().height(100.dp), RoundedCornerShape(8.dp))
            }
        }
    } else {
        LazyVerticalGrid(
            // Same phone clamp as the live grid so the skeleton previews the real 3-column layout
            // instead of teasing 2 giant tiles that immediately reflow.
            columns = GridCells.Adaptive(150.dp.coerceMaxWindowFraction(0.28f)),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(
                start = overscanH(), end = overscanH(),
                top = TvDimens.SpaceSm, bottom = overscanV()
            ),
            userScrollEnabled = false,
            modifier = Modifier.fillMaxSize()
        ) {
            items(24, key = { it }) {
                SkeletonBox(Modifier.aspectRatio(1f), RoundedCornerShape(12.dp))
            }
        }
    }
}

/**
 * Horizontal pinned rail ("★ Favorites" / "Recently watched"): a titled LazyRow of compact
 * landscape cards. Entirely offline — entries resolve from the already-loaded channel list.
 */
@Composable
private fun ChannelRail(
    title: String,
    channels: List<com.fenyx.jtv.data.Channel>,
    channelIndexMap: Map<String, Int>,
    launchGroup: String?,
    onChannelClick: (Int, String?) -> Unit,
    enterMs: Int
) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(enterMs)) + slideInVertically(tween(enterMs)) { -it / 3 }
    ) {
        Column(modifier = Modifier.padding(top = TvDimens.SpaceMd)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = TvOnSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(
                    start = overscanH(), end = overscanH()
                )
            )
            LazyRow(
                modifier = Modifier.focusGroup().focusRestorer(),
                horizontalArrangement = Arrangement.spacedBy(TvDimens.SpaceSm),
                contentPadding = PaddingValues(
                    start = overscanH(), end = overscanH(),
                    top = TvDimens.SpaceSm, bottom = TvDimens.SpaceXs
                )
            ) {
                itemsIndexed(channels, key = { _, ch -> ch.id }) { _, channel ->
                    RailCard(
                        channel = channel,
                        onClick = { onChannelClick(channelIndexMap[channel.id] ?: 0, launchGroup) }
                    )
                }
            }
        }
    }
}

/** Compact landscape card used by the pinned rails (logo left, name right). */
@Composable
private fun RailCard(
    channel: com.fenyx.jtv.data.Channel,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier.width(190.dp),
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = TvDimens.FocusedScale),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = TvDarkSurface,
            focusedContainerColor = TvDarkSurfaceVariant
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, TvFocusBorder),
                shape = RoundedCornerShape(10.dp)
            )
        )
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (channel.logoUrl.isNotEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(channel.logoUrl).size(96).build(),
                    contentDescription = channel.name,
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)).background(Color.White),
                    contentScale = ContentScale.Fit
                )
            } else {
                Box(
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)).background(TvDarkSurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PlayArrow, "Channel logo placeholder", tint = TvOnSurfaceVariant, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    channel.name,
                    color = TvOnSurface,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(TvLiveRed))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("LIVE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TvLiveRed, letterSpacing = 1.sp)
                }
            }
        }
    }
}
