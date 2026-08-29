package com.fenyx.jtv.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The catch-up request body MUST match server/src/jio/epg.ts → stream.ts byte-for-byte: that exact
 * format is verified working against the live Jio API (the web player replays through it today).
 */
class PlaybackBodyTest {

    @Test
    fun `live body is unchanged`() {
        assertEquals(
            "stream_type=Live&channel_id=123",
            JioApiClient.buildPlaybackBody("123", null)
        )
    }

    @Test
    fun `catchup body mirrors the server format`() {
        val cu = JioApiClient.CatchupParams(
            programId = "999", srno = "12345",
            beginMs = 1755850000000L, endMs = 1755853600000L,
            showtime = "2026-08-22 20:00"
        )
        assertEquals(
            "stream_type=Catchup&channel_id=42&srno=12345&programId=999" +
                "&begin=1755850000000&end=1755853600000&showtime=2026-08-22%2020%3A00",
            JioApiClient.buildPlaybackBody("42", cu)
        )
    }
}
