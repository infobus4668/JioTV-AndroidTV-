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
     * Language-scoped channel list for the Settings "Hide / Unhide Channels" manager: the
     * language filter (multi-select, empty = all) applied WITHOUT the hidden-channel exclusion —
     * hidden channels must stay listed so they can be unhidden — and without the sidebar group
     * filter. Sorted A–Z: the manager is a lookup surface, so a stable name order beats the
     * Home sort preference.
     */
    fun languageScoped(
        display: List<Channel>,
        variants: Map<String, List<ChannelLanguage.Variant>>,
        languages: Set<String>
    ): List<Channel> =
        display.filter { languageMatches(it, variants, languages) }
            .sortedBy { it.name.trim().lowercase() }

    /**
     * Applies the hidden-channel exclusion, then the language filter, then the sidebar
     * category / Favorites selection, then the canonical sort (favorites first, then channel
     * number — or A–Z by name when [sortAlphabetical] is set) — the single source of truth
     * for "what list am I looking at" across Home and the player, so every surface loads
     * the same order. Hidden always wins (even over Favorites) so a hidden channel can
     * never surface anywhere until it is unhidden.
     */
    fun apply(
        display: List<Channel>,
        variants: Map<String, List<ChannelLanguage.Variant>>,
        group: String?,
        favorites: Set<String>,
        languages: Set<String>,
        sortAlphabetical: Boolean = false,
        hidden: Set<String> = emptySet()
    ): List<Channel> {
        val visible = if (hidden.isEmpty()) display else display.filter { it.id !in hidden }
        val byLanguage = visible.filter { languageMatches(it, variants, languages) }
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
     * Hidden channels are excluded so counts match the visible grid.
     */
    fun countsByGroup(
        display: List<Channel>,
        variants: Map<String, List<ChannelLanguage.Variant>>,
        favorites: Set<String>,
        languages: Set<String>,
        hidden: Set<String> = emptySet()
    ): Map<String, Int> {
        val visible = if (hidden.isEmpty()) display else display.filter { it.id !in hidden }
        val byLang = visible.filter { languageMatches(it, variants, languages) }
        return buildMap {
            put(GROUP_ALL, byLang.size)
            put(GROUP_FAVORITES, byLang.count { it.id in favorites })
            byLang.groupBy { it.group }.forEach { (g, list) -> put(g, list.size) }
        }
    }

    /**
     * Live numpad matching: the channels whose 1-based list position (the number shown in every
     * overlay) starts with [prefix]. Returns (position, channel) pairs capped at [limit].
     * Mirrors commitNumericEntry's `number = index + 1` mapping exactly.
     */
    fun findByNumberPrefix(
        channels: List<Channel>,
        prefix: String,
        limit: Int = 3
    ): List<Pair<Int, Channel>> {
        if (prefix.isEmpty() || prefix.toIntOrNull() == null) return emptyList()
        return channels.withIndex()
            .filter { (i, _) -> (i + 1).toString().startsWith(prefix) }
            .map { (i, ch) -> i to ch }
            .take(limit)
    }

    /**
     * Alphabet jump: first index whose name starts with [letter] (case-insensitive, trimmed),
     * or -1. Linear scan on purpose — favorites-first ordering means names are never globally
     * sorted, and ≤1300 linear comparisons are nothing even on a weak TV.
     */
    fun firstIndexForLetter(names: List<String>, letter: Char): Int {
        val l = letter.uppercaseChar()
        return names.indexOfFirst { it.trim().uppercase().startsWith(l) }
    }
}
