package com.safevault.app.data

import com.safevault.app.crypto.KdfParams
import kotlinx.serialization.Serializable

/*
 * Everything in this file is stored inside the encrypted vault file, except VaultMeta,
 * which sits next to it in plain JSON and holds only non-secret data (salt, KDF settings,
 * the *wrapped* master key, and app settings).
 *
 * Fields typed as `Sealed` hold a second layer of encryption (see VaultKeys.sealField), so
 * even after the vault file is decrypted, passwords/PINs/CVVs stay encrypted in memory
 * until the user taps "reveal" or "copy".
 */
typealias Sealed = String

@Serializable
enum class ItemType { LOGIN, CARD }

@Serializable
enum class CardKind { DEBIT, CREDIT }

@Serializable
data class LoginData(
    val username: String = "",
    val password: Sealed? = null,
    val url: String = "",
)

@Serializable
data class CardData(
    val bank: String = "",
    val holderName: String = "",
    val number: Sealed? = null,
    val last4: String = "",
    val network: String = "",
    val kind: CardKind = CardKind.DEBIT,
    val expiry: String = "", // MM/YY
    val cvv: Sealed? = null,
    val pin: Sealed? = null,
)

@Serializable
data class VaultItem(
    val id: String,
    val type: ItemType,
    val name: String,
    val favorite: Boolean = false,
    val notes: Sealed? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val login: LoginData? = null,
    val card: CardData? = null,
)

@Serializable
data class VaultData(
    val items: List<VaultItem> = emptyList(),
)

/** Names of sealed fields; used as part of the encryption context. */
object Field {
    const val PASSWORD = "password"
    const val NOTES = "notes"
    const val CARD_NUMBER = "card_number"
    const val CVV = "cvv"
    const val PIN = "pin"

    fun context(itemId: String, field: String) = "$itemId/$field"
}

@Serializable
data class VaultSettings(
    /** 0 = lock as soon as the app leaves the screen. */
    val autoLockSeconds: Int = 0,
    val clipboardClearSeconds: Int = 30,
    /** Ask for fingerprint (or master password) again before showing a card PIN or CVV. */
    val reauthForCardSecrets: Boolean = true,
    /** Erase the vault after [MAX_FAILED_ATTEMPTS] wrong master passwords in a row. */
    val wipeAfterFailures: Boolean = false,
    /** Set once the user has dismissed the "turn on fingerprint" suggestion. */
    val biometricPromptDismissed: Boolean = false,
)

const val MAX_FAILED_ATTEMPTS = 10

@Serializable
data class VaultMeta(
    val formatVersion: Int = 1,
    val kdf: KdfParams,
    val salt: String,
    /** Master key encrypted with the Argon2id key from the master password. */
    val wrappedKey: String,
    /** Master key encrypted with the fingerprint-bound Android Keystore key, if enabled. */
    val biometricWrappedKey: String? = null,
    val failedAttempts: Int = 0,
    /** Current lockout length; 0 = none. Timed on the since-boot clock, see Lockout (D-034). */
    val lockoutMs: Long = 0,
    /** Android boot count when [lockoutEndsAt] was set. */
    val lockoutBoot: Int = 0,
    /** SystemClock.elapsedRealtime() at which the lockout ends, on boot [lockoutBoot]. */
    val lockoutEndsAt: Long = 0,
    val settings: VaultSettings = VaultSettings(),
)

/** Self-contained encrypted backup: everything needed to restore with the master password. */
@Serializable
data class BackupFile(
    val format: String = BACKUP_FORMAT,
    val formatVersion: Int = 1,
    val createdAt: Long,
    val kdf: KdfParams,
    val salt: String,
    val wrappedKey: String,
    /** The encrypted vault file, base64. */
    val data: String,
) {
    companion object {
        const val BACKUP_FORMAT = "safevault-backup"
    }
}
