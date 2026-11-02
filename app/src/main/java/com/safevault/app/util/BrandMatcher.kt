package com.safevault.app.util

/**
 * A known brand. [path] is a 24x24 SVG path from Simple Icons, or null when no free logo exists
 * and the avatar shows the first letter on [color] instead. The catalogue is generated (D-028).
 */
data class Brand(
    val slug: String,
    val title: String,
    val color: Long,
    val keywords: List<String>,
    val domains: List<String>,
    val path: String?,
)

/**
 * Picks a logo for an item from its website, name, bank and card network. Runs entirely on
 * the phone against the bundled catalogue; nothing is looked up online.
 */
object BrandMatcher {
    private val bySlug = brandCatalog.associateBy { it.slug }

    fun forLogin(name: String, url: String): Brand? = byDomain(url) ?: byName(name)

    /** Cards prefer the bank (or a bank named in the item name), then the card network. */
    fun forCard(name: String, bank: String, network: String): Brand? =
        byName(bank) ?: byName(name) ?: networkBrand(network)

    fun byDomain(url: String): Brand? {
        val host = hostOf(url) ?: return null
        var best: Brand? = null
        var bestLen = 0
        for (b in brandCatalog) for (d in b.domains) {
            if ((host == d || host.endsWith(".$d")) && d.length > bestLen) { best = b; bestLen = d.length }
        }
        return best
    }

    fun byName(text: String): Brand? {
        val words = text.lowercase().split(Regex("[^a-z0-9.]+")).map { it.trim('.') }.filter { it.isNotEmpty() }
        if (words.isEmpty()) return null
        val spaced = " " + words.joinToString(" ") + " "
        val joined = words.joinToString("")
        var best: Brand? = null
        var bestLen = 0
        for (b in brandCatalog) for (k in b.keywords) {
            val hit = spaced.contains(" $k ") || (k.length >= 5 && !k.contains(' ') && joined.contains(k))
            // Longest keyword wins, so "google pay" beats "google".
            if (hit && k.length > bestLen) { best = b; bestLen = k.length }
        }
        return best
    }

    private fun networkBrand(network: String): Brand? = when (network) {
        "Visa" -> bySlug["visa"]
        "Mastercard" -> bySlug["mastercard"]
        "American Express" -> bySlug["americanexpress"]
        "Discover" -> bySlug["discover"]
        "JCB" -> bySlug["jcb"]
        "Diners Club" -> bySlug["dinersclub"]
        else -> null
    }

    /** "https://www.Mail.Google.com/u/0" -> "mail.google.com"; plain words aren't hosts. */
    private fun hostOf(url: String): String? {
        val host = url.trim().lowercase()
            .substringAfter("://")
            .substringBefore('/').substringBefore('?').substringBefore('#')
            .substringAfter('@').substringBefore(':')
            .removePrefix("www.")
        return host.takeIf { it.contains('.') }
    }
}
