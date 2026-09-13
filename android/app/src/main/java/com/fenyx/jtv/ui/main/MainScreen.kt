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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    // Two-press Back-to-exit guard. With a single-entry nav stack the first Back fell through to
    // the system and killed the app instantly — an accidental press (remote, gesture nav) lost the
    // app with no undo. Composed EARLY so deeper BackHandlers (EPG grid's back-to-NOW) win.
    var lastBackExitPressAt by remember { mutableStateOf(0L) }
    androidx.activity.compose.BackHandler {
        val now = System.currentTimeMillis()
        if (now - lastBackExitPressAt < 2000) {
            (context as? android.app.Activity)?.finishAffinity()
        } else {
            lastBackExitPressAt = now
            android.widget.Toast.makeText(
                context, "Press Back again to exit", android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
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
    val hiddenChannels by viewModel.hiddenChannels.collectAsState()
    // Last played channel id (persisted on every load) — Home restores scroll + focus to it
    // on entry so Back-from-player lands deterministically instead of losing focus.
    val lastPlayedId by settingsManager.lastChannelIdFlow.collectAsState(initial = null)
    val categoryCounts by viewModel.categoryCounts.collectAsState()
    val filteredChannels by viewModel.filteredChannels.collectAsState()
    // Favorites pinned row: same filtered list the grid shows (honors language filter + sort).
    val favoriteRow = remember(filteredChannels, favoriteChannels) {
        if (favoriteChannels.isEmpty()) emptyList() else filteredChannels.filter { it.id in favoriteChannels }.take(12)
    }
    // Long-press hide target: the tile that asked for the Hide confirm dialog (null = no dialog).
    var hideTarget by remember { mutableStateOf<com.fenyx.jtv.data.Channel?>(null) }
    // Post-hide focus restore: the id of the neighbour that should take D-pad focus after a
    // hide. Each tile/row/cell watches this and focuses ITSELF on match (see ChannelCard):
    // no shared requester map (stale entries sent focus to strange places), no sleeps — the
    // node exists by definition when its own effect runs. Auto-expires so a stale id can
    // never yank focus later.
    var pendingHideFocusId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pendingHideFocusId) {
        if (pendingHideFocusId != null) {
            kotlinx.coroutines.delay(2000)
            pendingHideFocusId = null
        }
    }
    // Hoisted grid state for the entry-time restore-to-last-played scroll (see below).
    val gridState = rememberLazyGridState()
    // Hoisted list states for the EPG rows / time-grid layouts: the restore effect must scroll
    // whichever layout is active — the grid state alone left both EPG modes restoring nothing
    // (the time-grid also used to create a private state, so its restore silently no-op'd).
    val epgRowsState = rememberLazyListState()
    val epgGridListState = rememberLazyListState()

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
    //
    // overscanH/V keep the top bar + chips inside the panel's safe area on TVs — the
    // root Column has no tvOverscan() because the grid's contentPadding already applies it.
    val isTouch = LocalIsTouch.current
    Column(modifier = modifier.fillMaxSize().background(TvDarkBackground)) {

        // ─── Top Bar ───
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = overscanH(),
                    end = overscanH(),
                    top = overscanV()
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
            // Loading feedback: the Refresh button STAYS MOUNTED while a refresh runs — the old
            // if/else swapped it for a bare spinner, which unmounted the focused node mid-press
            // and left the D-pad focusless until something was clicked/touched. The icon becomes
            // a spinner and the click is disabled instead.
            // FORCE semantics: this used to call retry(), which only re-read the (24h-fresh) disk
            // cache — the button did nothing network-y. forceRefreshChannels() bypasses the TTL.
            TopBarIconButton(
                icon = Icons.Default.Refresh,
                contentDescription = "Refresh",
                tint = TvOnSurfaceVariant,
                enabled = !(isLoading && channels.isNotEmpty()),
                busy = isLoading && channels.isNotEmpty(),
                onClick = { viewModel.forceRefreshChannels() }
            )
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
        val contentModifier = Modifier.weight(1f).fillMaxWidth()
        val contentBody: @Composable () -> Unit = {
            Box(modifier = Modifier.fillMaxSize()) {
            if (isLoading && channels.isEmpty()) {
                // Skeleton placeholders shaped like the real layout: the screen reads as "loading"
                // instantly instead of a bare spinner, and there is no layout jump when data lands.
                HomeSkeleton(epgRows = epgStyle != SettingsManager.EPG_STYLE_OFF)
            } else if (error != null) {
                // Auto-focus Retry: without a focused node the remote was dead on this screen —
                // Compose moves focus only among existing focus targets, and the entry-restore
                // effect that would grant focus is gated on a non-empty channel list.
                val retryFocus = remember { FocusRequester() }
                LaunchedEffect(error) { runCatching { retryFocus.requestFocus() } }
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
                        modifier = Modifier.focusRequester(retryFocus),
                        onClick = { viewModel.retry() },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = TvDimens.FocusedScale),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = TvPrimaryContainer,
                            focusedContainerColor = TvPrimary
                        )
                    ) {
                        Text(
                            "Retry",
                            modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp),
                            color = TvOnPrimary,
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

                // Initial entry: restore scroll + focus to the last played channel (the
                // standard return-to-origin: Back-from-player lands where you left). Falls back
                // to the first tile when nothing was played yet or it is no longer visible
                // (hidden/filtered out). Runs once per entry so later filter changes never yank
                // focus; the focused tile is also scrolled into view FIRST so the focus request
                // always hits a composed node instead of falling back to the top bar.
                val firstItemFocus = remember { FocusRequester() }
                var initialFocusDone by remember { mutableStateOf(false) }
                // gridState is hoisted to screen level (shared with the post-hide restore effect).
                val restoreIndex = remember(filteredChannels, lastPlayedId) {
                    if (lastPlayedId == null) 0
                    else filteredChannels.indexOfFirst { it.id == lastPlayedId }.coerceAtLeast(0)
                }
                // Gate on the saved id too: DataStore can resolve after the cached channel list,
                // and restoring with a still-null id would wrongly land on the first tile.
                var lastPlayedResolved by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    settingsManager.lastChannelIdFlow.first()
                    lastPlayedResolved = true
                }
                LaunchedEffect(filteredChannels.isNotEmpty(), lastPlayedResolved, epgStyle) {
                    if (!initialFocusDone && filteredChannels.isNotEmpty() && lastPlayedResolved) {
                        initialFocusDone = true
                        runCatching {
                            when (epgStyle) {
                                SettingsManager.EPG_STYLE_ROWS -> epgRowsState.scrollToItem(restoreIndex)
                                SettingsManager.EPG_STYLE_GRID -> epgGridListState.scrollToItem(restoreIndex)
                                else -> gridState.scrollToItem(restoreIndex)
                            }
                            kotlinx.coroutines.delay(50)
                            firstItemFocus.requestFocus()
                        }
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
                            enterMs = TvMotion.ms(),
                            onHideRequest = { hideTarget = it },
                            hideFocusTarget = pendingHideFocusId,
                            onHideFocusConsumed = { pendingHideFocusId = null }
                        )
                    }

                    if (filteredChannels.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            // Wording matches the input: touch users tap a button, remote users
                            // press OK on the player side panel.
                            val favHint = if (isTouch)
                                "No favorites yet\nOpen a channel and tap the ★ star button to add channels here"
                            else
                                "No favorites yet\nOpen a channel, open the player panel and select ★ Star"
                            val msg = when {
                                selectedGroup == MainViewModel.GROUP_FAVORITES && languageFilter.isEmpty() ->
                                    favHint
                                languageFilter.isNotEmpty() ->
                                    "No channels match the selected language${if (languageFilter.size == 1) "" else "s"}"
                                hiddenChannels.isNotEmpty() ->
                                    "No visible channels here\nHidden channels can be restored in Settings → Hide / Unhide Channels"
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
                            initialFocusIndex = restoreIndex,
                            listState = epgGridListState,
                            modifier = Modifier.fillMaxSize(),
                            onHideRequest = { hideTarget = it },
                            hideFocusTarget = pendingHideFocusId,
                            onHideFocusConsumed = { pendingHideFocusId = null }
                        )
                    } else if (epgStyle == SettingsManager.EPG_STYLE_ROWS) {
                        LazyColumn(
                            state = epgRowsState,
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
                                    modifier = (if (index == restoreIndex) Modifier.focusRequester(firstItemFocus) else Modifier),
                                    onHideRequest = { hideTarget = it },
                                    hideFocusTarget = pendingHideFocusId,
                                    onHideFocusConsumed = { pendingHideFocusId = null }
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
                            state = gridState,
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
                                    modifier = (if (index == restoreIndex) Modifier.focusRequester(firstItemFocus) else Modifier),
                                    onHideRequest = { hideTarget = it },
                                    hideFocusTarget = pendingHideFocusId,
                                    onHideFocusConsumed = { pendingHideFocusId = null }
                                )
                            }
                        }
                    }
                }
            }
            }
        }

        // Refresh lives in the top bar (force network reload) on every device — the pull-to-
        // refresh gesture was removed: it fought vertical grid scrolling on touch, and D-pad
        // users always had the button.
        Box(modifier = contentModifier) {
            contentBody()
        }
    }

    // Hide confirm: every hide entry point on this screen routes here (grid, rail, EPG).
    hideTarget?.let { target ->
        HideChannelConfirmDialog(
            channelName = target.name,
            onHide = {
                // Stash the neighbour BEFORE hiding so D-pad focus lands back in the grid
                // (next tile, or previous when hiding the last one) instead of the top bar.
                val ids = filteredChannels.map { it.id }
                val i = ids.indexOf(target.id)
                pendingHideFocusId = ids.getOrNull(i + 1) ?: ids.getOrNull((i - 1).coerceAtLeast(0))
                viewModel.toggleHiddenChannel(target.id)
                hideTarget = null
            },
            onDismiss = { hideTarget = null }
        )
    }
}

