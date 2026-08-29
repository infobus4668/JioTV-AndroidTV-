package com.fenyx.jtv.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsManager(private val context: Context) {
    companion object {
        private val DEFAULT_LANGUAGE = stringPreferencesKey("default_language")
        private val DEFAULT_QUALITY = stringPreferencesKey("default_quality")
        private val HARDWARE_DECODER = booleanPreferencesKey("hardware_decoder")
        private val TUNNELING = booleanPreferencesKey("tunneling_enabled")
        private val PLAYBACK_BUFFER_SEC = intPreferencesKey("playback_buffer_sec")
        private val VOICE_BOOST = intPreferencesKey("voice_boost")          // 0=off,1=low,2=medium,3=high,4=max
        private val AUDIO_NORMALIZE = booleanPreferencesKey("audio_normalize")
        private val LAST_SELECTED_CATEGORY = stringPreferencesKey("last_selected_category")
        private val LAST_UPDATE_CHECK = stringPreferencesKey("last_update_check_timestamp")
        private val PLAYER_RESIZE_MODE = intPreferencesKey("player_resize_mode")
        
        // Collapse per-language duplicate channels (e.g. "Star Sports 1 Hindi/Tamil/Telugu") into one.
        private val GROUP_LANGUAGE_VARIANTS = booleanPreferencesKey("group_language_variants")

        private val EPG_MODE = booleanPreferencesKey("epg_mode")
        private val EPG_URL = stringPreferencesKey("epg_url")
        private val FAVORITE_CHANNELS = stringPreferencesKey("favorite_channels")

        // Language filter for the channel list (multi-select, empty = show all), stored like favorites.
        private val LANGUAGE_FILTER = stringPreferencesKey("language_filter")

        // Sort channel lists A–Z by name instead of by channel number.
        private val CHANNEL_SORT_ALPHABETICAL = booleanPreferencesKey("channel_sort_alphabetical")

        // Player: D-pad ↑/↓ opens a preview strip instead of zapping instantly (default off).
        private val ZAP_PREVIEW = booleanPreferencesKey("zap_preview")

        // Home EPG layout: "off" | "rows" (now/next rows) | "grid" (scrolling time grid). Falls back
        // to the legacy epg_mode boolean when unset so existing users keep their choice.
        private val EPG_STYLE = stringPreferencesKey("epg_style")

        // Recent searches (MRU first, capped) shown on the Search screen.
        private val RECENT_SEARCHES = stringPreferencesKey("recent_searches")

        // Home grid tile size in dp (adaptive columns): 130 compact / 150 comfortable / 175 large.
        private val GRID_DENSITY_DP = intPreferencesKey("grid_density_dp")

        // Player touch dock: which on-screen control buttons are shown (CSV of ids). Missing key =
        // factory default (DOCK_BUTTONS_DEFAULT). Lets users declutter the video — only the buttons
        // they actually use stay on screen.
        private val TOUCH_DOCK_BUTTONS = stringPreferencesKey("touch_dock_buttons")

        // ▲▼ zap buttons floating on the video's right edge (touch devices).
        private val ZAP_EDGE_BUTTONS = booleanPreferencesKey("zap_edge_buttons")

        const val RECENT_SEARCHES_MAX = 6
        const val EPG_STYLE_OFF = "off"
        const val EPG_STYLE_ROWS = "rows"
        const val EPG_STYLE_GRID = "grid"

        // Dock button ids + factory default. Keep in sync with the Settings screen's toggle list.
        // Volume intentionally absent: it's the right-edge swipe gesture in the player.
        const val DOCK_CHANNELS = "channels"
        const val DOCK_PROGRAMMES = "programmes"
        const val DOCK_NUMPAD = "numpad"
        const val DOCK_ASPECT = "aspect"
        const val DOCK_ROTATE = "rotate"
        const val DOCK_PIP = "pip"
        const val DOCK_PAUSE = "pause"
        const val DOCK_STATS = "stats"
        const val DOCK_SETTINGS = "settings"
        val DOCK_BUTTONS_DEFAULT = setOf(
            DOCK_CHANNELS, DOCK_PROGRAMMES, DOCK_ROTATE, DOCK_PAUSE, DOCK_SETTINGS
        )
        
        private val AUTH_SSO_TOKEN = stringPreferencesKey("auth_sso_token")
        private val AUTH_AUTH_TOKEN = stringPreferencesKey("auth_auth_token")
        private val AUTH_REFRESH_TOKEN = stringPreferencesKey("auth_refresh_token")
        private val AUTH_CRMID = stringPreferencesKey("auth_crmid")
        private val AUTH_UNIQUE_ID = stringPreferencesKey("auth_unique_id")
        private val AUTH_DEVICE_ID = stringPreferencesKey("auth_device_id")
        private val AUTH_USER_ID = stringPreferencesKey("auth_user_id")

        private val AUTOPLAY_LAST_CHANNEL = booleanPreferencesKey("autoplay_last_channel")
        private val LAST_CHANNEL_ID = stringPreferencesKey("last_channel_id")
        private val LAST_CHANNEL_GROUP = stringPreferencesKey("last_channel_group")

        // Onboarding / account source. setupMode: null = not chosen yet, "phone" = OTP login,
        // "server" = pull credentials from a JTV proxy server.
        private val SETUP_MODE = stringPreferencesKey("setup_mode")
        private val SERVER_URL = stringPreferencesKey("server_url")
        private val SERVER_TOKEN = stringPreferencesKey("server_token")
    }

    val setupModeFlow: Flow<String?> = context.dataStore.data.map { it[SETUP_MODE] }
    val serverUrlFlow: Flow<String> = context.dataStore.data.map { it[SERVER_URL] ?: "" }
    val serverTokenFlow: Flow<String> = context.dataStore.data.map { it[SERVER_TOKEN] ?: "" }

    suspend fun setSetupMode(mode: String?) {
        context.dataStore.edit { prefs ->
            if (mode == null) prefs.remove(SETUP_MODE) else prefs[SETUP_MODE] = mode
        }
    }

    suspend fun setServerConfig(url: String, token: String) {
        context.dataStore.edit { prefs ->
            prefs[SERVER_URL] = url
            prefs[SERVER_TOKEN] = token
        }
    }

    val defaultLanguageFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[DEFAULT_LANGUAGE] ?: "hi"
    }

    val defaultQualityFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[DEFAULT_QUALITY] ?: "auto"
    }

    val hardwareDecoderFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[HARDWARE_DECODER] ?: true
    }

    // Off by default: tunneling causes random black screens on many Amlogic/MediaTek TVs.
    val tunnelingFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[TUNNELING] ?: false
    }

    // Max playback buffer in seconds. Higher = smoother (rides out network/CDN jitter) at the cost of
    // more RAM and slightly higher channel-zap time. Default 60s.
    val playbackBufferSecFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[PLAYBACK_BUFFER_SEC] ?: 60
    }

    // Audio enhancement (applied via AudioEffects on the player session). Defaults to 2 (Medium) so
    // dialogue is clearer out of the box; users who explicitly set a level (incl. 0/Off) keep theirs.
    val voiceBoostFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[VOICE_BOOST] ?: 2
    }
    val audioNormalizeFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[AUDIO_NORMALIZE] ?: false
    }

    val lastSelectedCategoryFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[LAST_SELECTED_CATEGORY]
    }

    val lastUpdateCheckFlow: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[LAST_UPDATE_CHECK]?.toLongOrNull() ?: 0L
    }

    val playerResizeModeFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[PLAYER_RESIZE_MODE] ?: 0 // Default: RESIZE_MODE_FIT
    }

    // On by default: most users want one tile per channel and pick language in the player.
    val groupLanguageVariantsFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[GROUP_LANGUAGE_VARIANTS] ?: true
    }

    val epgModeFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[EPG_MODE] ?: false
    }

    val epgUrlFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[EPG_URL] ?: "https://avkb.short.gy/epg.xml.gz"
    }

    val autoplayLastChannelFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[AUTOPLAY_LAST_CHANNEL] ?: false
    }

    val lastChannelIdFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[LAST_CHANNEL_ID]
    }

    val lastChannelGroupFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[LAST_CHANNEL_GROUP]
    }

    val favoriteChannelsFlow: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        val serialized = preferences[FAVORITE_CHANNELS] ?: ""
        if (serialized.isEmpty()) emptySet() else serialized.split(",").toSet()
    }

    val languageFilterFlow: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        val serialized = preferences[LANGUAGE_FILTER] ?: ""
        if (serialized.isEmpty()) emptySet() else serialized.split(",").filter { it.isNotBlank() }.toSet()
    }

    val channelSortAlphabeticalFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[CHANNEL_SORT_ALPHABETICAL] ?: false
    }

    val zapPreviewFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[ZAP_PREVIEW] ?: false
    }

    suspend fun setZapPreview(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[ZAP_PREVIEW] = enabled }
    }

    // Migration: an unset style derives from the legacy epg_mode toggle (true → rows).
    val epgStyleFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[EPG_STYLE]
            ?: if (preferences[EPG_MODE] == true) EPG_STYLE_ROWS else EPG_STYLE_OFF
    }

    suspend fun setEpgStyle(style: String) {
        context.dataStore.edit { preferences ->
            when (style) {
                EPG_STYLE_GRID, EPG_STYLE_ROWS, EPG_STYLE_OFF -> preferences[EPG_STYLE] = style
            }
            // Keep the legacy boolean in sync so older builds that still read it behave sensibly.
            preferences[EPG_MODE] = style != EPG_STYLE_OFF
        }
    }

    val recentSearchesFlow: Flow<List<String>> = context.dataStore.data.map { preferences ->
        Mru.parse(preferences[RECENT_SEARCHES] ?: "")
    }

    suspend fun pushRecentSearch(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        context.dataStore.edit { preferences ->
            val existing = Mru.parse(preferences[RECENT_SEARCHES] ?: "")
                .filterNot { it.equals(q, ignoreCase = true) }
            preferences[RECENT_SEARCHES] = Mru.serialize((listOf(q) + existing).take(RECENT_SEARCHES_MAX))
        }
    }

    suspend fun clearRecentSearches() {
        context.dataStore.edit { preferences -> preferences.remove(RECENT_SEARCHES) }
    }

    val gridDensityDpFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        // 0 = Auto (resolve per screen in MainScreen). Legacy default was 150, but "Auto" is a
        // better default: 720p TVs get smaller tiles, 1080p stays comfortable, phones rely on the
        // window clamp. Users who explicitly chose a size keep their stored value.
        preferences[GRID_DENSITY_DP] ?: 0
    }

    suspend fun setGridDensityDp(dp: Int) {
        context.dataStore.edit { preferences -> preferences[GRID_DENSITY_DP] = dp.coerceIn(120, 200) }
    }

    val touchDockButtonsFlow: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        val serialized = preferences[TOUCH_DOCK_BUTTONS]
        if (serialized == null) DOCK_BUTTONS_DEFAULT
        else serialized.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
    }

    suspend fun setTouchDockButtons(buttons: Set<String>) {
        context.dataStore.edit { preferences ->
            preferences[TOUCH_DOCK_BUTTONS] = buttons.joinToString(",")
        }
    }

    val zapEdgeButtonsFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[ZAP_EDGE_BUTTONS] ?: true
    }

    suspend fun setZapEdgeButtons(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[ZAP_EDGE_BUTTONS] = enabled }
    }

    val authDataFlow: Flow<JioApiClient.AuthData?> = context.dataStore.data.map { preferences ->
        val ssoToken = preferences[AUTH_SSO_TOKEN]
        if (ssoToken.isNullOrEmpty()) {
            null
        } else {
            JioApiClient.AuthData(
                ssoToken = ssoToken,
                authToken = preferences[AUTH_AUTH_TOKEN] ?: "",
                crmid = preferences[AUTH_CRMID] ?: "",
                uniqueId = preferences[AUTH_UNIQUE_ID] ?: "",
                deviceId = preferences[AUTH_DEVICE_ID] ?: "",
                userId = preferences[AUTH_USER_ID] ?: "",
                refreshToken = preferences[AUTH_REFRESH_TOKEN] ?: ""
            )
        }
    }

    suspend fun setDefaultLanguage(language: String) {
        context.dataStore.edit { preferences ->
            preferences[DEFAULT_LANGUAGE] = language
        }
    }

    suspend fun setDefaultQuality(quality: String) {
        context.dataStore.edit { preferences ->
            preferences[DEFAULT_QUALITY] = quality
        }
    }

    suspend fun setHardwareDecoder(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[HARDWARE_DECODER] = enabled
        }
    }

    suspend fun setTunneling(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[TUNNELING] = enabled
        }
    }

    suspend fun setPlaybackBufferSec(seconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[PLAYBACK_BUFFER_SEC] = seconds
        }
    }

    suspend fun setVoiceBoost(level: Int) {
        context.dataStore.edit { preferences -> preferences[VOICE_BOOST] = level }
    }

    suspend fun setAudioNormalize(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[AUDIO_NORMALIZE] = enabled }
    }

    suspend fun setLastSelectedCategory(category: String) {
        context.dataStore.edit { preferences ->
            preferences[LAST_SELECTED_CATEGORY] = category
        }
    }

    suspend fun setLastUpdateCheck(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[LAST_UPDATE_CHECK] = timestamp.toString()
        }
    }

    suspend fun setPlayerResizeMode(mode: Int) {
        context.dataStore.edit { preferences ->
            preferences[PLAYER_RESIZE_MODE] = mode
        }
    }

    suspend fun setGroupLanguageVariants(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[GROUP_LANGUAGE_VARIANTS] = enabled }
    }

    suspend fun setEpgMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[EPG_MODE] = enabled
        }
    }

    suspend fun setEpgUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[EPG_URL] = url
        }
    }

    suspend fun setAutoplayLastChannel(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTOPLAY_LAST_CHANNEL] = enabled
        }
    }

    suspend fun setLastChannelId(id: String) {
        context.dataStore.edit { preferences ->
            preferences[LAST_CHANNEL_ID] = id
        }
    }

    suspend fun setLastChannelGroup(group: String?) {
        context.dataStore.edit { preferences ->
            if (group != null) {
                preferences[LAST_CHANNEL_GROUP] = group
            } else {
                preferences.remove(LAST_CHANNEL_GROUP)
            }
        }
    }

    suspend fun toggleFavoriteChannel(channelId: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[FAVORITE_CHANNELS] ?: ""
            val set = if (current.isEmpty()) mutableSetOf() else current.split(",").toMutableSet()
            if (set.contains(channelId)) {
                set.remove(channelId)
            } else {
                set.add(channelId)
            }
            preferences[FAVORITE_CHANNELS] = set.joinToString(",")
        }
    }

    suspend fun setLanguageFilter(languages: Set<String>) {
        context.dataStore.edit { preferences ->
            preferences[LANGUAGE_FILTER] = languages.filter { it.isNotBlank() }.joinToString(",")
        }
    }

    suspend fun setChannelSortAlphabetical(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[CHANNEL_SORT_ALPHABETICAL] = enabled
        }
    }

    suspend fun saveAuthData(authData: JioApiClient.AuthData) {
        context.dataStore.edit { preferences ->
            preferences[AUTH_SSO_TOKEN] = authData.ssoToken
            preferences[AUTH_AUTH_TOKEN] = authData.authToken
            preferences[AUTH_REFRESH_TOKEN] = authData.refreshToken
            preferences[AUTH_CRMID] = authData.crmid
            preferences[AUTH_UNIQUE_ID] = authData.uniqueId
            preferences[AUTH_DEVICE_ID] = authData.deviceId
            preferences[AUTH_USER_ID] = authData.userId
        }
    }

    suspend fun clearAuthData() {
        context.dataStore.edit { preferences ->
            preferences.remove(AUTH_SSO_TOKEN)
            preferences.remove(AUTH_AUTH_TOKEN)
            preferences.remove(AUTH_REFRESH_TOKEN)
            preferences.remove(AUTH_CRMID)
            preferences.remove(AUTH_UNIQUE_ID)
            preferences.remove(AUTH_DEVICE_ID)
            preferences.remove(AUTH_USER_ID)
        }
    }
}
