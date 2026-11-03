package com.safevault.app.data

import com.safevault.app.crypto.Aead
import com.safevault.app.crypto.CryptoException
import com.safevault.app.crypto.KEY_BYTES
import com.safevault.app.crypto.Kdf
import com.safevault.app.crypto.KdfParams
import com.safevault.app.crypto.VaultKeys
import com.safevault.app.crypto.fromB64
import com.safevault.app.crypto.randomBytes
import com.safevault.app.crypto.toB64
import kotlinx.serialization.json.Json

/**
 * Pure-Kotlin vault format logic (no Android classes), so it is covered by JVM unit tests.
 *
 * Key hierarchy:
 *   master password --Argon2id(salt)--> KEK --AES-GCM--> wraps random master key
 *   master key --HMAC--> fileKey (whole file) + fieldKey (each secret field)
 */
object VaultCodec {
    private val WRAP_AAD = "safevault/wrap/password/v1".toByteArray()

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    class NewVault(val meta: VaultMeta, val masterKey: ByteArray)

    fun createVault(password: CharArray, kdf: KdfParams = KdfParams()): NewVault {
        val master = randomBytes(KEY_BYTES)
        val salt = randomBytes(16)
        val meta = VaultMeta(
            kdf = kdf,
            salt = salt.toB64(),
            wrappedKey = wrapMaster(master, password, salt, kdf),
        )
        return NewVault(meta, master)
    }

    /** Returns the master key, or null if the password is wrong. */
    fun unwrapMaster(password: CharArray, salt: String, wrappedKey: String, kdf: KdfParams): ByteArray? {
        val kek = Kdf.derive(password, salt.fromB64(), kdf)
        return try {
            Aead.open(kek, wrappedKey.fromB64(), WRAP_AAD)
        } catch (e: CryptoException) {
            null
        } finally {
            kek.fill(0)
        }
    }

    fun unwrapMaster(password: CharArray, meta: VaultMeta): ByteArray? =
        unwrapMaster(password, meta.salt, meta.wrappedKey, meta.kdf)

    /** Re-wraps the same master key under a new password (fresh salt). Vault data is unchanged. */
    fun rewrap(meta: VaultMeta, master: ByteArray, newPassword: CharArray): VaultMeta {
        val salt = randomBytes(16)
        return meta.copy(salt = salt.toB64(), wrappedKey = wrapMaster(master, newPassword, salt, meta.kdf))
    }

    class Rotated(val meta: VaultMeta, val masterKey: ByteArray, val keys: VaultKeys, val items: List<VaultItem>, val encryptedData: ByteArray)

    /**
     * Password change: a brand-new master key, every secret re-sealed under it, wrapped with
     * [newPassword] (fresh salt). An old backup and the old password then reveal only the old
     * key, which opens nothing written after the change. Fingerprint unlock wraps the old key,
     * so it is turned off.
     */
    fun rotateMaster(meta: VaultMeta, items: List<VaultItem>, oldKeys: VaultKeys, newPassword: CharArray): Rotated {
        val master = randomBytes(KEY_BYTES)
        val keys = VaultKeys.fromMaster(master)
        val resealed = items.map { resealItem(it, oldKeys, keys) }
        val newMeta = rewrap(meta, master, newPassword).copy(biometricWrappedKey = null)
        return Rotated(newMeta, master, keys, resealed, encryptData(keys, VaultData(resealed)))
    }

    private fun wrapMaster(master: ByteArray, password: CharArray, salt: ByteArray, kdf: KdfParams): String {
        val kek = Kdf.derive(password, salt, kdf)
        try {
            return Aead.seal(kek, master, WRAP_AAD).toB64()
        } finally {
            kek.fill(0)
        }
    }

    fun encryptData(keys: VaultKeys, data: VaultData): ByteArray {
        val plain = json.encodeToString(VaultData.serializer(), data).toByteArray(Charsets.UTF_8)
        try {
            return keys.sealFile(plain)
        } finally {
            plain.fill(0)
        }
    }

    fun decryptData(keys: VaultKeys, blob: ByteArray): VaultData {
        val plain = keys.openFile(blob)
        try {
            return json.decodeFromString(VaultData.serializer(), String(plain, Charsets.UTF_8))
        } finally {
            plain.fill(0)
        }
    }

    fun makeBackup(meta: VaultMeta, encryptedData: ByteArray, now: Long): String =
        json.encodeToString(
            BackupFile.serializer(),
            BackupFile(
                createdAt = now,
                kdf = meta.kdf,
                salt = meta.salt,
                wrappedKey = meta.wrappedKey,
                data = encryptedData.toB64(),
            ),
        )

    /** Throws IllegalArgumentException if the text isn't a SafeVault backup. */
    fun parseBackup(text: String): BackupFile {
        val backup = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (e: Exception) {
            throw IllegalArgumentException("This file isn't a SafeVault backup.", e)
        }
        require(backup.format == BackupFile.BACKUP_FORMAT) { "This file isn't a SafeVault backup." }
        require(backup.formatVersion <= 1) { "This backup was made by a newer version of SafeVault." }
        return backup
    }

    class OpenedBackup(val masterKey: ByteArray, val data: VaultData)

    /** Returns null if the password is wrong. */
    fun openBackup(backup: BackupFile, password: CharArray): OpenedBackup? {
        val master = unwrapMaster(password, backup.salt, backup.wrappedKey, backup.kdf) ?: return null
        val keys = VaultKeys.fromMaster(master)
        try {
            return OpenedBackup(master, decryptData(keys, backup.data.fromB64()))
        } finally {
            keys.wipe()
        }
    }

    /**
     * Re-encrypts every sealed field of [item] from one field key to another. Used when
     * merging a backup that was made with a different master key.
     */
    fun resealItem(item: VaultItem, from: VaultKeys, to: VaultKeys): VaultItem {
        fun move(value: Sealed?, field: String): Sealed? = value?.let {
            val ctx = Field.context(item.id, field)
            to.sealField(from.openField(it, ctx), ctx)
        }
        return item.copy(
            notes = move(item.notes, Field.NOTES),
            login = item.login?.let { it.copy(password = move(it.password, Field.PASSWORD)) },
            card = item.card?.let {
                it.copy(
                    number = move(it.number, Field.CARD_NUMBER),
                    cvv = move(it.cvv, Field.CVV),
                    pin = move(it.pin, Field.PIN),
                )
            },
        )
    }

    /** Merge rule: items are matched by id; the most recently updated copy wins. */
    fun merge(current: List<VaultItem>, incoming: List<VaultItem>): MergeResult {
        val byId = current.associateBy { it.id }.toMutableMap()
        var added = 0
        var updated = 0
        for (item in incoming) {
            val existing = byId[item.id]
            when {
                existing == null -> { byId[item.id] = item; added++ }
                item.updatedAt > existing.updatedAt -> { byId[item.id] = item; updated++ }
            }
        }
        return MergeResult(byId.values.toList(), added, updated)
    }

    class MergeResult(val items: List<VaultItem>, val added: Int, val updated: Int)
}
