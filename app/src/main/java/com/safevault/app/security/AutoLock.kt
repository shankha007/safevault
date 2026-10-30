package com.safevault.app.security

import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.safevault.app.data.VaultRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Locks the vault when:
 *  - the app leaves the screen (immediately, or after the user's chosen delay), or
 *  - nobody has touched the screen for [INACTIVITY_MS] while it is open.
 *
 * While we deliberately open a system screen (the file picker for backups), leaving the app
 * is expected, so locking is held off for up to [EXTERNAL_GRACE_MS].
 */
class AutoLock(
    private val repo: VaultRepository,
    private val scope: CoroutineScope,
) : DefaultLifecycleObserver {
    private var backgroundJob: Job? = null
    private var inactivityJob: Job? = null
    private var lastInteraction = SystemClock.elapsedRealtime()
    private var externalActivityUntil = 0L

    fun onUserInteraction() {
        lastInteraction = SystemClock.elapsedRealtime()
    }

    /** Call right before launching the system file picker. */
    fun expectExternalActivity() {
        externalActivityUntil = SystemClock.elapsedRealtime() + EXTERNAL_GRACE_MS
    }

    fun externalActivityDone() {
        externalActivityUntil = 0L
    }

    override fun onStart(owner: LifecycleOwner) {
        backgroundJob?.cancel()
        lastInteraction = SystemClock.elapsedRealtime()
        inactivityJob?.cancel()
        inactivityJob = scope.launch {
            while (isActive) {
                delay(5_000)
                if (repo.isUnlocked && SystemClock.elapsedRealtime() - lastInteraction > INACTIVITY_MS) repo.lock()
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        inactivityJob?.cancel()
        if (!repo.isUnlocked) return
        val now = SystemClock.elapsedRealtime()
        val delayMs = if (externalActivityUntil > now) {
            externalActivityUntil - now
        } else {
            repo.settings.autoLockSeconds * 1000L
        }
        if (delayMs <= 0) {
            repo.lock()
        } else {
            backgroundJob = scope.launch {
                delay(delayMs)
                repo.lock()
            }
        }
    }

    companion object {
        const val INACTIVITY_MS = 5 * 60_000L
        const val EXTERNAL_GRACE_MS = 3 * 60_000L
    }
}
