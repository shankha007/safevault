package com.safevault.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardUtilsTest {
    /** Feeds keystrokes one at a time, the way the editor's onValueChange sees them. */
    private fun type(keys: String): String =
        keys.fold("") { acc, c -> CardUtils.sanitizeExpiryDigits(acc + c) }

    @Test
    fun yearDigitsKeepTheirOrder() {
        assertEquals("1227", type("1227"))
        assertEquals("12/27", CardUtils.expiryFromDigits(type("1227")))
        assertEquals("08/31", CardUtils.expiryFromDigits(type("0831")))
    }

    @Test
    fun singleDigitMonthGetsLeadingZero() {
        assertEquals("0529", type("529"))
        assertEquals("05/29", CardUtils.expiryFromDigits(type("529")))
    }

    @Test
    fun extraDigitsAndSymbolsAreDropped() {
        assertEquals("1227", CardUtils.sanitizeExpiryDigits("12/2799"))
        assertEquals("1", CardUtils.sanitizeExpiryDigits("1a"))
    }

    @Test
    fun savedExpiryRoundTrips() {
        assertEquals("1227", CardUtils.expiryToDigits("12/27"))
        assertEquals("12/27", CardUtils.expiryFromDigits(CardUtils.expiryToDigits("12/27")))
    }

    @Test
    fun expiryValidChecksMonth() {
        assertTrue(CardUtils.expiryValid("01/30"))
        assertFalse(CardUtils.expiryValid("13/30"))
        assertFalse(CardUtils.expiryValid("12/3"))
    }
}
