package com.safevault.app.util

import java.security.SecureRandom
import kotlin.math.log2

data class GeneratorOptions(
    val length: Int = 20,
    val upper: Boolean = true,
    val lower: Boolean = true,
    val digits: Boolean = true,
    val symbols: Boolean = true,
    val avoidAmbiguous: Boolean = true,
)

object PasswordGenerator {
    private const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val LOWER = "abcdefghijklmnopqrstuvwxyz"
    private const val DIGITS = "0123456789"
    private const val SYMBOLS = "!@#\$%^&*()-_=+[]{};:,.?/~"
    private const val AMBIGUOUS = "Il1O0o"

    private val random = SecureRandom()

    /** Guarantees at least one character from every selected group, then shuffles. */
    fun generate(options: GeneratorOptions): String {
        val groups = buildList {
            if (options.upper) add(UPPER)
            if (options.lower) add(LOWER)
            if (options.digits) add(DIGITS)
            if (options.symbols) add(SYMBOLS)
        }.map { group -> if (options.avoidAmbiguous) group.filterNot { it in AMBIGUOUS } else group }
            .ifEmpty { listOf(LOWER) }

        val length = options.length.coerceIn(groups.size, 128)
        val all = groups.joinToString("")
        val chars = CharArray(length)
        groups.forEachIndexed { i, group -> chars[i] = group[random.nextInt(group.length)] }
        for (i in groups.size until length) chars[i] = all[random.nextInt(all.length)]
        // Fisher-Yates shuffle so the guaranteed characters aren't always at the start.
        for (i in length - 1 downTo 1) {
            val j = random.nextInt(i + 1)
            val t = chars[i]; chars[i] = chars[j]; chars[j] = t
        }
        return String(chars)
    }
}

enum class Strength(val label: String, val score: Int) {
    VERY_WEAK("Very weak", 0),
    WEAK("Weak", 1),
    FAIR("Fair", 2),
    STRONG("Strong", 3),
    VERY_STRONG("Very strong", 4),
}

/**
 * Simple offline strength estimate: character-set entropy, with penalties for very common
 * passwords, repeated characters and simple sequences. Good enough to steer users away from
 * weak choices; it is not a cracking-time guarantee.
 */
object PasswordStrength {
    private val common = setOf(
        "password", "123456", "12345678", "123456789", "1234567890", "qwerty", "qwerty123",
        "111111", "000000", "abc123", "iloveyou", "admin", "welcome", "letmein", "monkey",
        "dragon", "football", "password1", "passw0rd", "india123", "sunshine", "princess",
    )

    fun entropyBits(pw: String): Double {
        if (pw.isEmpty()) return 0.0
        var pool = 0
        if (pw.any { it.isLowerCase() }) pool += 26
        if (pw.any { it.isUpperCase() }) pool += 26
        if (pw.any { it.isDigit() }) pool += 10
        if (pw.any { !it.isLetterOrDigit() }) pool += 32
        val unique = pw.toSet().size
        // Repeated characters add little; count each distinct character fully, repeats at half.
        val effectiveLength = unique + (pw.length - unique) * 0.5
        return effectiveLength * log2(pool.toDouble().coerceAtLeast(2.0))
    }

    fun evaluate(pw: String): Strength {
        if (pw.isEmpty()) return Strength.VERY_WEAK
        val lower = pw.lowercase()
        if (lower in common || lower.trimEnd { it.isDigit() || !it.isLetterOrDigit() } in common) {
            return Strength.VERY_WEAK
        }
        var bits = entropyBits(pw)
        if (hasSequence(lower)) bits -= 15
        return when {
            bits < 28 -> Strength.VERY_WEAK
            bits < 45 -> Strength.WEAK
            bits < 60 -> Strength.FAIR
            bits < 80 -> Strength.STRONG
            else -> Strength.VERY_STRONG
        }
    }

    private fun hasSequence(s: String): Boolean {
        val runs = listOf("abcdefghijklmnopqrstuvwxyz", "0123456789", "qwertyuiop", "asdfghjkl", "zxcvbnm")
        return runs.any { run -> (0..run.length - 4).any { s.contains(run.substring(it, it + 4)) } }
    }
}
