package com.safevault.app.ui

import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.crypto.Cipher
import kotlin.coroutines.resume

val LocalActivity = staticCompositionLocalOf<FragmentActivity> { error("No activity") }

sealed interface AuthOutcome {
    data class Success(val cipher: Cipher?) : AuthOutcome
    /** The user pressed the "use master password" button. */
    data object UsePassword : AuthOutcome
    data object Cancelled : AuthOutcome
    data class Failed(val message: String) : AuthOutcome
}

/** Shows the system fingerprint dialog and suspends until it finishes. */
suspend fun FragmentActivity.biometricPrompt(
    title: String,
    subtitle: String? = null,
    cipher: Cipher? = null,
    negativeText: String = "Use master password",
): AuthOutcome = suspendCancellableCoroutine { cont ->
    val prompt = BiometricPrompt(
        this,
        ContextCompat.getMainExecutor(this),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (cont.isActive) cont.resume(AuthOutcome.Success(result.cryptoObject?.cipher))
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (!cont.isActive) return
                cont.resume(
                    when (errorCode) {
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON -> AuthOutcome.UsePassword
                        BiometricPrompt.ERROR_USER_CANCELED, BiometricPrompt.ERROR_CANCELED -> AuthOutcome.Cancelled
                        else -> AuthOutcome.Failed(errString.toString())
                    },
                )
            }
            // onAuthenticationFailed (finger not recognised) keeps the dialog open; nothing to do.
        },
    )
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .apply { if (subtitle != null) setSubtitle(subtitle) }
        .setNegativeButtonText(negativeText)
        .setAllowedAuthenticators(BIOMETRIC_STRONG)
        .setConfirmationRequired(false)
        .build()
    if (cipher != null) prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher)) else prompt.authenticate(info)
    cont.invokeOnCancellation { prompt.cancelAuthentication() }
}
