package com.fenyx.jtv.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeEpgParserTest {

    @Test
    fun `parses catchup fields from native epg json`() {
        val json = JSONObject("""
        {
          "epg": [
            {"showname":"Past Show","description":"d1","startEpoch":1000,"endEpoch":2000,
             "srno":"12345","showId":"999","showtime":"2026-08-22 20:00","isCatchupAvailable":true},
            {"showname":"Live Now","description":"d2","startEpoch":3000,"endEpoch":4000,
             "srno":"","isCatchupAvailable":false},
            {"showname":"Bad","description":"","startEpoch":0,"endEpoch":0}
          ]
        }""".trimIndent())

        val programs = EpgRepository.parseNativeEpg(json)
        assertEquals(2, programs.size)

        val past = programs[0]
        assertEquals("Past Show", past.title)
        assertEquals(1000L, past.startMs)
        assertEquals("12345", past.srno)
        assertEquals("999", past.showId)
        assertEquals("2026-08-22 20:00", past.showtime)
        assertTrue(past.catchupAvailable)

        // Blank srno parses to null (not empty string) so replay affordances grey out cleanly.
        assertNull(programs[1].srno)
    }

    @Test
    fun `missing or malformed sections yield empty list`() {
        assertTrue(EpgRepository.parseNativeEpg(JSONObject("{}")).isEmpty())
        assertTrue(EpgRepository.parseNativeEpg(JSONObject("""{"epg":[]}""")).isEmpty())
    }

    @Test
    fun `isReplayable requires a past stop time and srno`() {
        val now = System.currentTimeMillis()
        val pastNoSrno = EpgProgram("a", "", now - 7200_000, now - 3600_000, srno = null)
        assertFalse(pastNoSrno.isReplayable)

        val pastWithSrno = EpgProgram(
            "b", "", now - 7200_000, now - 3600_000,
            srno = "42", showId = "7", showtime = "t", catchupAvailable = false
        )
        assertTrue(pastWithSrno.isReplayable)

        val future = EpgProgram(
            "c", "", now + 3600_000, now + 7200_000,
            srno = "42", catchupAvailable = false
        )
        assertFalse(future.isReplayable)

        // Explicitly flagged catch-up even while still airing (Jio does this near the live edge).
        val flagged = EpgProgram(
            "d", "", now - 600_000, now + 600_000,
            srno = "43", catchupAvailable = true
        )
        assertTrue(flagged.isReplayable)
    }
}
