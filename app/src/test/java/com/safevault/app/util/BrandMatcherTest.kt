package com.safevault.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrandMatcherTest {
    private fun login(name: String, url: String = "") = BrandMatcher.forLogin(name, url)?.slug

    @Test
    fun matchesCommonAppsByName() {
        assertEquals("gmail", login("Gmail"))
        assertEquals("netflix", login("Netflix family"))
        assertEquals("instagram", login("My Instagram"))
        assertEquals("x", login("Twitter"))
        assertEquals("googlepay", login("Google Pay"))
        assertEquals("google", login("Google account"))
    }

    @Test
    fun websiteBeatsNameAndSubdomainsCount() {
        assertEquals("gmail", login("Work email", "https://mail.google.com/u/0"))
        assertEquals("google", login("Work email", "accounts.google.com"))
        assertEquals("github", login("Code", "www.GitHub.com/login"))
        assertEquals("zerodha", login("Trading", "kite.zerodha.com"))
    }

    @Test
    fun noFalseMatchesInsideOtherWords() {
        assertNull(login("Exam portal"))        // contains "x" and "exam"
        assertNull(login("Visa application"))   // card networks aren't name keywords
        assertNull(login("Office wifi"))
        assertNull(login("Notes", "not a url"))
    }

    @Test
    fun cardsPreferBankThenNetwork() {
        assertEquals("hdfcbank", BrandMatcher.forCard("HDFC debit card", "", "Visa")?.slug)
        assertEquals("icicibank", BrandMatcher.forCard("Travel card", "ICICI Bank", "Mastercard")?.slug)
        assertEquals("visa", BrandMatcher.forCard("Salary card", "Some Co-op Bank", "Visa")?.slug)
        assertNull(BrandMatcher.forCard("Card", "", "RuPay"))
    }

    @Test
    fun brandsWithoutFreeLogoUseColourOnly() {
        val linkedIn = BrandMatcher.forLogin("LinkedIn", "")!!
        assertNull(linkedIn.path)
        assertEquals("Microsoft", BrandMatcher.forLogin("Mail", "outlook.live.com")?.title)
    }

    @Test
    fun catalogueIsWellFormed() {
        assertTrue(brandCatalog.size > 50)
        assertEquals(brandCatalog.size, brandCatalog.map { it.slug }.toSet().size)
        brandCatalog.forEach { b ->
            assertTrue(b.title, b.color ushr 24 == 0xFFL)
            b.path?.let { assertTrue(b.title, it.startsWith("M", ignoreCase = true)) }
        }
    }
}
