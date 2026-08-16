package com.fenyx.jtv.data

/**
 * Pure channel-list filtering shared by the Home grid and the player's zap list. The language
 * filter is a persisted multi-select (empty set = show all); a collapsed dub family
 * ([ChannelLanguage.collapse]) survives it when ANY of its language variants matches, so
 * "Star Sports 1" stays visible to a Tamil user even though the representative tile may be the
 * un-suffixed feed.
 */
object ChannelFilter {

    // Sentinel sidebar categories (kept here so the data-layer filter stays self-contained).
    const val GROUP_ALL = "__ALL__"
    const val GROUP_FAVORITES = "__FAVORITES__"

    fun languageMatches(
        channel: Channel,
        variants: Map<String, List<ChannelLanguage.Variant>>,
        filter: Set<String>
    ): Boolean {
        if (filter.isEmpty()) return true
        if (channel.language in filter) return true
        // Collapsed family: match on any variant's API language OR its name-detected token.
        return variants[channel.id]?.any { v ->
            v.channel.language in filter ||
                (v.langCode != null && ChannelLanguage.displayName(v.langCode) in filter)
        } == true
    }

    /**
     * Applies the language filter, then the sidebar category / Favorites selection, then the
     * canonical sort (favorites first, then channel number — or A–Z by name when
     * [sortAlphabetical] is set) — the single source of truth for "what list am I looking at"
     * across Home and the player, so every surface loads the same order.
     */
    fun apply(
        display: List<Channel>,
        variants: Map<String, List<ChannelLanguage.Variant>>,
        group: String?,
        favorites: Set<String>,
        languages: Set<String>,
        sortAlphabetical: Boolean = false
    ): List<Channel> {
        val byLanguage = display.filter { languageMatches(it, variants, languages) }
        val byGroup = when (group) {
            null, GROUP_ALL -> byLanguage
            GROUP_FAVORITES -> byLanguage.filter { favorites.contains(it.id) }
            else -> byLanguage.filter { it.group == group }
        }
        return if (sortAlphabetical) {
            byGroup.sortedWith(
                // Case/whitespace-insensitive A–Z; channel number breaks exact-name ties stably.
                compareByDescending<Channel> { favorites.contains(it.id) }
                    .thenBy { it.name.trim().lowercase() }
                    .thenBy { it.channelNumber }
            )
        } else {
            byGroup.sortedWith(
                compareByDescending<Channel> { favorites.contains(it.id) }.thenBy { it.channelNumber }
            )
        }
    }

    /**
     * Channel counts per sidebar category (incl. the All/Favorites sentinels) under the given
     * language filter — the number the grid would show if that chip were selected.
     */
    fun countsByGroup(
        display: List<Channel>,
        variants: Map<String, List<ChannelLanguage.Variant>>,
        favorites: Set<String>,
        languages: Set<String>
    ): Map<String, Int> {
        val byLang = display.filter { languageMatches(it, variants, languages) }
        return buildMap {
            put(GROUP_ALL, byLang.size)
            put(GROUP_FAVORITES, byLang.count { it.id in favorites })
            byLang.groupBy { it.group }.forEach { (g, list) -> put(g, list.size) }
        }
    }
}
