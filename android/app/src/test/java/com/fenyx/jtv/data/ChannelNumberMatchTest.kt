package com.fenyx.jtv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChannelNumberMatchTest {

    private val channels = listOf(
        ch("A"), ch("B"), ch("C"), ch("D"), ch("E"), ch("F"), ch("G"), ch("H"), ch("I"), ch("J")
    )

    private fun ch(name: String) = Channel(
        id = name, name = name, logoUrl = "", group = "",
        streamUrl = "", channelNumber = 0
    )

    @Test
    fun `prefix matches 1-based positions like commitNumericEntry`() {
        // "1" matches positions 1 and 10 (numbers 1..10 in a 10-item list).
        val hits = ChannelFilter.findByNumberPrefix(channels, "1")
        assertEquals(listOf(0 to channels[0], 9 to channels[9]), hits)
    }

    @Test
    fun `full number resolves exactly one channel`() {
        val hits = ChannelFilter.findByNumberPrefix(channels, "7")
        assertEquals(1, hits.size)
        assertEquals(6, hits[0].first)
        assertEquals("G", hits[0].second.name)
    }

    @Test
    fun `limit caps results`() {
        val many = (1..50).map { ch("Ch$it") }
        val hits = ChannelFilter.findByNumberPrefix(many, "1", limit = 3)
        assertEquals(3, hits.size) // 1, 10, 11
    }

    @Test
    fun `empty or non-numeric prefix matches nothing`() {
        assertEquals(emptyList<Pair<Int, Channel>>(), ChannelFilter.findByNumberPrefix(channels, ""))
        assertEquals(emptyList<Pair<Int, Channel>>(), ChannelFilter.findByNumberPrefix(channels, "1x"))
    }
}
