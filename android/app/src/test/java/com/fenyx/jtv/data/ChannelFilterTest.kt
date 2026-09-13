package com.fenyx.jtv.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelFilterTest {

    private fun ch(
        id: String,
        name: String,
        group: String = "Sports",
        language: String = JioLanguages.OTHER
    ) = Channel(id = id, name = name, logoUrl = "", group = group, streamUrl = "",
        channelNumber = id.toInt(), language = language)

    @Test
    fun apply_emptyLanguageFilterShowsEverything() {
        val channels = listOf(
            ch("1", "Hindi Channel", language = "Hindi"),
            ch("2", "Tamil Channel", language = "Tamil")
        )
        val out = ChannelFilter.apply(channels, emptyMap(), ChannelFilter.GROUP_ALL, emptySet(), emptySet())
        assertEquals(2, out.size)
    }

    @Test
    fun apply_filtersBySelectedLanguages() {
        val channels = listOf(
            ch("1", "Hindi Channel", language = "Hindi"),
            ch("2", "Tamil Channel", language = "Tamil"),
            ch("3", "Telugu Channel", language = "Telugu")
        )
        val out = ChannelFilter.apply(
            channels, emptyMap(), ChannelFilter.GROUP_ALL, emptySet(), setOf("Tamil", "Telugu")
        )
        assertEquals(listOf("2", "3"), out.map { it.id })
    }

    @Test
    fun apply_collapsedFamilySurvivesWhenAnyVariantMatches() {
        // The representative tile is the Hindi feed (lowest channel number); a Tamil-only filter
        // must still keep the family visible via its Tamil variant.
        val feeds = listOf(
            ch("1", "Star Sports 1 Hindi", language = "Hindi"),
            ch("2", "Star Sports 1 Tamil", language = "Tamil"),
            ch("3", "Star Sports 1 Telugu", language = "Telugu")
        )
        val (display, variants) = ChannelLanguage.collapse(feeds)
        assertEquals(1, display.size)

        val tamilOnly = ChannelFilter.apply(display, variants, ChannelFilter.GROUP_ALL, emptySet(), setOf("Tamil"))
        assertEquals(1, tamilOnly.size)

        val noneMatch = ChannelFilter.apply(display, variants, ChannelFilter.GROUP_ALL, emptySet(), setOf("Kannada"))
        assertEquals(0, noneMatch.size)
    }

    @Test
    fun apply_familySurvivesViaNameDetectedVariantLanguage() {
        // Variants whose API language is "Other" but whose NAME carries the language token.
        val feeds = listOf(
            ch("1", "Star Sports 1 Hindi", language = JioLanguages.OTHER),
            ch("2", "Star Sports 1 Tamil", language = JioLanguages.OTHER)
        )
        val (display, variants) = ChannelLanguage.collapse(feeds)
        val out = ChannelFilter.apply(display, variants, ChannelFilter.GROUP_ALL, emptySet(), setOf("Tamil"))
        assertEquals(1, out.size)
    }

    @Test
    fun apply_intersectsLanguageWithCategoryAndFavorites() {
        val channels = listOf(
            ch("1", "A", group = "News", language = "Hindi"),
            ch("2", "B", group = "News", language = "Tamil"),
            ch("3", "C", group = "Sports", language = "Tamil")
        )
        val news = ChannelFilter.apply(channels, emptyMap(), "News", emptySet(), setOf("Tamil"))
        assertEquals(listOf("2"), news.map { it.id })

        val favs = setOf("1")
        val favList = ChannelFilter.apply(channels, emptyMap(), ChannelFilter.GROUP_FAVORITES, favs, setOf("Hindi"))
        assertEquals(listOf("1"), favList.map { it.id })
    }

    @Test
    fun apply_sortsFavoritesFirstThenChannelNumber() {
        val channels = listOf(
            ch("5", "E", language = "Hindi"),
            ch("2", "B", language = "Hindi"),
            ch("9", "I", language = "Hindi")
        )
        val out = ChannelFilter.apply(channels, emptyMap(), null, favorites = setOf("9"), languages = emptySet())
        assertEquals(listOf("9", "2", "5"), out.map { it.id })
    }

    @Test
    fun countsByGroup_respectsLanguageFilter() {
        val channels = listOf(
            ch("1", "A", group = "News", language = "Hindi"),
            ch("2", "B", group = "News", language = "Tamil"),
            ch("3", "C", group = "Sports", language = "Tamil")
        )
        val counts = ChannelFilter.countsByGroup(channels, emptyMap(), favorites = setOf("2"), languages = setOf("Tamil"))
        assertEquals(2, counts[ChannelFilter.GROUP_ALL])          // both Tamil channels
        assertEquals(1, counts[ChannelFilter.GROUP_FAVORITES])    // favorite "2" is Tamil
        assertEquals(1, counts["News"])
        assertEquals(1, counts["Sports"])
    }

    @Test
    fun countsByGroup_emptyLanguageFilterCountsEverything() {
        val channels = listOf(
            ch("1", "A", group = "News", language = "Hindi"),
            ch("2", "B", group = "News", language = "Tamil")
        )
        val counts = ChannelFilter.countsByGroup(channels, emptyMap(), emptySet(), emptySet())
        assertEquals(2, counts[ChannelFilter.GROUP_ALL])
        assertEquals(2, counts["News"])
    }

    @Test
    fun apply_sortsAlphabeticallyWhenEnabled() {
        val channels = listOf(
            ch("5", "Zee TV", language = "Hindi"),
            ch("2", "Aaj Tak", language = "Hindi"),
            ch("9", "Colors HD", language = "Hindi")
        )
        val out = ChannelFilter.apply(
            channels, emptyMap(), null, emptySet(), emptySet(), sortAlphabetical = true
        )
        assertEquals(listOf("Aaj Tak", "Colors HD", "Zee TV"), out.map { it.name })
        // Default stays by channel number.
        val byNumber = ChannelFilter.apply(channels, emptyMap(), null, emptySet(), emptySet())
        assertEquals(listOf("2", "5", "9"), byNumber.map { it.id })
    }

    @Test
    fun apply_alphabeticalKeepsFavoritesFirst() {
        val channels = listOf(
            ch("1", "Aaj Tak", language = "Hindi"),
            ch("2", "Zee TV", language = "Hindi"),
            ch("3", "Colors HD", language = "Hindi")
        )
        val out = ChannelFilter.apply(
            channels, emptyMap(), null, favorites = setOf("2"), languages = emptySet(),
            sortAlphabetical = true
        )
        assertEquals(listOf("Zee TV", "Aaj Tak", "Colors HD"), out.map { it.name })
    }

    @Test
    fun apply_alphabeticalIsCaseAndWhitespaceInsensitive() {
        val channels = listOf(
            ch("1", "sony sab", language = "Hindi"),
            ch("2", "  Star Plus", language = "Hindi"),
            ch("3", "AAJ TAK", language = "Hindi")
        )
        val out = ChannelFilter.apply(
            channels, emptyMap(), null, emptySet(), emptySet(), sortAlphabetical = true
        )
        assertEquals(listOf("AAJ TAK", "sony sab", "  Star Plus"), out.map { it.name })
    }

    @Test
    fun apply_hiddenChannelsAreExcludedEverywhere() {
        val channels = listOf(
            ch("1", "A", group = "News", language = "Hindi"),
            ch("2", "B", group = "News", language = "Hindi"),
            ch("3", "C", group = "Sports", language = "Hindi")
        )
        val out = ChannelFilter.apply(
            channels, emptyMap(), ChannelFilter.GROUP_ALL, emptySet(), emptySet(), hidden = setOf("2")
        )
        assertEquals(listOf("1", "3"), out.map { it.id })
        // Hidden beats the Favorites sentinel too.
        val favs = ChannelFilter.apply(
            channels, emptyMap(), ChannelFilter.GROUP_FAVORITES,
            favorites = setOf("2"), languages = emptySet(), hidden = setOf("2")
        )
        assertEquals(0, favs.size)
    }

    @Test
    fun countsByGroup_excludesHiddenChannels() {
        val channels = listOf(
            ch("1", "A", group = "News", language = "Hindi"),
            ch("2", "B", group = "News", language = "Hindi")
        )
        val counts = ChannelFilter.countsByGroup(
            channels, emptyMap(), emptySet(), emptySet(), hidden = setOf("2")
        )
        assertEquals(1, counts[ChannelFilter.GROUP_ALL])
        assertEquals(1, counts["News"])
    }

    // ─── languageScoped (Settings channel manager) ───

    @Test
    fun languageScoped_keepsHiddenChannelsListed() {
        // The whole point of the manager: hidden channels MUST appear (to be unhidden), unlike apply().
        val channels = listOf(
            ch("1", "A", language = "Hindi"),
            ch("2", "B", language = "Hindi")
        )
        val out = ChannelFilter.languageScoped(channels, emptyMap(), emptySet())
        assertEquals(listOf("1", "2"), out.map { it.id })
    }

    @Test
    fun languageScoped_appliesLanguageFilterAndSortsAZ() {
        val channels = listOf(
            ch("3", "Zee TV", language = "Hindi"),
            ch("1", "Aaj Tak", language = "Hindi"),
            ch("2", "Sun TV", language = "Tamil")
        )
        val out = ChannelFilter.languageScoped(channels, emptyMap(), setOf("Hindi"))
        assertEquals(listOf("Aaj Tak", "Zee TV"), out.map { it.name })
    }

    @Test
    fun languageScoped_emptyFilterReturnsAllSorted() {
        val channels = listOf(
            ch("2", "b", language = "Hindi"),
            ch("1", "a", language = "Tamil")
        )
        val out = ChannelFilter.languageScoped(channels, emptyMap(), emptySet())
        assertEquals(listOf("1", "2"), out.map { it.id })
    }

    @Test
    fun languageScoped_collapsedFamilySurvivesWhenAnyVariantMatches() {
        val feeds = listOf(
            ch("1", "Star Sports 1 Hindi", language = "Hindi"),
            ch("2", "Star Sports 1 Tamil", language = "Tamil")
        )
        val (display, variants) = ChannelLanguage.collapse(feeds)
        val tamilOnly = ChannelFilter.languageScoped(display, variants, setOf("Tamil"))
        assertEquals(1, tamilOnly.size)
    }
}
