package com.fenyx.jtv.data

import androidx.compose.runtime.Immutable

@Immutable
data class Channel(
    val id: String,
    val name: String,
    val logoUrl: String,
    val group: String,
    val streamUrl: String,
    val isDrm: Boolean = false,
    val channelNumber: Int = 0,
    val licenseUrl: String? = null,
    // Display language ("Hindi", "Tamil", …) resolved from the API's channelLanguageId (see
    // [JioLanguages]). Powers the language filter; "Other" = unresolvable.
    val language: String = JioLanguages.OTHER,
    // Raw API channelLanguageId (-1 = unknown). Persisted in the cache so reads re-resolve exactly.
    val languageId: Int = -1
)
