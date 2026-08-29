package com.fenyx.jtv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpgWindowClipTest {

    private val winStart = 1_000_000L
    private val winEnd = 5_000_000L

    @Test
    fun `fully inside window passes through`() {
        val r = EpgRepository.clipProgramToWindow(1_500_000, 2_500_000, winStart, winEnd)!!
        assertEquals(1_500_000L, r.first)
        assertEquals(2_499_999L, r.last)
    }

    @Test
    fun `starts before and ends inside is clipped to window start`() {
        val r = EpgRepository.clipProgramToWindow(0, 2_000_000, winStart, winEnd)!!
        assertEquals(winStart, r.first)
        assertEquals(1_999_999L, r.last)
    }

    @Test
    fun `spans whole window is clipped both sides`() {
        val r = EpgRepository.clipProgramToWindow(0, 9_000_000, winStart, winEnd)!!
        assertEquals(winStart, r.first)
        assertEquals(winEnd - 1, r.last)
    }

    @Test
    fun `touching boundaries does not overlap`() {
        assertNull(EpgRepository.clipProgramToWindow(0, winStart, winStart, winEnd))
        assertNull(EpgRepository.clipProgramToWindow(winEnd, winEnd + 1, winStart, winEnd))
    }

    @Test
    fun `degenerate range returns null`() {
        assertNull(EpgRepository.clipProgramToWindow(3_000_000, 3_000_000, winStart, winEnd))
    }
}
