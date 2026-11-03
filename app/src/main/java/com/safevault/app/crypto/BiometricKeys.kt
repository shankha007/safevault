package com.safevault.app.crypto

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * A hardware-backed AES key in the Android Keystore that can only be used right after a
 * strong biometric (fingerprint / secure face) check. It wraps the vault's master key so
 * the vault can be opened with a fingerprint instead of typing the master password.
 *
 * The key is invalidated by Android if a new fingerprint is enrolled, so someone who adds
 * their own fingerprint to the phone can't use it to open the vault.
 */
object BiometricKeys {
    private const val ALIAS = "safevault_biometric_v1"
    /** Separate key whose only job is to prove a real fingerprint check for "confirm it's you". */
    private const val REAUTH_ALIAS = "safevault_reauth_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12

    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    /** A fresh key and an encrypt-mode cipher to pass to BiometricPrompt. */
    fun encryptCipher(): Cipher {
        deleteKey()
        val key = createKey(ALIAS)
        return Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
    }

    /**
     * A decrypt-mode cipher for [wrapped], or null if the key no longer exists, has been
     * invalidated (for example because fingerprints changed) or can't be used at all. Any of
     * these turns fingerprint unlock off instead of crashing the unlock screen every launch.
     */
    fun decryptCipher(wrapped: String): Cipher? {
        return try {
            val key = keyStore().getKey(ALIAS, null) as? SecretKey ?: return null
            val iv = wrapped.fromB64().copyOfRange(0, IV_BYTES)
            Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            }
        } catch (e: KeyPermanentlyInvalidatedException) {
            deleteKey()
            null
        } catch (e: Exception) {
            // Damaged blob, Keystore error after an OS update, and so on.
            deleteKey()
            null
        }
    }

    /** After a successful prompt: seals the master key with the authenticated cipher. */
    fun wrap(cipher: Cipher, master: ByteArray): String = (cipher.iv + cipher.doFinal(master)).toB64()

    fun unwrap(cipher: Cipher, wrapped: String): ByteArray {
        val blob = wrapped.fromB64()
        return cipher.doFinal(blob, IV_BYTES, blob.size - IV_BYTES)
    }

    fun deleteKey() {
        runCatching { keyStore().deleteEntry(ALIAS) }
    }

    /**
     * Encrypt-mode cipher on the "confirm it's you" key, to pass to BiometricPrompt, or null
     * when the check has to use the master password: no strong biometric, no key yet, or the
     * fingerprints changed since the key was made (the key is then deleted).
     */
    fun reauthCipher(): Cipher? = try {
        val key = keyStore().getKey(REAUTH_ALIAS, null) as? SecretKey
        key?.let { Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, it) } }
    } catch (e: KeyPermanentlyInvalidatedException) {
        deleteReauthKey()
        null
    } catch (e: Exception) {
        null
    }

    /**
     * True only if the Keystore itself accepts [cipher], which it does only right after a real
     * strong-biometric check. A faked "succeeded" callback (hooking tools) doesn't pass this.
     */
    fun reauthProven(cipher: Cipher?): Boolean = cipher != null && runCatching { cipher.doFinal(ByteArray(16)) }.isSuccess

    /**
     * Makes sure the "confirm it's you" key exists and still works. Called only right after the
     * master password was proven, so a fingerprint added later by someone else (which
     * invalidates the key) can't get a new key without the master password (D-036).
     */
    fun ensureReauthKey() {
        runCatching {
            val key = keyStore().getKey(REAUTH_ALIAS, null) as? SecretKey
            val usable = key != null && runCatching {
                Cipher.getInstance(TRANSFORMATION).init(Cipher.ENCRYPT_MODE, key)
            }.isSuccess
            if (!usable) {
                deleteReauthKey()
                createKey(REAUTH_ALIAS) // fails without an enrolled fingerprint; that's fine
            }
        }
    }

    fun deleteReauthKey() {
        runCatching { keyStore().deleteEntry(REAUTH_ALIAS) }
    }

    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun createKey(alias: String): SecretKey {
        fun spec(strongBox: Boolean): KeyGenParameterSpec {
            val b = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                b.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            } else {
                @Suppress("DEPRECATION")
                b.setUserAuthenticationValidityDurationSeconds(-1)
            }
            if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) b.setIsStrongBoxBacked(true)
            return b.build()
        }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        // Prefer the dedicated security chip (StrongBox) when the phone has one; otherwise the
        // key lives in the regular hardware-backed Keystore (TEE).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                generator.init(spec(strongBox = true))
                return generator.generateKey()
            } catch (e: StrongBoxUnavailableException) {
                // fall through
            } catch (e: java.security.ProviderException) {
                // some phones report missing StrongBox this way
            }
        }
        generator.init(spec(strongBox = false))
        return generator.generateKey()
    }
}
