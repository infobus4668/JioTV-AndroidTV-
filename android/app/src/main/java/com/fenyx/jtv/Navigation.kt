package com.fenyx.jtv

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.compose.ui.platform.LocalContext
import com.fenyx.jtv.data.SettingsManager
import com.fenyx.jtv.ui.login.LoginScreen
import com.fenyx.jtv.ui.main.MainScreen
import com.fenyx.jtv.ui.main.MainViewModel
import com.fenyx.jtv.ui.settings.SettingsScreen
import com.fenyx.jtv.ui.player.TvPlayerScreen
import kotlinx.coroutines.launch

// Sentinel for "the persisted setup mode hasn't loaded yet" so we don't flash the chooser on launch.
private const val SETUP_LOADING = "__loading__"

/**
 * Page padding for screens WITHOUT text input: system bars + display cutout, but NEVER the IME
 * (keyboard) inset. `safeDrawingPadding()` includes the IME inset, so when the keyboard was just
 * used (or its inset is reported stale after the login keyboard closes — common on MIUI) the whole
 * page shrinks from the bottom, leaving a huge dead band under the content — the "black area below
 * the channel grid at first login" bug. Input-free pages must never react to the keyboard.
 */
@Composable
private fun Modifier.contentScreenPadding(): Modifier =
    windowInsetsPadding(WindowInsets.safeDrawing.exclude(WindowInsets.ime))

