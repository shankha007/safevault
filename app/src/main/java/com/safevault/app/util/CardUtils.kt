package com.safevault.app.util

object CardUtils {
    /** Best-guess card network from the leading digits. */
    fun detectNetwork(number: String): String {
        val n = number.filter { it.isDigit() }
        if (n.isEmpty()) return ""
        val two = n.take(2).toIntOrNull() ?: 0
        val four = n.take(4).toIntOrNull() ?: 0
        val six = n.take(6).toIntOrNull() ?: 0
        return when {
            n.startsWith("4") -> "Visa"
            two in 51..55 || four in 2221..2720 -> "Mastercard"
            two == 34 || two == 37 -> "American Express"
            two in listOf(60, 65, 81, 82) || n.startsWith("508") || n.startsWith("353") || n.startsWith("356") -> "RuPay"
            two == 36 || two in 38..39 || n.startsWith("300") || n.startsWith("305") -> "Diners Club"
            six in 622126..622925 || two == 62 -> "UnionPay"
            four in 3528..3589 -> "JCB"
            n.startsWith("6011") || n.startsWith("644") -> "Discover"
            two in listOf(50, 56, 57, 58, 63, 67) -> "Maestro"
            else -> ""
        }
    }

    /** Luhn checksum; catches most typos in a card number. */
    fun luhnValid(number: String): Boolean {
        val digits = number.filter { it.isDigit() }
        if (digits.length !in 12..19) return false
        var sum = 0
        digits.reversed().forEachIndexed { i, c ->
            var d = c - '0'
            if (i % 2 == 1) { d *= 2; if (d > 9) d -= 9 }
            sum += d
        }
        return sum % 10 == 0
    }

    /** "1234567812345678" -> "1234 5678 1234 5678" (Amex: 4-6-5). */
    fun formatNumber(number: String): String {
        val n = number.filter { it.isDigit() }
        val groups = if (n.length == 15 && (n.startsWith("34") || n.startsWith("37"))) listOf(4, 6, 5) else List(5) { 4 }
        val out = StringBuilder()
        var i = 0
        for (g in groups) {
            if (i >= n.length) break
            if (out.isNotEmpty()) out.append(' ')
            out.append(n, i, minOf(i + g, n.length))
            i += g
        }
        if (i < n.length) out.append(' ').append(n.substring(i))
        return out.toString()
    }

    /** True if MM/YY is a real month. */
    fun expiryValid(expiry: String): Boolean {
        val m = Regex("""^(\d{2})/(\d{2})$""").find(expiry) ?: return false
        return m.groupValues[1].toInt() in 1..12
    }
}
