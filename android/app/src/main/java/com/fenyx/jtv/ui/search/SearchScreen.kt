package com.fenyx.jtv.ui.search

import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.fenyx.jtv.data.SettingsManager
import com.fenyx.jtv.theme.*
import kotlinx.coroutines.launch
import com.fenyx.jtv.ui.main.ChannelCard
import com.fenyx.jtv.ui.main.HideChannelConfirmDialog
import com.fenyx.jtv.ui.main.MainViewModel

/**
 * D-pad-friendly channel search over the (collapsed) channel list. Focus lands on the input so the
 * leanback IME opens immediately; results filter live by name so the user rarely types a full word.
 * Selecting a result opens it in the player against the full channel list.
 *
 * Extras: remote-mic voice search (hidden when no recognition service exists) and persisted
 * recent-search chips shown while the query is empty.
 */
@Composable
fun SearchScreen(
    viewModel: MainViewModel,
    onChannelClick: (Int, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val scope = rememberCoroutineScope()
    val allChannels by viewModel.displayChannels.collectAsState()
    val hiddenChannels by viewModel.hiddenChannels.collectAsState()
    val indexMap = remember(allChannels) { allChannels.withIndex().associate { (i, c) -> c.id to i } }
    val recentSearches by settingsManager.recentSearchesFlow.collectAsState(initial = emptyList())

    var query by remember { mutableStateOf("") }
    // Focus ring state for the search field (TV D-pad cue).
    var searchFieldFocused by remember { mutableStateOf(false) }
    // Long-press hide target (same confirm flow as Home).
    var hideTarget by remember { mutableStateOf<com.fenyx.jtv.data.Channel?>(null) }
    // Post-hide focus restore: the matching result tile focuses itself (see ChannelCard).
    // Auto-expires so a stale id can never yank focus later.
    var pendingFocusId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pendingFocusId) {
        if (pendingFocusId != null) {
            kotlinx.coroutines.delay(2000)
            pendingFocusId = null
        }
    }
    val results = remember(query, allChannels, hiddenChannels) {
        val q = query.trim()
        if (q.isEmpty()) emptyList()
        else allChannels.filter { ch ->
            ch.id !in hiddenChannels &&
                // Match the representative's name, or any collapsed language variant's name, so a hidden
                // feed like "Colors Kannada" is still findable via its "Colors" tile.
                (ch.name.contains(q, ignoreCase = true) ||
                    viewModel.variantsFor(ch.id).any { it.channel.name.contains(q, ignoreCase = true) })
        }.take(150)
    }

    // ─── Voice search (remote mic) ───
    // Only offered when a recognition service actually resolves — many TV boxes have none, in which
    // case the mic simply isn't shown instead of crashing into an ActivityNotFoundException.
    val voiceAvailable = remember {
        runCatching { SpeechRecognizer.isRecognitionAvailable(context) }.getOrDefault(false)
    }
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val text = res.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (!text.isNullOrBlank()) query = text.trim()
    }
    fun launchVoice() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak a channel name")
        }
        runCatching { voiceLauncher.launch(intent) }
    }

    val fieldFocus = remember { FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val isTouch = LocalIsTouch.current
    LaunchedEffect(Unit) {
        runCatching { fieldFocus.requestFocus() }
        // Auto-opening the IME is a TV behaviour (focus alone doesn't open the leanback
        // keyboard). On phones it instantly covered the recent-chips area; the keyboard
        // appears when the user taps the field instead.
        if (!isTouch) {
            kotlinx.coroutines.delay(50)
            keyboard?.show()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TvDarkBackground)
            .padding(
                // TV overscan on TVs; compact margins on touch devices.
                horizontal = overscanH(),
                vertical = overscanV()
            )
    ) {
        // ─── Search field ───
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(TvDarkSurface)
                // D-pad cue: the field is focusable but had no focus ring — the cursor was the
                // only signal that the field held focus on TVs.
                .border(
                    2.dp,
                    // Focus ring is a D-pad cue: on touch the field was outlined at screen open
                    // even though nothing navigates to it by focus there.
                    if (searchFieldFocused && !isTouch) TvFocusBorder else Color.Transparent,
                    RoundedCornerShape(10.dp)
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Search, contentDescription = null, tint = TvPrimary, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text("Search channels…", color = TvOnSurfaceVariant, fontSize = 18.sp)
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(fieldFocus)
                        .focusable()
                        .onFocusChanged { searchFieldFocused = it.isFocused || it.hasFocus },
                    textStyle = TextStyle(color = TvOnSurface, fontSize = 18.sp),
                    cursorBrush = SolidColor(TvPrimary),
                    singleLine = true,
                    // Enter = commit on the IME; no autocorrect mangling channel names.
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Search,
                        autoCorrectEnabled = false,
                        capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words
                    )
                )
            }
            if (query.isNotEmpty()) {
                Surface(
                    onClick = { query = "" },
                    // 48dp minimum tap target (was 40dp — thumb-hostile on phones).
                    modifier = Modifier.size(48.dp),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
                    colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = TvOnSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
            }
            if (voiceAvailable) {
                Surface(
                    onClick = { launchVoice() },
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = TvDarkSurfaceVariant,
                        focusedContainerColor = TvPrimaryContainer
                    ),
                    border = ClickableSurfaceDefaults.border(
                        focusedBorder = androidx.tv.material3.Border(
                            border = androidx.compose.foundation.BorderStroke(2.dp, TvFocusBorder),
                            shape = RoundedCornerShape(8.dp)
                        )
                    )
                ) {
                    Text(
                        "🎙",
                        modifier = Modifier
                            .defaultMinSize(minWidth = 40.dp, minHeight = 40.dp)
                            .wrapContentSize(Alignment.Center)
                            // Spoken label: TalkBack announced the raw emoji glyph before.
                            .semantics { contentDescription = "Voice search" },
                        fontSize = 18.sp,
                        color = TvOnSurface
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when {
            query.isBlank() -> {
                if (recentSearches.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Type to search across all channels", color = TvOnSurfaceVariant)
                    }
                } else {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Recent searches",
                                style = MaterialTheme.typography.titleSmall,
                                color = TvOnSurfaceVariant,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Surface(
                                onClick = { scope.launch { settingsManager.clearRecentSearches() } },
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = TvDarkSurface,
                                    focusedContainerColor = TvDarkSurfaceVariant
                                )
                            ) {
                                Text(
                                    "Clear",
                                    color = TvOnSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall,
                                    // 48dp-class tap area (the old 5dp vertical padding made the
                                    // chip ~27dp tall — well under the touch minimum).
                                    modifier = Modifier
                                        .padding(horizontal = 12.dp)
                                        .defaultMinSize(minHeight = 48.dp)
                                        .wrapContentHeight(Alignment.CenterVertically)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        // Chips can exceed a narrow phone window — scroll instead of clipping.
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            recentSearches.forEach { term ->
                                Surface(
                                    onClick = { query = term },
                                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(20.dp)),
                                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                                    colors = ClickableSurfaceDefaults.colors(
                                        containerColor = TvDarkSurface,
                                    focusedContainerColor = TvPrimaryContainer.copy(alpha = 0.45f)
                                ),
                                border = ClickableSurfaceDefaults.border(
                                    focusedBorder = androidx.tv.material3.Border(
                                        border = androidx.compose.foundation.BorderStroke(2.dp, TvFocusBorder),
                                        shape = RoundedCornerShape(20.dp)
                                    )
                                )
                                ) {
                                    Text(
                                        term,
                                        color = TvOnSurface,
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            results.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No channels match \"${query.trim()}\"", color = TvOnSurfaceVariant)
                }
            }
            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                // Truncation notice: the list silently capped at 150 and users couldn't tell.
                if (results.size >= 150) {
                    Text(
                        "Showing first 150 matches — refine your search",
                        color = TvOnSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
                LazyVerticalGrid(
                    // Phone clamp (3 columns) — matches the home grid so search results and the
                    // home grid show tiles at the same size on narrow screens.
                    columns = GridCells.Adaptive(150.dp.coerceMaxWindowFraction(0.28f)),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize().focusRestorer(),
                    contentPadding = PaddingValues(bottom = overscanV())
                ) {
                    itemsIndexed(items = results, key = { _, ch -> ch.id }) { _, channel ->
                        ChannelCard(
                            channel = channel,
                            onClick = {
                                val q = query.trim()
                                if (q.isNotEmpty()) scope.launch { settingsManager.pushRecentSearch(q) }
                                onChannelClick(indexMap[channel.id] ?: 0, null)
                            },
                            number = channel.channelNumber.takeIf { it > 0 }
                                ?: (indexMap[channel.id]?.plus(1) ?: 0),
                            onHideRequest = { hideTarget = it },
                            hideFocusTarget = pendingFocusId,
                            onHideFocusConsumed = { pendingFocusId = null }
                        )
                    }
                }
                }
            }
        }
    }

    // Long-press Hide confirm.
    hideTarget?.let { target ->
        HideChannelConfirmDialog(
            channelName = target.name,
            onHide = {
                val ids = results.map { it.id }
                val i = ids.indexOf(target.id)
                pendingFocusId = ids.getOrNull(i + 1) ?: ids.getOrNull((i - 1).coerceAtLeast(0))
                viewModel.toggleHiddenChannel(target.id)
                hideTarget = null
            },
            onDismiss = { hideTarget = null }
        )
    }
}