@Composable
fun ChannelCard(
    channel: com.fenyx.jtv.data.Channel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    // Positional zap number (1-based list index). Falls back to the channel's own number; hidden when 0.
    number: Int = 0,
    // When non-null, a touch long-press on the tile invokes this instead of opening the
    // channel (callers show the Hide confirm dialog). Null = plain tile, zero behavior change.
    onHideRequest: ((com.fenyx.jtv.data.Channel) -> Unit)? = null,
    // Post-hide restore: when this equals the channel id the tile focuses itself. The node
    // exists by definition when its own effect runs — no shared map, no timing guesses.
    hideFocusTarget: String? = null,
    onHideFocusConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    // Compact windows (portrait phones) get a smaller logo and tighter padding so the shrunken
    // tile doesn't look empty or crowd its two-line name against the LIVE badge.
    val compact = isCompactWidth()
    val logoSize = if (compact) 44.dp else 56.dp
    val tileNumber = if (number > 0) number else channel.channelNumber
    // Long-press-to-hide goes through the Surface's NATIVE onLongClick (single
    // scroll-aware detector inside tv-material): reliable for touch holds inside the
    // Lazy grid AND for remote long-OK. The previous outer pointerInput
    // detectTapGestures competed with the Surface's own clickable (nested detectors —
    // the inner one wins, so touch long-press never fired). MENU key is kept as an
    // extra for remotes that have it.
    val hidePress = rememberHideLongPress(channel, onHideRequest)
    // Post-hide self-focus (see [hideFocusTarget]): fires only for the stashed neighbour.
    val selfFocus = remember(channel.id) { FocusRequester() }
    LaunchedEffect(hideFocusTarget) {
        if (hideFocusTarget != null && channel.id == hideFocusTarget) {
            onHideFocusConsumed()
            runCatching { selfFocus.requestFocus() }
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .focusRequester(selfFocus)
            .then(hidePress.menuKeyModifier),
        onClick = onClick,
        onLongClick = if (onHideRequest != null) hidePress.fire else null,
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
                    // Decorative: the channel name Text below is already announced — a duplicate
                    // description made TalkBack read the name twice per tile.
                    contentDescription = null,
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
    modifier: Modifier = Modifier,
    // Long-press (touch) / long-OK (remote) asks for the Hide confirm dialog. Null = plain row.
    onHideRequest: ((com.fenyx.jtv.data.Channel) -> Unit)? = null,
    hideFocusTarget: String? = null,
    onHideFocusConsumed: () -> Unit = {}
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
    val hidePress = rememberHideLongPress(channel, onHideRequest)
    val selfFocus = remember(channel.id) { FocusRequester() }
    LaunchedEffect(hideFocusTarget) {
        if (hideFocusTarget != null && channel.id == hideFocusTarget) {
            onHideFocusConsumed()
            runCatching { selfFocus.requestFocus() }
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 100.dp)
            .focusRequester(selfFocus)
            .then(hidePress.menuKeyModifier),
        onClick = onClick,
        onLongClick = if (onHideRequest != null) hidePress.fire else null,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = TvDimens.FocusedScale),
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

/**
 * Shared long-press-to-hide wiring for every channel tile/row/cell. Single trigger:
 * the Surface / combinedClickable NATIVE onLongClick (one scroll-aware detector inside
 * tv-material / foundation). It fires for a touch hold AND for a remote long-OK hold,
 * and it suppresses the trailing onClick itself — no guards, no nested gesture
 * detectors (the old outer pointerInput competed with the Surface's own clickable and
 * touch long-press never fired inside lazy grids). [menuKeyModifier] is an extra for
 * remotes with a dedicated MENU key (fires for the focused tile only). Inert when
 * [onHideRequest] is null.
 */
class HideLongPress(
    val menuKeyModifier: Modifier,
    val fire: () -> Unit
)

@Composable
fun rememberHideLongPress(
    channel: com.fenyx.jtv.data.Channel,
    onHideRequest: ((com.fenyx.jtv.data.Channel) -> Unit)?
): HideLongPress {
    val haptic = LocalHapticFeedback.current
    fun fire() {
        // Touch confirmation buzz (no-op on TVs without a vibrator). Makes the hold feel
        // instant instead of "did it register?" while the dialog composes.
        runCatching { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
        onHideRequest?.invoke(channel)
    }
    // Remote MENU key on the focused tile/row/cell. Preview phase reaches only the focused
    // item's chain, so no focus tracking is needed — and OK is never touched here because
    // long-OK already arrives via the native onLongClick above.
    val keys = if (onHideRequest != null) {
        Modifier.onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown && event.key == Key.Menu) {
                fire()
                true
            } else false
        }
    } else Modifier
    return HideLongPress(keys, ::fire)
}

/**
 * Long-press confirm dialog: "Hide <name>?" with Hide + Cancel. Shared by Home and Search
 * (both use [ChannelCard]). Cancel holds initial focus so an accidental long-press —
 * e.g. by kids — never hides on a stray OK press; Hide needs a deliberate move.
 */
@Composable
fun HideChannelConfirmDialog(
    channelName: String,
    onHide: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 420.dp)
                .background(TvDarkSurface, RoundedCornerShape(16.dp))
                .padding(24.dp)
        ) {
            Text(
                "Hide \"$channelName\"?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TvOnBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "It will disappear from Home, search, EPG and zap lists. Restore anytime in Settings → Hide / Unhide Channels.",
                style = MaterialTheme.typography.bodySmall,
                color = TvOnSurfaceVariant
            )
            Spacer(modifier = Modifier.height(20.dp))
            val cancelFocus = remember { FocusRequester() }
            LaunchedEffect(Unit) { runCatching { cancelFocus.requestFocus() } }
            Row(
                modifier = Modifier.fillMaxWidth().focusGroup(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).focusRequester(cancelFocus),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = TvOnBackground,
                        focusedContainerColor = Color.White,
                        contentColor = TvDarkBackground,
                        focusedContentColor = TvDarkBackground
                    ),
                    border = ClickableSurfaceDefaults.border(
                        focusedBorder = androidx.tv.material3.Border(
                            border = androidx.compose.foundation.BorderStroke(3.dp, TvFocusBorder),
                            shape = RoundedCornerShape(10.dp)
                        )
                    )
                ) {
                    Text(
                        "Cancel",
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        color = TvDarkBackground,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                }
                Surface(
                    onClick = onHide,
                    modifier = Modifier.weight(1f),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = TvLiveRed.copy(alpha = 0.18f),
                        focusedContainerColor = TvLiveRed.copy(alpha = 0.35f)
                    ),
                    border = ClickableSurfaceDefaults.border(
                        focusedBorder = androidx.tv.material3.Border(
                            border = androidx.compose.foundation.BorderStroke(3.dp, TvFocusBorder),
                            shape = RoundedCornerShape(10.dp)
                        )
                    )
                ) {
                    Text(
                        "Hide",
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        color = TvLiveRed,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
/** Compact icon-only top-bar button (Search / Refresh / Settings). */
private fun TopBarIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
    // Renders a spinner instead of the icon (e.g. Refresh while a refresh runs) while keeping the
    // same node in composition so D-pad focus never drops.
    busy: Boolean = false
) {
    // Touch devices get a >=48dp hit area so the top-bar buttons are thumb-friendly on
    // phones; TVs keep the compact remote-driven size.
    val isTouch = LocalIsTouch.current
    val vpad = if (isTouch) 13.dp else 8.dp
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = if (isTouch) Modifier.heightIn(min = 48.dp) else Modifier,
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
        Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = vpad), contentAlignment = Alignment.Center) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = TvOnSurfaceVariant
                )
            } else {
                Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(22.dp))
            }
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
    val isTouch = LocalIsTouch.current
    val vpad = if (isTouch) 10.dp else 7.dp
    Surface(
        onClick = onClick,
        modifier = if (isTouch) Modifier.heightIn(min = 48.dp) else Modifier,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(20.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = TvDimens.FocusedScale),
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
        modifier = Modifier.fillMaxWidth().focusGroup().focusRestorer()
            // Mouse wheel reaches chips beyond the first viewport (mouse-primary devices had no
            // way to scroll this row — dozens of categories never fit on screen).
            .mouseWheelToHorizontal(listState),
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
    enterMs: Int,
    onHideRequest: ((com.fenyx.jtv.data.Channel) -> Unit)? = null,
    hideFocusTarget: String? = null,
    onHideFocusConsumed: () -> Unit = {}
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
            val railState = rememberLazyListState()
            LazyRow(
                state = railState,
                modifier = Modifier.focusGroup().focusRestorer()
                    // Mouse wheel support, same as the category chips row.
                    .mouseWheelToHorizontal(railState),
                horizontalArrangement = Arrangement.spacedBy(TvDimens.SpaceSm),
                contentPadding = PaddingValues(
                    start = overscanH(), end = overscanH(),
                    top = TvDimens.SpaceSm, bottom = TvDimens.SpaceXs
                )
            ) {
                itemsIndexed(channels, key = { _, ch -> ch.id }) { _, channel ->
                    RailCard(
                        channel = channel,
                        onClick = { onChannelClick(channelIndexMap[channel.id] ?: 0, launchGroup) },
                        onHideRequest = onHideRequest,
                        hideFocusTarget = hideFocusTarget,
                        onHideFocusConsumed = onHideFocusConsumed
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
    onClick: () -> Unit,
    onHideRequest: ((com.fenyx.jtv.data.Channel) -> Unit)? = null,
    hideFocusTarget: String? = null,
    onHideFocusConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val hidePress = rememberHideLongPress(channel, onHideRequest)
    val selfFocus = remember(channel.id) { FocusRequester() }
    LaunchedEffect(hideFocusTarget) {
        if (hideFocusTarget != null && channel.id == hideFocusTarget) {
            onHideFocusConsumed()
            runCatching { selfFocus.requestFocus() }
        }
    }
    // Compact windows shrink the fixed rail width (matches the tile/row adaptation above).
    val railCardWidth = if (isCompactWidth()) 160.dp else 190.dp
    Surface(
        modifier = Modifier
            .width(railCardWidth)
            .focusRequester(selfFocus)
            .then(hidePress.menuKeyModifier),
        onClick = onClick,
        onLongClick = if (onHideRequest != null) hidePress.fire else null,
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
                    // Decorative — the name Text next to it is announced already.
                    contentDescription = null,
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
