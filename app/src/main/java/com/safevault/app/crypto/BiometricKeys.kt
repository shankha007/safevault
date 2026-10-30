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
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12

    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    /** A fresh key and an encrypt-mode cipher to pass to BiometricPrompt. */
    fun encryptCipher(): Cipher {
        deleteKey()
        val key = createKey()
        return Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
    }

    /**
     * A decrypt-mode cipher for [wrapped], or null if the key no longer exists or has been
     * invalidated (for example because fingerprints changed).
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

    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun createKey(): SecretKey {
        fun spec(strongBox: Boolean): KeyGenParameterSpec {
            val b = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
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
