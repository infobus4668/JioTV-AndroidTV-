package com.fenyx.jtv.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun isNewer_sameVersionIsNotNewer() {
        assertFalse(UpdateChecker.isNewer("1.5.2", "1.5.2"))
        assertFalse(UpdateChecker.isNewer("1.5.2-mod.2", "1.5.2-mod.2"))
    }

    @Test
    fun isNewer_numericCoreDecides() {
        assertTrue(UpdateChecker.isNewer("1.6.0", "1.5.2"))
        assertTrue(UpdateChecker.isNewer("2.0", "1.9.9"))
        assertTrue(UpdateChecker.isNewer("1.5.10", "1.5.9"))
        assertFalse(UpdateChecker.isNewer("1.5.2", "1.6.0"))
    }

    @Test
    fun isNewer_leadingFullVersionBeatsPatchCount() {
        // "2.0" (local list) vs "1.5.2" — major version wins even though it has fewer segments.
        assertTrue(UpdateChecker.isNewer("2.0", "1.5.2"))
    }

    @Test
    fun isNewer_preReleaseSuffixNeverMakesItNewer() {
        // A mod.2 suffix must not make "1.5.2-mod.2" newer than the plain "1.5.2" upstream.
        assertFalse(UpdateChecker.isNewer("1.5.2-mod.2", "1.5.2"))
        // But a higher upstream core does win regardless of suffix.
        assertTrue(UpdateChecker.isNewer("1.6.0", "1.5.2-mod.2"))
    }

    @Test
    fun isNewer_missingSegmentsCountAsZero() {
        // "1.5" == "1.5.0" numerically → not newer.
        assertFalse(UpdateChecker.isNewer("1.5", "1.5.0"))
        assertFalse(UpdateChecker.isNewer("1.5.0", "1.5"))
        // "1.5.1" > "1.5"
        assertTrue(UpdateChecker.isNewer("1.5.1", "1.5"))
    }

    @Test
    fun isNewer_stripsLeadingV() {
        assertTrue(UpdateChecker.isNewer("v1.6.0", "1.5.2"))
        assertFalse(UpdateChecker.isNewer("v1.5.2", "1.6.0"))
    }
}