package com.fenyx.jtv.ui.player

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.tv.material3.MaterialTheme
import com.fenyx.jtv.theme.Surface
import androidx.core.net.toUri
import java.util.Locale
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.fenyx.jtv.data.Channel
import com.fenyx.jtv.data.SettingsManager
import com.fenyx.jtv.theme.*
import com.fenyx.jtv.ui.settings.SettingsItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/** One selectable audio track from the currently playing stream. */
@androidx.annotation.OptIn(UnstableApi::class)
private data class AudioOption(
    val label: String,
    val group: androidx.media3.common.TrackGroup,
    val trackIndex: Int,
    val selected: Boolean
)

/** Display label for a (possibly sentinel) category: "__ALL__" -> "All", "__FAVORITES__" -> "★ Favorites". */
private fun groupLabel(group: String?): String = when (group) {
    com.fenyx.jtv.data.ChannelFilter.GROUP_ALL -> "All"
    com.fenyx.jtv.data.ChannelFilter.GROUP_FAVORITES -> "★ Favorites"
    null -> "Channels"
    else -> group
}

@SuppressLint("SetJavaScriptEnabled")
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun TvPlayerScreen(
    channels: List<Channel>,
    initialIndex: Int,
    allChannelsByGroup: Map<String, List<Channel>>,
    groups: List<String>,
    onBack: () -> Unit,
    onSettings: () -> Unit = {},
    variantsFor: (String) -> List<com.fenyx.jtv.data.ChannelLanguage.Variant> = { emptyList() },
    initialGroup: String? = null,
    // EPG hooks wired from MainViewModel by Navigation: the current guide snapshot (reactive — its
    // identity changes when the VM publishes new data) and the lazy native fetcher (semaphore-capped).
    playerEpgData: Map<String, List<com.fenyx.jtv.data.EpgProgram>> = emptyMap(),
    onRequestChannelEpg: (String) -> Unit = {},
    // Catch-up intent from the EPG time-grid: start this past programme as a VOD replay.
    initialCatchup: com.fenyx.jtv.data.EpgProgram? = null,
    // Channel id the initial replay belongs to (must match the launched channel).
    initialCatchupChannelId: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val isTouch = com.fenyx.jtv.theme.LocalIsTouch.current

    // ─── Orientation: follow the device (phones) ───
    // The player no longer forces landscape: it opens in whatever orientation the system hands it
    // (portrait by default) and rotates with the device when the user's auto-rotate is on. The ⟳
    // dock button explicitly toggles portrait ↔ landscape for auto-rotate-off users. On exit the
    // system default is restored so the rest of the app isn't left locked. The manifest declares
    // orientation|screenSize in configChanges so switches never recreate the activity — playback
    // continues seamlessly.
    val playerActivity = context as? android.app.Activity
    DisposableEffect(isTouch) {
        onDispose {
            if (isTouch) {
                playerActivity?.requestedOrientation =
                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    // Saveable so the current channel/group survive leaving the player (e.g. opening full Settings and
    // coming back) instead of resetting to the channel the player was originally launched with.
    // Init from the launch group (which may be the "All"/"Favorites" pseudo-category) NOT the first
    // channel's real category — otherwise launching from "All" navigated the wrong list and played a
    // random channel.
    var currentGroup by rememberSaveable { mutableStateOf(initialGroup) }
    var currentIndex by rememberSaveable { mutableIntStateOf(initialIndex.coerceIn(0, (channels.size - 1).coerceAtLeast(0))) }
    // Derived from the saved group so it rebuilds correctly after a state restore. For pseudo-categories
    // ("All"/"Favorites") there's no per-group entry, so fall back to the passed `channels` list.
    val currentChannels = remember(currentGroup, allChannelsByGroup, channels) {
        val g = currentGroup
        if (g != null) (allChannelsByGroup[g] ?: channels) else channels
    }
    val currentChannel = remember(currentIndex, currentChannels) { currentChannels.getOrNull(currentIndex) }

    // Collapsed per-language feeds for the channel on screen (empty when it isn't a language family).
    val currentVariants = remember(currentChannel) { currentChannel?.let { variantsFor(it.id) } ?: emptyList() }
    // When the user picks a different language we play that sibling channel_id without disturbing the
    // visible channel list. Reset whenever the logical channel changes (a zap). `playingChannel` is
    // derived lower down, once `language` (the preferred audio language) is in scope.
    var langOverride by remember { mutableStateOf<Channel?>(null) }
    // ─── Catch-up replay state ───
    // The replay is stored WITH its owning channel id and DERIVED against the current channel:
    // zapping makes `catchup` read null immediately and deterministically (no effect-ordering
    // race between "reset" and the media-load effect).
    var catchupProg by remember { mutableStateOf(initialCatchup) }
    var catchupChId by remember { mutableStateOf<String?>(initialCatchupChannelId) }
    val catchup = catchupProg?.takeIf { catchupChId != null && catchupChId == currentChannel?.id }
    // True when the ACTIVE replay resolved to the DRM DASH track (no clear HLS offered) — lets
    // failure messages say "your device can't decode this" instead of a generic retry line.
    var replayWasDrm by remember { mutableStateOf(false) }
    // Second-chance escalation: when a replay's clear HLS keeps failing (dead `.mp4.urlset` CDN
    // path — Jio-side, per-channel), the next refetch asks Jio for the DRM DASH instead. Mirrors
    // the companion server's dead-HLS → DRM fallback for live.
    var replayDrmRetry by remember { mutableStateOf(false) }

    fun startReplay(prog: com.fenyx.jtv.data.EpgProgram?) {
        catchupProg = prog
        catchupChId = if (prog != null) currentChannel?.id else null
        // Every new replay starts clean: clear-HLS first, DRM only as the escalation.
        replayDrmRetry = false
        replayWasDrm = false
    }
    // Replay control bar visibility (short-OK reveals it during a replay).
    var showCatchupBar by remember { mutableStateOf(false) }
    val catchupBarFocus = remember { FocusRequester() }
    LaunchedEffect(currentChannel) { langOverride = null }
    LaunchedEffect(catchup) { if (catchup == null) showCatchupBar = false }
    var showLangSelector by remember { mutableStateOf(false) }

    var showOverlay by remember { mutableStateOf(true) }
    var showChannelList by remember { mutableStateOf(false) }
    var showCategoryList by remember { mutableStateOf(false) }
    var showSettingsOverlay by remember { mutableStateOf(false) }
    // Programme guide sheet (Info double-press) and the optional zap-preview strip.
    var showProgrammes by remember { mutableStateOf(false) }
    var showZapStrip by remember { mutableStateOf(false) }
    var stripIndex by remember { mutableIntStateOf(0) }
    // Touch: on-screen numpad for channel entry (phones have no digit keys).
    var showTouchNumpad by remember { mutableStateOf(false) }

    val settingsManager = remember { SettingsManager(context) }
    val favoriteChannels by settingsManager.favoriteChannelsFlow.collectAsState(initial = emptySet())
    val playerSetupMode by settingsManager.setupModeFlow.collectAsState(initial = null)
    // Optional "Zap preview": ↑/↓ opens a preview strip instead of zapping instantly.
    val zapPreviewEnabled by settingsManager.zapPreviewFlow.collectAsState(initial = false)
    // Configurable touch dock (Settings → Player Touch Dock): only the enabled buttons show.
    val dockButtons by settingsManager.touchDockButtonsFlow.collectAsState(initial = SettingsManager.DOCK_BUTTONS_DEFAULT)
    // ▲▼ edge zap buttons (touch) — toggleable for the same decluttering reason.
    val zapEdgeButtons by settingsManager.zapEdgeButtonsFlow.collectAsState(initial = true)
    // "Refresh Login" (server mode) state shown in the right-side overlay.
    var refreshingCreds by remember { mutableStateOf(false) }

    // Auto-hide overlay. Keyed on the panels too: opening a panel used to cancel the pending hide
    // and closing it left the banner/dock on screen FOREVER (the timer never re-armed). Now the
    // countdown simply restarts on any panel toggle, so closing a panel by tapping outside lands
    // on a clean video.
    LaunchedEffect(showOverlay, showChannelList, showCategoryList, showSettingsOverlay) {
        if (showOverlay) {
            delay(5000)
            showOverlay = false
        }
    }

    var quality by remember { mutableStateOf("auto") }
    var language by remember { mutableStateOf("hi") }
    var resizeMode by remember { mutableIntStateOf(0) }

    // Follow the user's Default Audio Language: for a collapsed family, auto-select the matching
    // language feed unless the user has manually overridden it for this channel. The feed actually
    // sent to the player is: manual override, else preferred-language feed, else the logical channel.
    val preferredVariant = remember(currentVariants, language) {
        currentVariants.firstOrNull { it.langCode == language }?.channel
    }
    val playingChannel = langOverride ?: preferredVariant ?: currentChannel
    
    var showAudioSelector by remember { mutableStateOf(false) }
    var showQualitySelector by remember { mutableStateOf(false) }

    // Real audio tracks exposed by the current stream (for reliable language switching).
    var audioTracks by remember { mutableStateOf<List<AudioOption>>(emptyList()) }
    // Subtitle/caption tracks, if the stream carries any (rendered by PlayerView when selected).
    var subtitleTracks by remember { mutableStateOf<List<AudioOption>>(emptyList()) }

    // Sleep timer: 0 = off. When set, a 1s ticker drives the visible remaining-time chip and the
    // player exits when it reaches zero.
    var sleepTimerMin by remember { mutableIntStateOf(0) }
    var sleepRemainingSec by remember { mutableIntStateOf(0) }
    LaunchedEffect(sleepTimerMin) {
        if (sleepTimerMin <= 0) {
            sleepRemainingSec = 0
        } else {
            val total = sleepTimerMin * 60
            for (elapsed in 0 until total) {
                sleepRemainingSec = total - elapsed
                delay(1_000)
            }
            sleepRemainingSec = 0
            onBack()
        }
    }

    // Gate the first prepare() on the saved prefs being loaded. `quality` starts at "auto" and the real
    // value arrives from DataStore a beat later; because the media-source effect used to key on
    // `quality`, that late arrival re-fetched the stream and re-prepared the player *while it was
    // already playing* — a visible hitch a second into every channel. Quality doesn't affect the stream
    // URL at all (Jio serves one MPD containing every rendition), so it must not drive a reload.
    var prefsLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        quality = settingsManager.defaultQualityFlow.first()
        language = settingsManager.defaultLanguageFlow.first()
        resizeMode = settingsManager.playerResizeModeFlow.first()
        prefsLoaded = true
    }

    // Player state
    var isBuffering by remember { mutableStateOf(true) }
    // True when the user paused via long-press OK.
    var userPaused by remember { mutableStateOf(false) }
    // Non-null only when playback has failed and auto-recovery has been exhausted.
    var playbackError by remember { mutableStateOf<String?>(null) }

    // ─── Diagnostics overlay (long-press INFO) ───
    // Declared BEFORE the track listener below, which writes the selected-format lines.
    var showStats by remember { mutableStateOf(false) }
    var statVideo by remember { mutableStateOf<String?>(null) }
    var statAudio by remember { mutableStateOf<String?>(null) }
    var statsTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(showStats) {
        while (showStats) { statsTick++; delay(1_000) }
    }

    // Stream auto-recovery: Jio live URLs carry a short-lived Akamai cookie (__hdnea__) that expires
    // after a while, which surfaces as a sudden black screen. On error we re-fetch the stream URL
    // (which regenerates the cookie). The counter resets every time playback recovers (STATE_READY),
    // so a long session can recover indefinitely instead of dying after a fixed number of errors.
    val retryCount = remember { mutableIntStateOf(0) }
    var streamRefreshTrigger by remember { mutableIntStateOf(0) }
    // Bumping this re-attaches the audio effect (see the buffering watchdog + AudioEnhancer) to kick a
    // stalled AudioTrack alive without touching the stream.
    var audioKick by remember { mutableIntStateOf(0) }
    val kickCount = remember { mutableIntStateOf(0) }

    // Player-affecting prefs. Read reactively (NEVER block the main thread here — doing so caused
    // jank/black-screen on entry). Tunneling is applied live via track-selection params below; the
    // hardware-decoder mode can only be set at construction, so the player is keyed on it.
    val tunnelingPref by settingsManager.tunnelingFlow.collectAsState(initial = false)
    val hardwareOnlyPref by settingsManager.hardwareDecoderFlow.collectAsState(initial = true)
    val bufferSecPref by settingsManager.playbackBufferSecFlow.collectAsState(initial = 60)

    // Audio enhancement settings + the effect engine.
    val voiceBoost by settingsManager.voiceBoostFlow.collectAsState(initial = 2)
    val audioNormalize by settingsManager.audioNormalizeFlow.collectAsState(initial = false)
    // LoudnessEnhancer handles makeup loudness; the dialogue processor does center-channel voice
    // isolation. The processor is stable across player rebuilds and reads its level live.
    val audioEnhancer = remember { com.fenyx.jtv.player.AudioEnhancer() }
    val dialogueProcessor = remember { com.fenyx.jtv.player.DialogueAudioProcessor() }

    // ExoPlayer is built using our custom factory to ensure Android TV optimizations. Keyed on the
    // settings that can only be applied at construction so changing any of them rebuilds the player;
    // the DisposableEffect below releases the previous instance when that happens.
    val exoPlayer = remember(hardwareOnlyPref, tunnelingPref, bufferSecPref) {
        com.fenyx.jtv.player.JioExoPlayerFactory.create(
            context,
            language,
            tunneling = tunnelingPref,
            hardwareOnly = hardwareOnlyPref,
            maxBufferSec = bufferSecPref,
            dialogueProcessor = dialogueProcessor
        )
    }

    // Deterministic audio session id: generate one and bind the player to it so audio-effect
    // attachment is reliable (the onAudioSessionIdChanged callback was unreliable on this hardware).
    val audioSessionId = remember(exoPlayer) {
        val am = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
        val id = am.generateAudioSessionId()
        runCatching { exoPlayer.setAudioSessionId(id) }
        id
    }

    // Holds the freshest Akamai `__hdnea__` token + the CURRENT stream's header sets. All three
    // are cleared on every load so a replay (or a channel zap) can never inherit the previous
    // stream's token/headers — that cross-contamination is a guaranteed CDN 403.
    val tokenHolder = remember { java.util.concurrent.atomic.AtomicReference("") }
    val streamHeadersHolder = remember {
        java.util.concurrent.atomic.AtomicReference<Map<String, String>>(emptyMap())
    }
    val licenseHeadersHolder = remember {
        java.util.concurrent.atomic.AtomicReference<Map<String, String>>(emptyMap())
    }

    // Reuse data source factories to avoid GC pressure on every channel switch
    val httpDataSourceFactory = remember {
        DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)
    }
    // Per-request resolution (mirrors the companion server's proxy exactly):
    //  - AES KEY requests (.pkey / aes128.key / tv.media.jio.com) authenticate with the
    //    LICENSE-style headers and NO __hdnea__ cookie — sending stream headers there 403s.
    //  - Everything else (manifests + segments) uses the stream headers, with the freshest
    //    __hdnea__ applied BOTH as a URL query rewrite and as the Cookie header.
    // With no token seeded (e.g. a VOD replay without one) requests pass through untouched.
    val resolvingDataSourceFactory = remember {
        androidx.media3.datasource.ResolvingDataSource.Factory(httpDataSourceFactory) { dataSpec ->
            val uriStr = dataSpec.uri.toString()
            // Key requests authenticate like the Widevine license server (license headers, NO
            // __hdnea__ cookie). Match generously: .pkey, AES key files, bare /key endpoints and
            // Jio's dedicated key host — some channels serve keys from different hosts than the
            // segments, and sending stream headers there 403s that channel's replay.
            val isKeyRequest = Regex(
                """(\.pkey(\?|$)|aes\d*\.key|/key(\?|$)|tv\.media\.jio\.com/)""",
                RegexOption.IGNORE_CASE
            ).containsMatchIn(uriStr)

            if (isKeyRequest) {
                return@Factory dataSpec.withRequestHeaders(HashMap(licenseHeadersHolder.get()))
            }

            val token = tokenHolder.get()
            var spec = dataSpec
            if (token.isNotEmpty()) {
                val marker = "__hdnea__="
                val i = uriStr.indexOf(marker)
                if (i >= 0) {
                    spec = spec.withUri((uriStr.substring(0, i) + marker + token).toUri())
                }
            }
            val headers = HashMap(streamHeadersHolder.get())
            if (token.isNotEmpty()) headers["Cookie"] = "__hdnea__=" + token
            spec.withRequestHeaders(headers)
        }
    }
    val mediaSourceFactory = remember {
        DefaultMediaSourceFactory(context)
            .setDataSourceFactory(resolvingDataSourceFactory)
            // Retry transient/expiry errors (incl. 403/404 from an expired token) instead of failing
            // fatally and reloading. Works with the token rewrite above so the retry uses a fresh token.
            .setLoadErrorHandlingPolicy(com.fenyx.jtv.player.JioLoadErrorHandlingPolicy())
    }

    LaunchedEffect(exoPlayer, language, quality) {
        // NOTE: the anti-glitch setAllowVideoNonSeamlessAdaptiveness(false) is applied in
        // JioExoPlayerFactory — it lives on DefaultTrackSelector.Parameters.Builder, not on the base
        // TrackSelectionParameters.Builder that buildUpon() returns here. It persists across these
        // updates because buildUpon() carries the existing parameters forward.
        val builder = exoPlayer.trackSelectionParameters.buildUpon()
            .setPreferredAudioLanguage(language)
        // Tunneling is applied at construction (see remember key above), not here, because the base
        // TrackSelectionParameters.Builder doesn't expose setTunnelingEnabled in Media3 1.4.

        // An explicit quality choice is a FLOOR as well as a ceiling. Previously these were max-only
        // caps, so ABR was free to sit on Jio's ~80 kbps / 480p rendition even when the user had asked
        // for 1080p — the "doesn't follow my quality setting, starts blurry and creeps up" complaint.
        // Pinning min == max keeps the chosen tier locked. This can't black-screen: DefaultTrackSelector
        // has exceedVideoConstraintsIfNecessary=true by default, so a channel whose top rendition is
        // below the floor still falls back to its best available.
        when (quality) {
            // "low" is a deliberate data-saver / weak-link choice, so keep it a ceiling only and let
            // ABR drop further if the network genuinely can't hold 480p.
            "low" -> builder.setMaxVideoSize(854, 480)
            "medium" -> builder.setMinVideoSize(1280, 720).setMaxVideoSize(1280, 720)
            "high" -> builder.setMinVideoSize(1920, 1080).setMaxVideoSize(1920, 1080)
            // "auto": no floor — ABR adapts freely, but it now *starts* high because the bandwidth
            // meter is seeded optimistically in JioExoPlayerFactory instead of ramping from the floor.
            else -> builder.setMaxVideoSize(1920, 1080)
        }

        exoPlayer.trackSelectionParameters = builder.build()
    }

    // Listen for errors (keyed on exoPlayer so a rebuilt player gets its own listener)
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                val opts = mutableListOf<AudioOption>()
                // Diagnostic: log EVERY audio track the loaded manifest exposes, including ones the
                // device can't decode (adaptiveSupported/isSupported), so "missing language" reports can
                // be traced to (a) not present in the stream, or (b) unsupported codec. Filter logcat by
                // tag "TvPlayerAudio".
                val diag = StringBuilder()
                for (g in tracks.groups) {
                    if (g.type == androidx.media3.common.C.TRACK_TYPE_AUDIO) {
                        for (i in 0 until g.length) {
                            val f = g.getTrackFormat(i)
                            val lang = f.language
                            val label = f.label
                                ?: lang?.takeIf { it.isNotBlank() && it != "und" }?.let {
                                    runCatching { java.util.Locale(it).displayLanguage }
                                        .getOrNull()?.replaceFirstChar { c -> c.uppercase() }
                                }
                                ?: "Audio ${i + 1}"
                            diag.append("\n  [${lang ?: "?"}] label=${f.label} codec=${f.codecs ?: f.sampleMimeType} " +
                                "ch=${f.channelCount} supported=${g.isTrackSupported(i)} selected=${g.isTrackSelected(i)}")
                            // Keep offering every track (don't hide unsupported ones — that would look
                            // like the language is "missing"); the log records support so we can tell
                            // whether a silent track is an unsupported codec vs. genuinely absent.
                            opts.add(AudioOption(label, g.mediaTrackGroup, i, g.isTrackSelected(i)))
                        }
                    }
                }
                android.util.Log.d("TvPlayerAudio", "audio tracks (${opts.size} playable):$diag")
                audioTracks = opts

                // Subtitle/caption tracks (some Jio streams carry them). Kept visible even when
                // unsupported so "why is there no CC" stays diagnosable.
                val subs = mutableListOf<AudioOption>()
                for (g in tracks.groups) {
                    if (g.type == androidx.media3.common.C.TRACK_TYPE_TEXT) {
                        for (i in 0 until g.length) {
                            val f = g.getTrackFormat(i)
                            val label = f.label
                                ?: f.language?.takeIf { it.isNotBlank() && it != "und" }?.let {
                                    runCatching { java.util.Locale(it).displayLanguage }
                                        .getOrNull()?.replaceFirstChar { c -> c.uppercase() }
                                }
                                ?: "Subtitle ${i + 1}"
                            subs.add(AudioOption(label, g.mediaTrackGroup, i, g.isTrackSelected(i)))
                        }
                    }
                }
                subtitleTracks = subs

                // Diagnostic: which VIDEO rendition did ABR actually pick? Filter logcat by tag
                // "TvPlayerVideo" to confirm the quality setting is being honoured (the selected line
                // should match the chosen tier, not Jio's ~80 kbps floor). Also feeds the on-screen
                // diagnostics overlay (long-press INFO).
                val vdiag = StringBuilder()
                for (g in tracks.groups) {
                    if (g.type == androidx.media3.common.C.TRACK_TYPE_VIDEO) {
                        for (i in 0 until g.length) {
                            val f = g.getTrackFormat(i)
                            vdiag.append("\n  ${f.width}x${f.height} @${f.bitrate}bps " +
                                "codec=${f.codecs} supported=${g.isTrackSupported(i)} " +
                                "SELECTED=${g.isTrackSelected(i)}")
                            if (g.isTrackSelected(i)) {
                                statVideo = "${f.width}×${f.height} @${f.bitrate / 1000}kbps ${f.codecs ?: ""}"
                            }
                        }
                    }
                    if (g.type == androidx.media3.common.C.TRACK_TYPE_AUDIO) {
                        for (i in 0 until g.length) {
                            if (g.isTrackSelected(i)) {
                                val f = g.getTrackFormat(i)
                                statAudio = "${f.language ?: "?"} ${f.codecs ?: ""} ch${f.channelCount} @${f.bitrate / 1000}kbps"
                            }
                        }
                    }
                }
                android.util.Log.d("TvPlayerVideo", "video renditions:$vdiag")
            }
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    // Playback recovered -> clear any error and reset the recovery budget so the
                    // next expiry (minutes/hours later) gets a fresh set of retries.
                    retryCount.intValue = 0
                    playbackError = null
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                // Deep diagnostics: Media3's errorCode splits network(4xxx)/parse(3xxx)/decode(5xxx)
                // and the cause carries the failing HTTP code — one log line identifies whether a
                // dead replay is a 404 path, a bad manifest, or a codec problem.
                val cause = error.cause
                val httpCode = (cause as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)?.responseCode
                android.util.Log.e(
                    "TvPlayer",
                    "onPlayerError code=${error.errorCode} http=$httpCode " +
                        "cause=${cause?.javaClass?.simpleName}: ${cause?.message?.take(160)}"
                )
                // Token/cookie expiration (often 403 Forbidden) causes a black screen.
                // We MUST re-fetch the stream URL entirely, not just retry the same expired URL.
                if (retryCount.intValue < 5) {
                    // Replay escalation: after the first HLS failure, ask for the DRM DASH track
                    // on subsequent attempts (dead clear-urlset fallback, per-channel).
                    if (catchup != null && retryCount.intValue >= 1) replayDrmRetry = true
                    retryCount.intValue++
                    // Small backoff so a flapping CDN doesn't get hammered. Driven via the
                    // streamRefreshTrigger LaunchedEffect which re-fetches the stream URL.
                    val backoffMs = 800L * retryCount.intValue
                    scope.launch {
                        delay(backoffMs)
                        streamRefreshTrigger++
                    }
                } else {
                    // Auto-recovery exhausted: stop the spinner and show an actionable message
                    // instead of an indefinite black screen.
                    isBuffering = false
                    playbackError = if (catchup != null && replayWasDrm) {
                        "This replay is DRM-protected and this device can't decode it."
                    } else {
                        "Playback stopped. Press OK to retry."
                    }
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    LaunchedEffect(playingChannel, quality) {
        retryCount.intValue = 0 // Reset retries on intentional channel/language/quality change
        playbackError = null
    }

    // Keyed on exoPlayer too: if the player is rebuilt (e.g. saved buffer/decoder/tunneling prefs load
    // a moment after open, or the hardware-decoder toggle changes), the NEW instance must be given the
    // media source — otherwise it buffers forever until some other change re-triggers this. That was the
    // "loads until I change a setting" bug.
    LaunchedEffect(exoPlayer, playingChannel, prefsLoaded, streamRefreshTrigger, catchup) {
        val ch = playingChannel
        // Wait for the saved prefs before the first prepare(), so the correct quality constraints are
        // already in place and playback never has to switch rendition (and re-init the secure decoder)
        // right after it starts. NOTE: `quality` is deliberately NOT a key here — it does not change
        // the stream URL, only track selection, which the separate effect below applies live.
        if (ch != null && prefsLoaded) {
            // Persist the representative (logical) channel for autoplay, not the language sibling.
            currentChannel?.let { settingsManager.setLastChannelId(it.id) }
            settingsManager.setLastChannelGroup(currentGroup)
            isBuffering = true
            exoPlayer.stop()
            // Invalidate the previous stream's token + headers BEFORE any new request can go out:
            // a replay/zap must never inherit the last stream's CDN credentials.
            tokenHolder.set("")
            streamHeadersHolder.set(emptyMap())
            licenseHeadersHolder.set(emptyMap())
            
            val authData = settingsManager.authDataFlow.first()
            
            if (authData == null) {
                android.util.Log.e("TvPlayer", "Missing auth data")
                isBuffering = false
                playbackError = "Not logged in. Please sign in again."
                return@LaunchedEffect
            }
            
            val chNumber = ch.channelNumber.toString()
            android.util.Log.d("TvPlayer", "Fetching stream URL for channel $chNumber" +
                if (catchup != null) " (CATCHUP: ${catchup!!.title})" else "")

            val cuParams = catchup?.let {
                com.fenyx.jtv.data.JioApiClient.CatchupParams(
                    programId = it.showId ?: "",
                    srno = it.srno ?: "",
                    beginMs = it.startMs,
                    endMs = it.stopMs,
                    showtime = it.showtime ?: ""
                )
            }
            val result = com.fenyx.jtv.data.JioApiClient.getStreamUrl(
                context, chNumber, authData, catchup = cuParams, preferDrm = replayDrmRetry
            )
            
            if (result.isSuccess) {
                val streamData = result.getOrNull()!!
                val finalUrl = streamData.streamUrl

                // A "successful" geturl with an empty URL happens on delisted/unentitled content —
                // fail immediately with context instead of preparing nothing (infinite buffering).
                if (finalUrl.isBlank()) {
                    isBuffering = false
                    playbackError = if (catchup != null)
                        "Jio returned no stream for this replay. It may no longer be available."
                    else
                        "No stream URL returned for this channel. Press OK to retry."
                    return@LaunchedEffect
                }

                // Seed THIS stream's credentials: token (for URL rewrite + Cookie) and the two
                // header sets the per-request resolver picks between (segments vs AES keys).
                tokenHolder.set(com.fenyx.jtv.data.JioApiClient.extractHdneaToken(finalUrl))
                streamHeadersHolder.set(streamData.headers)
                licenseHeadersHolder.set(streamData.licenseHeaders)
                replayWasDrm = catchup != null && streamData.isMpd
                if (replayWasDrm) {
                    android.util.Log.d("TvPlayer", "Catch-up resolved to DRM DASH (no clear HLS) — needs Widevine L1")
                }

                // Security: stream URLs embed short-lived auth credentials in the query string.
                // Log only the path — never the full URL — so tokens don't leak into logcat.
                android.util.Log.d(
                    "TvPlayer",
                    "Loading stream: ${finalUrl.substringBefore('?')}?…(redacted) (isMpd: ${streamData.isMpd})"
                )

                val mediaItemBuilder = MediaItem.Builder()
                    .setUri(finalUrl)
                    .setMimeType(if (streamData.isMpd) MimeTypes.APPLICATION_MPD else MimeTypes.APPLICATION_M3U8)
                // Live-only: play ~20s behind the edge to absorb CDN jitter. A catch-up replay is a
                // plain VOD — a live offset there is meaningless and can stall preparation.
                if (catchup == null) {
                    mediaItemBuilder.setLiveConfiguration(
                        MediaItem.LiveConfiguration.Builder()
                            .setTargetOffsetMs(20000)
                            .build()
                    )
                }

                if (streamData.isMpd && streamData.licenseUrl.isNotEmpty()) {
                    val drmConfig = MediaItem.DrmConfiguration.Builder(androidx.media3.common.C.WIDEVINE_UUID)
                        .setLicenseUri(streamData.licenseUrl)
                        .setLicenseRequestHeaders(streamData.licenseHeaders)
                        // Don't hard-block the pipeline on the Widevine license round-trip: render any
                        // clear leading segments while the key is still being fetched. Shaves the
                        // license RTT off every channel zap. The device already does secure hardware
                        // (L1) decode, so protected segments still wait for their key as required.
                        .setPlayClearContentWithoutKey(true)
                        // NOTE: multiSession is deliberately left at the default (false). Enabling it
                        // spun up a fresh Widevine session on every key rotation, and logcat showed the
                        // resulting CryptoHal/CDM churn contributing to the mid-playback hitch.
                        .build()
                    mediaItemBuilder.setDrmConfiguration(drmConfig)
                }

                // Headers are now supplied per-request by the ResolvingDataSource resolver
                // (stream vs key sets); the factory itself stays header-free.

                val mediaSource = mediaSourceFactory.createMediaSource(mediaItemBuilder.build())

                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    exoPlayer.setMediaSource(mediaSource)
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                    userPaused = false // a new channel always starts playing
                }
            } else {
                val fetchErr = result.exceptionOrNull()?.message ?: ""
                android.util.Log.e("TvPlayer", "Failed to fetch stream: $fetchErr (catchup=${catchup != null})")
                val authExpired = fetchErr.contains("401") || fetchErr.contains("403") || fetchErr.contains("419")

                // REPLAYS fail fast and loud: a bad srno/programId/entitlement is deterministic —
                // retrying the identical request just spins. Only an auth expiry is worth one
                // refresh-retry; everything else surfaces immediately with the upstream reason.
                if (catchup != null && !authExpired) {
                    isBuffering = false
                    playbackError = "Couldn't start this replay (${fetchErr.take(80)})."
                    return@LaunchedEffect
                }

                if (retryCount.intValue >= 5) {
                    isBuffering = false
                    playbackError = when {
                        // Replay entitlement is per-show/per-channel: a persistent 403 here means
                        // Jio doesn't allow this replay for the account — not a login problem.
                        catchup != null && fetchErr.contains("403") ->
                            "This show isn't available for replay on your account."
                        authExpired && (playerSetupMode == "server" || playerSetupMode == "jtv") ->
                            "Server login expired. Re-login the Jio account on your JTV server, then press OK."
                        authExpired -> "Login expired. Please sign in again."
                        else -> "Couldn't load this channel. Press OK to retry."
                    }
                } else {
                    retryCount.intValue++
                    delay(800L * retryCount.intValue)
                    streamRefreshTrigger++
                }
            }
        }
    }

    // Buffering watchdog. A stuck first load (common right after setup: the stream is fetched and
    // prepare()d, but playback never leaves STATE_BUFFERING and no PlaybackException is thrown, so the
    // onPlayerError retry path can't fire) used to sit on the spinner forever until the user changed a
    // player setting by hand. The real cause is the AudioTrack for our explicit audio session not
    // starting until an audio effect is bound (see AudioEnhancer) — so recovery ESCALATES:
    //   1) re-attach the audio effect (audioKick) — the same thing the manual "Voice Boost" toggle did;
    //   2) if that still doesn't help, re-fetch the stream URL (streamRefreshTrigger);
    //   3) finally, surface an actionable error instead of an endless spinner.
    // Reset per channel so every zap gets a fresh recovery budget.
    LaunchedEffect(playingChannel) { kickCount.intValue = 0 }
    LaunchedEffect(isBuffering, playingChannel, streamRefreshTrigger, audioKick, catchup) {
        if (isBuffering && playingChannel != null && playbackError == null && !userPaused) {
            // Replays take longer to first frame (VOD manifest + key fetch); give them double
            // the patience before escalating.
            delay(if (catchup != null) 12_000 else 6_000)
            if (isBuffering && playbackError == null && !userPaused) {
                when {
                    kickCount.intValue < 3 -> {
                        kickCount.intValue++
                        android.util.Log.d("TvPlayer", "Buffering watchdog: audio kick ${kickCount.intValue}")
                        audioKick++
                    }
                    retryCount.intValue < 5 -> {
                        retryCount.intValue++
                        android.util.Log.d("TvPlayer", "Buffering watchdog: re-fetch ${retryCount.intValue}")
                        // Same replay escalation as the error path: HLS failed silently (no
                        // exception fired) → next fetch takes the DRM DASH track.
                        if (catchup != null) replayDrmRetry = true
                        streamRefreshTrigger++
                    }
                    else -> {
                        isBuffering = false
                        playbackError = if (catchup != null && replayWasDrm) {
                            "This replay is DRM-protected and this device can't decode it."
                        } else {
                            "Playback stopped. Press OK to retry."
                        }
                    }
                }
            }
        }
    }

    // ─── Transparent token refresh ───
    // The Jio `__hdnea__` token expires ~120s after issue. This loop fetches a fresh stream URL a few
    // seconds BEFORE expiry and publishes the new token to tokenHolder, so the ResolvingDataSource
    // keeps rewriting requests with a valid token. Playback never sees a 403 -> no reload, no buffering.
    //
    // LIVE ONLY, and deliberately keyed on `catchup`: the refreshed URL is resolved for whatever
    // mode is current. In replay (VOD) mode injecting another stream's CDN-signed token into these
    // segment URLs would 403 them — VOD replays keep their original token and rely on error-retry.
    LaunchedEffect(playingChannel, catchup) {
        if (catchup != null) return@LaunchedEffect
        val ch = playingChannel ?: return@LaunchedEffect
        while (true) {
            val token = tokenHolder.get()
            val expSec = com.fenyx.jtv.data.JioApiClient.extractTokenExpiryEpochSec(token)
            val nowSec = System.currentTimeMillis() / 1000
            // Refresh 15s before expiry; if we can't read an expiry, re-check in 60s.
            val waitMs = if (expSec > 0) ((expSec - nowSec - 15) * 1000).coerceIn(5_000, 110_000)
                         else 60_000L
            delay(waitMs)

            val authData = settingsManager.authDataFlow.first() ?: continue
            val res = com.fenyx.jtv.data.JioApiClient.getStreamUrl(
                context, ch.channelNumber.toString(), authData
            )
            if (res.isSuccess) {
                val newToken = com.fenyx.jtv.data.JioApiClient.extractHdneaToken(res.getOrNull()!!.streamUrl)
                if (newToken.isNotEmpty()) {
                    tokenHolder.set(newToken)
                    android.util.Log.d("TvPlayer", "Token refreshed for channel ${ch.channelNumber}")
                }
            }
        }
    }

    // Apply audio enhancements whenever the session id or any audio setting changes.
    // - dialogueProcessor: center-channel voice isolation (live, no rebuild needed), level 0..4
    // - audioEnhancer: LoudnessEnhancer makeup/normalize bound to the session id
    LaunchedEffect(voiceBoost) {
        dialogueProcessor.setLevel(voiceBoost)
    }
    LaunchedEffect(audioSessionId, voiceBoost, audioNormalize, audioKick) {
        audioEnhancer.apply(audioSessionId, audioNormalize, voiceBoost)
    }
    DisposableEffect(Unit) {
        onDispose { audioEnhancer.release() }
    }

    // Release the player when it is replaced (hardware-decoder toggle) or the screen leaves.
    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    // Stop playback when the app is backgrounded (Home / app switch) so audio doesn't keep playing
    // in the background, EXCEPT when Picture-in-Picture is active (the whole point of PiP is that
    // the video keeps playing in the small window). Resume when foregrounded (unless user paused).
    val lifecycleOwner = LocalLifecycleOwner.current
    val userPausedState = rememberUpdatedState(userPaused)
    val inPip = remember { mutableStateOf(false) }
    val pipSupported = isTouch && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O
    DisposableEffect(lifecycleOwner, exoPlayer, pipSupported) {
        val act = playerActivity
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    // The system put us into PiP (auto-enter on swipe-home, Android 12+).
                    if (act?.isInPictureInPictureMode == true) inPip.value = true
                    else exoPlayer.pause()
                }
                Lifecycle.Event.ON_START -> {
                    if (inPip.value) inPip.value = false
                    if (!userPausedState.value) exoPlayer.play()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // Hide all overlays while in PiP — the video must stand alone in the tiny window.
    LaunchedEffect(inPip.value) {
        if (inPip.value) showOverlay = false
    }

    // ─── MediaSession ───
    // Publishes the player to the system so Bluetooth headset buttons, Google Assistant and the
    // Android 13+ media controls in the notification shade can drive playback. (Background audio
    // via a MediaSessionService + media notification is a larger architectural change, deliberately
    // out of scope — the session is bound to the player's screen lifetime like the player itself.)
    val mediaSession = remember(exoPlayer) {
        runCatching {
            val sessionIntent = android.content.Intent(context, com.fenyx.jtv.MainActivity::class.java)
            val pendingIntent = android.app.PendingIntent.getActivity(
                context, 0, sessionIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            androidx.media3.session.MediaSession.Builder(context, exoPlayer)
                .setSessionActivity(pendingIntent)
                .build()
        }.getOrNull()
    }
    DisposableEffect(mediaSession) {
        onDispose { runCatching { mediaSession?.release() } }
    }

    // ─── Picture-in-Picture (phones, API 26+) ───
    // [inPip]/[pipSupported] live up top next to the lifecycle observer (which keeps playback
    // running while in PiP). Here we only build the params: 16:9 aspect ratio, and on Android 12+
    // auto-enter on swipe-home while actually playing.
    fun buildPipParams(): android.app.PictureInPictureParams =
        android.app.PictureInPictureParams.Builder()
            .setAspectRatio(android.util.Rational(16, 9))
            .apply {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    setAutoEnterEnabled(exoPlayer.isPlaying)
                }
            }
            .build()

    // Android 12+ auto-enter reads the LAST params set via setPictureInPictureParams, so keep them
    // fresh (reflecting whether we're actually playing). Called on every play/pause transition.
    LaunchedEffect(userPaused) {
        if (pipSupported) {
            runCatching { playerActivity?.setPictureInPictureParams(buildPipParams()) }
        }
    }

    // ─── On-screen volume (touch) ───
    // STREAM_MUSIC control for the right-edge swipe gesture (the classical player gesture — no
    // on-screen slider). Reads/writes silently so the system volume UI doesn't double-flash over
    // the video. lastAudibleVolume is kept so a future mute action can restore the level.
    val audioManager = remember {
        context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
    }
    var volumeLevel by remember { mutableStateOf(0f) }
    var lastAudibleVolume by remember { mutableStateOf(0.5f) }
    val maxVolume = remember { audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    fun readVolume() {
        volumeLevel = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC).toFloat() / maxVolume
        if (volumeLevel > 0.01f) lastAudibleVolume = volumeLevel
    }
    fun applyVolume(v: Float) {
        volumeLevel = v.coerceIn(0f, 1f)
        if (volumeLevel > 0.01f) lastAudibleVolume = volumeLevel
        audioManager.setStreamVolume(
            android.media.AudioManager.STREAM_MUSIC,
            (volumeLevel * maxVolume).toInt().coerceIn(0, maxVolume),
            0 // no flags: keep the system volume toast off the video
        )
    }

    // Current time. Formatter is hoisted (was reallocated on every 30s tick).
    val clockFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val bannerTimeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    var currentTime by remember { mutableStateOf("") }
    // Shared slow clock (ms) for the now-playing progress bar; 15s granularity is plenty for a
    // 30–60min programme and keeps recompositions rare on weak CPUs.
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = clockFormat.format(Date())
            nowMs = System.currentTimeMillis()
            delay(15000)
        }
    }

    // ─── Now-playing guide data (P2) ───
    // One lazy native fetch per logical channel; the semaphore in MainViewModel caps concurrency.
    val channelIdForEpg = currentChannel?.id
    LaunchedEffect(channelIdForEpg) {
        channelIdForEpg?.let(onRequestChannelEpg)
    }
    val currentProgram = remember(playerEpgData, channelIdForEpg, nowMs) {
        val id = channelIdForEpg ?: return@remember null
        playerEpgData[id]?.find { it.startMs <= nowMs && it.stopMs > nowMs }
    }

    // Replay position ticker (VOD): drives banner progress + the Phase-3 control bar.
    var replayPosMs by remember { mutableLongStateOf(0L) }
    var replayDurMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(catchup, exoPlayer) {
        while (catchup != null) {
            replayPosMs = exoPlayer.currentPosition.coerceAtLeast(0)
            replayDurMs = exoPlayer.duration.takeIf { it > 0 } ?: 0
            delay(500)
        }
        replayDurMs = 0
    }
    fun mmss(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        return String.format(Locale.US, "%d:%02d", total / 60, total % 60)
    }

    // Live-edge distance for the banner dot. ExoPlayer reports how far BEHIND the live edge we
    // play (we target ~20s); TIME_UNSET when unknown (e.g. VOD-like manifests).
    var liveBehindSec by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(exoPlayer) {
        while (true) {
            val offset = exoPlayer.currentLiveOffset
            liveBehindSec = if (offset == androidx.media3.common.C.TIME_UNSET) null
                            else (offset / 1000).toInt().coerceAtLeast(0)
            delay(5_000)
        }
    }

    // ─── Key handler ───
    var numericBuffer by remember { mutableStateOf("") }
    var showNumericOverlay by remember { mutableStateOf(false) }
    var numericJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // Long-press OK to pause/resume. A short OK still toggles the info overlay (or resumes if paused).
    var centerLongFired by remember { mutableStateOf(false) }
    var centerLongJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // Long-press → cycles the aspect/view mode with a brief on-screen confirmation; a short → still
    // opens the settings panel. Mirrors the long-OK pattern so repeats don't re-trigger.
    var rightLongFired by remember { mutableStateOf(false) }
    var rightLongJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    // Long-press INFO → diagnostics overlay.
    var infoLongFired by remember { mutableStateOf(false) }
    var infoLongJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var aspectOsd by remember { mutableStateOf<String?>(null) }
    val resizeModes = listOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT to "Fit",
        AspectRatioFrameLayout.RESIZE_MODE_FILL to "Fill",
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Zoom",
        AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH to "Stretch W",
        AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT to "Stretch H"
    )
    fun cycleAspectRatio() {
        val idx = resizeModes.indexOfFirst { it.first == resizeMode }
        val next = resizeModes[(idx + 1).mod(resizeModes.size)]
        resizeMode = next.first
        scope.launch { settingsManager.setPlayerResizeMode(next.first) }
        aspectOsd = next.second
    }
    LaunchedEffect(aspectOsd) {
        if (aspectOsd != null) {
            delay(1200)
            aspectOsd = null
        }
    }

    // Live numpad matching: as digits accumulate, preview which channels they point at.
    val numpadMatches = remember(numericBuffer, currentChannels) {
        com.fenyx.jtv.data.ChannelFilter.findByNumberPrefix(currentChannels, numericBuffer)
    }

    var listSelectedIndex by remember { mutableIntStateOf(0) }
    var categorySelectedIndex by remember { mutableIntStateOf(0) }

    // ─── Alphabet jump rail (channel sidebar) ───
    // Letters present in the current (filtered/sorted) list. The rail shares the sidebar's
    // index-driven key model: ← activates it, ↑↓ moves letter + jumps the selection, →/OK dives
    // back into the list at the jumped position.
    val railLetters = remember(currentChannels) {
        currentChannels.mapNotNull { ch -> ch.name.trim().firstOrNull()?.uppercaseChar() }
            .filter { it in 'A'..'Z' }
            .distinct()
            .sorted()
    }
    var letterActive by remember { mutableStateOf(false) }
    var letterIndex by remember { mutableIntStateOf(0) }

    fun activateLetterRail() {
        if (railLetters.isEmpty()) return
        // Start on the letter of whatever channel is selected right now.
        val cur = currentChannels.getOrNull(listSelectedIndex)?.name?.trim()?.firstOrNull()?.uppercaseChar()
        letterIndex = railLetters.indexOf(cur).takeIf { it >= 0 } ?: 0
        letterActive = true
    }

    fun jumpToLetter(pos: Int) {
        if (railLetters.isEmpty()) return
        letterIndex = ((pos % railLetters.size) + railLetters.size) % railLetters.size
        val target = railLetters[letterIndex]
        val idx = com.fenyx.jtv.data.ChannelFilter.firstIndexForLetter(
            currentChannels.map { it.name }, target
        )
        if (idx >= 0) listSelectedIndex = idx
    }

    // Double-press INFO opens the programme sheet; a single press toggles the banner.
    var lastInfoPressAt by remember { mutableLongStateOf(0L) }

    // Zap-preview strip auto-cancels back to the playing channel after 6s of INACTIVITY — the
    // timer restarts on every browse move so active browsing never gets yanked back mid-list.
    LaunchedEffect(showZapStrip, stripIndex) {
        if (showZapStrip) {
            delay(6_000)
            showZapStrip = false
        }
    }

    LaunchedEffect(showChannelList, currentChannels) {
        if (showChannelList) {
            listSelectedIndex = currentIndex
            letterActive = false
        }
    }
    LaunchedEffect(showCategoryList, groups) {
        if (showCategoryList) categorySelectedIndex = groups.indexOf(currentGroup).coerceAtLeast(0)
    }

    fun commitNumericEntry() {
        val num = numericBuffer.toIntOrNull()
        if (num != null && currentChannels.isNotEmpty()) {
            val idx = (num - 1).coerceIn(0, currentChannels.size - 1)
            currentIndex = idx
        }
        numericBuffer = ""
        showNumericOverlay = false
    }

    // ─── Jump to LIVE ───
    // Playback targets ~20s behind the edge; a long pause can leave you minutes behind with no
    // way back before now. seekToDefaultPosition() snaps a live window back to the edge.
    fun jumpToLive() {
        exoPlayer.seekToDefaultPosition()
        exoPlayer.play()
        userPaused = false
        showOverlay = true
    }

    // Shared by long-OK (remote) and long-press-on-video (touch).
    fun pauseToggle() {
        if (userPaused) { exoPlayer.play(); userPaused = false }
        else { exoPlayer.pause(); userPaused = true; showOverlay = true }
    }

    // Zap used by both the remote keys and the on-screen ▲▼ touch buttons.
    fun zapBy(delta: Int) {
        if (currentChannels.isEmpty()) return
        currentIndex = (currentIndex + delta + currentChannels.size) % currentChannels.size
        showOverlay = true
    }

    // On resume after a LONG pause (>3 min behind), snap forward to the live edge automatically —
    // nobody re-joins a live broadcast on purpose several minutes late.
    LaunchedEffect(userPaused) {
        if (!userPaused) {
            val off = exoPlayer.currentLiveOffset
            if (off != androidx.media3.common.C.TIME_UNSET && off > 180_000) {
                android.util.Log.d("TvPlayer", "Resume ${off / 1000}s behind live -> jumping to LIVE")
                exoPlayer.seekToDefaultPosition()
            }
        } else {
            // Refresh the behind-edge figure immediately so the pause hint is accurate.
            val off = exoPlayer.currentLiveOffset
            liveBehindSec = if (off == androidx.media3.common.C.TIME_UNSET) null
                            else (off / 1000).toInt().coerceAtLeast(0)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                // ── Long-press OK = pause/resume (only in the normal watching view) ──
                val isCenter = keyEvent.key == Key.Enter ||
                    keyEvent.key == Key.DirectionCenter ||
                    keyEvent.key == Key.NumPadEnter
                val normalWatching = !showCategoryList && !showChannelList &&
                    !showAudioSelector && !showQualitySelector && !showSettingsOverlay &&
                    !showLangSelector && !showNumericOverlay && !showProgrammes &&
                    !showZapStrip && !showCatchupBar && playbackError == null
                if (isCenter && normalWatching) {
                    when (keyEvent.type) {
                        KeyEventType.KeyDown -> {
                            if (keyEvent.nativeKeyEvent.repeatCount == 0) {
                                centerLongFired = false
                                centerLongJob?.cancel()
                                centerLongJob = scope.launch {
                                    delay(450) // long-press threshold
                                    centerLongFired = true
                                    if (userPaused) { exoPlayer.play(); userPaused = false }
                                    else { exoPlayer.pause(); userPaused = true; showOverlay = true }
                                }
                            }
                            return@onPreviewKeyEvent true
                        }
                        KeyEventType.KeyUp -> {
                            centerLongJob?.cancel()
                            if (!centerLongFired) {
                                // Short press: resume if paused; during a replay reveal the control
                                // bar; otherwise toggle the info overlay.
                                if (userPaused) { exoPlayer.play(); userPaused = false }
                                else if (catchup != null) showCatchupBar = true
                                else showOverlay = !showOverlay
                            }
                            centerLongFired = false
                            return@onPreviewKeyEvent true
                        }
                        else -> return@onPreviewKeyEvent true
                    }
                }

                // ── Long-press → cycles aspect ratio; short → opens the settings panel ──
                if (normalWatching && keyEvent.key == Key.DirectionRight) {
                    when (keyEvent.type) {
                        KeyEventType.KeyDown -> {
                            if (keyEvent.nativeKeyEvent.repeatCount == 0) {
                                rightLongFired = false
                                rightLongJob?.cancel()
                                rightLongJob = scope.launch {
                                    delay(450)
                                    rightLongFired = true
                                    cycleAspectRatio()
                                }
                            }
                            return@onPreviewKeyEvent true
                        }
                        KeyEventType.KeyUp -> {
                            rightLongJob?.cancel()
                            if (!rightLongFired) showSettingsOverlay = true
                            rightLongFired = false
                            return@onPreviewKeyEvent true
                        }
                        else -> return@onPreviewKeyEvent true
                    }
                }

                // ── Long-press INFO → diagnostics overlay; short/double keep banner/sheet behavior ──
                if (normalWatching && keyEvent.key == Key.Info) {
                    when (keyEvent.type) {
                        KeyEventType.KeyDown -> {
                            if (keyEvent.nativeKeyEvent.repeatCount == 0) {
                                infoLongFired = false
                                infoLongJob?.cancel()
                                infoLongJob = scope.launch {
                                    delay(600)
                                    infoLongFired = true
                                    showStats = !showStats
                                }
                            }
                            return@onPreviewKeyEvent true
                        }
                        KeyEventType.KeyUp -> {
                            infoLongJob?.cancel()
                            if (!infoLongFired) {
                                if (showProgrammes) {
                                    showProgrammes = false
                                } else {
                                    val nowMs2 = System.currentTimeMillis()
                                    if (nowMs2 - lastInfoPressAt < 450) {
                                        showProgrammes = true
                                        showOverlay = true
                                        lastInfoPressAt = 0L
                                    } else {
                                        showOverlay = !showOverlay
                                        lastInfoPressAt = nowMs2
                                    }
                                }
                            }
                            infoLongFired = false
                            return@onPreviewKeyEvent true
                        }
                        else -> return@onPreviewKeyEvent true
                    }
                }

                // ── Replay control bar open: hand arrows/OK to the focused control; keep Back ──
                if (showCatchupBar) {
                    when {
                        keyEvent.type == KeyEventType.KeyDown &&
                            (keyEvent.key == Key.DirectionUp || keyEvent.key == Key.DirectionDown ||
                                keyEvent.key == Key.DirectionLeft || keyEvent.key == Key.DirectionRight ||
                                isCenter) -> return@onPreviewKeyEvent false // focused bar control handles it
                        else -> {}
                    }
                }

                if (keyEvent.type == KeyEventType.KeyDown) {
                    val digit = when (keyEvent.key) {
                        Key.Zero -> 0; Key.One -> 1; Key.Two -> 2; Key.Three -> 3
                        Key.Four -> 4; Key.Five -> 5; Key.Six -> 6; Key.Seven -> 7
                        Key.Eight -> 8; Key.Nine -> 9
                        else -> null
                    }
                    if (digit != null) {
                        if (!(numericBuffer.isEmpty() && digit == 0) && numericBuffer.length < 4) {
                            numericBuffer += digit.toString()
                            showNumericOverlay = true
                            numericJob?.cancel()
                            numericJob = scope.launch {
                                delay(1200)
                                commitNumericEntry()
                            }
                        }
                        return@onPreviewKeyEvent true
                    }

                    if (keyEvent.key == Key.Back || keyEvent.key == Key.Escape) {
                        if (showStats) { showStats = false; return@onPreviewKeyEvent true }
                        if (showCatchupBar) { showCatchupBar = false; return@onPreviewKeyEvent true }
                        if (showAudioSelector) { showAudioSelector = false; return@onPreviewKeyEvent true }
                        if (showLangSelector) { showLangSelector = false; return@onPreviewKeyEvent true }
                        if (showQualitySelector) { showQualitySelector = false; return@onPreviewKeyEvent true }
                        if (showProgrammes) { showProgrammes = false; return@onPreviewKeyEvent true }
                        if (showSettingsOverlay) { showSettingsOverlay = false; return@onPreviewKeyEvent true }
                        if (showCategoryList) { showCategoryList = false; return@onPreviewKeyEvent true }
                        if (showChannelList) { showChannelList = false; return@onPreviewKeyEvent true }
                        if (showNumericOverlay) { showNumericOverlay = false; numericBuffer = ""; return@onPreviewKeyEvent true }
                        if (showZapStrip) { showZapStrip = false; return@onPreviewKeyEvent true }
                        return@onPreviewKeyEvent false
                    }

                    when (keyEvent.key) {
                        Key.ChannelUp -> {
                            if (currentChannels.isNotEmpty() && !showSettingsOverlay) {
                                currentIndex = (currentIndex + 1) % currentChannels.size
                                showOverlay = true
                            }
                            true
                        }
                        Key.ChannelDown -> {
                            if (currentChannels.isNotEmpty() && !showSettingsOverlay) {
                                currentIndex = (currentIndex - 1 + currentChannels.size) % currentChannels.size
                                showOverlay = true
                            }
                            true
                        }
                        Key.DirectionUp -> {
                            if (showSettingsOverlay || showProgrammes) {
                                false
                            } else if (showZapStrip) {
                                if (currentChannels.isNotEmpty()) {
                                    stripIndex = (stripIndex - 1 + currentChannels.size) % currentChannels.size
                                }
                                true
                            } else if (showCategoryList) {
                                if (groups.isNotEmpty()) {
                                    categorySelectedIndex = (categorySelectedIndex - 1 + groups.size) % groups.size
                                }
                                true
                            } else if (showChannelList) {
                                if (letterActive) jumpToLetter(letterIndex - 1)
                                else if (currentChannels.isNotEmpty()) {
                                    listSelectedIndex = (listSelectedIndex - 1 + currentChannels.size) % currentChannels.size
                                }
                                true
                            } else if (zapPreviewEnabled && currentChannels.isNotEmpty()) {
                                // Optional preview mode: open the strip instead of zapping instantly.
                                stripIndex = currentIndex
                                showZapStrip = true
                                showOverlay = false
                                true
                            } else {
                                if (currentChannels.isNotEmpty()) {
                                    currentIndex = (currentIndex - 1 + currentChannels.size) % currentChannels.size
                                    showOverlay = true
                                }
                                true
                            }
                        }
                        Key.DirectionDown -> {
                            if (showSettingsOverlay || showProgrammes) {
                                false
                            } else if (showZapStrip) {
                                if (currentChannels.isNotEmpty()) {
                                    stripIndex = (stripIndex + 1) % currentChannels.size
                                }
                                true
                            } else if (showCategoryList) {
                                if (groups.isNotEmpty()) {
                                    categorySelectedIndex = (categorySelectedIndex + 1) % groups.size
                                }
                                true
                            } else if (showChannelList) {
                                if (letterActive) jumpToLetter(letterIndex + 1)
                                else if (currentChannels.isNotEmpty()) {
                                    listSelectedIndex = (listSelectedIndex + 1) % currentChannels.size
                                }
                                true
                            } else if (zapPreviewEnabled && currentChannels.isNotEmpty()) {
                                stripIndex = currentIndex
                                showZapStrip = true
                                showOverlay = false
                                true
                            } else {
                                if (currentChannels.isNotEmpty()) {
                                    currentIndex = (currentIndex + 1) % currentChannels.size
                                    showOverlay = true
                                }
                                true
                            }
                        }
                        Key.DirectionLeft -> {
                            if (showSettingsOverlay) {
                                showSettingsOverlay = false
                                true
                            } else if (showCategoryList) {
                                false
                            } else if (showChannelList) {
                                if (letterActive) {
                                    letterActive = false
                                    showCategoryList = true
                                } else {
                                    activateLetterRail()
                                }
                                true
                            } else {
                                showChannelList = true
                                showOverlay = true
                                true
                            }
                        }
                        Key.DirectionRight -> {
                            if (showCategoryList) { showCategoryList = false; true }
                            else if (showChannelList) {
                                if (letterActive) { letterActive = false; true }
                                else { showChannelList = false; true }
                            }
                            else if (!showSettingsOverlay) { showSettingsOverlay = true; true }
                            else false
                        }
                        Key.Enter, Key.DirectionCenter, Key.NumPadEnter -> {
                            if (showZapStrip) {
                                // Confirm the strip selection: tune and close.
                                currentIndex = stripIndex
                                showZapStrip = false
                                showOverlay = true
                                true
                            } else if (showCategoryList) {
                                val group = groups.getOrNull(categorySelectedIndex)
                                if (group != null) {
                                    currentGroup = group // currentChannels derives from this
                                    currentIndex = 0
                                    showCategoryList = false
                                }
                                true
                            } else if (showChannelList) {
                                if (letterActive) {
                                    // OK on the rail dives back into the list at the jumped position.
                                    letterActive = false
                                } else {
                                    currentIndex = listSelectedIndex
                                    showChannelList = false
                                    showCategoryList = false
                                }
                                true
                            } else if (playbackError != null) {
                                // Let the focused Retry / Other-channels button take the OK press —
                                // the Retry button auto-focuses when the error appears, so a plain
                                // OK still retries exactly as before.
                                false
                            } else {
                                showOverlay = !showOverlay
                                true
                            }
                        }
                        Key.Back, Key.Escape -> {
                            if (showCategoryList) { showCategoryList = false; true }
                            else if (showChannelList) { showChannelList = false; true }
                            else if (showOverlay) { showOverlay = false; true }
                            else { onBack(); true }
                        }
                        // ── Standard TV media remote keys ──
                        // Play/Pause toggle from the dedicated remote key.
                        Key.MediaPlayPause -> {
                            if (userPaused) { exoPlayer.play(); userPaused = false }
                            else { exoPlayer.pause(); userPaused = true; showOverlay = true }
                            true
                        }
                        Key.MediaPlay -> {
                            if (userPaused) { exoPlayer.play(); userPaused = false }
                            true
                        }
                        Key.MediaPause -> {
                            exoPlayer.pause(); userPaused = true; showOverlay = true
                            true
                        }
                        // MEDIA_NEXT/PREVIOUS zap channels like CH+/-. While PAUSED, ⏭ instead
                        // means "jump to the live edge" — zapping away mid-pause is rarely the intent.
                        Key.MediaNext -> {
                            if (userPaused) {
                                jumpToLive()
                            } else if (currentChannels.isNotEmpty() && !showSettingsOverlay) {
                                currentIndex = (currentIndex + 1) % currentChannels.size
                                showOverlay = true
                            }
                            true
                        }
                        Key.MediaPrevious -> {
                            if (currentChannels.isNotEmpty() && !showSettingsOverlay) {
                                currentIndex = (currentIndex - 1 + currentChannels.size) % currentChannels.size
                                showOverlay = true
                            }
                            true
                        }
                        // GUIDE opens the channel list; INFO toggles the banner, double-press opens
                        // the programme sheet.
                        Key.Guide -> {
                            if (!showSettingsOverlay) { showChannelList = true; showOverlay = true }
                            true
                        }
                        Key.Info -> {
                            if (!showChannelList && !showCategoryList && !showSettingsOverlay && !showZapStrip) {
                                if (showProgrammes) {
                                    showProgrammes = false
                                } else {
                                    val nowMs2 = System.currentTimeMillis()
                                    if (nowMs2 - lastInfoPressAt < 450) {
                                        showProgrammes = true
                                        showOverlay = true
                                        lastInfoPressAt = 0L
                                    } else {
                                        showOverlay = !showOverlay
                                        lastInfoPressAt = nowMs2
                                    }
                                }
                            }
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        AndroidView(
            factory = { ctx ->
                androidx.media3.ui.PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    keepScreenOn = true
                }
            },
            update = { view ->
                view.resizeMode = resizeMode
                // Reattach if the player instance was rebuilt (e.g. hardware-decoder toggle).
                if (view.player !== exoPlayer) view.player = exoPlayer
            },
            modifier = Modifier.fillMaxSize()
        )

        if (isTouch) {
            val tapError by rememberUpdatedState(playbackError)
            val tapBarOpen by rememberUpdatedState(showCatchupBar)
            // Panels read via rememberUpdatedState: the gesture lambda is stale otherwise.
            val tapStatsOpen by rememberUpdatedState(showStats)
            val tapProgrammesOpen by rememberUpdatedState(showProgrammes)
            val tapSettingsOpen by rememberUpdatedState(showSettingsOverlay)
            val tapCategoryOpen by rememberUpdatedState(showCategoryList)
            val tapChannelsOpen by rememberUpdatedState(showChannelList)
            val tapNumericOpen by rememberUpdatedState(showNumericOverlay)
            val tapZapStripOpen by rememberUpdatedState(showZapStrip)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                when {
                                    tapError != null -> {} // error overlay owns this tap (its buttons)
                                    tapBarOpen -> showCatchupBar = false
                                    // Tapping anywhere outside an open panel closes it — the
                                    // panels themselves don't cover the whole screen, so the
                                    // old behaviour (toggle overlay) left them stuck open.
                                    tapStatsOpen -> showStats = false
                                    tapProgrammesOpen -> showProgrammes = false
                                    tapSettingsOpen -> showSettingsOverlay = false
                                    tapCategoryOpen -> showCategoryList = false
                                    tapChannelsOpen -> { letterActive = false; showChannelList = false }
                                    tapNumericOpen -> { showNumericOverlay = false; numericBuffer = "" }
                                    tapZapStripOpen -> showZapStrip = false
                                    else -> showOverlay = !showOverlay
                                }
                            },
                            onLongPress = { if (tapError == null && !tapBarOpen) pauseToggle() }
                        )
                    }
            )

            // ─── Right-edge volume gesture (touch) ───
            // Classical player gesture: swipe up/down anywhere along the right ~30% of the screen
            // (portrait AND landscape) to change volume. Only vertical drags are consumed, so taps
            // in the strip still reach the full-screen tap handler — no dead zone.
            var volumeOsd by remember { mutableStateOf<Float?>(null) }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .fillMaxWidth(0.30f)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = { readVolume() },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                val next = (volumeLevel - dragAmount / (size.height.toFloat() * 0.85f))
                                    .coerceIn(0f, 1f)
                                applyVolume(next)
                                volumeOsd = next
                            }
                        )
                    }
            )
            // Volume OSD: a small centered readout while the finger is down, fading right after.
            LaunchedEffect(volumeOsd) {
                if (volumeOsd != null) {
                    delay(900)
                    volumeOsd = null
                }
            }
            AnimatedVisibility(
                visible = volumeOsd != null,
                enter = fadeIn(tween(TvMotion.ms())),
                exit = fadeOut(tween(TvMotion.ms())),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if ((volumeOsd ?: 0f) <= 0.01f) "🔇" else "🔊",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "${((volumeOsd ?: 0f) * 100).toInt()}%",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // ─── Diagnostics overlay (long-press INFO) ───
        AnimatedVisibility(
            visible = showStats,
            enter = fadeIn(tween(TvMotion.ms())),
            exit = fadeOut(tween(TvMotion.ms())),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(
                    start = overscanH(),
                    top = overscanV() + 8.dp
                )
        ) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.78f))
                    .clickable { showStats = false } // touch: tap anywhere on the panel closes it
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                statsTick // read so the 1s ticker recomposes the live numbers
                val bufferedSec =
                    ((exoPlayer.bufferedPosition - exoPlayer.currentPosition) / 1000).coerceAtLeast(0)
                StatLine("VIDEO", statVideo)
                StatLine("AUDIO", statAudio)
                StatLine("BUFFER", "${bufferedSec}s ahead")
                StatLine("LIVE EDGE", liveBehindSec?.let { "+${it}s behind" })
                StatLine("QUALITY", quality)
                StatLine("SPEED", "${exoPlayer.playbackParameters.speed}×")
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "hold INFO or tap to close",
                    color = Color.White.copy(alpha = 0.45f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        // ─── Zap splash card (tune-in) ───
        // Replaces the old center spinner-over-frozen-frame with a branded channel card so the
        // ~1s tune-in reads as intentional instead of a stall. Pure local drawing + the logo Coil
        // is already fetching — no extra work on weak TVs.
        val zapMs = TvMotion.ms()
        AnimatedVisibility(
            visible = isBuffering && playbackError == null,
            enter = fadeIn(tween(zapMs)) + scaleIn(initialScale = 0.92f, animationSpec = tween(zapMs)),
            exit = fadeOut(tween(zapMs)),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(
                    start = overscanH(),
                    // Clears the bottom touch dock + hint bar (~120dp combined on portrait phones);
                    // the old 40dp put the tuning card UNDER the dock, their elements colliding.
                    bottom = 132.dp
                )
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentChannel?.logoUrl?.isNotEmpty() == true) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(currentChannel.logoUrl)
                            .size(112)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.size(52.dp).clip(CircleShape).background(Color.White),
                        contentScale = ContentScale.Fit
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        String.format(Locale.US, "%02d", currentIndex + 1),
                        color = TvPrimary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        currentChannel?.name ?: "Tuning in…",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                CircularProgressIndicator(color = TvPrimary, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            }
        }

        // ─── Pause Indicator ───
        if (userPaused) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(width = 12.dp, height = 40.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color.White)
                            )
                            Box(
                                modifier = Modifier
                                    .size(width = 12.dp, height = 40.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color.White)
                            )
                        }
                    }
                    // Behind-edge hint + the way back: ⏭ (MediaNext) snaps to LIVE. The chip is a
                    // real button: live drift → seekToDefaultPosition(); catch-up replay → exit
                    // the replay and reload the live feed (remotes have ⏭, phones only had text).
                    Spacer(modifier = Modifier.height(14.dp))
                    val behind = liveBehindSec ?: 0
                    Text(
                        buildString {
                            append("⏭  Jump to LIVE")
                            if (liveBehindSec != null && behind > 35) {
                                val m = behind / 60; val s = behind % 60
                                append("   ·   +${m}:${s.toString().padStart(2, '0')} behind")
                            }
                        },
                        color = if (liveBehindSec != null && behind > 120) Color(0xFFFFB300) else Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .clickable {
                                if (catchup != null) {
                                    // Exit the replay and reload the live stream (mirrors the
                                    // replay bar's ● LIVE action).
                                    startReplay(null)
                                    showCatchupBar = false
                                    showOverlay = true
                                    retryCount.intValue = 0
                                    playbackError = null
                                    isBuffering = true
                                    streamRefreshTrigger++
                                    userPaused = false
                                } else {
                                    jumpToLive()
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // ─── Playback Error Overlay ───
        // Auto-recovery exhausted. Retry is auto-focused so a plain OK still retries; the second
        // button offers an explicit way out without remembering "press Back".
        if (playbackError != null && !isBuffering) {
            val retryFocus = remember { FocusRequester() }
            LaunchedEffect(playbackError) {
                runCatching { retryFocus.requestFocus() }
            }
            fun manualRetry() {
                playbackError = null
                retryCount.intValue = 0
                isBuffering = true
                streamRefreshTrigger++
            }
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("⚠", fontSize = 40.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        playbackError ?: "",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        currentChannel?.name ?: "",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(
                            modifier = Modifier.focusRequester(retryFocus),
                            onClick = { manualRetry() },
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = TvPrimaryContainer,
                                focusedContainerColor = TvPrimary
                            )
                        ) {
                            Text(
                                "Retry",
                                modifier = Modifier.padding(horizontal = 28.dp, vertical = 10.dp),
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Surface(
                            onClick = onBack,
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = TvDarkSurfaceVariant,
                                focusedContainerColor = TvDarkSurface.copy(alpha = 0.9f)
                            ),
                            border = ClickableSurfaceDefaults.border(
                                focusedBorder = androidx.tv.material3.Border(
                                    border = androidx.compose.foundation.BorderStroke(2.dp, TvFocusBorder),
                                    shape = RoundedCornerShape(10.dp)
                                )
                            )
                        ) {
                            Text(
                                "Other channels",
                                modifier = Modifier.padding(horizontal = 28.dp, vertical = 10.dp),
                                color = TvOnSurface,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // ─── Numeric Channel Entry Overlay (+ live match preview) ───
        AnimatedVisibility(
            visible = showNumericOverlay && numericBuffer.isNotEmpty(),
            enter = fadeIn(tween(TvMotion.ms())) + slideInVertically(tween(TvMotion.ms())) { -it / 2 },
            exit = fadeOut(tween(TvMotion.ms())),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Column(
                modifier = Modifier
                    .padding(top = 40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = numericBuffer,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                // Preview which channels the typed digits currently point at.
                if (numpadMatches.isEmpty()) {
                    Text("No match", color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.labelSmall)
                } else {
                    numpadMatches.forEach { (position, channel) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "$position",
                                color = TvPrimary,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.width(28.dp)
                            )
                            Text(
                                channel.name,
                                color = Color.White.copy(alpha = 0.85f),
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // ─── Channel Info Overlay (Top) ───
        // Narrow portrait windows (phones) get a denser overlay: smaller logo/clock and a
        // fluid progress bar — the TV-tuned sizes squeezed the channel name into ellipsis.
        val compactLayout = isCompactWidth()
        AnimatedVisibility(
            // Hidden while any full-height panel (channel/category list, settings) is open: the
            // banner showing through the translucent panel read as ghost text, and its progress
            // bar struck through the list rows like a rendering glitch.
            visible = showOverlay && !showChannelList && !showCategoryList && !showSettingsOverlay,
            enter = fadeIn(tween(TvMotion.ms())) + slideInVertically(tween(TvMotion.ms())) { -it },
            exit = fadeOut(tween(TvMotion.ms())) + slideOutVertically(tween(TvMotion.ms())) { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                        )
                    )
                    // Overscan-safe: keep the channel info off the panel edge (gradient still bleeds).
                    // Resolves per device — compact margins on phones.
                    .padding(horizontal = overscanH(), vertical = overscanV())
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val ch = currentChannel
                    if (ch?.logoUrl?.isNotEmpty() == true) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(ch.logoUrl)
                                .size(96)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier.size(if (compactLayout) 38.dp else 48.dp).clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Channel number
                            Text(
                                String.format(Locale.US, "%02d", currentIndex + 1),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                ch?.name ?: "",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val isFav = favoriteChannels.contains(ch?.id)

                            if (catchup != null) {
                                // Replay mode badge — replaces the LIVE dot entirely.
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFFFFB300).copy(alpha = 0.22f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        "▶ REPLAY",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFFFFB300),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else
                            // Pulsing live-state chip: solid red at the edge, amber "+Ns" when
                            // playback has drifted well behind live (e.g. after a long stall).
                            if (!isFav) {
                                val pulse = rememberInfiniteTransition(label = "liveDot")
                                val dotAlpha by pulse.animateFloat(
                                    initialValue = 0.45f,
                                    targetValue = 1f,
                                    animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
                                    label = "dotAlpha"
                                )
                                val behind = liveBehindSec ?: 0
                                val atEdge = liveBehindSec == null || behind <= 35
                                Row(
                                    modifier = Modifier
                                        .background(TvPrimary.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(if (atEdge) TvLiveRed else Color(0xFFFFB300).copy(alpha = dotAlpha))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        if (atEdge) "LIVE" else "+${behind}s",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (atEdge) TvPrimary else Color(0xFFFFB300),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFFFFD700).copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        "⭐ FAVORITE",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFFFFD700),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                ch?.group ?: "",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }

                        // ─── Now-playing line (native EPG, fetched lazily once per channel) ───
                        val displayProg = catchup ?: currentProgram
                        displayProg?.let { prog ->
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                prog.title,
                                color = Color.White.copy(alpha = 0.95f),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            // fillMaxWidth + weight: the fixed 220dp bar plus its time label
                            // overflowed narrow portrait windows by a few dp, clipping the label.
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // VOD progress from the player clock; live from the programme window.
                                val progress: Float
                                val label: String
                                if (catchup != null && replayDurMs > 0) {
                                    progress = (replayPosMs.toFloat() / replayDurMs).coerceIn(0f, 1f)
                                    label = "${mmss(replayPosMs)} / ${mmss(replayDurMs)}"
                                } else {
                                    val durationMs = (prog.stopMs - prog.startMs).coerceAtLeast(1)
                                    progress = ((nowMs - prog.startMs).toFloat() / durationMs).coerceIn(0f, 1f)
                                    label =
                                        "${bannerTimeFormat.format(Date(prog.startMs))} – ${bannerTimeFormat.format(Date(prog.stopMs))}"
                                }
                                androidx.compose.material3.LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(1.5.dp)),
                                    color = if (catchup != null) Color(0xFFFFB300) else TvPrimary,
                                    // 0.15 vanished on the black video — the bar read as a
                                    // floating dash with no track.
                                    trackColor = Color.White.copy(alpha = 0.35f)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    label,
                                    color = Color.White.copy(alpha = 0.55f),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                    // The clock is pushed right by the info Column's weight(1f) above — an extra
                    // weighted spacer here used to halve the name's width and truncate "Aaryaa TV"
                    // to "Aary…" with 200dp still free.
                    Text(
                        currentTime,
                        // Compact windows shrink the clock: at headline size it ate ~40% of a
                        // portrait phone's top row and forced the channel name into ellipsis.
                        style = if (compactLayout) MaterialTheme.typography.titleMedium
                                else MaterialTheme.typography.headlineMedium,
                        color = Color.White.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Light
                    )
                }
            }
        }

        // ─── Sleep-timer remaining chip ───
        // Visible whenever a timer is armed so it can't silently surprise-exit the app.
        AnimatedVisibility(
            visible = sleepRemainingSec > 0,
            enter = fadeIn(tween(TvMotion.ms())),
            exit = fadeOut(tween(TvMotion.ms())),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = overscanV(), end = overscanH())
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("⏻", color = TvPrimary, style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    String.format(Locale.US, "%d:%02d", sleepRemainingSec / 60, sleepRemainingSec % 60),
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // ─── Aspect-mode OSD (long-press →) ───
        AnimatedVisibility(
            visible = aspectOsd != null,
            enter = fadeIn(tween(TvMotion.ms())),
            exit = fadeOut(tween(TvMotion.ms())),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            ) {
                Text(aspectOsd ?: "", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }

        // ─── Touch control cluster (bottom dock) ───
        // Every remote-only action gets a tap target: channels, guide, numpad, aspect, rotate,
        // play/pause, stats, settings. Visible together with the overlay (same 5s auto-hide).
        // Docked bottom-center on ALL touch devices — the top-right slot collided with the
        // channel info overlay in portrait and (now that the player locks to landscape) also
        // clipped the EPG/progress lines in landscape. A bottom dock is thumb-reachable and
        // clears both the info overlay and the hint bar.
        val touchClusterVisible = isTouch && showOverlay && !showChannelList && !showCategoryList &&
            !showSettingsOverlay && !showProgrammes && !showZapStrip && !showCatchupBar &&
            !showNumericOverlay && !showAudioSelector && !showQualitySelector && !showLangSelector &&
            playbackError == null && !inPip.value
        AnimatedVisibility(
            visible = touchClusterVisible,
            enter = fadeIn(tween(TvMotion.ms())),
            exit = fadeOut(tween(TvMotion.ms())),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    // Clears the bottom hint bar (~50dp) plus breathing room.
                    bottom = 72.dp,
                    start = overscanH(),
                    end = overscanH()
                )
        ) {
            // FlowRow so the keys wrap instead of overflowing the window edge; centered in the dock.
            // Which buttons appear is user-configurable (Settings → Player Touch Dock).
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (SettingsManager.DOCK_CHANNELS in dockButtons)
                    TouchKey("☰", "Channels") { showChannelList = true; showOverlay = true }
                if (SettingsManager.DOCK_PROGRAMMES in dockButtons)
                    TouchKey("📅", "Programmes") {
                        channelIdForEpg?.let(onRequestChannelEpg) // never fetch with a blank id
                        showProgrammes = true
                        showOverlay = true
                    }
                if (SettingsManager.DOCK_NUMPAD in dockButtons)
                    TouchKey("#", "Channel number") { showTouchNumpad = true }
                if (SettingsManager.DOCK_ASPECT in dockButtons)
                    TouchKey("⛶", "Aspect ratio") { cycleAspectRatio() }
                if (SettingsManager.DOCK_ROTATE in dockButtons)
                    TouchKey("🔄", "Rotate screen") {
                        val act = context as? android.app.Activity
                        if (act != null) {
                            val landscape = act.resources.configuration.orientation ==
                                android.content.res.Configuration.ORIENTATION_LANDSCAPE
                            act.requestedOrientation = if (landscape)
                                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            else
                                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        }
                    }
                if (SettingsManager.DOCK_PIP in dockButtons)
                    TouchKey("⧉", "Picture in picture") {
                        if (pipSupported) {
                            runCatching { playerActivity?.enterPictureInPictureMode(buildPipParams()) }
                        }
                    }
                if (SettingsManager.DOCK_PAUSE in dockButtons)
                    TouchKey(if (userPaused) "▶" else "⏸", if (userPaused) "Play" else "Pause") { pauseToggle() }
                if (SettingsManager.DOCK_STATS in dockButtons)
                    TouchKey("📊", "Stream info") { showStats = !showStats }
                if (SettingsManager.DOCK_SETTINGS in dockButtons)
                    TouchKey("⚙", "Player settings") { showSettingsOverlay = true }
            }
        }

        // ─── Volume: right-edge swipe gesture only (see the touch gesture box above). The old
        // dock icon + slider panel were removed — swiping the right edge up/down is the classical,
        // one-handed control. ───

        // ─── Zap buttons (right edge, touch) ───
        if (zapEdgeButtons) AnimatedVisibility(
            visible = touchClusterVisible,
            enter = fadeIn(tween(TvMotion.ms())),
            exit = fadeOut(tween(TvMotion.ms())),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = overscanH())
        ) {
            // Pill container behind both keys: the 55%-black chips were invisible on the black
            // video, leaving the ▲▼ glyphs floating with no affordance.
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TouchKey("▲", "Next channel") { zapBy(+1) }
                TouchKey("▼", "Previous channel") { zapBy(-1) }
            }
        }

        // ─── On-screen numpad (touch) ───
        if (showTouchNumpad && isTouch) {
            TouchNumpadDialog(
                onSubmit = { num ->
                    if (currentChannels.isNotEmpty()) {
                        currentIndex = (num - 1).coerceIn(0, currentChannels.size - 1)
                    }
                    showTouchNumpad = false
                    showOverlay = true
                },
                onDismiss = { showTouchNumpad = false }
            )
        }

        // ─── Player Settings Overlay (Right) ───
        val settingsFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
        LaunchedEffect(showSettingsOverlay) {
            if (showSettingsOverlay) {
                try {
                    settingsFocusRequester.requestFocus()
                } catch (e: Exception) { }
            } else {
                try {
                    focusRequester.requestFocus()
                } catch (e: Exception) { }
            }
        }

        AnimatedVisibility(
            visible = showSettingsOverlay,
            enter = fadeIn(tween(TvMotion.ms())) + slideInHorizontally(tween(TvMotion.ms())) { it },
            exit = fadeOut(tween(TvMotion.ms())) + slideOutHorizontally(tween(TvMotion.ms())) { it },
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Box(
                modifier = Modifier
                    // Designed at 320dp for TVs; shrink to the window on narrow phones.
                    .fillMaxWidth(0.85f)
                    .widthIn(max = 320.dp)
                    .fillMaxHeight()
                    .background(TvDarkSurface.copy(alpha = 0.95f))
                    .padding(24.dp)
            ) {
                Column(
                    // Scrollable so all items (incl. Open Settings at the bottom) are reachable; the
                    // focused item auto-scrolls into view as you press Down on the remote.
                    modifier = Modifier
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        "Player Settings",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    val chId = currentChannel?.id ?: ""
                    val isFav = favoriteChannels.contains(chId)
                    
                    val qualities = listOf("auto", "high", "medium", "low")
                    val languages = listOf("hi", "en", "ta", "te", "ml", "bn", "mr", "gu", "pa", "or", "as")

                    
                    Box(modifier = Modifier.focusRequester(settingsFocusRequester)) {
                        SettingsItem(
                            title = "Favorite Channel",
                            subtitle = if (isFav) "Remove from favorites" else "Add to favorites",
                            value = if (isFav) "★" else "☆",
                            valueColor = if (isFav) Color(0xFFFFD700) else TvOnSurfaceVariant,
                            onClick = {
                                scope.launch { settingsManager.toggleFavoriteChannel(chId) }
                            }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    SettingsItem(
                        title = "Video Quality",
                        subtitle = "Current: $quality",
                        value = "Change",
                        valueColor = TvPrimary,
                        onClick = {
                            showQualitySelector = true
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Language-feed switch for collapsed channel families (e.g. Star Sports Hindi/Tamil).
                    // Selecting a language reloads that sibling feed. Distinct from in-stream audio tracks.
                    if (currentVariants.size > 1) {
                        val curLang = currentVariants.firstOrNull { it.channel.id == playingChannel?.id }?.langCode
                        SettingsItem(
                            title = "Language",
                            subtitle = "Switch language feed for this channel",
                            value = com.fenyx.jtv.data.ChannelLanguage.displayName(curLang),
                            valueColor = TvPrimary,
                            onClick = { showLangSelector = true }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    SettingsItem(
                        title = "Audio Track / Subtitles",
                        subtitle = audioTracks.firstOrNull { it.selected }?.label?.let { "Audio: $it" } ?: "Default",
                        value = buildString {
                            if (audioTracks.size > 1) append("${audioTracks.size} tracks")
                            else append("Change")
                            if (subtitleTracks.isNotEmpty()) {
                                val on = subtitleTracks.any { it.selected }
                                append(if (on) " · CC on" else " · CC off")
                            }
                        },
                        valueColor = TvPrimary,
                        onClick = {
                            showAudioSelector = true
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    SettingsItem(
                        title = "Voice Boost",
                        subtitle = "Suppress background & clear dialogue",
                        value = when (voiceBoost) { 0 -> "Off"; 1 -> "Low"; 2 -> "Medium"; 3 -> "High"; else -> "Max" },
                        valueColor = if (voiceBoost == 0) TvOnSurfaceVariant else TvPrimary,
                        onClick = {
                            val next = (voiceBoost + 1) % 5 // Off -> Low -> Medium -> High -> Max -> Off
                            scope.launch { settingsManager.setVoiceBoost(next) }
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    SettingsItem(
                        title = "Auto Volume",
                        subtitle = "Normalize loudness across channels",
                        value = if (audioNormalize) "On" else "Off",
                        valueColor = if (audioNormalize) TvPrimary else TvOnSurfaceVariant,
                        onClick = {
                            scope.launch { settingsManager.setAudioNormalize(!audioNormalize) }
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    SettingsItem(
                        title = "Sleep Timer",
                        subtitle = if (sleepTimerMin == 0) "Off" else "Turns off in $sleepTimerMin min",
                        value = if (sleepTimerMin == 0) "Off" else "$sleepTimerMin min",
                        valueColor = if (sleepTimerMin == 0) TvOnSurfaceVariant else TvPrimary,
                        onClick = {
                            // Cycle Off -> 15 -> 30 -> 60 -> Off
                            sleepTimerMin = when (sleepTimerMin) {
                                0 -> 15
                                15 -> 30
                                30 -> 60
                                else -> 0
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Server mode: force a credential re-pull from the proxy and reload the stream, so a
                    // rotated/expired shared token can be fixed without leaving the player.
                    if (playerSetupMode == "server" || playerSetupMode == "jtv") {
                        SettingsItem(
                            title = "Refresh Login",
                            subtitle = "Fetch fresh credentials from your server",
                            value = if (refreshingCreds) "Refreshing…" else "Refresh",
                            valueColor = TvPrimary,
                            onClick = {
                                if (!refreshingCreds) scope.launch {
                                    refreshingCreds = true
                                    com.fenyx.jtv.data.JioApiClient.refreshCredentials(context)
                                    refreshingCreds = false
                                    // Reload the current stream with the fresh credentials.
                                    retryCount.intValue = 0
                                    playbackError = null
                                    isBuffering = true
                                    streamRefreshTrigger++
                                    showSettingsOverlay = false
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    SettingsItem(
                        title = "Open Settings",
                        subtitle = "Go to full app settings",
                        icon = Icons.Default.Settings,
                        valueColor = TvPrimary,
                        onClick = {
                            showSettingsOverlay = false
                            onSettings()
                        }
                    )
                }
            }
        }

        val qualityOptions = listOf(
            "auto" to "Auto", "high" to "High (1080p)", "medium" to "Medium (720p)", "low" to "Low (480p)"
        )
        val languageOptions = listOf(
            "hi" to "Hindi", "en" to "English", "ta" to "Tamil", "te" to "Telugu", 
            "ml" to "Malayalam", "bn" to "Bengali", "mr" to "Marathi", "gu" to "Gujarati", 
            "pa" to "Punjabi", "or" to "Oriya", "as" to "Assamese"
        )

        if (showQualitySelector) {
            Dialog(onDismissRequest = { showQualitySelector = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                TvPickerDialog(
                    title = "Select Quality",
                    options = qualityOptions,
                    currentValue = quality,
                    onSelect = { value ->
                        quality = value
                        scope.launch { settingsManager.setDefaultQuality(value) }
                        showQualitySelector = false
                    },
                    onDismiss = { showQualitySelector = false }
                )
            }
        }

        if (showAudioSelector) {
            Dialog(onDismissRequest = { showAudioSelector = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                if (audioTracks.isEmpty() && subtitleTracks.isEmpty()) {
                    // Fallback before tracks are known: pick a preferred language by code.
                    TvPickerDialog(
                        title = "Preferred Audio Language",
                        options = languageOptions,
                        currentValue = language,
                        onSelect = { value ->
                            language = value
                            scope.launch { settingsManager.setDefaultLanguage(value) }
                            showAudioSelector = false
                        },
                        onDismiss = { showAudioSelector = false }
                    )
                } else {
                    TrackSelectionDialog(
                        title = "Audio / Subtitles",
                        audioOptions = audioTracks,
                        subtitleOptions = subtitleTracks,
                        onSelectAudio = { opt ->
                            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                                .setOverrideForType(
                                    androidx.media3.common.TrackSelectionOverride(opt.group, listOf(opt.trackIndex))
                                )
                                .build()
                            // Remember the chosen language as the default for other channels too.
                            opt.group.getFormat(opt.trackIndex).language?.let { lang ->
                                language = lang
                                scope.launch { settingsManager.setDefaultLanguage(lang) }
                            }
                        },
                        onSelectSubtitleOff = {
                            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                                .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true)
                                .build()
                        },
                        onSelectSubtitle = { opt ->
                            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                                .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, false)
                                .setOverrideForType(
                                    androidx.media3.common.TrackSelectionOverride(opt.group, listOf(opt.trackIndex))
                                )
                                .build()
                        },
                        onDismiss = { showAudioSelector = false }
                    )
                }
            }
        }

        if (showLangSelector) {
            Dialog(onDismissRequest = { showLangSelector = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                val opts = currentVariants.map { it.channel.id to com.fenyx.jtv.data.ChannelLanguage.displayName(it.langCode) }
                TvPickerDialog(
                    title = "Channel Language",
                    options = opts,
                    currentValue = playingChannel?.id ?: "",
                    onSelect = { value ->
                        val v = currentVariants.firstOrNull { it.channel.id == value }
                        if (v != null) {
                            langOverride = v.channel
                            // Remember the chosen language so other channels + this one default to it.
                            v.langCode?.let { lc ->
                                language = lc
                                scope.launch { settingsManager.setDefaultLanguage(lc) }
                            }
                        }
                        showLangSelector = false
                    },
                    onDismiss = { showLangSelector = false }
                )
            }
        }

        // ─── Programme Sheet (double-press INFO) ───
        val sheetMs = TvMotion.ms(TvMotion.SHEET_MS)
        val programmesFocus = remember { FocusRequester() }
        val channelProgs = remember(playerEpgData, channelIdForEpg) {
            val id = channelIdForEpg ?: return@remember emptyList<com.fenyx.jtv.data.EpgProgram>()
            (playerEpgData[id] ?: emptyList()).sortedBy { it.startMs }
        }
        // REPLAY: yesterday's + today's finished shows Jio can serve again (newest first).
        val replayablePast = remember(channelProgs, nowMs) {
            channelProgs.filter { it.stopMs <= nowMs && it.isReplayable }
                .sortedByDescending { it.startMs }
                .take(8)
        }
        val sheetUpcoming = remember(channelProgs, nowMs) {
            channelProgs.filter { it.stopMs > nowMs }.take(10)
        }
        LaunchedEffect(showProgrammes) {
            if (showProgrammes) runCatching { programmesFocus.requestFocus() }
        }
        AnimatedVisibility(
            visible = showProgrammes,
            enter = fadeIn(tween(sheetMs)) + slideInVertically(tween(sheetMs)) { it / 2 },
            exit = fadeOut(tween(sheetMs)) + slideOutVertically(tween(sheetMs)) { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.92f))
                    .padding(horizontal = overscanH(), vertical = TvDimens.SpaceMd)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Programme guide",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            currentChannel?.name ?: "",
                            style = MaterialTheme.typography.labelMedium,
                            color = TvOnSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    if (replayablePast.isEmpty() && sheetUpcoming.isEmpty()) {
                        Text(
                            if (playerEpgData[channelIdForEpg].isNullOrEmpty()) "Loading programme data…"
                            else "No programme data",
                            color = TvOnSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (replayablePast.isNotEmpty()) {
                                item {
                                    Text(
                                        "▶ Replay — finished shows",
                                        color = Color(0xFFFFB300),
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(start = 12.dp, top = 2.dp, bottom = 4.dp)
                                    )
                                }
                                itemsIndexed(items = replayablePast) { index, prog ->
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .then(if (index == 0) Modifier.focusRequester(programmesFocus) else Modifier),
                                        onClick = {
                                            // Start the replay: the load effect keys on derived
                                            // `catchup`, which flips non-null here.
                                            startReplay(prog)
                                            showProgrammes = false
                                            showOverlay = false
                                        },
                                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                                        colors = ClickableSurfaceDefaults.colors(
                                            containerColor = Color(0xFFFFB300).copy(alpha = 0.08f),
                                            focusedContainerColor = TvDarkSurfaceVariant
                                        ),
                                        border = ClickableSurfaceDefaults.border(
                                            focusedBorder = androidx.tv.material3.Border(
                                                border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFFFB300)),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("▶", color = Color(0xFFFFB300), style = MaterialTheme.typography.labelSmall)
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                bannerTimeFormat.format(Date(prog.startMs)),
                                                color = TvOnSurfaceVariant,
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.width(60.dp)
                                            )
                                            Text(
                                                prog.title,
                                                color = Color.White.copy(alpha = 0.92f),
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                            if (sheetUpcoming.isNotEmpty()) {
                                item {
                                    Text(
                                        "Up next / now",
                                        color = TvOnSurfaceVariant,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 4.dp)
                                    )
                                }
                                itemsIndexed(items = sheetUpcoming) { _, prog ->
                                    val isNow = nowMs >= prog.startMs && nowMs < prog.stopMs
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = {},
                                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                                        colors = ClickableSurfaceDefaults.colors(
                                            containerColor = Color.Transparent,
                                            focusedContainerColor = TvDarkSurfaceVariant
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (isNow) {
                                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(TvLiveRed))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("NOW", color = TvLiveRed, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                                                Spacer(modifier = Modifier.width(10.dp))
                                            }
                                            Text(
                                                bannerTimeFormat.format(Date(prog.startMs)),
                                                color = TvOnSurfaceVariant,
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.width(60.dp)
                                            )
                                            Text(
                                                prog.title,
                                                color = if (isNow) TvPrimary else Color.White.copy(alpha = 0.9f),
                                                fontWeight = if (isNow) FontWeight.Bold else FontWeight.Normal,
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ─── Zap Preview Strip (optional; Settings → Playback → Zap Preview) ───
        // ↑/↓ opens a horizontal preview band instead of zapping instantly: ↑↓/←→ browse, OK tunes,
        // Back or 5s of silence cancels back to the playing channel.
        val zapStripState = rememberLazyListState()
        LaunchedEffect(showZapStrip, stripIndex) {
            if (!showZapStrip) return@LaunchedEffect
            currentChannels.getOrNull(stripIndex)?.let { onRequestChannelEpg(it.id) }
            runCatching { zapStripState.animateScrollToItem(stripIndex.coerceAtMost((currentChannels.size - 1).coerceAtLeast(0))) }
        }
        AnimatedVisibility(
            visible = showZapStrip,
            enter = fadeIn(tween(sheetMs)) + slideInVertically(tween(sheetMs)) { it },
            exit = fadeOut(tween(sheetMs)) + slideOutVertically(tween(sheetMs)) { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.88f))
                    .padding(vertical = TvDimens.SpaceSm)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = overscanH()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        groupLabel(currentGroup),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "↑↓ browse • OK watch • Back cancel",
                        color = Color.White.copy(alpha = 0.55f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(
                    state = zapStripState,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = overscanH())
                ) {
                    itemsIndexed(items = currentChannels, key = { _, ch -> ch.id }) { index, channel ->
                        val isSelected = index == stripIndex
                        val stripProgram = playerEpgData[channel.id]?.find { it.startMs <= nowMs && it.stopMs > nowMs }
                        Surface(
                            onClick = { stripIndex = index },
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = if (isSelected) TvPrimaryContainer.copy(alpha = 0.5f) else TvDarkSurface,
                                focusedContainerColor = TvDarkSurfaceVariant,
                                contentColor = TvOnSurface
                            ),
                            border = ClickableSurfaceDefaults.border(
                                focusedBorder = androidx.tv.material3.Border(
                                    border = androidx.compose.foundation.BorderStroke(2.dp, TvFocusBorder),
                                    shape = RoundedCornerShape(10.dp)
                                )
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    String.format(Locale.US, "%02d", index + 1),
                                    color = if (isSelected || index == currentIndex) TvPrimary else Color.White.copy(alpha = 0.5f),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.width(26.dp)
                                )
                                if (channel.logoUrl.isNotEmpty()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context).data(channel.logoUrl).size(64).build(),
                                        contentDescription = null,
                                        modifier = Modifier.size(30.dp).clip(CircleShape)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        channel.name,
                                        color = Color.White,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (isSelected) {
                                        Text(
                                            stripProgram?.title ?: "",
                                            color = TvOnSurfaceVariant,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ─── Replay Control Bar (short-OK during a replay) ───
        LaunchedEffect(showCatchupBar, userPaused) {
            if (!showCatchupBar) return@LaunchedEffect
            if (userPaused) return@LaunchedEffect // stay open while paused
            delay(6_000)
            showCatchupBar = false
        }
        LaunchedEffect(showCatchupBar) {
            if (showCatchupBar) runCatching { catchupBarFocus.requestFocus() }
        }
        AnimatedVisibility(
            visible = showCatchupBar && catchup != null,
            enter = fadeIn(tween(sheetMs)) + slideInVertically(tween(sheetMs)) { it },
            exit = fadeOut(tween(sheetMs)) + slideOutVertically(tween(sheetMs)) { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = overscanH(), vertical = TvDimens.SpaceLg)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.88f))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                fun seekBy(deltaMs: Long) {
                    val dur = exoPlayer.duration
                    val target = (exoPlayer.currentPosition + deltaMs).coerceIn(0L, if (dur > 0) dur else Long.MAX_VALUE)
                    exoPlayer.seekTo(target)
                }
                Surface(
                    modifier = Modifier.size(width = 64.dp, height = 44.dp),
                    onClick = { seekBy(-30_000) },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                    colors = ClickableSurfaceDefaults.colors(containerColor = TvDarkSurfaceVariant)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("⏪", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
                Surface(
                    modifier = Modifier.focusRequester(catchupBarFocus),
                    onClick = {
                        if (userPaused) { exoPlayer.play(); userPaused = false }
                        else { exoPlayer.pause(); userPaused = true; showOverlay = true }
                    },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = TvPrimaryContainer,
                        focusedContainerColor = TvPrimary
                    )
                ) {
                    Box(modifier = Modifier.size(width = 56.dp, height = 44.dp), contentAlignment = Alignment.Center) {
                        Text(if (userPaused) "▶" else "⏸", color = Color.White, fontSize = 18.sp)
                    }
                }
                Surface(
                    modifier = Modifier.size(width = 64.dp, height = 44.dp),
                    onClick = { seekBy(30_000) },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                    colors = ClickableSurfaceDefaults.colors(containerColor = TvDarkSurfaceVariant)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("⏩", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                // Scrubber: D-pad ←/→ steps through 100 discrete points when focused.
                if (replayDurMs > 0) {
                    androidx.compose.material3.Slider(
                        value = replayPosMs.toFloat() / replayDurMs.toFloat(),
                        onValueChange = { frac -> exoPlayer.seekTo((frac * replayDurMs).toLong()) },
                        valueRange = 0f..1f,
                        steps = 100,
                        modifier = Modifier.weight(1f).height(32.dp)
                    )
                    Text(
                        "${mmss(replayPosMs)} / ${mmss(replayDurMs)}",
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        modifier = Modifier.width(110.dp)
                    )
                } else {
                    Text("buffering…", color = TvOnSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                }

                Surface(
                    onClick = {
                        // Exit replay: clearing the derived replay re-keys the load effect onto live.
                        startReplay(null)
                        showCatchupBar = false
                        showOverlay = true
                        retryCount.intValue = 0
                        playbackError = null
                        isBuffering = true
                        streamRefreshTrigger++
                    },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color(0xFFFFB300).copy(alpha = 0.25f),
                        focusedContainerColor = Color(0xFFFFB300)
                    )
                ) {
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            "● LIVE",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }

        // ─── Bottom Hint Bar ───
        AnimatedVisibility(
            // Also hidden under the settings panel: the hint used to overlap the Voice Boost
            // row's subtitle at the panel's bottom edge.
            visible = showOverlay && !showChannelList && !showCategoryList &&
                !showProgrammes && !showZapStrip && !showSettingsOverlay,
            enter = fadeIn(tween(TvMotion.ms())) + slideInVertically(tween(TvMotion.ms())) { it },
            exit = fadeOut(tween(TvMotion.ms())) + slideOutVertically(tween(TvMotion.ms())) { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
                    // Overscan-safe hint bar (compact margins on touch devices).
                    .padding(horizontal = overscanH(), vertical = TvDimens.SpaceMd)
            ) {
                Text(
                    if (catchup != null)
                        if (isTouch) "TAP CONTROLS  •  LONG-PRESS PAUSE  •  ⏪⏩ SEEK"
                        else "↑↓/CH ZAP  •  OK CONTROLS  •  ← LIST  •  INFO (×2 GUIDE)  •  BACK EXIT"
                    else if (isTouch)
                        "TAP MENU  •  ▲▼ ZAP  •  # NUMBER  •  LONG-PRESS PAUSE"
                    else
                        "↑↓/CH ZAP  •  ← LIST  •  0-9 GO  •  OK INFO (×2 GUIDE)  •  →⏱ ASPECT  •  BACK EXIT",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        // ─── Category List Sidebar (Far Left side) ───
        // Fixed TV widths overflow portrait phones (220 + 348 > a 360dp window), so both panels
        // clamp to a fraction of the current window while keeping their designed size on TVs.
        val categoryWidth = 220.dp.coerceMaxWindowFraction(0.45f)
        val channelListWidth = 348.dp.coerceMaxWindowFraction(0.80f)
        AnimatedVisibility(
            visible = showCategoryList,
            enter = fadeIn(tween(TvMotion.ms())) + slideInHorizontally(tween(TvMotion.ms())) { -it },
            exit = fadeOut(tween(TvMotion.ms())) + slideOutHorizontally(tween(TvMotion.ms())) { -it },
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            val catState = rememberLazyListState()
            LaunchedEffect(categorySelectedIndex) {
                // Position the selected category two rows down from the top so the entries above it
                // (All / ★ Favorites when near the top of the list) stay visible instead of being
                // scrolled out of the viewport.
                catState.animateScrollToItem((categorySelectedIndex - 2).coerceAtLeast(0))
            }
            Box(
                modifier = Modifier
                    .width(categoryWidth)
                    .fillMaxHeight()
                    .background(Color.Black.copy(alpha = 0.95f))
                    .padding(8.dp)
            ) {
                Column {
                    Text(
                        "Categories",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )

                    LazyColumn(state = catState) {
                        itemsIndexed(items = groups, key = { _, group -> group }) { index: Int, group: String ->
                            val isSelected = index == categorySelectedIndex
                            val isCurrentGroup = group == currentGroup
                            Surface(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                onClick = {
                                    categorySelectedIndex = index
                                    currentGroup = group // currentChannels derives from this
                                    currentIndex = 0
                                    showCategoryList = false
                                },
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = if (isSelected) TvPrimaryContainer.copy(alpha = 0.4f) else Color.Transparent,
                                    focusedContainerColor = TvDarkSurfaceVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isCurrentGroup) {
                                        Box(
                                            modifier = Modifier
                                                .width(3.dp)
                                                .height(18.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(TvPrimary)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Text(
                                        if (group.startsWith("__")) groupLabel(group)
                                        else com.fenyx.jtv.data.CategoryIcons.decorate(groupLabel(group)),
                                        color = if (isSelected || isCurrentGroup) TvPrimary else Color.White,
                                        fontWeight = if (isSelected || isCurrentGroup) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ─── Channel List Sidebar (Left side, pushed by Category list if open) ───
        val channelListOffset by animateDpAsState(if (showCategoryList) categoryWidth else 0.dp)
        AnimatedVisibility(
            visible = showChannelList,
            enter = fadeIn(tween(TvMotion.ms())) + slideInHorizontally(tween(TvMotion.ms())) { -it },
            exit = fadeOut(tween(TvMotion.ms())) + slideOutHorizontally(tween(TvMotion.ms())) { -it },
            modifier = Modifier.align(Alignment.CenterStart).padding(start = channelListOffset)
        ) {
            val listState = rememberLazyListState()
            LaunchedEffect(listSelectedIndex) {
                listState.animateScrollToItem(listSelectedIndex.coerceAtLeast(0))
            }

            Box(
                modifier = Modifier
                    .width(channelListWidth)
                    .fillMaxHeight()
                    .background(Color.Black.copy(alpha = 0.9f))
                    .padding(8.dp)
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // ─── A-Z jump rail: ← activates, ↑↓ jumps, →/OK dives back into the list ───
                    if (railLetters.isNotEmpty()) {
                        LetterRail(
                            letters = railLetters,
                            selectedIndex = letterIndex,
                            active = letterActive,
                            onSelect = { pos ->
                                letterActive = true
                                jumpToLetter(pos)
                            },
                            modifier = Modifier.fillMaxHeight()
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            groupLabel(currentGroup),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                        Text(
                            if (letterActive) "↑↓ letter  •  → channels  •  ← categories"
                            else "← Letters  •  ${currentChannels.size} channels",
                            style = MaterialTheme.typography.bodySmall,
                            color = TvOnSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                    LazyColumn(state = listState) {
                        itemsIndexed(items = currentChannels, key = { _, channel -> channel.id }) { index: Int, channel: Channel ->
                            val isSelected = index == listSelectedIndex
                            val isPlaying = index == currentIndex
                             Surface(
                                 modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                 onClick = {
                                     listSelectedIndex = index
                                     currentIndex = index
                                     showChannelList = false
                                     showCategoryList = false
                                     showOverlay = true
                                 },
                                 shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                                 scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
                                 colors = ClickableSurfaceDefaults.colors(
                                     // 0.4 on near-black read as "barely selected" on AMOLED phones;
                                     // 0.55 + the accent bar below keeps the current row obvious.
                                     containerColor = if (isSelected) TvPrimaryContainer.copy(alpha = 0.55f) else Color.Transparent,
                                     focusedContainerColor = TvDarkSurfaceVariant
                                 )
                             ) {
                                 Row(
                                     modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                     verticalAlignment = Alignment.CenterVertically
                                 ) {
                                     // Left accent bar marking the selected row (same cue the
                                     // categories sidebar uses for the active group).
                                     if (isSelected) {
                                         Box(
                                             modifier = Modifier
                                                 .width(3.dp)
                                                 .height(18.dp)
                                                 .clip(RoundedCornerShape(2.dp))
                                                 .background(TvPrimary)
                                         )
                                         Spacer(modifier = Modifier.width(8.dp))
                                     }
                                     // Channel number
                                     Text(
                                         String.format(Locale.US, "%02d", index + 1),
                                         color = if (isSelected) TvPrimary else Color.White.copy(alpha = 0.5f),
                                         fontWeight = FontWeight.Bold,
                                         modifier = Modifier.width(32.dp)
                                     )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    if (channel.logoUrl.isNotEmpty()) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(channel.logoUrl)
                                                .size(64)
                                                .build(),
                                            contentDescription = null,
                                            modifier = Modifier.size(32.dp).clip(CircleShape)
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier.size(32.dp).clip(CircleShape).background(TvDarkSurfaceVariant),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.PlayArrow, null, tint = TvOnSurfaceVariant, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                     Spacer(modifier = Modifier.width(10.dp))
                                     Text(
                                         channel.name,
                                         color = if (isPlaying) TvPrimary else Color.White,
                                         fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
                                         maxLines = 1,
                                         overflow = TextOverflow.Ellipsis,
                                         style = MaterialTheme.typography.bodyMedium
                                     )
                                 }
                             }
                         }
                     }
                 }
                }
             }
         }
     }

    // ─── System back (phone gesture / nav bar) ───
    // Remotes deliver Back as a key event (handled in onPreviewKeyEvent above); touch devices
    // deliver it through the OnBackPressedDispatcher instead. Mirror the same peel order so
    // gesture-back closes overlays step-by-step instead of exiting the app.
    BackHandler {
        when {
            showStats -> showStats = false
            showCatchupBar -> showCatchupBar = false
            showProgrammes -> showProgrammes = false
            showSettingsOverlay -> showSettingsOverlay = false
            showCategoryList -> showCategoryList = false
            showChannelList -> { letterActive = false; showChannelList = false }
            showNumericOverlay -> { showNumericOverlay = false; numericBuffer = "" }
            showZapStrip -> showZapStrip = false
            else -> onBack()
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

/**
 * Vertical A–Z rail beside the channel list. Index-driven (no focus of its own): the sidebar key
 * handler moves [selectedIndex] and jumps the list; [active] just lights the rail up so the user
 * can see where their ↑↓ presses are going.
 */
@Composable
private fun StatLine(label: String, value: String?) {
    Row {
        Text(
            label,
            color = TvOnSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(70.dp)
        )
        Text(
            value ?: "—",
            color = Color.White.copy(alpha = 0.9f),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
    Spacer(modifier = Modifier.height(3.dp))
}

/**
 * Touch pill used by the player's on-screen control cluster and zap buttons. 44dp minimum height
 * so it's comfortably tappable on phones; also focusable for hybrid remote/mouse use.
 */
@Composable
private fun TouchKey(
    label: String,
    contentDescription: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(width = 46.dp, height = 44.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Black.copy(alpha = 0.55f),
            focusedContainerColor = TvPrimaryContainer,
            contentColor = Color.White
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, TvFocusBorder),
                shape = RoundedCornerShape(12.dp)
            )
        )
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/**
 * Touch numpad for channel entry — the tap equivalent of pressing digits on a remote. Type up to
 * 4 digits, GO tunes, ⌫ corrects. Mirrors commitNumericEntry's number = index + 1 mapping.
 */
@Composable
private fun TouchNumpadDialog(
    onSubmit: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var buffer by remember { mutableStateOf("") }
    val enterState = remember { MutableTransitionState(false).apply { targetState = true } }
    val ms = TvMotion.ms(TvMotion.SHEET_MS)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
            AnimatedVisibility(
                visibleState = enterState,
                enter = fadeIn(tween(ms)) + scaleIn(initialScale = 0.95f, animationSpec = tween(ms))
            ) {
                Column(
                    modifier = Modifier
                        .background(com.fenyx.jtv.theme.TvDarkSurface, RoundedCornerShape(16.dp))
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Go to channel",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = com.fenyx.jtv.theme.TvOnBackground
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        buffer.ifEmpty { "—" },
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (buffer.isEmpty()) TvOnSurfaceVariant else com.fenyx.jtv.theme.TvPrimary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(TvDarkSurfaceVariant)
                            .padding(horizontal = 28.dp, vertical = 6.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    val keys = listOf(
                        listOf("1", "2", "3"), listOf("4", "5", "6"),
                        listOf("7", "8", "9"), listOf("⌫", "0", "GO")
                    )
                    keys.forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { key ->
                                Surface(
                                    onClick = {
                                        when (key) {
                                            "⌫" -> buffer = buffer.dropLast(1)
                                            "GO" -> buffer.toIntOrNull()?.let(onSubmit)
                                            else -> if (buffer.length < 4 && !(buffer.isEmpty() && key == "0")) {
                                                buffer += key
                                            }
                                        }
                                    },
                                    modifier = Modifier.size(width = 72.dp, height = 60.dp),
                                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                                    colors = ClickableSurfaceDefaults.colors(
                                        containerColor = TvDarkSurfaceVariant,
                                        focusedContainerColor = TvPrimary,
                                        contentColor = TvOnSurface,
                                        focusedContentColor = TvOnPrimary
                                    )
                                ) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(
                                            key,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = if (key == "GO" && buffer.toIntOrNull() != null) TvPrimary else Color.Unspecified
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "tap outside to cancel",
                        color = TvOnSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

/** Section header inside [TrackSelectionDialog]. */
@Composable
private fun PickerSectionLabel(text: String) {
    Text(
        text,
        color = TvPrimary,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp)
    )
}

/** One selectable row shared by the audio/subtitle picker. */
@Composable
private fun PickerOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) com.fenyx.jtv.theme.TvPrimaryContainer.copy(alpha = 0.3f) else Color.Transparent,
            focusedContainerColor = com.fenyx.jtv.theme.TvDarkSurfaceVariant
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, com.fenyx.jtv.theme.TvPrimary),
                shape = RoundedCornerShape(8.dp)
            )
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                color = if (selected) com.fenyx.jtv.theme.TvPrimary else com.fenyx.jtv.theme.TvOnSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )
            if (selected) {
                Text("✓", color = com.fenyx.jtv.theme.TvPrimary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Combined AUDIO + SUBTITLES picker. Subtitles section appears only when the stream carries text
 * tracks; "Off" disables the text renderer outright.
 */
@Composable
private fun TrackSelectionDialog(
    title: String,
    audioOptions: List<AudioOption>,
    subtitleOptions: List<AudioOption>,
    onSelectAudio: (AudioOption) -> Unit,
    onSelectSubtitleOff: () -> Unit,
    onSelectSubtitle: (AudioOption) -> Unit,
    onDismiss: () -> Unit
) {
    val enterState = remember { MutableTransitionState(false).apply { targetState = true } }
    val ms = TvMotion.ms(TvMotion.SHEET_MS)
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visibleState = enterState,
            enter = fadeIn(tween(ms)) + scaleIn(initialScale = 0.95f, animationSpec = tween(ms))
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 440.dp)
                .background(com.fenyx.jtv.theme.TvDarkSurface, RoundedCornerShape(16.dp))
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = com.fenyx.jtv.theme.TvOnBackground
            )
            Spacer(modifier = Modifier.height(12.dp))
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f, fill = false).heightIn(max = 420.dp)
            ) {
                item { PickerSectionLabel("AUDIO") }
                    itemsIndexed(audioOptions) { _, opt ->
                        PickerOptionRow(opt.label, opt.selected) { onSelectAudio(opt); onDismiss() }
                    }
                    if (subtitleOptions.isNotEmpty()) {
                        item { Spacer(modifier = Modifier.height(6.dp)); PickerSectionLabel("SUBTITLES") }
                        item {
                            // "Off" reads as selected when no subtitle track is selected at all.
                            PickerOptionRow("Off", !subtitleOptions.any { it.selected }) {
                                onSelectSubtitleOff(); onDismiss()
                            }
                        }
                        itemsIndexed(subtitleOptions) { _, opt ->
                            PickerOptionRow(opt.label, opt.selected) { onSelectSubtitle(opt); onDismiss() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LetterRail(
    letters: List<Char>,
    selectedIndex: Int,
    active: Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(42.dp)
            .background(if (active) TvDarkSurfaceVariant else Color.Transparent, RoundedCornerShape(8.dp))
            .then(
                if (active) Modifier.border(2.dp, TvFocusBorder, RoundedCornerShape(8.dp)) else Modifier
            )
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        letters.forEachIndexed { i, L ->
            val selected = i == selectedIndex
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            selected && active -> TvPrimary
                            selected -> TvPrimaryContainer.copy(alpha = 0.55f)
                            else -> Color.Transparent
                        }
                    )
                    .clickable { onSelect(i) }
            ) {
                Text(
                    L.toString(),
                    color = if (selected) Color.White else Color.White.copy(alpha = 0.5f),
                    fontSize = 10.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
fun TvPickerDialog(
    title: String,
    options: List<Pair<String, String>>,
    currentValue: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    // Dialog windows pop with no transition of their own; animate the content in so every dialog
    // shares the app's motion feel.
    val enterState = remember { MutableTransitionState(false).apply { targetState = true } }
    val ms = TvMotion.ms(TvMotion.SHEET_MS)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visibleState = enterState,
            enter = fadeIn(tween(ms)) + scaleIn(initialScale = 0.95f, animationSpec = tween(ms))
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 400.dp)
                .background(com.fenyx.jtv.theme.TvDarkSurface, RoundedCornerShape(16.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = com.fenyx.jtv.theme.TvOnBackground
            )
            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                // weight(fill=false): on short TV panels (~540dp usable) a fixed-max list squeezed
                // the Cancel button flat; now the list yields space and scrolls instead.
                modifier = Modifier.weight(1f, fill = false).heightIn(max = 400.dp)
            ) {
                items(options.size) { index ->
                    val (value, label) = options[index]
                    val isSelected = value == currentValue

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onSelect(value) },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = if (isSelected) com.fenyx.jtv.theme.TvPrimaryContainer.copy(alpha = 0.3f) else Color.Transparent,
                            focusedContainerColor = com.fenyx.jtv.theme.TvDarkSurfaceVariant
                        ),
                        border = ClickableSurfaceDefaults.border(
                            focusedBorder = androidx.tv.material3.Border(
                                border = androidx.compose.foundation.BorderStroke(2.dp, com.fenyx.jtv.theme.TvPrimary),
                                shape = RoundedCornerShape(8.dp)
                            )
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                label,
                                color = if (isSelected) com.fenyx.jtv.theme.TvPrimary else com.fenyx.jtv.theme.TvOnSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
        }
    }
}
