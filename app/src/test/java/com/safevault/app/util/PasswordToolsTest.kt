package com.safevault.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordToolsTest {
    @Test
    fun generatedPasswordHasRequestedLengthAndAllGroups() {
        repeat(200) {
            val pw = PasswordGenerator.generate(GeneratorOptions(length = 12))
            assertEquals(12, pw.length)
            assertTrue(pw.any { it.isUpperCase() })
            assertTrue(pw.any { it.isLowerCase() })
            assertTrue(pw.any { it.isDigit() })
            assertTrue(pw.any { !it.isLetterOrDigit() })
            assertTrue(pw.none { it in "Il1O0o" })
        }
    }

    @Test
    fun digitsOnlyWorksForPins() {
        val pin = PasswordGenerator.generate(
            GeneratorOptions(length = 6, upper = false, lower = false, symbols = false, avoidAmbiguous = false),
        )
        assertTrue(pin.all { it.isDigit() })
    }

    @Test
    fun generatedPasswordsDiffer() {
        val set = (1..100).map { PasswordGenerator.generate(GeneratorOptions()) }.toSet()
        assertEquals(100, set.size)
    }

    @Test
    fun strengthRatings() {
        assertEquals(Strength.VERY_WEAK, PasswordStrength.evaluate("password"))
        assertEquals(Strength.VERY_WEAK, PasswordStrength.evaluate("Password123"))
        assertEquals(Strength.VERY_WEAK, PasswordStrength.evaluate("12345678"))
        assertTrue(PasswordStrength.evaluate("k7#Rq9!vLm2@xT4z").score >= Strength.STRONG.score)
        assertTrue(PasswordStrength.evaluate(PasswordGenerator.generate(GeneratorOptions())) == Strength.VERY_STRONG)
    }
}
