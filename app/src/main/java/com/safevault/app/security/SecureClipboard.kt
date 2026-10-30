package com.safevault.app.security

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Copies secrets to the clipboard, marks them as sensitive (Android 13+ hides the preview
 * and keeps them out of clipboard history on many keyboards) and clears them after a delay.
 */
class SecureClipboard(context: Context, private val scope: CoroutineScope) {
    private val cm = context.getSystemService(ClipboardManager::class.java)
    private var clearJob: Job? = null

    fun copy(label: String, text: String, clearAfterSeconds: Int) {
        val clip = ClipData.newPlainText(label, text)
        clip.description.extras = PersistableBundle().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            } else {
                putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
        }
        cm.setPrimaryClip(clip)
        val copied = text.hashCode()
        clearJob?.cancel()
        clearJob = scope.launch {
            delay(clearAfterSeconds * 1000L)
            clearIfStillOurs(copied)
        }
    }

    private fun clearIfStillOurs(hash: Int) {
        // In the background Android 10+ won't let us read the clipboard (returns null); in
        // that case clear anyway, because we can't tell and leaving a password is worse.
        val current = runCatching { cm.primaryClip?.getItemAt(0)?.text?.toString() }.getOrNull()
        if (current != null && current.hashCode() != hash) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { cm.clearPrimaryClip() }
        } else {
            runCatching { cm.setPrimaryClip(ClipData.newPlainText("", "")) }
        }
    }
}