@Composable
private fun LoadingScreen() {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.CircularProgressIndicator(color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun MainNavigation() {
    val context = LocalContext.current
    val settingsManager = androidx.compose.runtime.remember { SettingsManager(context) }
    val authData by settingsManager.authDataFlow.collectAsState(initial = null)

    val backStack = rememberNavBackStack(Main)
    val mainViewModel: MainViewModel = viewModel()

    val autoplayLastChannel by settingsManager.autoplayLastChannelFlow.collectAsState(initial = null)
    val lastChannelId by settingsManager.lastChannelIdFlow.collectAsState(initial = null)
    val lastChannelGroup by settingsManager.lastChannelGroupFlow.collectAsState(initial = null)
    val hasAutoPlayed = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    // Use the COLLAPSED (display) list the player actually navigates, and wait for it to be populated
    // before autoplaying — otherwise autoplay fired against the raw list (wrong index) or before the
    // collapse ran (0 channels), which is what caused the black "0 channels / Loading…" screen on boot.
    val allChannels by mainViewModel.displayChannels.collectAsState()
    val isLoading by mainViewModel.isLoading.collectAsState()

    androidx.compose.runtime.LaunchedEffect(autoplayLastChannel, lastChannelId, allChannels, isLoading) {
        when (autoplayLastChannel) {
            true -> {
                when {
                    hasAutoPlayed.value -> { /* already handled */ }
                    // No channel was ever saved (or auth was cleared) -> don't hang on the spinner,
                    // just show the home screen.
                    lastChannelId == null -> hasAutoPlayed.value = true
                    // Kick off the channel load (served from cache when fresh, so this is fast).
                    allChannels.isEmpty() && !isLoading -> mainViewModel.fetchChannels()
                    allChannels.isNotEmpty() -> {
                        hasAutoPlayed.value = true
                        val channelIndex = allChannels.indexOfFirst { it.id == lastChannelId }
                        if (channelIndex != -1) {
                            backStack.add(Player(channelIndex = channelIndex, group = lastChannelGroup))
                        }
                    }
                    // Load finished but produced no channels (e.g. offline first run) -> fall through
                    // to the home screen instead of spinning forever.
                    !isLoading -> hasAutoPlayed.value = true
                }
            }
            false -> hasAutoPlayed.value = true
            else -> { /* null: setting not loaded yet */ }
        }
    }

    // Onboarding router. When not logged in, pick the setup flow from the chosen method:
    //  - not chosen yet (first boot) -> Setup chooser
    //  - "phone" -> OTP LoginScreen (with a way back to the chooser)
    //  - "server" -> ServerSetupScreen (pull shared credentials)
    val setupMode by settingsManager.setupModeFlow.collectAsState(initial = SETUP_LOADING)
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    if (authData == null) {
        when (setupMode) {
            SETUP_LOADING -> LoadingScreen()
            null -> com.fenyx.jtv.ui.setup.SetupScreen(
                onChoosePhone = { scope.launch { settingsManager.setSetupMode("phone") } },
                onChooseServer = { scope.launch { settingsManager.setSetupMode("server") } },
                onChooseJtv = { scope.launch { settingsManager.setSetupMode("jtv") } },
                modifier = Modifier.contentScreenPadding()
            )
            // NOTE: no safeDrawingPadding here — the setup screen paints its own full-bleed background
            // and top-anchors its content, so the page must NOT get the IME inset (that's what pushed
            // the whole screen up when the keyboard opened).
            "server" -> com.fenyx.jtv.ui.setup.ServerSetupScreen(
                onBack = { scope.launch { settingsManager.setSetupMode(null) } }
            )
            "jtv" -> com.fenyx.jtv.ui.setup.ServerSetupScreen(
                jtvMode = true,
                onBack = { scope.launch { settingsManager.setSetupMode(null) } }
            )
            else -> LoginScreen(
                onChangeMethod = { scope.launch { settingsManager.setSetupMode(null) } },
                modifier = Modifier.safeDrawingPadding()
            )
        }
    } else if (autoplayLastChannel == null || (autoplayLastChannel == true && !hasAutoPlayed.value)) {
        // Show blank loading screen while evaluating autoplay
        LoadingScreen()
    } else {
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            entryProvider =
                entryProvider {
                entry<Main> {
                    MainScreen(
                        onChannelClick = { index, group ->
                            backStack.add(Player(channelIndex = index, group = group))
                        },
                        onCatchupClick = { index, prog ->
                            backStack.add(
                                Player(
                                    channelIndex = index,
                                    group = null, // zap context: full All list
                                    cuTitle = prog.title,
                                    cuStartMs = prog.startMs,
                                    cuEndMs = prog.stopMs,
                                    cuSrno = prog.srno,
                                    cuShowId = prog.showId,
                                    cuShowtime = prog.showtime
                                )
                            )
                        },
                        onSettingsClick = {
                            backStack.add(Settings)
                        },
                        onSearchClick = {
                            backStack.add(Search)
                        },
                        viewModel = mainViewModel,
                        modifier = Modifier.contentScreenPadding()
                    )
                }
                entry<Search> {
                    com.fenyx.jtv.ui.search.SearchScreen(
                        viewModel = mainViewModel,
                        onChannelClick = { index, group ->
                            backStack.add(Player(channelIndex = index, group = group))
                        },
                        modifier = Modifier.contentScreenPadding()
                    )
                }
                entry<Settings> {
                    SettingsScreen(
                        modifier = Modifier.contentScreenPadding(),
                        mainViewModel = mainViewModel
                    )
                }
                entry<Player> { playerArgs ->
                    val groups by mainViewModel.groups.collectAsState()
                    val favoriteChannels by mainViewModel.favoriteChannels.collectAsState()
                    // Reactive (not a one-shot snapshot) so if the player is opened while the collapsed
                    // list is still being built, it recomposes and fills in — no more Settings-and-back
                    // workaround to recover from a "0 channels" launch.
                    val allChannels by mainViewModel.displayChannels.collectAsState()
                    // EPG snapshot for the player's now-playing banner / programme sheet. The map's
                    // identity changes only when the VM publishes new data, so this stays cheap.
                    val playerEpgData by mainViewModel.epgData.collectAsState()

                    // The player's category sidebar gets the SAME pseudo-categories as Home ("All" and,
                    // when present, "★ Favorites") so the full list is also browsable mid-playback.
                    // getChannelsByGroup resolves the sentinels (and applies the language filter/sort).
                    val playerGroups = androidx.compose.runtime.remember(groups, favoriteChannels) {
                        buildList {
                            add(MainViewModel.GROUP_ALL)
                            if (favoriteChannels.isNotEmpty()) add(MainViewModel.GROUP_FAVORITES)
                            addAll(groups)
                        }
                    }

                    // Memoize the expensive per-group grouping + index lookups so they run once per
                    // channel-list change, not on every recomposition (this was a real source of
                    // player-open / settings-open lag: it re-filtered all ~1300 channels for every group).
                    val channels = androidx.compose.runtime.remember(playerArgs.group, allChannels) {
                        if (playerArgs.group != null) mainViewModel.getChannelsByGroup(playerArgs.group)
                        else allChannels
                    }
                    val allChannelsByGroup = androidx.compose.runtime.remember(playerGroups, allChannels) {
                        playerGroups.associateWith { group -> mainViewModel.getChannelsByGroup(group) }
                    }
                    val filteredIndex = androidx.compose.runtime.remember(channels, allChannels, playerArgs.channelIndex) {
                        val targetChannel = allChannels.getOrNull(playerArgs.channelIndex)
                        if (targetChannel != null) channels.indexOf(targetChannel).coerceAtLeast(0) else 0
                    }

                    TvPlayerScreen(
                        channels = channels,
                        initialIndex = filteredIndex,
                        allChannelsByGroup = allChannelsByGroup,
                        groups = playerGroups,
                        onBack = { backStack.removeLastOrNull() },
                        onSettings = {
                            backStack.add(Settings)
                        },
                        variantsFor = { id -> mainViewModel.variantsFor(id) },
                        initialGroup = playerArgs.group,
                        playerEpgData = playerEpgData,
                        onRequestChannelEpg = mainViewModel::fetchNativeEpgIfMissing,
                        initialCatchup = if (playerArgs.cuStartMs != null && playerArgs.cuEndMs != null) {
                            com.fenyx.jtv.data.EpgProgram(
                                title = playerArgs.cuTitle ?: "",
                                description = "",
                                startMs = playerArgs.cuStartMs!!,
                                stopMs = playerArgs.cuEndMs!!,
                                srno = playerArgs.cuSrno,
                                showId = playerArgs.cuShowId,
                                showtime = playerArgs.cuShowtime
                            )
                        } else null,
                        // Bind the replay to its channel so zapping deterministically drops it.
                        initialCatchupChannelId =
                            mainViewModel.getAllChannels().getOrNull(playerArgs.channelIndex)?.id
                    )
                }
            },
        )
    }
}
