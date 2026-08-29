package com.fenyx.jtv.data

import org.junit.Assert.assertEquals
import org.junit.Test

class JioLanguagesTest {

    // Regression guard: the mapping must match the dictionary API's authoritative
    // `languageIdMapping`, verified against the live channel list on 2026-08-16. The previous
    // table (copied from the companion server) disagreed from id 5 onward and mis-filtered
    // Telugu channels as Tamil, Tamil as Bengali, etc.
    @Test
    fun resolve_matchesOfficialDictionaryMapping() {
        assertEquals("Hindi", JioLanguages.resolve(1, "Star Gold"))
        assertEquals("Marathi", JioLanguages.resolve(2, "Star Pravah"))
        assertEquals("Punjabi", JioLanguages.resolve(3, "DD Punjabi"))
        assertEquals("Urdu", JioLanguages.resolve(4, "DD Urdu"))
        assertEquals("Bengali", JioLanguages.resolve(5, "Zee Bangla"))
        assertEquals("English", JioLanguages.resolve(6, "Star Movies"))
        assertEquals("Malayalam", JioLanguages.resolve(7, "Asianet"))
        assertEquals("Tamil", JioLanguages.resolve(8, "Star Vijay"))
        assertEquals("Gujarati", JioLanguages.resolve(9, "Colors Gujarati"))
        assertEquals("Odia", JioLanguages.resolve(10, "Tarang"))
        assertEquals("Telugu", JioLanguages.resolve(11, "Gemini TV"))
        assertEquals("Bhojpuri", JioLanguages.resolve(12, "Bhojpuri Cinema"))
        assertEquals("Kannada", JioLanguages.resolve(13, "Suvarna"))
        assertEquals("Assamese", JioLanguages.resolve(14, "Rang"))
        assertEquals("Nepali", JioLanguages.resolve(15, "Nepal One"))
        assertEquals("French", JioLanguages.resolve(16, "France 24"))
    }

    @Test
    fun resolve_liveDictionaryMappingTakesPrecedence() {
        // The dictionary's languageIdMapping is parsed at runtime and wins over the built-in
        // table, so ids Jio adds later (e.g. 21 in the live list) resolve without an app update.
        val dict = mapOf("21" to "Rajasthani")
        assertEquals("Rajasthani", JioLanguages.resolve(21, "Sundrani TV", dict))
        // …and can correct a built-in label.
        assertEquals("Corrected", JioLanguages.resolve(8, "Star Vijay", mapOf("8" to "Corrected")))
    }

    @Test
    fun resolve_unknownIdFallsBackToNameToken() {
        // Covers stale caches saved before channelLanguageId was captured.
        assertEquals("Tamil", JioLanguages.resolve(null, "Star Sports 1 Tamil"))
        assertEquals("Bengali", JioLanguages.resolve(null, "Star Sports 1 Bangla HD"))
    }

    @Test
    fun resolve_unknownIdWithoutTokenIsOther() {
        assertEquals(JioLanguages.OTHER, JioLanguages.resolve(null, "Sony SAB"))
        assertEquals(JioLanguages.OTHER, JioLanguages.resolve(999, "Mystery Channel"))
    }

    @Test
    fun availableIn_sortsAlphabeticallyAndPinsOtherLast() {
        val channels = listOf(
            Channel("1", "A", "", "News", "", channelNumber = 1, language = "Tamil"),
            Channel("2", "B", "", "News", "", channelNumber = 2, language = "Hindi"),
            Channel("3", "C", "", "News", "", channelNumber = 3, language = "Tamil"), // dup
            Channel("4", "D", "", "News", "", channelNumber = 4, language = JioLanguages.OTHER)
        )
        assertEquals(listOf("Hindi", "Tamil", "Other"), JioLanguages.availableIn(channels))
    }

    @Test
    fun availableIn_omitsOtherWhenEverythingResolved() {
        val channels = listOf(
            Channel("1", "A", "", "News", "", channelNumber = 1, language = "Hindi"),
            Channel("2", "B", "", "News", "", channelNumber = 2, language = "English")
        )
        assertEquals(listOf("English", "Hindi"), JioLanguages.availableIn(channels))
    }
}
