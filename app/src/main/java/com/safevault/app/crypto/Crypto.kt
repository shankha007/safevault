package com.safevault.app.crypto

import kotlinx.serialization.Serializable
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.nio.CharBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Thrown when decryption fails: wrong key, wrong password, or tampered data. */
class CryptoException(message: String, cause: Throwable? = null) : Exception(message, cause)

const val KEY_BYTES = 32
private const val GCM_IV_BYTES = 12
private const val GCM_TAG_BITS = 128

private val secureRandom = SecureRandom()

fun randomBytes(size: Int): ByteArray = ByteArray(size).also { secureRandom.nextBytes(it) }

fun ByteArray.toB64(): String = Base64.getEncoder().encodeToString(this)
fun String.fromB64(): ByteArray = Base64.getDecoder().decode(this)

/**
 * Argon2id settings. Stored next to the vault so they can be raised in a later version
 * without breaking existing vaults. Defaults are above the OWASP minimum
 * (m=19 MiB, t=2, p=1) while still unlocking in about a second on a mid-range phone.
 */
@Serializable
data class KdfParams(
    val memoryKiB: Int = 47_104, // 46 MiB
    val iterations: Int = 2,
    val parallelism: Int = 1,
)

object Kdf {
    /** Turns the master password into a 256-bit key-encryption key. */
    fun derive(password: CharArray, salt: ByteArray, params: KdfParams): ByteArray {
        val pwBytes = password.toUtf8()
        try {
            val argon = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withSalt(salt)
                .withMemoryAsKB(params.memoryKiB)
                .withIterations(params.iterations)
                .withParallelism(params.parallelism)
                .build()
            val out = ByteArray(KEY_BYTES)
            Argon2BytesGenerator().apply { init(argon) }.generateBytes(pwBytes, out)
            return out
        } finally {
            pwBytes.fill(0)
        }
    }

    private fun CharArray.toUtf8(): ByteArray {
        val buffer = Charsets.UTF_8.encode(CharBuffer.wrap(this))
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        if (buffer.hasArray()) buffer.array().fill(0)
        return bytes
    }
}

/** AES-256-GCM. Output layout: 12-byte random IV followed by ciphertext and 16-byte tag. */
object Aead {
    fun seal(key: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray {
        val iv = randomBytes(GCM_IV_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(aad)
        return iv + cipher.doFinal(plaintext)
    }

    fun open(key: ByteArray, blob: ByteArray, aad: ByteArray): ByteArray {
        if (blob.size < GCM_IV_BYTES + GCM_TAG_BITS / 8) throw CryptoException("Ciphertext too short")
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, "AES"),
                GCMParameterSpec(GCM_TAG_BITS, blob, 0, GCM_IV_BYTES),
            )
            cipher.updateAAD(aad)
            return cipher.doFinal(blob, GCM_IV_BYTES, blob.size - GCM_IV_BYTES)
        } catch (e: GeneralSecurityException) {
            throw CryptoException("Decryption failed", e)
        }
    }
}

/**
 * Keys used while the vault is unlocked, derived from the random 256-bit master key:
 *  - fileKey encrypts the whole vault file (outer layer)
 *  - fieldKey encrypts each secret field on its own (inner layer)
 * Using separate subkeys means one key is never used for two jobs.
 */
class VaultKeys private constructor(
    private val fileKey: ByteArray,
    private val fieldKey: ByteArray,
) {
    fun sealFile(plaintext: ByteArray): ByteArray = Aead.seal(fileKey, plaintext, FILE_AAD)
    fun openFile(blob: ByteArray): ByteArray = Aead.open(fileKey, blob, FILE_AAD)

    /**
     * Encrypts one secret value. [context] (item id + field name) is bound in as associated
     * data, so a ciphertext can't be cut from one field and pasted into another.
     */
    fun sealField(value: String, context: String): String =
        Aead.seal(fieldKey, value.toByteArray(Charsets.UTF_8), fieldAad(context)).toB64()

    fun openField(sealed: String, context: String): String =
        String(Aead.open(fieldKey, sealed.fromB64(), fieldAad(context)), Charsets.UTF_8)

    fun wipe() {
        fileKey.fill(0)
        fieldKey.fill(0)
    }

    companion object {
        private val FILE_AAD = "safevault/file/v1".toByteArray()
        private fun fieldAad(context: String) = "safevault/field/v1/$context".toByteArray()

        fun fromMaster(master: ByteArray) = VaultKeys(
            fileKey = hmac(master, "safevault/subkey/file"),
            fieldKey = hmac(master, "safevault/subkey/field"),
        )

        private fun hmac(key: ByteArray, label: String): ByteArray {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(key, "HmacSHA256"))
            return mac.doFinal(label.toByteArray())
        }
    }
}
