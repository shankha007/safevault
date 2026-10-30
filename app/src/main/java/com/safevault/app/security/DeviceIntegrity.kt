package com.safevault.app.security

import java.io.File

/**
 * Best-effort root check, used only to warn the user. A determined attacker with root can
 * hide from any check like this, so the app never relies on it for protection.
 */
object DeviceIntegrity {
    private val suPaths = listOf(
        "/system/bin/su", "/system/xbin/su", "/sbin/su", "/system/su", "/su/bin/su",
        "/data/local/xbin/su", "/data/local/bin/su", "/system/sd/xbin/su", "/data/adb/magisk",
        "/system/app/Superuser.apk",
    )

    fun looksRooted(): Boolean =
        suPaths.any { runCatching { File(it).exists() }.getOrDefault(false) } ||
            (android.os.Build.TAGS?.contains("test-keys") == true)
}
