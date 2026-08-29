package com.fenyx.jtv.data

/**
 * Pure most-recently-used list helpers shared by "Recently watched" and recent searches.
 * Kept dependency-free so they are trivially unit-testable.
 */
object Mru {

    /** Parses a persisted comma-separated MRU string into a list (blank entries dropped). */
    fun parse(raw: String): List<String> =
        if (raw.isBlank()) emptyList() else raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    /** Serializes an MRU list back to the comma-separated storage form. */
    fun serialize(list: List<String>): String = list.joinToString(",")

    /**
     * Pushes [id] to the front of [current], removing any earlier occurrence, capped at [max] items.
     * A blank id is ignored.
     */
    fun push(current: List<String>, id: String, max: Int): List<String> {
        if (id.isBlank()) return current
        return (listOf(id) + current.filter { it != id }).take(max)
    }
}
