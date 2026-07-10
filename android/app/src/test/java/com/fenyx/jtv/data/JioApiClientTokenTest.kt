package com.fenyx.jtv.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure-function tests for the Akamai `__hdnea__` token helpers. These drive the transparent
 * token-refresh path in the player, so they are the safety net for the Media3 upgrade — a regression
 * here would silently break live playback (403 loops / black screens) without failing to compile.
 */
class JioApiClientTokenTest {

    @Test
    fun extractHdnea_returnsEverythingAfterTheMarker() {
        val url = "https://cdn.example/live/master.m3u8?__hdnea__=st=100~exp=1783000000~hmac=abc123"
        assertEquals("st=100~exp=1783000000~hmac=abc123", JioApiClient.extractHdneaToken(url))
    }

    @Test
    fun extractHdnea_emptyWhenMarkerAbsent() {
        assertEquals("", JioApiClient.extractHdneaToken("https://cdn.example/live/master.m3u8?foo=bar"))
    }

    @Test
    fun extractHdnea_emptyForEmptyInput() {
        assertEquals("", JioApiClient.extractHdneaToken(""))
    }

    @Test
    fun extractExpiry_readsExpEpochSeconds() {
        assertEquals(1783000000L, JioApiClient.extractTokenExpiryEpochSec("st=100~exp=1783000000~hmac=abc"))
    }

    @Test
    fun extractExpiry_zeroWhenNoExpField() {
        assertEquals(0L, JioApiClient.extractTokenExpiryEpochSec("st=100~hmac=abc"))
    }

    @Test
    fun extractExpiry_zeroForEmptyToken() {
        assertEquals(0L, JioApiClient.extractTokenExpiryEpochSec(""))
    }

    @Test
    fun tokenHelpers_composeEndToEnd() {
        // The real flow: pull the token out of the stream URL, then read its expiry from that token.
        val url = "https://cdn.jio/live/x.m3u8?__hdnea__=st=1~exp=1799999999~hmac=deadbeef"
        val token = JioApiClient.extractHdneaToken(url)
        assertEquals(1799999999L, JioApiClient.extractTokenExpiryEpochSec(token))
    }
}
