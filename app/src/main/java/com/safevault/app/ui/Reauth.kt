package com.safevault.app.ui

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.safevault.app.crypto.BiometricKeys
import com.safevault.app.data.VaultRepository
import com.safevault.app.ui.components.PasswordPromptDialog
import com.safevault.app.ui.components.passwordCheckError
import kotlinx.coroutines.CompletableDeferred

/**
 * "Confirm it's you" before showing the most sensitive values (card PIN and CVV).
 * Uses the fingerprint if available, otherwise asks for the master password.
 * A successful check is remembered for [GRACE_MS] so the user isn't asked again and again.
 */
class ReauthController(private val repo: VaultRepository) {
    private var lastSuccess = 0L
    internal var pending by mutableStateOf<CompletableDeferred<Boolean>?>(null)

    suspend fun confirm(activity: androidx.fragment.app.FragmentActivity, what: String): Boolean {
        if (!repo.settings.reauthForCardSecrets) return true
        if (SystemClock.elapsedRealtime() - lastSuccess < GRACE_MS) return true

        // The prompt is tied to a Keystore key (D-036), so only a real fingerprint check
        // counts. No usable key (new fingerprint enrolled, or none yet) means the password.
        val cipher = if (BiometricKeys.isAvailable(activity)) BiometricKeys.reauthCipher() else null
        val ok = if (cipher != null) {
            when (val r = activity.biometricPrompt(title = "Confirm it's you", subtitle = "to show the $what", cipher = cipher)) {
                is AuthOutcome.Success -> BiometricKeys.reauthProven(r.cipher)
                AuthOutcome.UsePassword -> askPassword()
                else -> false
            }
        } else {
            askPassword()
        }
        if (ok) lastSuccess = SystemClock.elapsedRealtime()
        return ok
    }

    private suspend fun askPassword(): Boolean {
        val d = CompletableDeferred<Boolean>()
        pending = d
        return d.await().also { pending = null }
    }

    companion object {
        const val GRACE_MS = 60_000L
    }
}

@Composable
fun ReauthHost(controller: ReauthController, repo: VaultRepository) {
    val pending = controller.pending ?: return
    PasswordPromptDialog(
        title = "Confirm it's you",
        message = "Enter your master password to continue.",
        confirmText = "Confirm",
        onSubmit = { pw ->
            val error = passwordCheckError(repo.verifyPassword(pw))
            pw.fill(' ')
            if (error == null) pending.complete(true)
            error
        },
        onDismiss = { pending.complete(false) },
    )
}
