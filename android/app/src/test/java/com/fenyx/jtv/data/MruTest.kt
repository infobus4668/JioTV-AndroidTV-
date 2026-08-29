package com.fenyx.jtv.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MruTest {

    @Test
    fun `parse drops blanks and whitespace`() {
        assertEquals(listOf("12", "5"), Mru.parse(" 12 , , 5, "))
        assertEquals(emptyList<String>(), Mru.parse(""))
        assertEquals(emptyList<String>(), Mru.parse("   "))
    }

    @Test
    fun `serialize round-trips`() {
        val list = listOf("a", "b", "c")
        assertEquals(list, Mru.parse(Mru.serialize(list)))
    }

    @Test
    fun `push moves id to front and dedupes`() {
        val pushed = Mru.push(listOf("1", "2", "3"), "2", max = 8)
        assertEquals(listOf("2", "1", "3"), pushed)
    }

    @Test
    fun `push caps at max keeping newest`() {
        var list = listOf("1", "2", "3")
        for (n in 4..8) list = Mru.push(list, n.toString(), max = 4)
        // Newest four survive; oldest were evicted.
        assertEquals(listOf("8", "7", "6", "5"), list)
    }

    @Test
    fun `push ignores blank ids`() {
        assertEquals(listOf("1", "2"), Mru.push(listOf("1", "2"), "", max = 8))
    }
}
