package com.fenyx.jtv.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.fenyx.jtv.theme.*
import com.fenyx.jtv.ui.main.ChannelCard
import com.fenyx.jtv.ui.main.MainViewModel

/**
 * Simple, D-pad-friendly channel search over the (collapsed) channel list. Focus lands on the input
 * so the leanback IME opens immediately; results filter live by name so the user rarely types a full
 * word. Selecting a result opens it in the player against the full channel list.
 */
@Composable
fun SearchScreen(
    viewModel: MainViewModel,
    onChannelClick: (Int, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val allChannels by viewModel.displayChannels.collectAsState()
    val indexMap = remember(allChannels) { allChannels.withIndex().associate { (i, c) -> c.id to i } }

    var query by remember { mutableStateOf("") }
    val results = remember(query, allChannels) {
        val q = query.trim()
        if (q.isEmpty()) emptyList()
        else allChannels.filter { ch ->
            // Match the representative's name, or any collapsed language variant's name, so a hidden
            // feed like "Colors Kannada" is still findable via its "Colors" tile.
            ch.name.contains(q, ignoreCase = true) ||
                viewModel.variantsFor(ch.id).any { it.channel.name.contains(q, ignoreCase = true) }
        }.take(150)
    }

    val fieldFocus = remember { FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        runCatching { fieldFocus.requestFocus() }
        kotlinx.coroutines.delay(50)
        keyboard?.show() // TV: focus alone doesn't open the on-screen keyboard
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TvDarkBackground)
            .padding(
                horizontal = TvDimens.OverscanHorizontal,
                vertical = TvDimens.OverscanVertical
            )
    ) {
        // ─── Search field ───
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(TvDarkSurface)
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
                        .focusable(),
                    textStyle = TextStyle(color = TvOnSurface, fontSize = 18.sp),
                    cursorBrush = SolidColor(TvPrimary),
                    singleLine = true
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when {
            query.isBlank() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Type to search across all channels", color = TvOnSurfaceVariant)
                }
            }
            results.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No channels match \"${query.trim()}\"", color = TvOnSurfaceVariant)
                }
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize().focusRestorer(),
                    contentPadding = PaddingValues(bottom = TvDimens.OverscanVertical)
                ) {
                    itemsIndexed(items = results, key = { _, ch -> ch.id }) { _, channel ->
                        ChannelCard(
                            channel = channel,
                            onClick = { onChannelClick(indexMap[channel.id] ?: 0, null) }
                        )
                    }
                }
            }
        }
    }
}
