package com.fenyx.jtv.ui.settings

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.Text
import androidx.tv.material3.MaterialTheme
import com.fenyx.jtv.theme.Surface
import com.fenyx.jtv.theme.LocalIsTouch
import com.fenyx.jtv.ui.main.tvFastNavKeys
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusGroup
import com.fenyx.jtv.data.SettingsManager
import com.fenyx.jtv.theme.*
import kotlinx.coroutines.launch

import com.fenyx.jtv.data.EpgSyncStatus
import com.fenyx.jtv.ui.main.MainViewModel
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Check

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SettingsScreen(modifier: Modifier = Modifier, mainViewModel: MainViewModel) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val scope = rememberCoroutineScope()

    val language by settingsManager.defaultLanguageFlow.collectAsState(initial = "hi")
    val quality by settingsManager.defaultQualityFlow.collectAsState(initial = "auto")
    val hwDecoder by settingsManager.hardwareDecoderFlow.collectAsState(initial = true)
    val tunneling by settingsManager.tunnelingFlow.collectAsState(initial = false)
    val playbackBufferSec by settingsManager.playbackBufferSecFlow.collectAsState(initial = 60)
    val playerResizeMode by settingsManager.playerResizeModeFlow.collectAsState(initial = 0)
    val epgMode by settingsManager.epgModeFlow.collectAsState(initial = false)
    // Home EPG layout: off / rows / grid (replaces the old on-off "EPG Mode" toggle; legacy
    // epg_mode still migrates automatically inside SettingsManager when no style is stored).
    val epgStyle by settingsManager.epgStyleFlow.collectAsState(initial = SettingsManager.EPG_STYLE_OFF)
    val zapPreview by settingsManager.zapPreviewFlow.collectAsState(initial = false)
    val gridDensityDp by settingsManager.gridDensityDpFlow.collectAsState(initial = 150)
    val epgUrl by settingsManager.epgUrlFlow.collectAsState(initial = "https://avkb.short.gy/epg.xml.gz")
    val epgSyncStatus by mainViewModel.epgSyncStatus.collectAsState()
    val autoplayLastChannel by settingsManager.autoplayLastChannelFlow.collectAsState(initial = false)
    val groupLanguageVariants by settingsManager.groupLanguageVariantsFlow.collectAsState(initial = true)
    val sortAlphabetical by settingsManager.channelSortAlphabeticalFlow.collectAsState(initial = false)
    val setupMode by settingsManager.setupModeFlow.collectAsState(initial = null)
    val serverUrl by settingsManager.serverUrlFlow.collectAsState(initial = "")
    val serverRefreshing by mainViewModel.serverRefreshing.collectAsState()
    val serverRefreshMsg by mainViewModel.serverRefreshMsg.collectAsState()
    // Player touch dock: which on-screen buttons show over the video + the edge ▲▼ zap keys.
    val touchDockButtons by settingsManager.touchDockButtonsFlow.collectAsState(initial = SettingsManager.DOCK_BUTTONS_DEFAULT)
    val zapEdgeButtons by settingsManager.zapEdgeButtonsFlow.collectAsState(initial = true)
    // Dock layout: single group at a chosen bottom position, or nav-left/playback-right split.
    val dockSplit by settingsManager.touchDockSplitFlow.collectAsState(initial = false)
    val dockAlign by settingsManager.touchDockAlignFlow.collectAsState(initial = SettingsManager.DOCK_ALIGN_CENTER)

    // Channel-language filter (moved here from the Home screen): multi-select, applies to the
    // home grid AND the player's zap list everywhere via MainViewModel.
    val availableChannelLanguages by mainViewModel.availableLanguages.collectAsState()
    val channelLanguageFilter by mainViewModel.languageFilter.collectAsState()
    val allChannels by mainViewModel.channels.collectAsState()
    val hiddenChannels by mainViewModel.hiddenChannels.collectAsState()
    // Language-scoped channel list for the hide/unhide manager (hidden channels included, A–Z).
    val manageChannels by mainViewModel.manageChannels.collectAsState()
    val channelLanguageCounts = remember(allChannels) {
        allChannels.groupingBy { it.language }.eachCount()
    }

    var showLanguagePicker by remember { mutableStateOf(false) }
    var showChannelLangPicker by remember { mutableStateOf(false) }
    var showChannelManager by remember { mutableStateOf(false) }
    var showDockAlignPicker by remember { mutableStateOf(false) }
    var showQualityPicker by remember { mutableStateOf(false) }
    var showPlayerResizeModePicker by remember { mutableStateOf(false) }
    var showBufferPicker by remember { mutableStateOf(false) }
    var showEpgUrlDialog by remember { mutableStateOf(false) }
    var showEpgLayoutPicker by remember { mutableStateOf(false) }
    var showDensityPicker by remember { mutableStateOf(false) }
    // Destructive-action confirmations (Sign-in Method change / Logout).
    var showSignOutConfirm by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    val isTouch = LocalIsTouch.current
    // The on-screen player dock also serves mouse-capable non-touch devices (TV emulator,
    // air-mouse boxes), so its configuration section shows for both.
    val hasMouse = com.fenyx.jtv.theme.LocalHasMouse.current
    val pointerUi = isTouch || hasMouse

    // ─── Update check ───
    // Sideloaded APKs get no store update prompts, so Settings offers a manual check + a banner
    // if a newer release exists. Auto-checks once per 24h on screen entry (rate-limited via
    // lastUpdateCheckFlow). Failures (offline / API rate-limit) are silent — the menu item
    // remains a manual "Check for updates".
    val lastUpdateCheck by settingsManager.lastUpdateCheckFlow.collectAsState(initial = 0L)
    val localVersion = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: ""
    }
    var updateAvailable by remember { mutableStateOf<com.fenyx.jtv.data.UpdateChecker.Release?>(null) }
    var showingNoUpdate by remember { mutableStateOf(false) }
    var updateChecking by remember { mutableStateOf(false) }

    fun checkForUpdates(force: Boolean = false) {
        val now = System.currentTimeMillis()
        val due = force || (now - lastUpdateCheck) > 24L * 60 * 60 * 1000
        if (!due || updateChecking) return
        updateChecking = true
        showingNoUpdate = false
        scope.launch {
            val res = com.fenyx.jtv.data.UpdateChecker.latestRelease()
            if (res.isSuccess) {
                val rel = res.getOrThrow()
                if (com.fenyx.jtv.data.UpdateChecker.isNewer(rel.version, localVersion)) {
                    updateAvailable = rel
                } else {
                    updateAvailable = null
                    showingNoUpdate = true
                }
                settingsManager.setLastUpdateCheck(now)
            }
            updateChecking = false
        }
    }
    LaunchedEffect(Unit) { checkForUpdates() }

    val densityOptions = listOf(
        0 to "Auto (screen)",
        120 to "Compact (more tiles)",
        130 to "Compact+",
        150 to "Comfortable",
        175 to "Large (fewer tiles)"
    )

    val epgLayoutOptions = listOf(
        SettingsManager.EPG_STYLE_OFF to "Off (channel grid only)",
        SettingsManager.EPG_STYLE_ROWS to "Rows (now + next)",
        SettingsManager.EPG_STYLE_GRID to "Time grid (channels × hours)"
    )

    // Initial focus so the first D-pad press works on entry (previously nothing was focused).
    val firstItemFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstItemFocus.requestFocus() } }

    val bufferOptions = listOf(
        30 to "Data Saver (30s)",
        60 to "Balanced (60s)",
        90 to "Smooth (90s)",
        120 to "Max (120s)"
    )

    // Per-button toggle for the player's touch dock (Settings → Player Touch Dock).
    @Composable
    fun DockToggle(id: String, title: String, subtitle: String) {
        SettingsToggle(
            title = title,
            subtitle = subtitle,
            isEnabled = id in touchDockButtons,
            onClick = {
                scope.launch {
                    val next = touchDockButtons.toMutableSet()
                    if (id in next) next.remove(id) else next.add(id)
                    settingsManager.setTouchDockButtons(next)
                }
            }
        )
    }

    val languages = listOf(
        "hi" to "Hindi", "en" to "English", "ta" to "Tamil", "te" to "Telugu",
        "kn" to "Kannada", "ml" to "Malayalam", "bn" to "Bengali", "mr" to "Marathi",
        "gu" to "Gujarati", "pa" to "Punjabi", "or" to "Odia", "as" to "Assamese"
    )

    val qualities = listOf(
        "auto" to "Auto", "high" to "High (1080p)", "medium" to "Medium (720p)", "low" to "Low (480p)"
    )

    val resizeModes = listOf(
        0 to "Fit (Default)", 
        3 to "Fill (Crop)", 
        4 to "Zoom", 
        1 to "Stretch Width", 
        2 to "Stretch Height"
    )

    // ─── Root Box ───
    // No root Back interception: every picker is a androidx Dialog (a separate window that
    // receives and dismisses Back itself via onDismissRequest), so the old root handler never
    // fired — it was dead code that silently missed whichever dialog got added next.
    Box(modifier = modifier.fillMaxSize()) {
        // ─── Main Settings Layout ───
        // Two-pane on TV/tablet widths (title rail + list). On narrow phone windows the fixed
        // 280dp title rail would leave ~100dp for the actual settings, so it collapses into a
        // slim top header and the list takes the full width.
        val compactWindow = isCompactWidth()
        Column(modifier = Modifier.fillMaxSize().background(TvDarkBackground)) {
            if (compactWindow) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TvDarkSurface)
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    Text(
                        "Settings",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = TvOnBackground
                    )
                    Text(
                        "Configure your JTV experience",
                        style = MaterialTheme.typography.bodySmall,
                        color = TvOnSurfaceVariant
                    )
                }
            }
            // Weight(1f): fills only the space under the optional compact header instead of
            // overflowing past it (fillMaxSize inside a Column ignores earlier siblings).
            Row(modifier = Modifier.weight(1f).fillMaxWidth().background(TvDarkBackground)) {
            // Left: Title panel (wide screens only — see above)
            if (!compactWindow) Column(
                modifier = Modifier
                    .width(280.dp)
                    .fillMaxHeight()
                    .background(TvDarkSurface)
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "Settings",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = TvOnBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Configure your JTV experience",
                    style = MaterialTheme.typography.titleMedium,
                    color = TvOnSurfaceVariant
                )
            }

            // Right: Settings items
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .focusRestorer()
                    // Overscan-safe margins so settings rows never sit under the panel bezel;
                    // resolves per device (compact margins on touch devices).
                    .padding(
                        start = TvDimens.SpaceLg, end = overscanH(),
                        top = overscanV(), bottom = overscanV()
                    ),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Update banner — appears after a check finds a newer release.
                if (updateAvailable != null) {
                    item {
                        val rel = updateAvailable!!
                        val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                        Surface(
                            onClick = {
                                runCatching { uriHandler.openUri(rel.url) }
                            },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = TvPrimaryContainer.copy(alpha = 0.35f),
                                focusedContainerColor = TvPrimaryContainer
                            ),
                            border = ClickableSurfaceDefaults.border(
                                focusedBorder = androidx.tv.material3.Border(
                                    border = androidx.compose.foundation.BorderStroke(1.5.dp, TvPrimary),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("⬇️", style = MaterialTheme.typography.titleLarge)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Update available: v${rel.version}",
                                        color = TvPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        // Mouse-capable non-touch devices (PC emulators, air-mouse
                                        // boxes) get "Click" — "Press OK" named a key they don't have.
                                        when {
                                            isTouch -> "Tap to open the download page"
                                            hasMouse -> "Click to open the download page"
                                            else -> "Press OK to open the download page"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TvOnSurfaceVariant
                                    )
                                }
                                Text("Open", color = TvPrimary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                item { SectionHeader("Account") }

                item {
                    SettingsItem(
                        modifier = Modifier.focusRequester(firstItemFocus),
                        title = "Sign-in Method",
                        subtitle = when (setupMode) {
                            "server" -> "Self-hosted server: ${serverUrl.ifEmpty { "(not set)" }}"
                            "jtv" -> "JTV Server (access code)"
                            else -> "Phone (OTP) on this device"
                        },
                        value = "Change",
                        valueColor = TvPrimary,
                        // Confirmation required: one stray OK/tap used to wipe credentials and
                        // dump the user to first-boot setup with no way back but re-login.
                        onClick = { showSignOutConfirm = true }
                    )
                }

                if (setupMode == "server" || setupMode == "jtv") {
                    item {
                        SettingsItem(
                            title = "Refresh from Server",
                            subtitle = "Pull the latest login + channel list from the server now",
                            value = if (serverRefreshing) "Refreshing…" else (serverRefreshMsg ?: "Refresh"),
                            valueColor = TvPrimary,
                            onClick = { mainViewModel.refreshFromServer() }
                        )
                    }
                }

                item {
                    SettingsItem(
                        title = "Logout from JTV",
                        subtitle = "Clear your credentials and exit",
                        icon = Icons.AutoMirrored.Filled.ExitToApp,
                        valueColor = TvError,
                        onClick = { showLogoutConfirm = true }
                    )
                }

                item { SectionHeader("Channels") }

                item {
                    SettingsItem(
                        title = "Channel Languages",
                        subtitle = if (channelLanguageFilter.isEmpty()) {
                            "All languages are shown"
                        } else {
                            "Showing only: ${channelLanguageFilter.sorted().joinToString(", ")}"
                        },
                        value = if (channelLanguageFilter.isEmpty()) "All" else "${channelLanguageFilter.size} selected",
                        valueColor = TvPrimary,
                        onClick = { showChannelLangPicker = true }
                    )
                }

                item {
                    SettingsToggle(
                        title = "Group Language Variants",
                        subtitle = "Show one tile per channel and pick the language in the player (e.g. Star Sports Hindi/Tamil/Telugu). Turn off to see every language as its own channel.",
                        isEnabled = groupLanguageVariants,
                        onClick = { scope.launch { settingsManager.setGroupLanguageVariants(!groupLanguageVariants) } }
                    )
                }

                item {
                    SettingsToggle(
                        title = "Sort Channels A–Z",
                        subtitle = "Order channel lists alphabetically instead of by channel number (home grid and player)",
                        isEnabled = sortAlphabetical,
                        onClick = { scope.launch { settingsManager.setChannelSortAlphabetical(!sortAlphabetical) } }
                    )
                }

                item {
                    SettingsItem(
                        title = "Hide / Unhide Channels",
                        subtitle = if (channelLanguageFilter.isEmpty()) {
                            "Manage every channel right here — hide or unhide without leaving Settings"
                        } else {
                            val langLabel = "${channelLanguageFilter.size} selected language" +
                                if (channelLanguageFilter.size == 1) "" else "s"
                            "Manage $langLabel right here — hide or unhide without leaving Settings"
                        },
                        value = if (hiddenChannels.isEmpty()) "Manage" else "${hiddenChannels.size} hidden",
                        valueColor = TvPrimary,
                        onClick = { showChannelManager = true }
                    )
                }

                item {
                    SettingsItem(
                        title = "Refresh Channel List",
                        subtitle = "Pull the latest channel list from Jio now (new channels, corrected languages)",
                        value = if (serverRefreshing) "Refreshing…" else (serverRefreshMsg ?: "Refresh"),
                        valueColor = TvPrimary,
                        onClick = { mainViewModel.forceRefreshChannels() }
                    )
                }

                item {
                    SettingsItem(
                        title = "Grid Tile Size",
                        subtitle = "Home screen channel tile size",
                        value = densityOptions.find { it.first == gridDensityDp }?.second ?: "Comfortable",
                        valueColor = TvPrimary,
                        onClick = { showDensityPicker = true }
                    )
                }

                item { SectionHeader("EPG (Electronic Program Guide)") }

                item {
                    SettingsItem(
                        title = "EPG Layout",
                        subtitle = "How the home screen shows programme info. The time grid fetches guide data lazily per visible channel.",
                        value = epgLayoutOptions.find { it.first == epgStyle }?.second
                            ?: if (epgMode) "Rows" else "Off",
                        valueColor = TvPrimary,
                        onClick = { showEpgLayoutPicker = true }
                    )
                }

                item {
                    SettingsItem(
                        title = "EPG Source URL",
                        subtitle = epgUrl,
                        value = "Edit",
                        valueColor = TvPrimary,
                        onClick = { showEpgUrlDialog = true }
                    )
                }

                item {
                    // Disabled while a sync runs: the row kept full focus/press treatment but the
                    // click silently no-op'd — the disabled visual now matches the behaviour.
                    SettingsItem(
                        title = "Refresh EPG Data",
                        subtitle = "Force download and parse the latest EPG",
                        value = when (epgSyncStatus) {
                            EpgSyncStatus.IDLE -> "Sync Now"
                            EpgSyncStatus.DOWNLOADING -> "Downloading..."
                            EpgSyncStatus.EXTRACTING -> "Extracting..."
                            EpgSyncStatus.PARSING -> "Parsing..."
                            EpgSyncStatus.COMPLETED -> "Done"
                            EpgSyncStatus.ERROR -> "Error"
                        },
                        valueColor = when (epgSyncStatus) {
                            EpgSyncStatus.ERROR -> TvError
                            EpgSyncStatus.COMPLETED -> TvOnlineGreen
                            EpgSyncStatus.IDLE -> TvPrimary
                            else -> TvOnSurfaceVariant
                        },
                        clickable = epgSyncStatus == EpgSyncStatus.IDLE ||
                            epgSyncStatus == EpgSyncStatus.COMPLETED ||
                            epgSyncStatus == EpgSyncStatus.ERROR,
                        onClick = {
                            if (epgSyncStatus == EpgSyncStatus.IDLE || epgSyncStatus == EpgSyncStatus.COMPLETED || epgSyncStatus == EpgSyncStatus.ERROR) {
                                mainViewModel.fetchEpg(forceRefresh = true)
                            }
                        }
                    )
                }

                item { SectionHeader("Playback") }

                item {
                    SettingsToggle(
                        title = "Autoplay Last Channel",
                        subtitle = "Automatically resume your last watched channel when app opens",
                        isEnabled = autoplayLastChannel,
                        onClick = { scope.launch { settingsManager.setAutoplayLastChannel(!autoplayLastChannel) } }
                    )
                }

                item {
                    SettingsToggle(
                        title = "Zap Preview",
                        subtitle = "D-pad ↑/↓ opens a channel preview strip (OK to tune) instead of zapping instantly. Off = classic instant zap.",
                        isEnabled = zapPreview,
                        onClick = { scope.launch { settingsManager.setZapPreview(!zapPreview) } }
                    )
                }

                item {
                    SettingsItem(
                        title = "Default Quality",
                        subtitle = "Video quality for all channels",
                        value = qualities.find { it.first == quality }?.second ?: "Auto",
                        valueColor = TvPrimary,
                        onClick = { showQualityPicker = true }
                    )
                }

                item {
                    SettingsItem(
                        title = "Playback Buffer",
                        subtitle = "Higher = fewer interruptions, smoother on weak networks (uses more memory)",
                        value = bufferOptions.find { it.first == playbackBufferSec }?.second ?: "${playbackBufferSec}s",
                        valueColor = TvPrimary,
                        onClick = { showBufferPicker = true }
                    )
                }

                item {
                    SettingsItem(
                        title = "Player View Mode",
                        subtitle = "Default video scaling (Fit, Fill, Zoom...)",
                        value = resizeModes.find { it.first == playerResizeMode }?.second ?: "Fit",
                        valueColor = TvPrimary,
                        onClick = { showPlayerResizeModePicker = true }
                    )
                }

                item {
                    SettingsItem(
                        title = "Preferred Audio Language",
                        subtitle = "Audio track auto-selected on multi-audio channels (playback only — use Channel Languages to filter the channel lists)",
                        value = languages.find { it.first == language }?.second ?: language,
                        valueColor = TvPrimary,
                        onClick = { showLanguagePicker = true }
                    )
                }

                item {
                    SettingsToggle(
                        title = "Hardware Decoder",
                        subtitle = "Recommended ON for low-end TVs. Off allows software fallback. Applies on next channel open.",
                        isEnabled = hwDecoder,
                        onClick = { scope.launch { settingsManager.setHardwareDecoder(!hwDecoder) } }
                    )
                }

                item {
                    SettingsToggle(
                        title = "Tunneling (A/V sync)",
                        subtitle = "Keep OFF if video randomly freezes/black-screens. Only enable for Amlogic audio-sync issues. Applies on next channel open.",
                        isEnabled = tunneling,
                        onClick = { scope.launch { settingsManager.setTunneling(!tunneling) } }
                    )
                }



                // Pointer-device section (touch AND mouse-capable devices): the dock never renders
                // on remote-only TVs, so a D-pad user had 10 dead rows to scroll past here.
                if (pointerUi) {
                item { SectionHeader("On-Screen Player Keys") }

                item {
                    Text(
                        "Choose which control buttons appear over the video on touch/mouse devices, and how they are laid out. Off-screen actions stay reachable from the panels.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TvOnSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                    )
                }

                item {
                    // LAYOUT: one group at a chosen bottom position, or two anchored groups.
                    SettingsToggle(
                        title = "Split Dock Into Two Groups",
                        subtitle = "Navigation keys (channels, programmes, number) anchor bottom-left; playback keys (pause, aspect, rotate, settings…) anchor bottom-right — thumb-reachable in one hand each. Each group wraps inside its own half, so they never collide.",
                        isEnabled = dockSplit,
                        onClick = { scope.launch { settingsManager.setTouchDockSplit(!dockSplit) } }
                    )
                }

                item {
                    // Only meaningful in the single-group layout; in split mode each group owns
                    // its edge, so the row disables itself instead of pretending to work.
                    SettingsItem(
                        title = "Dock Position",
                        subtitle = if (dockSplit) "Split layout anchors the groups to both corners — turn off Split to choose a position"
                        else "Where the on-screen keys sit at the bottom",
                        value = when (dockAlign) {
                            SettingsManager.DOCK_ALIGN_LEFT -> "Bottom left"
                            SettingsManager.DOCK_ALIGN_RIGHT -> "Bottom right"
                            else -> "Bottom center"
                        },
                        valueColor = TvPrimary,
                        clickable = !dockSplit,
                        onClick = { showDockAlignPicker = true }
                    )
                }

                item { DockToggle(SettingsManager.DOCK_CHANNELS, "Channels ☰", "Open the channel list panel") }
                item { DockToggle(SettingsManager.DOCK_PROGRAMMES, "Programmes 📅", "EPG schedule for the current channel") }
                item { DockToggle(SettingsManager.DOCK_NUMPAD, "Channel number #", "On-screen number pad for direct channel entry") }
                item { DockToggle(SettingsManager.DOCK_ASPECT, "Aspect ratio ⛶", "Cycle video scaling modes") }
                item { DockToggle(SettingsManager.DOCK_ROTATE, "Rotate ⟳", "Toggle portrait / landscape") }
                item { DockToggle(SettingsManager.DOCK_PIP, "Picture-in-picture ⧉", "Minimise the player to a floating window") }
                item { DockToggle(SettingsManager.DOCK_PAUSE, "Play / Pause ⏸", "Pause or resume playback") }
                item { DockToggle(SettingsManager.DOCK_STATS, "Stream info 📊", "Bitrate / decoder / buffer diagnostics") }
                item { DockToggle(SettingsManager.DOCK_SETTINGS, "Player settings ⚙", "Quick settings panel") }

                item {
                    SettingsToggle(
                        title = "Edge Zap Buttons ▲▼",
                        subtitle = "Floating next/previous buttons on the right edge of the video",
                        isEnabled = zapEdgeButtons,
                        onClick = { scope.launch { settingsManager.setZapEdgeButtons(!zapEdgeButtons) } }
                    )
                }
                }

                item { SectionHeader("About") }

                item {
                    SettingsItem(
                        title = "About",
                        subtitle = "JTV",
                        value = if (localVersion.isNotEmpty()) "v$localVersion" else "",
                        valueColor = TvOnSurfaceVariant,
                        clickable = false,
                        onClick = { }
                    )
                }

                item {
                    SettingsItem(
                        title = if (updateChecking) "Checking for updates…" else "Check for updates",
                        subtitle = when {
                            updateChecking -> "Querying the GitHub releases API…"
                            showingNoUpdate -> "You're on the latest version."
                            else -> when {
                                isTouch -> "Tap to check for a newer release."
                                hasMouse -> "Click to check for a newer release."
                                else -> "Press OK to check for a newer release."
                            }
                        },
                        value = if (updateChecking) "…" else "↻",
                        valueColor = TvPrimary,
                        onClick = { checkForUpdates(true) }
                    )
                }

                // Whole-point feedback if a manual check found nothing — clears the banner condition and
                // shows the "up to date" row text via showingNoUpdate (set above).

            }
        }
        }

        // ─── Dialogs ───
        if (showChannelLangPicker) {
            ChannelLanguagesDialog(
                available = availableChannelLanguages,
                selected = channelLanguageFilter,
                counts = channelLanguageCounts,
                onToggle = { mainViewModel.toggleLanguageFilter(it) },
                onClear = { mainViewModel.setLanguageFilter(emptySet()) },
                onDismiss = { showChannelLangPicker = false }
            )
        }

        if (showChannelManager) {
            ChannelManagerDialog(
                channels = manageChannels,
                allChannels = allChannels,
                hidden = hiddenChannels,
                languageSummary = if (channelLanguageFilter.isEmpty()) "All languages"
                else channelLanguageFilter.sorted().joinToString(", "),
                onToggle = { mainViewModel.toggleHiddenChannel(it) },
                onShowAll = { mainViewModel.clearHiddenChannels() },
                onDismiss = { showChannelManager = false }
            )
        }

        if (showLanguagePicker) {
            Dialog(onDismissRequest = { showLanguagePicker = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                PickerDialog(
                    title = "Select Language",
                    options = languages,
                    currentValue = language,
                    onSelect = { value ->
                        scope.launch { settingsManager.setDefaultLanguage(value) }
                        showLanguagePicker = false
                    },
                    onDismiss = { showLanguagePicker = false }
                )
            }
        }

        if (showQualityPicker) {
            Dialog(onDismissRequest = { showQualityPicker = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                PickerDialog(
                    title = "Select Quality",
                    options = qualities,
                    currentValue = quality,
                    onSelect = { value ->
                        scope.launch { settingsManager.setDefaultQuality(value) }
                        showQualityPicker = false
                    },
                    onDismiss = { showQualityPicker = false }
                )
            }
        }

        if (showPlayerResizeModePicker) {
            Dialog(onDismissRequest = { showPlayerResizeModePicker = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                PickerDialog(
                    title = "Player View Mode",
                    options = resizeModes.map { it.first.toString() to it.second },
                    currentValue = playerResizeMode.toString(),
                    onSelect = { value ->
                        scope.launch { settingsManager.setPlayerResizeMode(value.toInt()) }
                        showPlayerResizeModePicker = false
                    },
                    onDismiss = { showPlayerResizeModePicker = false }
                )
            }
        }

        if (showEpgLayoutPicker) {
            Dialog(onDismissRequest = { showEpgLayoutPicker = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                PickerDialog(
                    title = "EPG Layout",
                    options = epgLayoutOptions,
                    currentValue = epgStyle,
                    onSelect = { value ->
                        scope.launch { settingsManager.setEpgStyle(value) }
                        showEpgLayoutPicker = false
                    },
                    onDismiss = { showEpgLayoutPicker = false }
                )
            }
        }

        if (showDensityPicker) {
            Dialog(onDismissRequest = { showDensityPicker = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                PickerDialog(
                    title = "Grid Tile Size",
                    options = densityOptions.map { it.first.toString() to it.second },
                    currentValue = gridDensityDp.toString(),
                    onSelect = { value ->
                        scope.launch { settingsManager.setGridDensityDp(value.toInt()) }
                        showDensityPicker = false
                    },
                    onDismiss = { showDensityPicker = false }
                )
            }
        }

        if (showDockAlignPicker) {
            Dialog(onDismissRequest = { showDockAlignPicker = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                PickerDialog(
                    title = "Dock Position",
                    options = listOf(
                        SettingsManager.DOCK_ALIGN_CENTER.toString() to "Bottom center",
                        SettingsManager.DOCK_ALIGN_LEFT.toString() to "Bottom left",
                        SettingsManager.DOCK_ALIGN_RIGHT.toString() to "Bottom right"
                    ),
                    currentValue = dockAlign.toString(),
                    onSelect = { value ->
                        scope.launch { settingsManager.setTouchDockAlign(value.toInt()) }
                        showDockAlignPicker = false
                    },
                    onDismiss = { showDockAlignPicker = false }
                )
            }
        }

        if (showBufferPicker) {
            Dialog(onDismissRequest = { showBufferPicker = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                PickerDialog(
                    title = "Playback Buffer",
                    options = bufferOptions.map { it.first.toString() to it.second },
                    currentValue = playbackBufferSec.toString(),
                    onSelect = { value ->
                        scope.launch { settingsManager.setPlaybackBufferSec(value.toInt()) }
                        showBufferPicker = false
                    },
                    onDismiss = { showBufferPicker = false }
                )
            }
        }

        if (showEpgUrlDialog) {
            Dialog(onDismissRequest = { showEpgUrlDialog = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                var tempUrl by remember { mutableStateOf(epgUrl) }
                var urlFieldFocused by remember { mutableStateOf(false) }
                // Empty / non-http(s) URLs were accepted silently and EPG then just showed nothing.
                val urlInvalid = tempUrl.isNotBlank() &&
                    !tempUrl.trim().startsWith("http://", ignoreCase = true) &&
                    !tempUrl.trim().startsWith("https://", ignoreCase = true)
                val urlFieldFocus = remember { FocusRequester() }
                val epgKeyboard = LocalSoftwareKeyboardController.current
                LaunchedEffect(Unit) {
                    runCatching { urlFieldFocus.requestFocus() }
                    kotlinx.coroutines.delay(50)
                    epgKeyboard?.show() // TV: focus alone doesn't open the on-screen keyboard
                }
                Box(
                    modifier = Modifier.fillMaxSize().background(TvDarkBackground.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        // Never wider than the design width, but shrink to the window on phones
                        // (fixed 500dp overflowed narrow portrait screens).
                        modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 500.dp)
                            .background(TvDarkSurface, RoundedCornerShape(16.dp)).padding(24.dp)
                            // imePadding: on phones / short landscape panels the IME covered the
                            // centered Cancel/Save row (same bug LoginScreen fixed).
                            .imePadding(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Edit EPG URL", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TvOnBackground)
                        Spacer(modifier = Modifier.height(24.dp))
                        Box(
                            modifier = Modifier.fillMaxWidth().height(56.dp)
                                .background(TvDarkSurfaceVariant, RoundedCornerShape(8.dp))
                                .border(
                                    1.5.dp,
                                    when {
                                        urlInvalid -> TvError
                                        urlFieldFocused -> TvFocusBorder
                                        else -> Color.Transparent
                                    },
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            BasicTextField(
                                value = tempUrl,
                                onValueChange = { tempUrl = it },
                                modifier = Modifier.fillMaxWidth().focusRequester(urlFieldFocus)
                                    .onFocusChanged { urlFieldFocused = it.isFocused },
                                textStyle = androidx.compose.ui.text.TextStyle(color = TvOnSurface, fontSize = 16.sp),
                                cursorBrush = SolidColor(TvPrimary),
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    imeAction = androidx.compose.ui.text.input.ImeAction.Done
                                ),
                                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                    onDone = {
                                        if (!urlInvalid && tempUrl.isNotBlank()) {
                                            scope.launch { settingsManager.setEpgUrl(tempUrl.trim()) }
                                            showEpgUrlDialog = false
                                        }
                                    }
                                )
                            )
                        }
                        if (urlInvalid) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "URL must start with http:// or https://",
                                color = TvError,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Surface(
                                onClick = { showEpgUrlDialog = false },
                                modifier = Modifier.heightIn(min = 44.dp),
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                                colors = ClickableSurfaceDefaults.colors(containerColor = TvDarkSurfaceVariant, focusedContainerColor = TvDarkSurface),
                                border = ClickableSurfaceDefaults.border(
                                    focusedBorder = androidx.tv.material3.Border(
                                        border = androidx.compose.foundation.BorderStroke(2.dp, TvFocusBorder),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                )
                            ) { Text("Cancel", modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp), color = TvOnSurface) }
                            Spacer(modifier = Modifier.width(16.dp))
                            Surface(
                                onClick = {
                                    if (!urlInvalid && tempUrl.isNotBlank()) {
                                        scope.launch { settingsManager.setEpgUrl(tempUrl.trim()) }
                                        showEpgUrlDialog = false
                                    }
                                },
                                modifier = Modifier.heightIn(min = 44.dp),
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = if (urlInvalid || tempUrl.isBlank()) TvDarkSurfaceVariant else TvPrimaryContainer,
                                    focusedContainerColor = if (urlInvalid || tempUrl.isBlank()) TvDarkSurfaceVariant else TvPrimary
                                ),
                                border = ClickableSurfaceDefaults.border(
                                    focusedBorder = androidx.tv.material3.Border(
                                        border = androidx.compose.foundation.BorderStroke(2.dp, TvFocusBorder),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                )
                            ) {
                                Text(
                                    "Save",
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                                    color = if (urlInvalid || tempUrl.isBlank()) TvOnSurfaceVariant else TvOnPrimary
                                )
                            }
                        }
                    }
                }
            }
        }

        // ─── Destructive-action confirmations ───
        if (showSignOutConfirm) {
            ConfirmDialog(
                title = "Change sign-in method?",
                body = "This signs you out. You'll need to log in again on the next screen.",
                confirmLabel = "Sign out",
                onConfirm = {
                    scope.launch {
                        settingsManager.setSetupMode(null)
                        settingsManager.clearAuthData()
                    }
                    showSignOutConfirm = false
                },
                onDismiss = { showSignOutConfirm = false }
            )
        }
        if (showLogoutConfirm) {
            ConfirmDialog(
                title = "Logout from JTV?",
                body = "Your saved credentials will be cleared and the app will return to the login screen.",
                confirmLabel = "Logout",
                onConfirm = {
                    scope.launch { settingsManager.clearAuthData() }
                    showLogoutConfirm = false
                },
                onDismiss = { showLogoutConfirm = false }
            )
        }
    }
}

/**
 * Shared destructive-action confirmation: Cancel holds initial focus so a stray OK/Enter can
 * never confirm, mirroring the hide-channel dialog convention.
 */
@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
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
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TvOnBackground)
            Spacer(modifier = Modifier.height(8.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = TvOnSurfaceVariant)
            Spacer(modifier = Modifier.height(20.dp))
            val cancelFocus = remember { FocusRequester() }
            LaunchedEffect(Unit) { runCatching { cancelFocus.requestFocus() } }
            Row(modifier = Modifier.fillMaxWidth().focusGroup(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).focusRequester(cancelFocus),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
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
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = TvError.copy(alpha = 0.18f),
                        focusedContainerColor = TvError.copy(alpha = 0.35f)
                    ),
                    border = ClickableSurfaceDefaults.border(
                        focusedBorder = androidx.tv.material3.Border(
                            border = androidx.compose.foundation.BorderStroke(3.dp, TvFocusBorder),
                            shape = RoundedCornerShape(10.dp)
                        )
                    )
                ) {
                    Text(
                        confirmLabel,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        color = TvError,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * Multi-select picker for the global channel-language filter (none selected = all languages).
 * Selection persists and applies to every channel list: home grid, EPG view and the player's
 * channel-switching list.
 */
@Composable
private fun ChannelLanguagesDialog(
    available: List<String>,
    selected: Set<String>,
    counts: Map<String, Int>,
    onToggle: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Entry focus on the first row: every other dialog anchors focus, these two didn't — the
        // first OK press could land on whatever Compose happened to focus and toggle an
        // unintended language / unhide a channel.
        val firstRowFocus = remember { androidx.compose.ui.focus.FocusRequester() }
        LaunchedEffect(Unit) { runCatching { firstRowFocus.requestFocus() } }
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 480.dp)
                .background(TvDarkSurface, RoundedCornerShape(16.dp))
                .padding(24.dp)
        ) {
            Text(
                "Channel Languages",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TvOnBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Show channels only in the selected languages — applies everywhere",
                style = MaterialTheme.typography.bodySmall,
                color = TvOnSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                // weight(fill=false) so the LIST yields space to the Done button on short TV panels
                // (1080p TVs expose ~540dp of height; a fixed 380dp list left the button crushed
                // flat with its letters unrenderable). Non-weighted children measure first, so the
                // button always keeps its intrinsic height and the list scrolls instead.
                modifier = Modifier.weight(1f, fill = false).heightIn(max = 420.dp)
            ) {
                item {
                    LanguageToggleRow(
                        label = "All Languages",
                        selected = selected.isEmpty(),
                        count = null,
                        onClick = onClear,
                        modifier = Modifier.focusRequester(firstRowFocus)
                    )
                }
                items(available, key = { it }) { lang ->
                    LanguageToggleRow(
                        label = lang,
                        selected = lang in selected,
                        count = counts[lang],
                        onClick = { onToggle(lang) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            // Light pill with DARK letters + purple focus ring. This is the only bright-fill button
            // in the app: white-on-purple text washed out to invisible on real TV panels (limited
            // RGB range / dynamic contrast) while rendering fine on emulators — dark-on-light
            // survives any TV picture processing.
            Surface(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
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
                    "Done",
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    color = TvDarkBackground,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Settings "Hide / Unhide Channels" manager — the single hidden-channels UI (it replaced the old
 * unhide-only dialog). Lists every channel in the current language scope with a one-OK toggle per
 * row; the Hidden view additionally resolves hidden channels from the FULL list, so a channel
 * hidden before a language filter was set can always be found and unhidden. Hiding removes a
 * channel from the grid, favourites, search, EPG and zap lists everywhere (one shared persisted
 * set). D-pad: rows + All/Hidden/Visible chips + CH±/PgUp-Dn page jumps; mouse: hover-to-focus,
 * click, native wheel; touch: ≥48dp rows.
 */
@Composable
private fun ChannelManagerDialog(
    channels: List<com.fenyx.jtv.data.Channel>,
    allChannels: List<com.fenyx.jtv.data.Channel>,
    hidden: Set<String>,
    languageSummary: String,
    onToggle: (String) -> Unit,
    onShowAll: () -> Unit,
    onDismiss: () -> Unit
) {
    val isTouch = LocalIsTouch.current
    val focusManager = LocalFocusManager.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Re-anchors focus on view switches and row removals (an unhide in the Hidden view / a
        // hide in the Visible view removes the focused row — without this the dialog went
        // focusless and the remote died). Keyed on the list SIZE too: an in-place Hide↔Unhide
        // toggle in the All view changes no sizes, so focus is never yanked mid-browse.
        val firstRowFocus = remember { FocusRequester() }
        var view by remember { mutableStateOf(0) } // 0 = All · 1 = Hidden · 2 = Visible
        // The Hidden view ignores the language scope: every hidden channel, from the full list.
        val hiddenEverywhere = remember(allChannels, hidden) {
            allChannels.filter { it.id in hidden }.sortedBy { it.name.trim().lowercase() }
        }
        val visibleList = remember(channels, hiddenEverywhere, view) {
            when (view) {
                1 -> hiddenEverywhere
                2 -> channels.filter { it.id !in hidden }
                else -> channels
            }
        }
        LaunchedEffect(view, visibleList.size) {
            kotlinx.coroutines.delay(60)
            runCatching { firstRowFocus.requestFocus() }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 560.dp)
                .background(TvDarkSurface, RoundedCornerShape(16.dp))
                .padding(24.dp)
        ) {
            Text(
                "Hide / Unhide Channels",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TvOnBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Scope: $languageSummary · ${channels.size} channels. Hidden channels disappear " +
                    "from Home, search, EPG and zap lists; the Hidden tab lists them across all languages.",
                style = MaterialTheme.typography.bodySmall,
                color = TvOnSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Chips carry labels ONLY — the old "All · 1300 / Hidden · 12 / Visible · 1288" chips
            // summed to ~336dp and clipped the third chip off the right edge inside a ~283dp
            // portrait dialog. The active view's count now lives in the trailing text, which
            // shrinks (weight + ellipsis) instead of pushing the chips out.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ManagerChip("All", view == 0) { view = 0 }
                ManagerChip("Hidden", view == 1) { view = 1 }
                ManagerChip("Visible", view == 2) { view = 2 }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    when (view) {
                        1 -> "${hidden.size} hidden"
                        2 -> "${channels.size - hidden.size} visible"
                        else -> "${channels.size} channels"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = TvOnSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            if (visibleList.isEmpty()) {
                Text(
                    when (view) {
                        1 -> "Nothing hidden. Hide a channel here, from the Home long-press, or via the player's settings panel."
                        2 -> "Every channel in scope is hidden — switch to All or Hidden to bring some back."
                        else -> "No channels match the selected languages."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TvOnSurfaceVariant,
                    modifier = Modifier.padding(vertical = 20.dp)
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .heightIn(max = 420.dp)
                        .tvFastNavKeys(focusManager, pageRows = 6)
                        .focusRestorer()
                ) {
                    itemsIndexed(visibleList, key = { _, ch -> ch.id }) { idx, ch ->
                        val isHidden = ch.id in hidden
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (idx == 0) Modifier.focusRequester(firstRowFocus) else Modifier),
                            onClick = { onToggle(ch.id) },
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = if (isHidden) Color.Transparent
                                else TvDarkSurfaceVariant.copy(alpha = 0.4f),
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
                                modifier = Modifier
                                    .padding(horizontal = 14.dp, vertical = 13.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        ch.name,
                                        color = if (isHidden) TvOnSurfaceVariant else TvOnSurface,
                                        // Struck-through name reads unambiguously as "hidden".
                                        textDecoration = if (isHidden) TextDecoration.LineThrough else null,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        ch.group,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TvOnSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    if (isHidden) "Unhide" else "Hide",
                                    color = if (isHidden) TvPrimary else TvOnSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            if (hidden.isNotEmpty() && view != 2) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    onClick = onShowAll,
                    modifier = Modifier.fillMaxWidth(),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.Transparent,
                        focusedContainerColor = TvDarkSurfaceVariant
                    ),
                    border = ClickableSurfaceDefaults.border(
                        focusedBorder = androidx.tv.material3.Border(
                            border = androidx.compose.foundation.BorderStroke(2.dp, TvFocusBorder),
                            shape = RoundedCornerShape(8.dp)
                        )
                    )
                ) {
                    Text(
                        "Show all (${hidden.size})",
                        color = TvPrimary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp).fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
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
                    "Done",
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    color = TvDarkBackground,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/** All / Hidden / Visible view chip for [ChannelManagerDialog]; label only — counts live in the row's trailing text. */
@Composable
private fun ManagerChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val isTouch = LocalIsTouch.current
    Surface(
        onClick = onClick,
        modifier = if (isTouch) Modifier.heightIn(min = 48.dp) else Modifier,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) TvPrimaryContainer.copy(alpha = 0.45f)
            else TvDarkSurfaceVariant.copy(alpha = 0.5f),
            focusedContainerColor = TvPrimaryContainer
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, TvFocusBorder),
                shape = RoundedCornerShape(16.dp)
            )
        )
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            color = if (selected) TvPrimary else TvOnSurface,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1
        )
    }
}

/** One checkable language row inside [ChannelLanguagesDialog], with its channel count. */@Composable
private fun LanguageToggleRow(
    label: String,
    selected: Boolean,
    count: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) TvPrimaryContainer.copy(alpha = 0.3f) else Color.Transparent,
            focusedContainerColor = TvDarkSurfaceVariant
        ),
        border = ClickableSurfaceDefaults.border(
            // 1dp @ 40% alpha was invisible at couch distance — every other row uses the solid
            // 2dp ring; these dialog rows now match.
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, TvFocusBorder),
                shape = RoundedCornerShape(8.dp)
            )
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (selected) TvPrimary else Color.Transparent)
                    .border(1.dp, if (selected) TvPrimary else TvOnSurfaceVariant, RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    androidx.tv.material3.Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                label,
                color = if (selected) TvPrimary else TvOnSurface,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )
            if (count != null) {
                Text("· $count", style = MaterialTheme.typography.labelMedium, color = TvOnSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = TvPrimary,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp, start = 4.dp)
    )
}

@Composable
fun SettingsItem(
    title: String,
    subtitle: String,
    value: String = "",
    valueColor: Color = com.fenyx.jtv.theme.TvPrimary,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    modifier: Modifier = Modifier,
    // False = display-only row (e.g. About): not focusable, not clickable — a highlighted row
    // whose OK did nothing read as a bug and burned a D-pad stop.
    clickable: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        enabled = clickable,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
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
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = TvOnSurface, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(2.dp))
                // Capped at two lines: long subtitles (e.g. the language-variants explainer) used
                // to wrap to 3+ lines and crush the row against its value on portrait phones.
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TvOnSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            if (icon != null) {
                androidx.tv.material3.Icon(icon, contentDescription = null, tint = valueColor, modifier = Modifier.size(24.dp))
            } else if (value.isNotEmpty()) {
                Text(
                    value,
                    color = valueColor,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SettingsToggle(
    title: String,
    subtitle: String,
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
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
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = TvOnSurface, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(2.dp))
                // Two-line cap + fixed gap: keeps the toggle clear of the text on narrow windows
                // instead of letting long subtitles push it against the screen edge.
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TvOnSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
             Text(
                 if (isEnabled) "On" else "Off",
                 color = if (isEnabled) TvPrimary else TvOnSurfaceVariant,
                 fontWeight = FontWeight.SemiBold,
                 style = MaterialTheme.typography.labelLarge
             )
             Spacer(modifier = Modifier.width(10.dp))
             // Custom toggle
             Box(
                 modifier = Modifier
                     .width(48.dp)
                     .height(26.dp)
                     .clip(RoundedCornerShape(13.dp))
                     .background(if (isEnabled) TvPrimary.copy(alpha = 0.3f) else TvDarkSurfaceVariant)
                     .padding(3.dp)
             ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(if (isEnabled) TvPrimary else TvOnSurfaceVariant)
                        .align(if (isEnabled) Alignment.CenterEnd else Alignment.CenterStart)
                )
            }
        }
    }
}

@Composable
private fun PickerDialog(
    title: String,
    options: List<Pair<String, String>>,
    currentValue: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        Column(
            // Shrink to the window on phones; keep the designed width on TVs.
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 400.dp)
                .background(TvDarkSurface, RoundedCornerShape(16.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TvOnBackground
            )
            Spacer(modifier = Modifier.height(16.dp))

            // The currently-selected option anchors initial focus: without this the dialog opened
            // wherever Compose dropped focus, and picking "Zoom" meant arrowing from the top of
            // the list every single time.
            val selectedRowFocus = remember { FocusRequester() }
            LaunchedEffect(Unit) { runCatching { selectedRowFocus.requestFocus() } }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                // Same short-screen fix as the Channel Languages dialog: let the Cancel button keep
                // its height and the list take the leftovers.
                modifier = Modifier.weight(1f, fill = false).heightIn(max = 400.dp)
            ) {
                items(options.size) { index ->
                    val (value, label) = options[index]
                    val isSelected = value == currentValue

                    Surface(
                        modifier = Modifier.fillMaxWidth().then(
                            if (isSelected) Modifier.focusRequester(selectedRowFocus) else Modifier
                        ),
                        onClick = { onSelect(value) },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = if (isSelected) TvPrimaryContainer.copy(alpha = 0.3f) else Color.Transparent,
                            focusedContainerColor = TvDarkSurfaceVariant
                        ),
                        border = ClickableSurfaceDefaults.border(
                            focusedBorder = androidx.tv.material3.Border(
                                border = androidx.compose.foundation.BorderStroke(2.dp, TvPrimary),
                                shape = RoundedCornerShape(8.dp)
                            )
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(TvPrimary)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                            }
                            Text(
                                label,
                                color = if (isSelected) TvPrimary else TvOnSurface,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                onClick = onDismiss,
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = TvDarkSurfaceVariant,
                    focusedContainerColor = TvPrimaryContainer
                )
            ) {
                Text(
                    "Cancel",
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 10.dp),
                    color = TvOnSurface
                )
            }
        }
    }
}
