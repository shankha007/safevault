package com.safevault.app.data

import kotlin.math.min
import kotlin.math.pow

/**
 * Wrong-password lockout (D-010, D-034). Timed on the since-boot clock
 * (SystemClock.elapsedRealtime), which, unlike the date and time, can't be changed in
 * Settings. That clock restarts at boot, so a reboot restarts an unfinished lockout in full
 * instead of ending it.
 *
 * Pure Kotlin: the caller passes in the boot count and the clock, so this is unit-tested.
 */
object Lockout {
    /** First 4 mistakes are free (typos). After that: 30 s, 1 min, 2 min ... capped at 1 hour. */
    fun durationFor(failures: Int): Long {
        if (failures < 5) return 0
        return (min(30.0 * 2.0.pow(failures - 5), 3600.0) * 1000).toLong()
    }

    /** Records one more wrong password and starts the lockout it earns (possibly none). */
    fun fail(meta: VaultMeta, boot: Int, elapsed: Long): VaultMeta {
        val failures = meta.failedAttempts + 1
        val ms = durationFor(failures)
        return meta.copy(failedAttempts = failures, lockoutMs = ms, lockoutBoot = boot, lockoutEndsAt = elapsed + ms)
    }

    /**
     * Milliseconds until a password may be tried again, plus the meta to save when the
     * lockout's state changed (restarted after a reboot, or finished).
     */
    fun remaining(meta: VaultMeta, boot: Int, elapsed: Long): Pair<Long, VaultMeta?> {
        if (meta.lockoutMs <= 0) return 0L to null
        if (meta.lockoutBoot != boot) {
            return meta.lockoutMs to meta.copy(lockoutBoot = boot, lockoutEndsAt = elapsed + meta.lockoutMs)
        }
        // Capped at the lockout's own length in case the boot count isn't available (always 0).
        val left = (meta.lockoutEndsAt - elapsed).coerceIn(0, meta.lockoutMs)
        // Finished: forget it, so a later reboot doesn't bring it back. The failure count stays.
        return if (left == 0L) 0L to meta.copy(lockoutMs = 0, lockoutBoot = 0, lockoutEndsAt = 0) else left to null
    }

    fun clear(meta: VaultMeta) = meta.copy(failedAttempts = 0, lockoutMs = 0, lockoutBoot = 0, lockoutEndsAt = 0)
}
