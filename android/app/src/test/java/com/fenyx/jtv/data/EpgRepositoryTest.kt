package com.fenyx.jtv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.text.SimpleDateFormat
import java.util.Locale
import org.junit.Test

/**
 * Tests the XMLTV timestamp parser used by the EPG pipeline. The format carries an explicit timezone
 * offset, so parsing must yield an absolute instant independent of the device's default timezone.
 */
class EpgRepositoryTest {

    // A fresh formatter per test mirrors production's "one instance per parse" thread-safety rule.
    private fun fmt() = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.ENGLISH)

    @Test
    fun parses_validTimestampToPositiveEpoch() {
        assertTrue(EpgRepository.parseXmltvMillis("20260711183000 +0530", fmt()) > 0)
    }

    @Test
    fun parses_timezoneOffsetAsAbsoluteInstant() {
        // 18:30:00 at +0530 is the same instant as 13:00:00 at +0000.
        val ist = EpgRepository.parseXmltvMillis("20260711183000 +0530", fmt())
        val utc = EpgRepository.parseXmltvMillis("20260711130000 +0000", fmt())
        assertEquals(utc, ist)
    }

    @Test
    fun returnsZero_forMalformedValue() {
        assertEquals(0L, EpgRepository.parseXmltvMillis("not-a-timestamp", fmt()))
    }

    @Test
    fun returnsZero_forNullOrBlank() {
        assertEquals(0L, EpgRepository.parseXmltvMillis(null, fmt()))
        assertEquals(0L, EpgRepository.parseXmltvMillis("   ", fmt()))
    }
}
