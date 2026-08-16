package com.fenyx.jtv.data

/**
 * JioTV channelLanguageId → display name.
 *
 * The table matches the dictionary API's authoritative `languageIdMapping`
 * (…/apis/v1.3/dictionary/dictionary?langId=6), verified against the live ~1300-channel list: id 8
 * feeds are "Star Vijay"/"Colors Tamil" (Tamil), id 11 are "TV9 Telugu"/"Zee Cinemalu" (Telugu),
 * id 5 are "Star Jalsha"/"Zee Bangla" (Bengali), etc. NOTE: the companion server's hand-written
 * JIO_LANG table disagrees with this from id 5 onward (it claims 5=Tamil, 8=Telugu, …) — that table
 * is wrong; do not copy it. The live mapping is also parsed at runtime (see JioApiClient) and takes
 * precedence, keeping this table as the offline fallback.
 */
object JioLanguages {

    /** Display name for channels whose language can't be resolved. */
    const val OTHER = "Other"

    private val BY_ID: Map<Int, String> = mapOf(
        1 to "Hindi", 2 to "Marathi", 3 to "Punjabi", 4 to "Urdu", 5 to "Bengali", 6 to "English",
        7 to "Malayalam", 8 to "Tamil", 9 to "Gujarati", 10 to "Odia", 11 to "Telugu",
        12 to "Bhojpuri", 13 to "Kannada", 14 to "Assamese", 15 to "Nepali", 16 to "French"
    )

    /** Distinct languages present in [channels], alphabetical with [OTHER] pinned last (picker order). */
    fun availableIn(channels: List<Channel>): List<String> {
        val langs = channels.map { it.language }.filter { it.isNotBlank() && it != OTHER }.distinct().sorted()
        return if (channels.any { it.language == OTHER || it.language.isBlank() }) langs + OTHER else langs
    }

    /**
     * Resolves a channel's display language: the live dictionary's languageIdMapping first (ids Jio
     * may add later, e.g. 21), then the built-in table, then the trailing language token in the
     * channel name (also covers channel caches saved before the id was captured), else [OTHER].
     */
    fun resolve(apiId: Int?, channelName: String, dictionaryMapping: Map<String, String>? = null): String =
        dictionaryMapping?.get(apiId?.toString())
            ?: BY_ID[apiId]
            ?: ChannelLanguage.detect(channelName).langCode?.let { ChannelLanguage.displayName(it) }
            ?: OTHER
}
