package com.fenyx.jtv.data

/**
 * Maps a Jio category name to an emoji glyph for faster visual scanning of chips/sidebars
 * (pure function, unit-tested). Empty string = no icon; callers render the bare label.
 */
object CategoryIcons {

    fun emojiFor(group: String): String {
        val g = group.trim().lowercase()
        if (g.isEmpty()) return ""
        return when {
            "news" in g -> "📰"
            "sport" in g || "cricket" in g -> "🏏"
            "movie" in g || "film" in g || "cinema" in g -> "🎬"
            "music" in g -> "🎵"
            "kid" in g || "cartoon" in g || "child" in g -> "🧸"
            "infotain" in g || "document" in g || "knowledge" in g || "science" in g -> "🔬"
            "lifestyle" in g || "fashion" in g || "travel" in g -> "🌿"
            "devotion" in g || "religio" in g || "spiritual" in g -> "🕉️"
            "cook" in g || "food" in g -> "🍳"
            "education" in g || "learn" in g -> "🎓"
            "business" in g || "finance" in g -> "📈"
            "comedy" in g -> "😂"
            "entertain" in g -> "🎭"
            else -> ""
        }
    }

    /** Label form used everywhere: "🏏 Sports". Sentinels/empty pass through untouched. */
    fun decorate(label: String): String {
        val icon = emojiFor(label)
        return if (icon.isEmpty()) label else "$icon $label"
    }
}
