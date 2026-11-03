package com.safevault.app.data

import com.safevault.app.crypto.KdfParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LockoutTest {
    private val fresh = VaultMeta(kdf = KdfParams(), salt = "", wrappedKey = "")

    private fun failTimes(n: Int, boot: Int = 7, elapsed: Long = 1_000): VaultMeta =
        (1..n).fold(fresh) { m, _ -> Lockout.fail(m, boot, elapsed) }

    @Test
    fun firstFourMistakesAreFree() {
        val m = failTimes(4)
        assertEquals(4, m.failedAttempts)
        assertEquals(0L, Lockout.remaining(m, 7, 1_000).first)
    }

    @Test
    fun delaysDoubleAndCapAtAnHour() {
        assertEquals(30_000L, Lockout.durationFor(5))
        assertEquals(60_000L, Lockout.durationFor(6))
        assertEquals(3_600_000L, Lockout.durationFor(30))
    }

    @Test
    fun countsDownOnTheSinceBootClock() {
        val m = failTimes(5, elapsed = 10_000)
        assertEquals(30_000L, Lockout.remaining(m, 7, 10_000).first)
        assertEquals(20_000L, Lockout.remaining(m, 7, 20_000).first)
    }

    @Test
    fun finishedLockoutIsForgottenButFailuresStay() {
        val m = failTimes(5, elapsed = 10_000)
        val (left, updated) = Lockout.remaining(m, 7, 40_000)
        assertEquals(0L, left)
        assertNotNull(updated)
        assertEquals(0L, updated!!.lockoutMs)
        assertEquals(5, updated.failedAttempts)
        // So a later reboot doesn't bring it back.
        assertEquals(0L, Lockout.remaining(updated, 8, 0).first)
    }

    @Test
    fun rebootRestartsAnUnfinishedLockoutInFull() {
        val m = failTimes(6, boot = 7, elapsed = 10_000) // 60 s lockout
        val (left, updated) = Lockout.remaining(m, 8, 2_000) // rebooted 2 s ago
        assertEquals(60_000L, left)
        assertEquals(8, updated!!.lockoutBoot)
        assertEquals(62_000L, updated.lockoutEndsAt)
        assertNull(Lockout.remaining(updated, 8, 3_000).second)
    }

    @Test
    fun missingBootCountCantProduceAHugeLockout() {
        // Boot count always 0: a reboot looks like the same boot, and the clock restarted near 0.
        val m = failTimes(5, boot = 0, elapsed = 900_000_000)
        assertEquals(30_000L, Lockout.remaining(m, 0, 5_000).first)
    }

    @Test
    fun successClearsEverything() {
        val m = Lockout.clear(failTimes(7))
        assertEquals(0, m.failedAttempts)
        assertEquals(0L, m.lockoutMs)
    }

    @Test
    fun oldMetaWithWallClockLockoutStillLoads() {
        val json = """{"formatVersion":1,"kdf":{"memoryKiB":47104,"iterations":2,"parallelism":1},
            "salt":"AAAAAAAAAAAAAAAAAAAAAA==","wrappedKey":"AA==","failedAttempts":3,"lockoutUntil":99}"""
        val m = VaultCodec.json.decodeFromString(VaultMeta.serializer(), json)
        assertEquals(3, m.failedAttempts)
        assertEquals(0L, m.lockoutMs)
    }
}
