package com.safevault.app.ui

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.safevault.app.crypto.BiometricKeys
import com.safevault.app.data.VaultRepository
import com.safevault.app.ui.components.PasswordPromptDialog
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

        val ok = if (BiometricKeys.isAvailable(activity)) {
            when (activity.biometricPrompt(title = "Confirm it's you", subtitle = "to show the $what")) {
                is AuthOutcome.Success -> true
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
            val ok = repo.verifyPassword(pw)
            pw.fill(' ')
            if (ok) {
                pending.complete(true)
                null
            } else {
                "That password isn't right."
            }
        },
        onDismiss = { pending.complete(false) },
    )
}
