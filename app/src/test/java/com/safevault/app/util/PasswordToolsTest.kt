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

    @Test
    fun wordPlusDigitsIsNotStrong() {
        // VAPT M4: these were rated Fair or Strong and accepted as master passwords.
        for (pw in listOf("Mumbai@123", "India@2024", "Rahul@123", "Summer2024", "Summer24!")) {
            assertTrue(pw, PasswordStrength.evaluate(pw).score < Strength.FAIR.score)
        }
    }

    @Test
    fun masterPasswordPolicy() {
        for (pw in listOf("Mumbai@123", "India@2024!!", "Mumbai@12345", "short", "abcdefghijkl")) {
            assertTrue(pw, PasswordStrength.masterPasswordProblem(pw) != null)
        }
        for (pw in listOf("river mango lamp cloud", "k7#Rq9!vLm2@xT4z", PasswordGenerator.generate(GeneratorOptions()))) {
            assertEquals(pw, null, PasswordStrength.masterPasswordProblem(pw))
        }
    }
}
